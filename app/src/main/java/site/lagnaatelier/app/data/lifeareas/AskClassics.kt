package site.lagnaatelier.app.data.lifeareas

import androidx.annotation.StringRes
import org.json.JSONObject
import site.lagnaatelier.app.R

/*
 * "Ask the classics" (GET and POST /api/chart/ask-classics, the life-areas
 * page's AskClassicsPanel on the web):
 *
 *   GET without `question`  which ready-made questions the books can answer for this chart (no model)
 *   GET with `question`     the answer to one of them; only the id is sent
 *   POST {text, language}   a question the reader typed, screened by the site before it is answered
 *
 * Ids and limits from lib/knowledge/ask-questions.ts.
 */

enum class AskQuestion(val id: String, @param:StringRes @get:StringRes val label: Int) {
    CareerYear("career_year", R.string.more_ask_q_career_year),
    LoveYear("love_year", R.string.more_ask_q_love_year),
    MoneyYear("money_year", R.string.more_ask_q_money_year),
    Health("health", R.string.more_ask_q_health),
    Marriage("marriage", R.string.more_ask_q_marriage),
    Strengths("strengths", R.string.more_ask_q_strengths),
    Children("children", R.string.more_ask_q_children),
    Standing("standing", R.string.more_ask_q_standing);

    companion object {
        fun of(id: String?): AskQuestion? = entries.firstOrNull { it.id == id }
    }
}

/** Why the site's screen would not let a typed question through (ASK_REFUSALS). */
enum class AskRefusal(val apiValue: String, @param:StringRes @get:StringRes val message: Int) {
    Instructions("instructions", R.string.more_ask_refused_instructions),
    ForbiddenTopic("forbidden_topic", R.string.more_ask_refused_forbidden),
    NotAboutChart("not_about_chart", R.string.more_ask_refused_not_chart);

    companion object {
        /* A verdict this app does not know yet is still a refusal, worded as the widest one. */
        fun of(value: String): AskRefusal = entries.firstOrNull { it.apiValue == value } ?: NotAboutChart
    }
}

/** Where one question's answer stands. */
sealed interface AskAnswer {
    data object Pending : AskAnswer
    data class Ready(val reading: ClassicalReading) : AskAnswer

    /** The books have nothing on this question for this chart: an answer, not a failure. */
    data object Empty : AskAnswer
    data class Refused(val reason: AskRefusal) : AskAnswer

    /** Not answered; [message] is the app's own wording for why (the day's allowance, offline...). */
    data class Failed(@param:StringRes @get:StringRes val message: Int) : AskAnswer
}

object AskClassicsParser {
    /** The longest question a reader may type, and the shortest that counts as one. */
    const val MAX_LENGTH = 200
    const val MIN_LENGTH = 3

    /**
     * The words as the site will read them (screenInput, less its Unicode
     * clean-up): whitespace collapsed, angle brackets dropped, cut to the limit.
     */
    fun clean(raw: String): String =
        raw.replace('<', ' ').replace('>', ' ').replace(WHITESPACE, " ").trim().take(MAX_LENGTH)

    fun canAsk(raw: String): Boolean = clean(raw).length >= MIN_LENGTH

    /** {available: [ids]}, in the app's order, unknown ids dropped. */
    fun parseAvailable(json: String): List<AskQuestion> {
        val array = JSONObject(json).optJSONArray("available") ?: return emptyList()
        val ids = (0 until array.length()).mapNotNull { array.opt(it) as? String }.toSet()
        return AskQuestion.entries.filter { it.id in ids }
    }

    /** A ready-made question's answer ({question, reading, cached}) or a typed one's ({refused, reading, cached}). */
    fun parseAnswer(json: String): AskAnswer {
        val root = JSONObject(json)
        (root.opt("refused") as? String)?.takeIf { it.isNotBlank() }?.let { return AskAnswer.Refused(AskRefusal.of(it)) }
        val reading = LifeAreasParser.reading(root.optJSONObject("reading")) ?: return AskAnswer.Empty
        return AskAnswer.Ready(reading)
    }

    fun typedBody(text: String, language: String = "en"): String =
        JSONObject().put("text", clean(text)).put("language", language).toString()

    private val WHITESPACE = Regex("\\s+")
}
