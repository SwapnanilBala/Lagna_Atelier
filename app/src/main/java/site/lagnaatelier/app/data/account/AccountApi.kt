package site.lagnaatelier.app.data.account

import org.json.JSONObject
import site.lagnaatelier.app.data.LagnaApi

import java.time.Instant

/*
 * The account routes on lagnaatelier.site, as the app calls them:
 *
 *   POST   /api/auth/google/android/nonce  a one-use nonce for Google's request
 *   POST   /api/auth/google/android        { idToken, nonce } -> session token
 *   GET    /api/auth/session               who the session belongs to
 *   POST   /api/auth/signout               end the session on the server
 *   GET    /api/sync/charts                the account's charts
 *   POST   /api/sync/charts                keep a chart on the account (with consent)
 *   DELETE /api/sync/charts?chartId=       delete a chart's person from the account
 *
 * The session travels as LagnaApi's bearer header; nothing here puts a token
 * in a URL or a log line.
 */
class AccountApi(private val api: LagnaApi) {

    /** An answer from the site that is not the shape it should be. Not a network fault. */
    class BadAnswer(message: String) : Exception(message)

    /** Who is signed in, as the server reports it. */
    data class User(val email: String, val displayName: String?)

    data class Session(val token: String, val expiresAtMillis: Long, val user: User)

    /** A chart as GET /api/sync/charts lists it. */
    data class RemoteChart(
        val chartId: String,
        val queryString: String,
        val ascendantSign: String?,
        val savedAtMillis: Long?,
    )

    suspend fun nonce(): String = parseNonce(api.postJson(NONCE_PATH, "{}"))

    suspend fun exchange(idToken: String, nonce: String): Session =
        parseSession(api.postJson(SIGN_IN_PATH, JSONObject().put("idToken", idToken).put("nonce", nonce).toString()))

    /** The signed-in user, or null when the server no longer knows the session. */
    suspend fun currentUser(): User? = parseUser(api.getJson(SESSION_PATH))

    suspend fun signOut() {
        api.postJson(SIGN_OUT_PATH, "{}")
    }

    suspend fun listCharts(): List<RemoteChart> = parseCharts(api.getJson(CHARTS_PATH))

    /** Keeps [queryString]'s chart on the account; returns its id there. */
    suspend fun upload(queryString: String, ascendantSign: String?, moonSign: String?, consentPrompt: String): String =
        parseUpload(api.postJson(CHARTS_PATH, uploadBody(queryString, ascendantSign, moonSign, consentPrompt)))

    suspend fun delete(chartId: String) {
        require(chartId.matches(UUID)) { "Not a chart id" }
        api.deleteJson(CHARTS_PATH, mapOf("chartId" to chartId))
    }

    companion object {
        const val NONCE_PATH = "/api/auth/google/android/nonce"
        const val SIGN_IN_PATH = "/api/auth/google/android"
        const val SESSION_PATH = "/api/auth/session"
        const val SIGN_OUT_PATH = "/api/auth/signout"
        const val CHARTS_PATH = "/api/sync/charts"

        /** GET /api/sync/charts lists at most this many; a full page may not be the whole account. */
        const val LIST_LIMIT = 50

        private val UUID = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
        private val NONCE = Regex("""[A-Za-z0-9_-]{43}""")
        private val TOKEN = Regex("""[A-Za-z0-9_-]{20,128}""")

        fun parseNonce(body: String): String =
            (JSONObject(body).opt("nonce") as? String)?.takeIf(NONCE::matches)
                ?: throw BadAnswer("No usable nonce")

        fun parseSession(body: String): Session {
            val json = JSONObject(body)
            val token = (json.opt("token") as? String)?.takeIf(TOKEN::matches) ?: throw BadAnswer("No usable session")
            val expires = (json.opt("expiresAt") as? String)
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: throw BadAnswer("No session expiry")
            val user = json.optJSONObject("user")?.let(::userFrom) ?: throw BadAnswer("No user")
            return Session(token, expires, user)
        }

        fun parseUser(body: String): User? = JSONObject(body).optJSONObject("user")?.let(::userFrom)

        private fun userFrom(json: JSONObject): User? {
            val email = (json.opt("email") as? String)?.takeIf { it.isNotBlank() } ?: return null
            val name = (json.opt("displayName") as? String)?.trim()?.takeIf { it.isNotEmpty() }
            return User(email.take(320), name?.take(120))
        }

        fun parseCharts(body: String): List<RemoteChart> {
            val array = JSONObject(body).optJSONArray("charts") ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val chart = array.opt(index) as? JSONObject ?: return@mapNotNull null
                val id = (chart.opt("chartId") as? String)?.takeIf(UUID::matches) ?: return@mapNotNull null
                val query = (chart.opt("queryString") as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                RemoteChart(
                    chartId = id.lowercase(),
                    queryString = query,
                    ascendantSign = chart.opt("ascendantSign") as? String,
                    savedAtMillis = (chart.opt("savedAt") as? String)
                        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                )
            }
        }

        fun parseUpload(body: String): String =
            (JSONObject(body).opt("chartId") as? String)?.takeIf(UUID::matches)?.lowercase()
                ?: throw BadAnswer("No chart id")

        /** The site wants the words the reader agreed to, kept as the consent's evidence. */
        fun uploadBody(queryString: String, ascendantSign: String?, moonSign: String?, consentPrompt: String): String =
            JSONObject().apply {
                put("queryString", queryString)
                ascendantSign?.let { put("ascendantSign", it.take(20)) }
                moonSign?.let { put("moonSign", it.take(20)) }
                put("consent", JSONObject().apply {
                    put("granted", true)
                    put("prompt", consentPrompt.take(500))
                    /* The site's vocabulary: intake, nudge or settings. The app asks
                       from the Account tab, which is its settings. */
                    put("captureSource", "settings")
                })
            }.toString()
    }
}
