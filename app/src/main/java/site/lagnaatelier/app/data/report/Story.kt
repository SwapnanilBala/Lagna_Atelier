package site.lagnaatelier.app.data.report

import org.json.JSONArray
import org.json.JSONObject

/*
 * The verified story the PDF is made from: GET /api/chart/story-report on the
 * web, which answers { generated_at_utc, story } with story shaped as
 * lib/story-engine.ts PersonalStory and story.verification as
 * lib/story-verification.ts StoryVerification.
 *
 * Fields keep the web's names so the two can be read side by side.
 */

data class StorySignal(val label: String, val value: String)

data class StoryGlance(val label: String, val value: String, val context: String)

data class StoryTimelineItem(val status: String, val label: String, val window: String, val narrative: String)

data class StoryChapter(
    val id: String,
    val eyebrow: String,
    val title: String,
    val body: String,
    val highlights: List<String>,
    val signals: List<StorySignal>,
    /** Null when the engine sent none, as `opening?` on the web. */
    val opening: String?,
    /** Empty when the engine sent none; the web then reads [body]. */
    val narrative: List<String>,
    val practices: List<String>,
    val reflectionPrompt: String?,
    /** "well-supported", "supported" or "exploratory"; null when not sent. */
    val support: String?,
    val supportNote: String?,
)

data class VerificationCheck(
    val id: String,
    val label: String,
    /** "passed", "warning" or "failed". */
    val status: String,
    val detail: String,
    val critical: Boolean,
)

data class CalculationProfile(
    val provider: String,
    val engine: String,
    val ayanamsha: String,
    val houseSystem: String,
    val birthTimeReliability: String,
)

data class StoryVerification(
    /** "verified", "qualified" or "failed". */
    val status: String,
    val passedCount: Int,
    val warningCount: Int,
    val failedCount: Int,
    val checks: List<VerificationCheck>,
    val summary: String,
    val profile: CalculationProfile,
) {
    val totalCount: Int get() = passedCount + warningCount + failedCount
    val isVerified: Boolean get() = status == "verified"
    val isFailed: Boolean get() = status == "failed"
}

data class PersonalStory(
    val title: String,
    val subtitle: String,
    val introduction: String,
    val preface: List<String>,
    val atAGlance: List<StoryGlance>,
    val centralThemes: List<String>,
    val timeline: List<StoryTimelineItem>,
    val chapters: List<StoryChapter>,
    val verification: StoryVerification,
    val reflectionNote: String,
)

data class StoryReport(val generatedAtUtc: String, val story: PersonalStory)

object StoryParser {
    fun parseReport(body: String): StoryReport {
        val json = JSONObject(body)
        return StoryReport(
            generatedAtUtc = json.optString("generated_at_utc"),
            story = parseStory(json.getJSONObject("story")),
        )
    }

    fun parseStory(json: JSONObject): PersonalStory = PersonalStory(
        title = json.optString("title"),
        subtitle = json.optString("subtitle"),
        introduction = json.optString("introduction"),
        preface = json.optJSONArray("preface").strings(),
        atAGlance = json.optJSONArray("atAGlance").objects().map {
            StoryGlance(it.optString("label"), it.optString("value"), it.optString("context"))
        },
        centralThemes = json.optJSONArray("centralThemes").strings(),
        timeline = json.optJSONArray("timeline").objects().map {
            StoryTimelineItem(it.optString("status"), it.optString("label"), it.optString("window"), it.optString("narrative"))
        },
        chapters = json.getJSONArray("chapters").objects().map(::parseChapter),
        verification = parseVerification(json.getJSONObject("verification")),
        reflectionNote = json.optString("reflectionNote"),
    )

    private fun parseChapter(json: JSONObject) = StoryChapter(
        id = json.getString("id"),
        eyebrow = json.optString("eyebrow"),
        title = json.optString("title"),
        body = json.optString("body"),
        highlights = json.optJSONArray("highlights").strings(),
        signals = json.optJSONArray("signals").objects().map { StorySignal(it.optString("label"), it.optString("value")) },
        opening = json.optStringOrNull("opening"),
        narrative = json.optJSONArray("narrative").strings(),
        practices = json.optJSONArray("practices").strings(),
        reflectionPrompt = json.optStringOrNull("reflectionPrompt"),
        support = json.optStringOrNull("support"),
        supportNote = json.optStringOrNull("supportNote"),
    )

    private fun parseVerification(json: JSONObject): StoryVerification {
        val profile = json.optJSONObject("calculationProfile") ?: JSONObject()
        return StoryVerification(
            status = json.optString("status"),
            passedCount = json.optInt("passedCount"),
            warningCount = json.optInt("warningCount"),
            failedCount = json.optInt("failedCount"),
            checks = json.optJSONArray("checks").objects().map {
                VerificationCheck(
                    id = it.optString("id"),
                    label = it.optString("label"),
                    status = it.optString("status"),
                    detail = it.optString("detail"),
                    critical = it.optBoolean("critical"),
                )
            },
            summary = json.optString("summary"),
            profile = CalculationProfile(
                provider = profile.optString("provider"),
                engine = profile.optString("engine"),
                ayanamsha = profile.optString("ayanamsha"),
                houseSystem = profile.optString("houseSystem"),
                birthTimeReliability = profile.optString("birthTimeReliability"),
            ),
        )
    }
}

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { opt(it) as? String }

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

/* optString turns a JSON null into "null"; absent and null both mean "not sent". */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else opt(key) as? String
