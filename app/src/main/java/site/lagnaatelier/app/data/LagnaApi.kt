package site.lagnaatelier.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
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

    /**
     * A failed call. [message] is the site's own English, kept for logs and
     * never put on screen: the app words every failure itself, from [problem].
     * [code] is the site's error code (RATE_LIMITED, VALIDATION_FAILED...) and
     * [scope] the allowance a 429 refused under (error.details.scope).
     */
    class ApiException(
        message: String,
        val status: Int? = null,
        val code: String? = null,
        val scope: String? = null,
    ) : IOException(message) {
        val problem: ApiProblem get() = ApiProblem.of(status, scope)
    }

    /** A body larger than the app will hold in memory; treated as a failed call. */
    class ResponseTooLarge(limit: Int) : IOException("Response over $limit bytes")

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
        return parseGeocode(get("/api/geocode", params), suggestion, date, time ?: "12:00")
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
     * Every response is read through a cap: the routes answer in tens or
     * hundreds of kilobytes, so anything past maxBytes is a fault (or a
     * hostile network), and is refused before it can fill the heap.
     */

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
        maxBytes: Int = MAX_RESPONSE_BYTES,
    ): String = withContext(Dispatchers.IO) {
        val query = params.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        val url = if (query.isEmpty()) "$baseUrl$path" else "$baseUrl$path?$query"
        request(URL(url).openConnection() as HttpURLConnection, readTimeoutMs, body = null, maxBytes)
    }

    /**
     * POST [jsonBody] to [path]. The long-running routes (palm reading, the
     * report's prose) need a [readTimeoutMs] of minutes, not seconds.
     */
    suspend fun postJson(
        path: String,
        jsonBody: String,
        readTimeoutMs: Int = 30_000,
        maxBytes: Int = MAX_RESPONSE_BYTES,
    ): String = withContext(Dispatchers.IO) {
        request(URL("$baseUrl$path").openConnection() as HttpURLConnection, readTimeoutMs, body = jsonBody, maxBytes)
    }

    private fun request(connection: HttpURLConnection, readTimeoutMs: Int, body: String?, maxBytes: Int): String {
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
            if (status !in 200..299) {
                /* An error body is a few hundred bytes; one past the small cap is not read at all. */
                val text = runCatching { stream?.let { readCapped(it, MAX_ERROR_BYTES) } }.getOrNull().orEmpty()
                throw apiException(status, text)
            }
            return stream?.let { readCapped(it, maxBytes) }.orEmpty()
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
        /** What any one response may be: the chart is about 120 KB. */
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_ERROR_BYTES = 64 * 1024

        /** The largest offset accepted from anywhere: the world's zones lie within -12 h and +14 h. */
        const val MAX_OFFSET_MINUTES = 16 * 60

        /** Reads [stream] as UTF-8, refusing it once it passes [maxBytes]. Closes the stream. */
        fun readCapped(stream: InputStream, maxBytes: Int): String = stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (out.size() + read > maxBytes) throw ResponseTooLarge(maxBytes)
                out.write(buffer, 0, read)
            }
            String(out.toByteArray(), Charsets.UTF_8)
        }

        /** [stream], failing with [ResponseTooLarge] once more than [maxBytes] have been read from it. */
        fun capped(stream: InputStream, maxBytes: Int): InputStream = object : java.io.FilterInputStream(stream) {
            private var count = 0L

            private fun counted(n: Int): Int {
                if (n > 0) {
                    count += n
                    if (count > maxBytes) throw ResponseTooLarge(maxBytes)
                }
                return n
            }

            override fun read(): Int {
                val value = super.read()
                if (value >= 0) counted(1)
                return value
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int = counted(super.read(b, off, len))
        }

        /** The failed call for a non-2xx [status] and its [body], with the site's code and scope kept. */
        fun apiException(status: Int, body: String): ApiException {
            val error = runCatching { JSONObject(body) }.getOrNull()?.optJSONObject("error")
            val code = (error?.opt("code") as? String)?.takeIf { it.isNotBlank() }
            val scope = (error?.optJSONObject("details")?.opt("scope") as? String)?.takeIf { it.isNotBlank() }
            return ApiException(errorMessage(status, body), status, code, scope)
        }

        /*
         * A place from the geocode route's answer, or null when it was not
         * found. The route's offset is used when it is one a zone could keep;
         * a missing or impossible one is worked out from the time-zone id for
         * the birth date and time. With neither, the place is refused (an
         * exception, so the form says the search failed) rather than put at
         * UTC without a word.
         */
        fun parseGeocode(body: String, suggestion: PlaceSuggestion, date: LocalDate?, time: String): Place? {
            val json = JSONObject(body)
            if (!json.optBoolean("found")) return null
            val latitude = json.getDouble("lat")
            val longitude = json.getDouble("lon")
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) throw IOException("Coordinates out of range")
            val zone = (json.opt("timeZoneId") as? String).orEmpty()
            val given = (json.opt("timezoneOffsetMinutes") as? Number)?.toDouble()
                ?.takeIf { it.isFinite() && it == Math.floor(it) && kotlin.math.abs(it) <= MAX_OFFSET_MINUTES }
                ?.toInt()
            val offset = given
                ?: PlaceOffsets.offsetMinutes(zone, date, time)
                ?: throw IOException("No usable offset")
            return Place(
                city = suggestion.name,
                state = suggestion.state,
                country = suggestion.country,
                latitude = latitude,
                longitude = longitude,
                timeZoneId = zone,
                timezoneOffsetMinutes = offset,
            )
        }

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
