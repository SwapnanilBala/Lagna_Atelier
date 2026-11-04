package site.lagnaatelier.app.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.account.AccountApi
import site.lagnaatelier.app.data.account.AccountSession
import site.lagnaatelier.app.data.account.IdTokenResult
import site.lagnaatelier.app.data.account.IdTokenSource
import site.lagnaatelier.app.data.account.SessionVault

/*
 * Sign-in, the bearer header, and every way a session ends, against a fake
 * site on this machine. Google's sheet is replaced by a lambda that hands
 * back an id_token (or a refusal); everything after it is the real code.
 */
class AccountSessionTest {
    private val site = FakeSite()
    private val api = LagnaApi(site.baseUrl)
    private val storage = MemoryVault()
    private val vault = SessionVault(storage, XorCipher())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var signedOutCalls = 0
    private var signedInCalls = 0

    private val session = AccountSession(
        api = AccountApi(api),
        vault = vault,
        tokens = NoTokens,
        serverClientId = "web-client.apps.googleusercontent.com",
        scope = scope,
        onSignedIn = { signedInCalls++ },
        onSignedOut = { signedOutCalls++ },
    ).also { session ->
        api.sessionToken = { session.token }
        api.onSessionRejected = { session.sessionRejected() }
    }

    private val nonce = "n".repeat(43)
    private val sessionToken = "c2Vzc2lvbi10b2tlbi1mb3ItdGhlLWFwcC0xMjM0NTY3"

    @After
    fun close() = site.close()

    private fun settle() = runBlocking { scope.coroutineContext.job.children.forEach { it.join() } }

    private fun siteSignsIn() {
        site.on("POST /api/auth/google/android/nonce", body = """{"nonce":"$nonce"}""")
        site.on(
            "POST /api/auth/google/android",
            body = """{"token":"$sessionToken","expiresAt":"2099-01-01T00:00:00.000Z",
                "user":{"email":"someone@example.test","displayName":"Someone"}}""",
        )
    }

    private fun signIn(result: IdTokenResult = IdTokenResult.Token("header.payload.signature")) {
        session.signInWith { _, _ -> result }
        settle()
    }

    @Test
    fun signsInExchangesTheTokenAndKeepsTheSessionEncrypted() {
        siteSignsIn()
        var askedWith: Pair<String, String>? = null
        session.signInWith { clientId, sentNonce ->
            askedWith = clientId to sentNonce
            IdTokenResult.Token("header.payload.signature")
        }
        settle()

        assertEquals(AccountSession.Status.SignedIn, session.status)
        assertEquals("web-client.apps.googleusercontent.com" to nonce, askedWith)
        assertEquals("someone@example.test", session.user?.email)
        assertEquals(1, signedInCalls)

        val exchange = site.callsTo("POST /api/auth/google/android").single()
        assertTrue(exchange.body.contains("\"idToken\":\"header.payload.signature\""))
        assertTrue(exchange.body.contains("\"nonce\":\"$nonce\""))

        /* Kept, but not as plain text. */
        val sealed = storage.value!!
        assertFalse(sealed.contains(sessionToken))
        assertEquals(sessionToken, vault.read()?.token)
    }

    @Test
    fun sendsTheSessionAsABearerHeaderOnceSignedIn() = runBlocking {
        siteSignsIn()
        signIn()
        site.on("GET /api/sync/charts", body = """{"charts":[],"stored":0}""")

        AccountApi(api).listCharts()

        assertEquals("Bearer $sessionToken", site.callsTo("GET /api/sync/charts").single().authorization)
        /* Nothing before sign-in carried one. */
        assertNull(site.callsTo("POST /api/auth/google/android/nonce").single().authorization)
    }

    @Test
    fun aCancelledSheetSaysNothingAndStaysSignedOut() {
        siteSignsIn()
        signIn(IdTokenResult.Cancelled)

        assertEquals(AccountSession.Status.SignedOut, session.status)
        assertNull(session.message)
        assertTrue(site.callsTo("POST /api/auth/google/android").isEmpty())
    }

