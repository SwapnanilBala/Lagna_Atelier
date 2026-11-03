package site.lagnaatelier.app.data.palm

import org.json.JSONArray
import org.json.JSONObject
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.LagnaApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToLong

/*
 * The two palm routes, called the way the web client calls them
 * (app/(desktop)/insights/components/palm-reading-panel.tsx and
 * palm-qa-panel.tsx):
 *
 *   POST /api/palm-reading      { image, mediaType, classicalMode?, reader?, jyotishContext? }
 *   POST /api/palm-reading/ask  { reading, jyotishContext?, classicalMode, history, question }
 *
 * Both run a model the site pays for, and the reading is capped at two a week.
 */

/** Whose hand it is (lib/palm-readings/reader.ts), and so which hand the tradition reads. */
enum class PalmReader(val apiValue: String) {
    Woman("woman"),
    Man("man"),
    Unspecified("unspecified");

    companion object {
        fun from(value: String?): PalmReader = entries.firstOrNull { it.apiValue == value } ?: Unspecified
    }
}

object PalmRequests {
    /** The body for POST /api/palm-reading. Optional fields are left out when they say nothing, as on the web. */
    fun readingBody(
        imageBase64: String,
        mediaType: String,
        classicalMode: Boolean,
        reader: PalmReader,
        jyotishContext: JSONObject?,
    ): String = JSONObject().apply {
        put("image", imageBase64)
        put("mediaType", mediaType)
        if (classicalMode) put("classicalMode", true)
        if (reader != PalmReader.Unspecified) put("reader", reader.apiValue)
        if (jyotishContext != null) put("jyotishContext", jyotishContext)
    }.toString()

    /*
     * The chart facts the reading is weighed against: the web's
     * buildJyotishContext (insights/advanced/advanced-content.tsx), from what
     * this app's chart carries. The web also sends each planet's house and
     * nakshatra; the app's chart has neither, so those are left out rather
     * than guessed, and the route reads whatever is there.
     */
    fun jyotishContext(chart: ChartSummary, now: Instant = Instant.now()): JSONObject = JSONObject().apply {
        val ascendantLongitude = chart.ascendant.ordinal * 30.0 + chart.ascendantDegree
        put("ascendant", JSONObject().apply {
            put("sign", chart.ascendant.label)
            put("degree", chart.ascendantDegree)
            put("nakshatra", nakshatraAt(ascendantLongitude))
        })
        put("moonSign", chart.moonSign.label)
        put("moonNakshatra", chart.nakshatra)
        chart.placements.firstOrNull { it.abbreviation == "Su" }?.let { put("sunSign", it.sign.label) }
        put("currentMahadasha", JSONObject().apply {
            put("lord", chart.mahadasha.lord)
            put("remaining_years", round(remaining(chart.mahadasha.end, now) / YEAR_MS, 100))
        })
        put("currentAntardasha", JSONObject().apply {
            put("lord", chart.antardasha.lord)
            put("remaining_months", round(remaining(chart.antardasha.end, now) / MONTH_MS, 10))
        })
    }

    private val NAKSHATRAS = listOf(
        "Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra",
        "Punarvasu", "Pushya", "Ashlesha", "Magha", "Purva Phalguni",
        "Uttara Phalguni", "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha",
        "Jyeshtha", "Moola", "Purva Ashadha", "Uttara Ashadha", "Shravana",
        "Dhanishta", "Shatabhisha", "Purva Bhadrapada", "Uttara Bhadrapada", "Revati",
    )

    /** The nakshatra at a sidereal longitude, as the web names them. */
    fun nakshatraAt(longitude: Double): String {
        val normalised = ((longitude % 360) + 360) % 360
        return NAKSHATRAS[(normalised / (360.0 / 27)).toInt().coerceIn(0, 26)]
    }

    private const val YEAR_MS = 365.25 * 24 * 3600 * 1000
    private const val MONTH_MS = 30.4375 * 24 * 3600 * 1000

