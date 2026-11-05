package site.lagnaatelier.app.data.periods

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONObject
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.report.ReportApi
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/*
 * The routes behind the written period readings, the life chapters and
 * compatibility:
 *
 *   GET  /api/chart                  the natal facts the readings are built from
 *   POST /api/chart/current-period   one paragraph on the periods running now
 *   POST /api/chart/dasha-reading    one period's classical reading, on request
 *   POST /api/chart/life-shifts      the life chapters' readings, NDJSON ahead-first
 *   POST /api/compatibility          two charts read together (no language model)
 *
 * The three reading routes are written by a language model with a daily
 * allowance per caller, in the language they are asked for (ReadingLanguage):
 * current-period's is in the body its caller builds, the other two take it here; each is called only when the screen needs it, and a
 * 429 that names its scope comes back as ApiException, worded by ErrorText.
 */
class PeriodsApi(private val api: LagnaApi, private val baseUrl: String = LagnaApi.BASE_URL) {

    suspend fun natal(details: BirthDetails): NatalChart {
        val query = LinkedHashMap(details.chartQuery()).apply { remove("include_transits") }
        val body = api.getJson("/api/chart", query)
        return withContext(Dispatchers.Default) { NatalParser.parse(body) }
    }

    /** The paragraph, or null when the route answered without one. */
    suspend fun currentPeriod(requestBody: String): String? =
        CurrentPeriod.parseReading(api.postJson(CURRENT_PERIOD_PATH, requestBody, readTimeoutMs = 40_000))

    suspend fun dashaReading(details: BirthDetails, chain: PeriodChain, language: String = "en"): DashaReading.Answer {
        val body = api.postJson(
            "$DASHA_READING_PATH?${query(details)}",
            DashaReading.requestBody(chain, language),
            /* The route allows itself a minute, a retry included. */
            readTimeoutMs = 75_000,
        )
        return withContext(Dispatchers.Default) { DashaReading.parse(body) }
    }

    suspend fun compatibility(primary: BirthDetails, partner: BirthDetails): CompatibilityResult {
        val body = api.postJson(Compatibility.PATH, Compatibility.requestBody(primary, partner), readTimeoutMs = 60_000)
        return withContext(Dispatchers.Default) { Compatibility.parse(body) }
    }

    /**
     * The chapters' readings. Asked for as a stream, the route writes the
     * chapters ahead in a call of their own and sends them first; each batch is
     * handed to [onBatch] as it lands. A cache hit, or a set with nothing to
     * split, comes back as plain JSON, which is read the same way. Cancelling
     * the coroutine closes the connection.
     */
    suspend fun lifeShifts(
        details: BirthDetails,
        ids: List<String>,
        language: String = "en",
        onBatch: (Map<String, ClassicalReading>) -> Unit,
    ): Map<String, ClassicalReading> = withContext(Dispatchers.IO) {
        val connection = URL("$baseUrl$LIFE_SHIFTS_PATH?${query(details)}").openConnection() as HttpURLConnection
        val closeOnCancel = coroutineContext.job.invokeOnCompletion { connection.disconnect() }
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 75_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "$STREAM_TYPE, application/json")
            connection.outputStream.use { it.write(LifeShifts.requestBody(ids, language).toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            if (status !in 200..299) {
                val text = runCatching {
                    connection.errorStream?.let { LagnaApi.readCapped(it, LagnaApi.MAX_ERROR_BYTES) }
                }.getOrNull().orEmpty()
                throw LagnaApi.apiException(status, text)
            }
            val streamed = connection.contentType.orEmpty().contains(STREAM_TYPE)
            val reader = LagnaApi.capped(connection.inputStream, LagnaApi.MAX_RESPONSE_BYTES).bufferedReader(Charsets.UTF_8)
            val readings = reader.use {
                if (streamed) readStream(it.lineSequence(), onBatch) else LifeShifts.parseReadings(JSONObject(it.readText()))
            }
            if (readings.isEmpty()) throw IOException("No life chapter readings")
            readings
        } finally {
            closeOnCancel.dispose()
            connection.disconnect()
        }
    }

    companion object {
        const val CURRENT_PERIOD_PATH = "/api/chart/current-period"
        const val DASHA_READING_PATH = "/api/chart/dasha-reading"
        const val LIFE_SHIFTS_PATH = "/api/chart/life-shifts"
        const val STREAM_TYPE = "application/x-ndjson"

        /* The reading routes read the page's camelCase birth details, as the story report does. */
        fun query(details: BirthDetails): String =
            ReportApi.storyReportQuery(details).entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }

        /** Every batch's readings, gathered; each handed on as it is read. Stops at `done` or `error`. */
        fun readStream(lines: Sequence<String>, onBatch: (Map<String, ClassicalReading>) -> Unit): Map<String, ClassicalReading> {
            val all = LinkedHashMap<String, ClassicalReading>()
            for (line in lines) {
                when (val parsed = LifeShifts.parseLine(line)) {
                    is LifeShifts.StreamLine.Readings -> if (parsed.readings.isNotEmpty()) {
                        all.putAll(parsed.readings)
                        onBatch(LinkedHashMap(all))
                    }
                    LifeShifts.StreamLine.Done, LifeShifts.StreamLine.Failed -> break
                    null -> Unit
                }
            }
            return all
        }
    }
}
