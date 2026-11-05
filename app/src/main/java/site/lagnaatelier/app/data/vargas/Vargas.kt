package site.lagnaatelier.app.data.vargas

import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.Placement
import site.lagnaatelier.app.data.Sign

/*
 * The divisional charts (vargas): the twenty the engine calculates, D1 to D60,
 * from /api/chart's chart.divisional_charts, and the chart-specific notes for
 * the ten key ones from POST /api/chart/varga-commentary.
 *
 * Shapes from lib/astro-types.ts (DivisionalChartInfo) and
 * lib/varga-commentary.ts on the web; which ten are key, and how much each
 * depends on the birth time, from lib/divisional-chart-guide.ts.
 */

/** One point in a varga: its sign in the D1 and in this chart. */
data class VargaPosition(val name: String, val rashi: Sign, val sign: Sign) {
    /** The point keeps its D1 sign here: a consistency signal, not good or bad in itself. */
    val repeats: Boolean get() = rashi == sign
}

data class Varga(
    val division: Int,
    /** "D9". */
    val label: String,
    /** The engine's one line on the theme, "Marriage, dharma, and inner nature". */
    val description: String,
    /** D5, D6, D8 and D11 sit outside Parashara's sixteen. */
    val extended: Boolean,
    val positions: List<VargaPosition>,
) {
    val ascendant: Sign? get() = positions.firstOrNull { it.name == ASCENDANT }?.sign

    /** The nine grahas by their sign in this varga, as the South Indian chart draws them. */
    val placements: List<Placement>
        get() = positions.mapNotNull { p -> GRAHA_ABBREVIATIONS[p.name]?.let { Placement(it, p.sign) } }

    val key: KeyVarga? get() = KeyVarga.of(division)

    /**
     * The theme in the reader's language: the site's divisional.panel.themes
     * line for this division. [description] is the engine's English and stays
     * the fallback for a division the site does not list.
     */
    @get:StringRes
    val theme: Int? get() = THEMES[division]

    companion object {
        const val ASCENDANT = "Ascendant"

        private val THEMES = mapOf(
            1 to R.string.more_varga_theme_d1, 2 to R.string.more_varga_theme_d2, 3 to R.string.more_varga_theme_d3,
            4 to R.string.more_varga_theme_d4, 5 to R.string.more_varga_theme_d5, 6 to R.string.more_varga_theme_d6,
            7 to R.string.more_varga_theme_d7, 8 to R.string.more_varga_theme_d8, 9 to R.string.more_varga_theme_d9,
            10 to R.string.more_varga_theme_d10, 11 to R.string.more_varga_theme_d11, 12 to R.string.more_varga_theme_d12,
            16 to R.string.more_varga_theme_d16, 20 to R.string.more_varga_theme_d20, 24 to R.string.more_varga_theme_d24,
            27 to R.string.more_varga_theme_d27, 30 to R.string.more_varga_theme_d30, 40 to R.string.more_varga_theme_d40,
            45 to R.string.more_varga_theme_d45, 60 to R.string.more_varga_theme_d60,
        )

        /* The same two letters ChartParser uses for the D1. */
        val GRAHA_ABBREVIATIONS = linkedMapOf(
            "Sun" to "Su", "Moon" to "Mo", "Mars" to "Ma", "Mercury" to "Me", "Jupiter" to "Ju",
            "Venus" to "Ve", "Saturn" to "Sa", "Rahu" to "Ra", "Ketu" to "Ke",
        )
    }
}

/** How much a varga's conclusions depend on an exact birth time. */
enum class TimeSensitivity(@param:StringRes @get:StringRes val label: Int) {
    Foundation(R.string.more_varga_sensitivity_foundation),
    ExactTime(R.string.more_varga_sensitivity_exact),
    RectifiedTime(R.string.more_varga_sensitivity_rectified),
}

/**
 * The ten vargas the site explains in full and writes a note for
 * (IMPORTANT_DIVISIONAL_CHARTS). The prose is the site's divisional.guide copy.
 */