    private fun remaining(end: LocalDate, now: Instant): Double =
        (end.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0).toDouble()

    private fun round(value: Double, per: Int): Double = (value * per).roundToLong().toDouble() / per
}

/* ── follow-up questions ── */

enum class QaRole(val apiValue: String) { User("user"), Assistant("assistant") }

/** One turn of the thread. [offTopic] marks the route's fixed refusal, shown as a hint. */
data class QaTurn(val role: QaRole, val content: String, val offTopic: Boolean = false)

data class QaAnswer(val answer: String, val offTopic: Boolean)

object PalmQa {
    /** Six questions, twelve turns: MAX_HISTORY_TURNS on the server, MAX_TURNS on the web. */
    const val MAX_TURNS = 12
    const val MAX_QUESTIONS = MAX_TURNS / 2
    const val MAX_QUESTION_CHARS = 500
    private const val MAX_ANSWER_CHARS = 4000

    /** The thread is full once six questions have been asked, as on the web. */
    fun atLimit(turns: List<QaTurn>): Boolean = turns.count { it.role == QaRole.User } * 2 >= MAX_TURNS

    fun questionsLeft(turns: List<QaTurn>): Int = (MAX_QUESTIONS - turns.count { it.role == QaRole.User }).coerceAtLeast(0)

    /**
     * The body for POST /api/palm-reading/ask. [history] is the thread as it
     * stood before [question], exactly as it was said: the server appends the
     * question itself, and it treats the replayed thread as the reader's
     * account, so nothing here rewrites it.
     */
    fun requestBody(
        reading: JSONObject,
        jyotishContext: JSONObject?,
        classicalMode: Boolean,
        history: List<QaTurn>,
        question: String,
    ): String = JSONObject().apply {
        put("reading", reading)
        if (jyotishContext != null) put("jyotishContext", jyotishContext)
        put("classicalMode", classicalMode)
        put("history", JSONArray(history.map { JSONObject().put("role", it.role.apiValue).put("content", it.content) }))
        put("question", question.trim().take(MAX_QUESTION_CHARS))
    }.toString()

    /** The route's { answer, offTopic }, or null when there is no answer in it. */
    fun parseAnswer(body: String): QaAnswer? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val answer = PalmReadingParser.text(json.opt("answer"), MAX_ANSWER_CHARS)
        if (answer.isEmpty()) return null
        return QaAnswer(answer, json.opt("offTopic") == true)
    }
}

class PalmApi(private val api: LagnaApi) {
    /** Reads a palm. Throws [LagnaApi.ApiException] or an IOException; null body means "not a reading". */
    suspend fun read(body: String): PalmReading? =
        PalmReadingParser.parse(api.postJson(READING_PATH, body, readTimeoutMs = READING_TIMEOUT_MS))

    suspend fun ask(body: String): QaAnswer? =
        PalmQa.parseAnswer(api.postJson(ASK_PATH, body, readTimeoutMs = ASK_TIMEOUT_MS))

    companion object {
        const val READING_PATH = "/api/palm-reading"
        const val ASK_PATH = "/api/palm-reading/ask"

        /* The route may run for up to 300 seconds; a reading usually takes about a minute. */
        const val READING_TIMEOUT_MS = 300_000
        const val ASK_TIMEOUT_MS = 60_000

        /**
         * True when [status] means the server got as far as spending the
         * reading: a success, or a failure after the model was called. The
         * refusals before it (bad input, the caps, not configured) do not count.
         */
        fun counted(status: Int?): Boolean = when (status) {
            null -> true
            400, 413, 429, 503 -> false
            else -> true
        }

        /*
         * The weekly refusal. LagnaApi carries the message, not the body's
         * details.scope, so it is told by the route's fixed English
         * ("You have had this week's 2 palm readings..."): the other 429s
         * are about today or signing in, and the per-minute limit has no
         * such words.
         */
        fun isWeeklyCap(status: Int?, message: String?): Boolean =
            status == 429 && message.orEmpty().contains("this week", ignoreCase = true)
    }
}
