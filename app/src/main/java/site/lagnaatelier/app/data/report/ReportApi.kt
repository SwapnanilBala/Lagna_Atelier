package site.lagnaatelier.app.data.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONObject
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.TimeAccuracy
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/*
 * The report's two routes:
 *
 *   GET  /api/chart/story-report  the verified story (the full calculation, then the checks)
 *   POST /api/chart/story-prose   the written prose for it, streamed as NDJSON progress lines
 *
 * The prose call is a written model pass with a daily allowance per caller,
 * and it can take a minute or two, so its read timeout is minutes. It is
 * optional: every way it can fail comes back as [ProseResult.Unavailable] and
 * the PDF is made from the engine's own text.
 */
class ReportApi(private val api: LagnaApi, private val baseUrl: String = LagnaApi.BASE_URL) {

    suspend fun storyReport(details: BirthDetails): StoryReport =
        StoryParser.parseReport(
            api.getJson(STORY_REPORT_PATH, storyReportQuery(details), readTimeoutMs = 90_000, maxBytes = STORY_REPORT_MAX_BYTES),
        )

    /** Why the prose did not come, in the words the ready screen chooses between. */
    enum class ProseFailure { DailyLimit, TimedOut, Offline, Declined }

    sealed interface ProseResult {
        data class Written(val prose: StoryProse) : ProseResult
        data class Unavailable(val reason: ProseFailure, val message: String?) : ProseResult
    }

    /**
     * Asks for the written prose with progress lines (the web's Accept), and
     * reads either answer the route gives: the stream, or plain JSON for a
     * cache hit. Cancelling the coroutine closes the connection, so "Stop
     * writing" stops reading at once rather than at the next line.
     */
    suspend fun storyProse(story: PersonalStory, onProgress: (ProseProgress) -> Unit): ProseResult =
        withContext(Dispatchers.IO) {
            val connection = URL("$baseUrl$STORY_PROSE_PATH").openConnection() as HttpURLConnection
            val closeOnCancel = coroutineContext.job.invokeOnCompletion { connection.disconnect() }
            try {
                connection.connectTimeout = 15_000
                /* Per read: the planning stretch before the first word measured about 40 s. */
                connection.readTimeout = PROSE_READ_TIMEOUT_MS
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "${StoryProseCodec.STREAM_TYPE}, application/json")
                connection.outputStream.use { it.write(StoryProseCodec.requestBody(story).toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                if (status !in 200..299) {
                    val text = runCatching {
                        connection.errorStream?.let { LagnaApi.readCapped(it, LagnaApi.MAX_ERROR_BYTES) }
                    }.getOrNull().orEmpty()
                    return@withContext ProseResult.Unavailable(failureFor(status, text), LagnaApi.errorMessage(status, text))
                }
                val contentType = connection.contentType.orEmpty()
                val reader = LagnaApi.capped(connection.inputStream, PROSE_MAX_BYTES).bufferedReader(Charsets.UTF_8)
                if (!contentType.contains(StoryProseCodec.STREAM_TYPE)) {
                    val prose = StoryProseCodec.parsePlain(reader.use { it.readText() })
                    return@withContext prose?.let { ProseResult.Written(it) }
                        ?: ProseResult.Unavailable(ProseFailure.Declined, null)
                }
                reader.use {
                    readStream(it.lineSequence(), onProgress)
                }
            } catch (timeout: SocketTimeoutException) {
                coroutineContext.ensureActive()
                ProseResult.Unavailable(ProseFailure.TimedOut, timeout.message)
            } catch (io: IOException) {
                /* A disconnect from "Stop writing" lands here too; the caller is cancelled, so say so. */
                coroutineContext.ensureActive()
                ProseResult.Unavailable(ProseFailure.Offline, io.message)
            } finally {
                closeOnCancel.dispose()
                connection.disconnect()
            }
        }

    companion object {
        const val STORY_REPORT_PATH = "/api/chart/story-report"
        const val STORY_PROSE_PATH = "/api/chart/story-prose"
        const val PROSE_READ_TIMEOUT_MS = 300_000

        /* The story is about 30 KB and the prose stream less; these are ceilings, not estimates. */
        const val STORY_REPORT_MAX_BYTES = 8 * 1024 * 1024
        const val PROSE_MAX_BYTES = LagnaApi.MAX_RESPONSE_BYTES

        /*
         * The story route reads the page's own camelCase parameters
         * (lib/chart-params-url.ts readChartParams), not /api/chart's
         * snake_case. Name, date, time, offset, coordinates, country, state
         * and city are all required; a place with no state sends its country
         * there, which only labels the chart and changes no position.
         */
        fun storyReportQuery(details: BirthDetails): Map<String, String> {
            val place = details.place
            return linkedMapOf(
                "name" to details.name.trim(),
                "birthDate" to details.date.toString(),
                "birthTime" to details.time,
                "timezoneOffsetMinutes" to place.timezoneOffsetMinutes.toString(),
                "latitude" to place.latitude.toString(),
                "longitude" to place.longitude.toString(),
                "country" to place.country,
                "state" to place.state.ifBlank { place.country },
                "city" to place.city,
                "timeZoneId" to place.timeZoneId,
                "engineId" to details.engineId,
                "birthTimeAccuracy" to details.accuracy.apiValue,
                "birthTimeSource" to if (details.accuracy == TimeAccuracy.Exact) "exact" else "fallback",
                "birthTimeFallback" to (details.accuracy != TimeAccuracy.Exact).toString(),
            )
        }

        /*
         * The day's allowance is the route's own 429, which names its scope
         * (lib/llm-budget.ts). A 429 without one is the per-minute limit or
         * the firewall, which a later try gets past, so it is not called the day's.
         */
        fun failureFor(status: Int, body: String): ProseFailure {
            val details = runCatching { JSONObject(body) }.getOrNull()?.optJSONObject("error")?.optJSONObject("details")
            return if (status == 429 && details?.has("scope") == true) ProseFailure.DailyLimit else ProseFailure.Declined
        }

        /** The stream's lines to a result: progress handed on, then the prose, or why there is none. */
        fun readStream(lines: Sequence<String>, onProgress: (ProseProgress) -> Unit): ProseResult {
            for (line in lines) {
                when (val parsed = StoryProseCodec.parseLine(line)) {
                    is StoryProseCodec.StreamLine.Progress -> onProgress(parsed.progress)
                    is StoryProseCodec.StreamLine.Done -> return parsed.prose?.let { ProseResult.Written(it) }
                        ?: ProseResult.Unavailable(ProseFailure.Declined, null)
                    is StoryProseCodec.StreamLine.Error -> return ProseResult.Unavailable(ProseFailure.Declined, parsed.message)
                    null -> Unit
                }
            }
            /* The stream broke off before its last line. */
            return ProseResult.Unavailable(ProseFailure.Offline, null)
        }
    }
}
