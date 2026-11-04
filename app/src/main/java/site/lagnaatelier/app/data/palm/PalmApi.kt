package site.lagnaatelier.app.data.palm

import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.ApiProblem
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
 * Both run a model the site pays for, need an account (401 with
 * details.scope "sign_in" otherwise), and the reading is capped at two a
 * week. A photo the route's check turns away is a 422 VALIDATION_FAILED
 * with details.reason, and is not one of the week's two.
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

/** The photo check's reasons (error.details.reason on a 422), each with the app's own words. */
enum class PhotoRefusal(val apiValue: String, @param:StringRes @get:StringRes val message: Int) {
    NotAPalm("not_a_palm", R.string.palm_refused_not_a_palm),
    BackOfHand("back_of_hand", R.string.palm_refused_back_of_hand),
    Unclear("unclear", R.string.palm_refused_unclear),
}

/** A refused reading, sorted: each kind is shown differently on the Palm tab. */
sealed interface ReadingRefusal {
    /** 401: the sign-in card, in the camera's place. */
    data object SignIn : ReadingRefusal

    /** The week's two are spent: the date the next one opens. */
    data object Weekly : ReadingRefusal

    /** The photo check turned the photo away: what to change, and that it cost nothing. */
    data class Photo(val refusal: PhotoRefusal) : ReadingRefusal

    /** Anything else: the shared wording (ErrorText). */
    data object Other : ReadingRefusal
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
            400, 401, 413, 422, 429, 503 -> false
            else -> true
        }

        /** Signed out, or the session ended: the route reads palms for an account only. */
        fun needsSignIn(error: LagnaApi.ApiException): Boolean = error.status == 401

        /** What the Palm tab does with a refused reading; see [ReadingRefusal]. */
        fun readingRefusal(error: LagnaApi.ApiException): ReadingRefusal {
            if (needsSignIn(error)) return ReadingRefusal.SignIn
            if (isWeeklyCap(error)) return ReadingRefusal.Weekly
            return photoRefusal(error)?.let(ReadingRefusal::Photo) ?: ReadingRefusal.Other
        }

        /** Why the route's photo check turned the photo away, or null when it was some other refusal. */
        fun photoRefusal(error: LagnaApi.ApiException): PhotoRefusal? {
            if (error.status != 422 || error.code != "VALIDATION_FAILED") return null
            return PhotoRefusal.entries.firstOrNull { it.apiValue == error.reason }
        }

        /** The weekly refusal, told by the scope the route names (error.details.scope), not its English. */
        fun isWeeklyCap(error: LagnaApi.ApiException): Boolean = error.problem == ApiProblem.WeeklyLimit
    }
}
