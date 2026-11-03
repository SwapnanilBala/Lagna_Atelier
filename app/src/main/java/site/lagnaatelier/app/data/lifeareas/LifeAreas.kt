package site.lagnaatelier.app.data.lifeareas

import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject
import site.lagnaatelier.app.R

/*
 * The website's life areas (/insights/life-areas) as the app reads them:
 *
 *   GET /api/chart/life-domains   the seven areas, scored by the engine (no model)
 *   GET /api/chart/domain-brief   a short written brief for every area, in one call
 *   GET /api/chart/area-classics  "From the classics": a cited note per area, in one call
 *
 * Shapes from lib/astro-types.ts (LifeDomainInsight), lib/domain-briefs.ts and
 * lib/knowledge/classical-reading.ts on the web. Every reader here is lenient
 * about what it does not need: an area it cannot read is left out, never a crash.
 */

/** The seven areas, by the site's keys (lib/domain-briefs.ts DOMAIN_BRIEF_KEYS). */
enum class LifeAreaKey(val apiKey: String, @param:StringRes @get:StringRes val label: Int) {
    Love("love_life", R.string.more_area_love),
    Career("career", R.string.more_area_career),
    Family("family", R.string.more_area_family),
    Inheritance("inheritance", R.string.more_area_inheritance),
    Influence("influence", R.string.more_area_influence),
    LifeCycle("life_cycle", R.string.more_area_life_cycle),
    Travel("travel_destinations", R.string.more_area_travel);

    companion object {
        fun of(apiKey: String?): LifeAreaKey? = entries.firstOrNull { it.apiKey == apiKey }
    }
}

/** How active an area is against the reader's other areas (signal_profile.activity_band). */
enum class ActivityBand(val apiValue: String, @param:StringRes @get:StringRes val label: Int) {
    Prominent("prominent", R.string.more_band_prominent),
    Active("active", R.string.more_band_active),
    Developing("developing", R.string.more_band_developing);

    companion object {
        fun of(value: String?): ActivityBand? = entries.firstOrNull { it.apiValue == value }
    }
}

/** How firm the engine's conclusion is (evidence_matrix.conclusion_strength). */
enum class Conclusion(val apiValue: String, @param:StringRes @get:StringRes val label: Int) {
    Strong("strong", R.string.more_conclusion_strong),
    Moderate("moderate", R.string.more_conclusion_moderate),
    Cautious("cautious", R.string.more_conclusion_cautious);

    companion object {
        fun of(value: String?): Conclusion? = entries.firstOrNull { it.apiValue == value }
    }
}

/** Whether the rest of the chart bears the area out (evidence_matrix.confirmation_status). */
enum class Confirmation(val apiValue: String, @param:StringRes @get:StringRes val label: Int) {
    Confirmed("confirmed", R.string.more_confirmation_confirmed),
    Qualified("qualified", R.string.more_confirmation_qualified),
    Improved("improved", R.string.more_confirmation_improved),
    Contradictory("contradictory", R.string.more_confirmation_contradictory),
    Insufficient("insufficient", R.string.more_confirmation_insufficient);

    companion object {
        fun of(value: String?): Confirmation? = entries.firstOrNull { it.apiValue == value }
    }
}

data class Subtheme(val label: String, val summary: String)

/** One life area as the engine scored it. The text is the engine's own (display.*). */
data class LifeArea(
    val key: LifeAreaKey,
    val headline: String,
    /** The engine's paragraph; the written brief replaces it when one comes. */
    val body: String,
    val guidance: String,
    val longGame: String,
    val strengths: List<String>,
    val watchouts: List<String>,
    val timing: List<String>,
    /** 0 to 1, for ordering only: the site never prints it as a percentage. */
    val activityScore: Double,
    val band: ActivityBand?,
    val conclusion: Conclusion?,
    val confirmation: Confirmation?,
    val synthesis: String,
    val subthemes: List<Subtheme>,
)

/** A passage a classical note cites, numbered in the order the note first cites it. */
data class ClassicalSource(
    val number: Int,
    /** The book's slug: "brihat-jataka-1885", or "strijataka-1931" for the book on women's charts. */
    val book: String,
    /** Chapter and verse, "17.11", or a chapter alone for a prose book. */
    val ref: String,
    /** A translator's note on the verse rather than the verse itself. */
    val isNote: Boolean,
    val text: String,
) {
    val fromWomensBook: Boolean get() = book == ClassicalReading.WOMENS_BOOK
}

/** A run of the note and the numbers of the passages it rests on (often none). */
data class ClassicalSegment(val text: String, val sources: List<Int>)

