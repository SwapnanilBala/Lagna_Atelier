package site.lagnaatelier.app.data.account

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.LagnaApi
import java.io.IOException

/*
 * Signing in and out, and the session in between (SIGN_IN.md 2.1-2.4).
 *
 *   sign in   nonce from the site -> Google's sheet -> id_token -> the site
 *             swaps it for a session token -> kept encrypted (SessionVault)
 *   signed in every call carries the token (LagnaApi.sessionToken)
 *   ends      sign out (server, then here), or any 401 on a call that
 *             carried the token, or the site no longer knowing the session
 *
 * Ending, by any of those, makes every saved chart this phone's only again
 * ([onSignedOut]); it deletes nothing.
 */
class AccountSession(
    private val api: AccountApi,
    private val vault: SessionVault,
    private val tokens: IdTokenSource,
    /** The web client id from the build; blank means sign-in is not set up in this build. */
    private val serverClientId: String,
    private val scope: CoroutineScope,
    private val onSignedIn: () -> Unit = {},
    private val onSignedOut: () -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    enum class Status { Checking, NotSetUp, SignedOut, SigningIn, SignedIn, SigningOut }

    var status by mutableStateOf(if (serverClientId.isBlank()) Status.NotSetUp else Status.Checking)
        private set

    /** Who is signed in, for the Account tab. */
    var user by mutableStateOf<AccountApi.User?>(null)
        private set

    /** Whether the reader agreed to keep this phone's charts on the account. */
    var syncCharts by mutableStateOf(false)
        private set

    /** A sentence for the sign-in card after something went wrong; null when nothing did. */
    @get:StringRes
    var message by mutableStateOf<Int?>(null)
        private set

    @Volatile
    private var session: StoredSession? = null

    /** The token LagnaApi sends, or null. Read on the I/O thread. */
    val token: String? get() = session?.token

    val signedIn: Boolean get() = session != null

    /** Reads the stored session. Called once, as the app starts. */
    fun load() {
        if (serverClientId.isBlank()) {
            /* A build without the client id cannot sign in, so nothing it holds should be sent either. */
            vault.clear()
            return
        }
        scope.launch {
            val stored = withContext(io) { vault.read() }
            if (stored != null) adopt(stored) else status = Status.SignedOut
        }
    }

    fun dismissMessage() {
        message = null
    }

    /** Opens Google's sheet over [activity] and signs in with what it returns. */
    fun signIn(activity: Context) = signInWith { clientId, nonce -> tokens.idToken(activity, clientId, nonce) }

    /* The flow, apart from where the id_token comes from, so the tests can run it without a phone. */
    internal fun signInWith(requestToken: suspend (serverClientId: String, nonce: String) -> IdTokenResult) {
        if (serverClientId.isBlank()) {
            message = R.string.accounts_not_set_up
            return
        }
        if (status == Status.SigningIn || status == Status.SignedIn) return
        status = Status.SigningIn
        message = null
        scope.launch {
            try {
                val nonce = api.nonce()
                when (val result = requestToken(serverClientId, nonce)) {
                    is IdTokenResult.Token -> {
                        val signed = api.exchange(result.idToken, nonce)
                        val stored = StoredSession(
                            token = signed.token,
                            expiresAtMillis = signed.expiresAtMillis,
                            email = signed.user.email,
                            displayName = signed.user.displayName,
                        )
                        withContext(io) { vault.write(stored) }
                        adopt(stored)
                        onSignedIn()
                    }
                    IdTokenResult.Cancelled -> status = Status.SignedOut
                    IdTokenResult.NoAccount -> fail(R.string.accounts_no_google_account)
                    IdTokenResult.Failed -> fail(R.string.accounts_signin_failed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LagnaApi.ApiException) {
                fail(messageFor(e))
            } catch (e: IOException) {
                fail(R.string.accounts_signin_offline)
            } catch (e: Exception) {
                /* An answer the app could not read. */
                fail(R.string.accounts_signin_failed)
            }
        }
    }

    /** Ends the session on the server, then here, then asks Google to forget the choice. */
    fun signOut(context: Context) = signOutWith { tokens.forget(context) }

    internal fun signOutWith(forgetChoice: suspend () -> Unit) {
        if (session == null) return
        status = Status.SigningOut
        scope.launch {
            try {
                api.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                /* Ended here regardless. The server row expires on its own, and
                   "sign out everywhere" on the website ends it at once. */
            }
            endLocally()
            forgetChoice()
        }
    }

    /** A call that carried the token was refused: the session ended on the server. */
    fun sessionRejected() {
        if (session == null) return
        scope.launch { endLocally() }
    }

    /**
     * Asks the site whether the session still stands: [Verification.Valid],
     * [Verification.SignedOut] (and then it is ended here), or
     * [Verification.Unreachable] when the site could not be asked.
     */
    suspend fun verify(): Verification {
        if (session == null) return Verification.SignedOut
        return try {
            val current = api.currentUser()
            if (current == null) {
                endLocally()
                Verification.SignedOut
            } else {
                user = current
                Verification.Valid
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Verification.Unreachable
        }
    }

    enum class Verification { Valid, SignedOut, Unreachable }

    /** The reader's answer to "keep this phone's charts on your account". */
    fun chooseSyncCharts(on: Boolean) {
        val current = session ?: return
        val next = current.copy(syncCharts = on)
        session = next
        syncCharts = on
        scope.launch { withContext(io) { vault.write(next) } }
        if (on) onSignedIn()
    }

    private fun adopt(stored: StoredSession) {
        session = stored
        user = AccountApi.User(stored.email, stored.displayName)
        syncCharts = stored.syncCharts
        status = Status.SignedIn
    }

    private suspend fun endLocally() {
        val had = session != null
        session = null
        user = null
        syncCharts = false
        status = Status.SignedOut
        withContext(io) { vault.clear() }
        if (had) onSignedOut()
    }

    private fun fail(@StringRes text: Int) {
        message = text
        status = Status.SignedOut
    }

    companion object {
        /** The site's coarse codes to the app's own words. */
        @StringRes
        fun messageFor(error: LagnaApi.ApiException): Int = when {
            error.status == 429 -> R.string.accounts_signin_busy
            error.code == "not_configured" -> R.string.accounts_not_set_up_site
            error.code == "email_unverified" -> R.string.accounts_email_unverified
            else -> R.string.accounts_signin_failed
        }
    }
}
