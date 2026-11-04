package site.lagnaatelier.app.data.account

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

/** What asking Google for an id_token came to. */
sealed interface IdTokenResult {
    data class Token(val idToken: String) : IdTokenResult

    /** The reader closed the sheet. Said nothing about. */
    data object Cancelled : IdTokenResult

    /** No Google account on the phone, or none Google would offer. */
    data object NoAccount : IdTokenResult

    /** Anything else: Play services missing or out of date, a network fault, a refused client. */
    data object Failed : IdTokenResult
}

/** Asks Google for an id_token; a fake in the tests. */
interface IdTokenSource {
    suspend fun idToken(activity: Context, serverClientId: String, nonce: String): IdTokenResult

    /** Forget the chosen account, so the next sign-in asks again rather than picking it. */
    suspend fun forget(context: Context)
}

/*
 * Google's "Sign in with Google" sheet through Credential Manager
 * (SIGN_IN.md 2.2). The token is minted for the WEB client, the site's
 * AUTH_GOOGLE_ID, passed as serverClientId; Google records this app's
 * Android client as its azp, and the server checks both. The nonce comes
 * from the server and is signed into the token, so it cannot be replayed.
 *
 * Needs an Activity context for the sheet. Never logs either token.
 */
class GoogleIdTokens : IdTokenSource {
    override suspend fun idToken(activity: Context, serverClientId: String, nonce: String): IdTokenResult {
        val option = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                IdTokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                /* A passkey or password offered instead: not what was asked for. */
                IdTokenResult.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            IdTokenResult.Cancelled
        } catch (e: NoCredentialException) {
            IdTokenResult.NoAccount
        } catch (e: GetCredentialException) {
            IdTokenResult.Failed
        } catch (e: GoogleIdTokenParsingException) {
            IdTokenResult.Failed
        }
    }

    override suspend fun forget(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Signing out still happens; Google may just offer the same account first next time.
        }
    }
}