data class ClassicalReading(val segments: List<ClassicalSegment>, val sources: List<ClassicalSource>) {
    /**
     * The note as paragraphs of cited pieces, the web's readingParagraphs:
     * paragraphs are cut at blank lines wherever they fall, and a segment's
     * source numbers stay on its last piece with words, after the words they
     * support (the web's keeps them on the last piece even when it is blank,
     * and so drops them).
     */
    fun paragraphs(): List<List<ClassicalSegment>> {
        val paragraphs = mutableListOf(mutableListOf<ClassicalSegment>())
        for (segment in segments) {
            val parts = segment.text.split(PARAGRAPH_BREAK)
            /* The last part with words, so a segment ending in a blank line keeps its mark. */
            val marked = parts.indexOfLast { it.isNotBlank() }.takeIf { it >= 0 } ?: parts.lastIndex
            parts.forEachIndexed { index, part ->
                if (index > 0) paragraphs.add(mutableListOf())
                paragraphs.last().add(ClassicalSegment(part, if (index == marked) segment.sources else emptyList()))
            }
        }
        return paragraphs
            .map { pieces ->
                val trimmed = pieces.toMutableList()
                if (trimmed.isNotEmpty()) {
                    trimmed[0] = trimmed[0].copy(text = trimmed[0].text.trimStart())
                    trimmed[trimmed.lastIndex] = trimmed.last().copy(text = trimmed.last().text.trimEnd())
                }
                trimmed.filter { it.text.isNotEmpty() || it.sources.isNotEmpty() }
            }
            .filter { pieces -> pieces.any { it.text.isNotBlank() } }
    }

    companion object {
        const val WOMENS_BOOK = "strijataka-1931"
        private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")
    }
}

object LifeAreasParser {

    /** The areas, most active first (the order the site's brief ranks them in). */
    fun parseAreas(json: String): List<LifeArea> {
        val insights = JSONObject(json).optJSONArray("insights") ?: return emptyList()
        return insights.objects().mapNotNull(::area).sortedByDescending { it.activityScore }
    }

    private fun area(insight: JSONObject): LifeArea? {
        val key = LifeAreaKey.of(insight.optString("key")) ?: return null
        val display = insight.optJSONObject("display") ?: return null
        val headline = display.text("headline") ?: return null
        val profile = insight.optJSONObject("signal_profile")
        val matrix = insight.optJSONObject("evidence_matrix")
        return LifeArea(
            key = key,
            headline = headline,
            body = display.text("body").orEmpty(),
            guidance = display.text("guidance").orEmpty(),
            longGame = display.text("long_game").orEmpty(),
            strengths = display.optJSONArray("strengths").texts(),
            watchouts = display.optJSONArray("watchouts").texts(),
            timing = display.optJSONArray("timing").texts(),
            activityScore = (profile?.opt("activity_score") as? Number)?.toDouble()
                ?: (insight.opt("confidence_score") as? Number)?.toDouble()
                ?: 0.0,
            band = ActivityBand.of(profile?.optString("activity_band")),
            conclusion = Conclusion.of(matrix?.optString("conclusion_strength")),
            confirmation = Confirmation.of(matrix?.optString("confirmation_status")),
            synthesis = matrix?.text("synthesis").orEmpty(),
            subthemes = insight.optJSONArray("subthemes")?.objects().orEmpty().mapNotNull { row ->
                val label = row.text("label") ?: return@mapNotNull null
                Subtheme(label, row.text("summary").orEmpty())
            },
        )
    }

    /** Every area's brief from one domain-brief answer ({brief, briefs, cached}). */
    fun parseBriefs(json: String): Map<LifeAreaKey, String> {
        val briefs = JSONObject(json).optJSONObject("briefs") ?: return emptyMap()
        return LifeAreaKey.entries.mapNotNull { key -> briefs.text(key.apiKey)?.let { key to it } }.toMap()
    }

    /** The areas' classical notes ({readings: {area: reading}}). An area the books are silent on is absent. */
    fun parseAreaClassics(json: String): Map<LifeAreaKey, ClassicalReading> {
        val readings = JSONObject(json).optJSONObject("readings") ?: return emptyMap()
        return LifeAreaKey.entries.mapNotNull { key ->
            readings.optJSONObject(key.apiKey)?.let(::reading)?.let { key to it }
        }.toMap()
    }

    /**
     * A cited reading, or null when it has nothing to show: the web treats a
     * reading without segments or sources as no reading at all.
     */
    fun reading(json: JSONObject?): ClassicalReading? {
        if (json == null) return null
        val sources = json.optJSONArray("sources")?.objects().orEmpty().mapNotNull { row ->
            val number = (row.opt("number") as? Number)?.toInt() ?: return@mapNotNull null
            val text = row.text("text") ?: return@mapNotNull null
            ClassicalSource(
                number = number,
                book = row.optString("book"),
                ref = row.optString("ref"),
                isNote = row.optString("kind") == "note",
                text = text,
            )
        }
        val numbers = sources.map { it.number }.toSet()
        val segments = json.optJSONArray("segments")?.objects().orEmpty().map { row ->
            val cited = row.optJSONArray("sources")
            val marks = (0 until (cited?.length() ?: 0)).mapNotNull { (cited!!.opt(it) as? Number)?.toInt() }
            /* A mark with no passage to point at would be a dead footnote. */
            ClassicalSegment(row.optString("text"), marks.filter { it in numbers })
        }
        if (segments.none { it.text.isNotBlank() } || sources.isEmpty()) return null
        return ClassicalReading(segments, sources)
    }

    private fun JSONObject.text(name: String): String? = (opt(name) as? String)?.trim()?.ifEmpty { null }

    private fun JSONArray?.texts(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { (opt(it) as? String)?.trim()?.ifEmpty { null } }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
}