enum class KeyVarga(
    val division: Int,
    val sensitivity: TimeSensitivity,
    @param:StringRes @get:StringRes val title: Int,
    @param:StringRes @get:StringRes val focus: Int,
    @param:StringRes @get:StringRes val summary: Int,
    @param:StringRes @get:StringRes val readWith: Int,
    @param:StringRes @get:StringRes val question: Int,
    @param:StringRes @get:StringRes val timeNote: Int,
) {
    D1(1, TimeSensitivity.Foundation, R.string.more_varga_d1_name, R.string.more_varga_d1_focus, R.string.more_varga_d1_summary, R.string.more_varga_d1_read_with, R.string.more_varga_d1_question, R.string.more_varga_d1_time),
    D2(2, TimeSensitivity.ExactTime, R.string.more_varga_d2_name, R.string.more_varga_d2_focus, R.string.more_varga_d2_summary, R.string.more_varga_d2_read_with, R.string.more_varga_d2_question, R.string.more_varga_d2_time),
    D4(4, TimeSensitivity.ExactTime, R.string.more_varga_d4_name, R.string.more_varga_d4_focus, R.string.more_varga_d4_summary, R.string.more_varga_d4_read_with, R.string.more_varga_d4_question, R.string.more_varga_d4_time),
    D7(7, TimeSensitivity.ExactTime, R.string.more_varga_d7_name, R.string.more_varga_d7_focus, R.string.more_varga_d7_summary, R.string.more_varga_d7_read_with, R.string.more_varga_d7_question, R.string.more_varga_d7_time),
    D9(9, TimeSensitivity.ExactTime, R.string.more_varga_d9_name, R.string.more_varga_d9_focus, R.string.more_varga_d9_summary, R.string.more_varga_d9_read_with, R.string.more_varga_d9_question, R.string.more_varga_d9_time),
    D10(10, TimeSensitivity.ExactTime, R.string.more_varga_d10_name, R.string.more_varga_d10_focus, R.string.more_varga_d10_summary, R.string.more_varga_d10_read_with, R.string.more_varga_d10_question, R.string.more_varga_d10_time),
    D12(12, TimeSensitivity.ExactTime, R.string.more_varga_d12_name, R.string.more_varga_d12_focus, R.string.more_varga_d12_summary, R.string.more_varga_d12_read_with, R.string.more_varga_d12_question, R.string.more_varga_d12_time),
    D24(24, TimeSensitivity.ExactTime, R.string.more_varga_d24_name, R.string.more_varga_d24_focus, R.string.more_varga_d24_summary, R.string.more_varga_d24_read_with, R.string.more_varga_d24_question, R.string.more_varga_d24_time),
    D30(30, TimeSensitivity.ExactTime, R.string.more_varga_d30_name, R.string.more_varga_d30_focus, R.string.more_varga_d30_summary, R.string.more_varga_d30_read_with, R.string.more_varga_d30_question, R.string.more_varga_d30_time),
    D60(60, TimeSensitivity.RectifiedTime, R.string.more_varga_d60_name, R.string.more_varga_d60_focus, R.string.more_varga_d60_summary, R.string.more_varga_d60_read_with, R.string.more_varga_d60_question, R.string.more_varga_d60_time);

    companion object {
        fun of(division: Int): KeyVarga? = entries.firstOrNull { it.division == division }
    }
}

object VargaParser {
    private val signs = Sign.entries.associateBy { it.label }

    /**
     * chart.divisional_charts ({"9": {division, label, description, tradition,
     * positions}}) in division order. A varga with a sign this app does not
     * know is left out rather than failing the whole chart.
     */
    fun parse(divisionalCharts: JSONObject?): List<Varga> {
        if (divisionalCharts == null) return emptyList()
        return divisionalCharts.keys().asSequence().mapNotNull { key ->
            val row = divisionalCharts.optJSONObject(key) ?: return@mapNotNull null
            val division = (row.opt("division") as? Number)?.toInt() ?: key.toIntOrNull() ?: return@mapNotNull null
            val positions = row.optJSONArray("positions")?.objects()?.map { p ->
                VargaPosition(
                    name = p.optString("name"),
                    rashi = signs[p.optString("rashi_sign")] ?: return@mapNotNull null,
                    sign = signs[p.optString("divisional_sign")] ?: return@mapNotNull null,
                )
            } ?: return@mapNotNull null
            if (positions.isEmpty()) return@mapNotNull null
            Varga(
                division = division,
                label = row.optString("label").ifBlank { "D$division" },
                description = row.optString("description").trim(),
                extended = row.optString("tradition") == "extended",
                positions = positions,
            )
        }.sortedBy { it.division }.toList()
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
}

/*
 * The commentary route's body and answer. Only the ten key vargas are sent
 * (the route refuses any other), each with the ten points the site sends,
 * every field from a closed set: COMMENTARY_POINTS and the twelve signs.
 */
object VargaCommentary {
    const val PATH = "/api/chart/varga-commentary"

    val POINTS = listOf("Ascendant", "Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Rahu", "Ketu")

    /** {divisions: [{division, label, positions: [{name, rashi, divisional}]}], language}; null when no key varga is there. */
    fun requestBody(vargas: List<Varga>, language: String = "en"): String? {
        val divisions = JSONArray()
        for (key in KeyVarga.entries) {
            val varga = vargas.firstOrNull { it.division == key.division } ?: continue
            val positions = JSONArray()
            for (name in POINTS) {
                val position = varga.positions.firstOrNull { it.name == name } ?: continue
                positions.put(
                    JSONObject().put("name", name).put("rashi", position.rashi.label).put("divisional", position.sign.label),
                )
            }
            if (positions.length() == 0) continue
            divisions.put(JSONObject().put("division", key.division).put("label", varga.label).put("positions", positions))
        }
        if (divisions.length() == 0) return null
        return JSONObject().put("divisions", divisions).put("language", language).toString()
    }

    /** {notes: [{division, note}]} by division; blank notes and divisions that are not key ones are dropped. */
    fun parseNotes(json: String): Map<Int, String> {
        val notes = JSONObject(json).optJSONArray("notes") ?: return emptyMap()
        return (0 until notes.length()).mapNotNull { notes.optJSONObject(it) }.mapNotNull { row ->
            val division = (row.opt("division") as? Number)?.toInt() ?: return@mapNotNull null
            val note = (row.opt("note") as? String)?.trim().orEmpty()
            if (note.isEmpty() || KeyVarga.of(division) == null) null else division to note
        }.toMap()
    }
}