    @Test
    fun wordsEachFailureInTheAppsVoice() {
        siteSignsIn()
        signIn(IdTokenResult.NoAccount)
        assertEquals(R.string.accounts_no_google_account, session.message)

        site.on("POST /api/auth/google/android", 401, """{"error":{"code":"invalid_token","message":"x"}}""")
        signIn()
        assertEquals(R.string.accounts_signin_failed, session.message)
        assertNull(session.token)

        site.on("POST /api/auth/google/android", 403, """{"error":{"code":"email_unverified","message":"x"}}""")
        signIn()
        assertEquals(R.string.accounts_email_unverified, session.message)

        site.on("POST /api/auth/google/android/nonce", 503, """{"error":{"code":"not_configured","message":"x"}}""")
        signIn()
        assertEquals(R.string.accounts_not_set_up_site, session.message)

        site.on("POST /api/auth/google/android/nonce", 429, """{"detail":"Rate limit exceeded."}""")
        signIn()
        assertEquals(R.string.accounts_signin_busy, session.message)
        assertEquals(AccountSession.Status.SignedOut, session.status)
    }

    @Test
    fun aSessionTokenOfTheWrongShapeIsNotKept() {
        siteSignsIn()
        site.on("POST /api/auth/google/android", body = """{"token":"short","expiresAt":"2099-01-01T00:00:00Z","user":{"email":"a@b.c"}}""")
        signIn()

        assertNull(session.token)
        assertNull(storage.value)
        assertEquals(R.string.accounts_signin_failed, session.message)
    }

    @Test
    fun aRefusedCallEndsTheSessionHereAndForgetsTheAccountCharts() = runBlocking {
        siteSignsIn()
        signIn()
        site.on("GET /api/sync/charts", 401, """{"error":{"code":"UNAUTHORIZED","message":"x"}}""")

        runCatching { AccountApi(api).listCharts() }
        settle()

        assertEquals(AccountSession.Status.SignedOut, session.status)
        assertNull(session.token)
        assertNull(storage.value)
        assertEquals(1, signedOutCalls)
    }

    @Test
    fun signOutTellsTheSiteWithTheHeaderThenForgetsEverything() {
        siteSignsIn()
        signIn()
        site.on("POST /api/auth/signout", body = """{"signedOut":true}""")
        var forgot = false

        session.signOutWith { forgot = true }
        settle()

        assertEquals("Bearer $sessionToken", site.callsTo("POST /api/auth/signout").single().authorization)
        assertEquals(AccountSession.Status.SignedOut, session.status)
        assertNull(storage.value)
        assertTrue(forgot)
        assertEquals(1, signedOutCalls)
    }

    @Test
    fun signOutEndsHereEvenWhenTheSiteCannotBeReached() {
        siteSignsIn()
        signIn()
        site.on("POST /api/auth/signout", 503, """{"detail":"down"}""")

        session.signOutWith {}
        settle()

        assertEquals(AccountSession.Status.SignedOut, session.status)
        assertNull(session.token)
    }

    @Test
    fun verifyEndsASessionTheSiteNoLongerKnows() = runBlocking {
        siteSignsIn()
        signIn()

        site.on("GET /api/auth/session", body = """{"user":{"email":"someone@example.test","displayName":"Someone"}}""")
        assertEquals(AccountSession.Verification.Valid, session.verify())

        site.on("GET /api/auth/session", 503, """{"detail":"Could not reach the account store."}""")
        assertEquals(AccountSession.Verification.Unreachable, session.verify())
        assertEquals(AccountSession.Status.SignedIn, session.status)

        site.on("GET /api/auth/session", body = """{"user":null}""")
        assertEquals(AccountSession.Verification.SignedOut, session.verify())
        assertEquals(AccountSession.Status.SignedOut, session.status)
        assertNull(storage.value)
    }

    @Test
    fun aBuildWithoutTheClientIdIsNotSetUpAndSendsNothing() {
        storage.value = "left over from another build"
        val unconfigured = AccountSession(AccountApi(api), vault, NoTokens, serverClientId = "", scope = scope)

        unconfigured.load()
        unconfigured.signInWith { _, _ -> error("Google must not be asked") }
        settle()

        assertEquals(AccountSession.Status.NotSetUp, unconfigured.status)
        assertEquals(R.string.accounts_not_set_up, unconfigured.message)
        assertNull(storage.value)
        assertTrue(site.calls.isEmpty())
    }

    @Test
    fun theSyncAnswerIsKeptWithTheSession() {
        siteSignsIn()
        signIn()
        assertFalse(session.syncCharts)

        session.chooseSyncCharts(true)
        settle()

        assertTrue(session.syncCharts)
        assertTrue(vault.read()!!.syncCharts)
        assertEquals(2, signedInCalls) // turning it on asks for a sync
    }

    private object NoTokens : IdTokenSource {
        override suspend fun idToken(activity: android.content.Context, serverClientId: String, nonce: String) =
            IdTokenResult.Failed

        override suspend fun forget(context: android.content.Context) = Unit
    }
}
