package site.lagnaatelier.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/*
 * The three web routes the app needs, called the way the website calls them:
 *
 *   GET /api/suggest  place names as the reader types (60 a minute)
 *   GET /api/geocode  coordinates, time zone and UTC offset for a place (20 a minute)
 *   GET /api/chart    the whole chart (30 a minute)
 *
 * Plain HttpURLConnection and the platform's org.json, so the app carries no
 * networking library. None of these routes needs a cookie or a header.
 */
class LagnaApi(private val baseUrl: String = BASE_URL) {

    data class PlaceSuggestion(val name: String, val displayName: String, val state: String, val country: String)

    /** A failed call, with the message the site gave where it gave one. */
    class ApiException(message: String, val status: Int? = null) : IOException(message)

    suspend fun suggest(query: String): List<PlaceSuggestion> {
        if (query.trim().length < 2) return emptyList()
        val body = get("/api/suggest", mapOf("q" to query.trim().take(200)))
        /* An error comes back as an object, and the web client treats it as no results. */
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }.map {
            PlaceSuggestion(
                name = it.getString("name"),
                displayName = it.optString("displayName", it.getString("name")),
                state = it.optString("state"),
                country = it.optString("country"),
            )
        }
    }

    /** Places a suggestion on the map. Null when the site cannot find it. */
    suspend fun geocode(suggestion: PlaceSuggestion, date: LocalDate?, time: String?): Place? {
        val params = linkedMapOf(
            "city" to suggestion.name,
            "state" to suggestion.state,
            "country" to suggestion.country,
        )
        if (date != null) params["birthDate"] = date.toString()
        params["birthTime"] = time ?: "12:00"
        val json = JSONObject(get("/api/geocode", params))
        if (!json.optBoolean("found")) return null
        return Place(
            city = suggestion.name,
            state = suggestion.state,
            country = suggestion.country,
            latitude = json.getDouble("lat"),
            longitude = json.getDouble("lon"),
            timeZoneId = json.optString("timeZoneId"),
            timezoneOffsetMinutes = if (json.isNull("timezoneOffsetMinutes")) 0 else json.getInt("timezoneOffsetMinutes"),
        )
    }

    suspend fun chart(details: BirthDetails): ChartSummary =
        ChartParser.parse(get("/api/chart", details.chartQuery()), details)

    /*
     * The Moon and Sun right now. The chart route casts for any moment, so this
     * asks for one at 0°, 0° in UTC: where in the sky the Moon is depends only
     * on the instant, and that point keeps the server's time-zone lookup at UTC.
     * The card is dated by the phone's own day, not UTC's.
     */
    suspend fun todaySky(now: ZonedDateTime = ZonedDateTime.now()): TodaySky {
        val utc = now.withZoneSameInstant(ZoneOffset.UTC)
        val json = get(
            "/api/chart",
            linkedMapOf(
                "name" to "Today",
                "birth_date" to utc.toLocalDate().toString(),
                "birth_time" to utc.format(DateTimeFormatter.ofPattern("HH:mm")),
                "timezone_offset_minutes" to "0",
                "latitude" to "0",
                "longitude" to "0",
                "time_zone_id" to "UTC",
            ),
        )
        return ChartParser.parseTodaySky(json, now.toLocalDate())
    }

    private suspend fun get(path: String, params: Map<String, String>): String = getJson(path, params)

    /*
     * The two raw calls every feature builds on, with the site's error
     * messages carried through. Feature code (timing, palm, report) adds its
     * own typed calls on top of these rather than editing this class.
     */

    /** GET [path] with query [params]; returns the body, or throws [ApiException]. */
    suspend fun getJson(
        path: String,
        params: Map<String, String> = emptyMap(),
        readTimeoutMs: Int = 30_000,
    ): String = withContext(Dispatchers.IO) {
        val query = params.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        val url = if (query.isEmpty()) "$baseUrl$path" else "$baseUrl$path?$query"
        request(URL(url).openConnection() as HttpURLConnection, readTimeoutMs, body = null)
    }

    /**
     * POST [jsonBody] to [path]. The long-running routes (palm reading, the
     * report's prose) need a [readTimeoutMs] of minutes, not seconds.
     */
    suspend fun postJson(path: String, jsonBody: String, readTimeoutMs: Int = 30_000): String =
        withContext(Dispatchers.IO) {
            request(URL("$baseUrl$path").openConnection() as HttpURLConnection, readTimeoutMs, body = jsonBody)
        }

    private fun request(connection: HttpURLConnection, readTimeoutMs: Int, body: String?): String {
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw ApiException(errorMessage(status, text), status)
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val BASE_URL = "https://lagnaatelier.site"

        /*
         * The site answers in two shapes: its own {error: {message}}, and the
         * per-minute limit's {detail}. A 429 with no body is the Vercel firewall.
         */
        internal fun errorMessage(status: Int, body: String): String {
            val json = runCatching { JSONObject(body) }.getOrNull()
            val message = json?.optJSONObject("error")?.optString("message")?.ifBlank { null }
                ?: json?.optString("error")?.ifBlank { null }
                ?: json?.optString("detail")?.ifBlank { null }
            return message ?: when (status) {
                429 -> "Too many requests just now. Try again in a minute."
                in 500..599 -> "The site could not answer just now. Try again shortly."
                else -> "Something went wrong (HTTP $status)."
            }
        }
    }
}
