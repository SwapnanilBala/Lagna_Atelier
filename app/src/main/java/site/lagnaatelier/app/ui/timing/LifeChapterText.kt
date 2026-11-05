package site.lagnaatelier.app.ui.timing

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.AppLanguage
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.data.periods.LifeShift
import site.lagnaatelier.app.data.periods.ShiftFact
import site.lagnaatelier.app.data.periods.ShiftKind
import site.lagnaatelier.app.ui.reading.signNameRes
import java.text.NumberFormat
import java.util.Locale

/*
 * A life chapter's label, theme and evidence line in the app's language, from
 * the parts the engine keeps (LifeShift.kind, occurrence, planet, facts) and
 * the templates in res/values/strings_life_chapters.xml: "शनि वापसी (पहली)"
 * where the engine says "Saturn return (first)". The labels and themes are
 * the website's own translations (life-shift-labels.ts); English comes out
 * exactly as the engine words it. A label the templates cannot say (an
 * occurrence past the eighth) prints as the engine wrote it, as on the web.
 *
 * The narrative stays the engine's English, as on the website: it is what a
 * chapter shows only until its written reading lands in the app's language.
 */

/** What the chapter text takes from the app's resources, so the tests can supply the catalogs themselves. */
interface ChapterWords {
    fun string(@StringRes id: Int, vararg args: Any): String

    /** "first" to "8th", in order. */
    val ordinals: List<String>

    /** A planet's name from its English one, "Saturn" -> "शनि". */
    fun planet(name: String): String

    /** A sign's name from its English one, "Leo" -> "सिंह". */
    fun sign(name: String): String

    /** 29.5 as the language writes it. */
    fun number(value: Double): String
}

object LifeChapterText {
    /** "Saturn return (first)", "Mercury mahadasha begins". */
    fun label(shift: LifeShift, words: ChapterWords): String {
        if (shift.kind == ShiftKind.Mahadasha) return words.string(R.string.chapters_label_mahadasha, words.planet(shift.planet))
        val ordinal = words.ordinals.getOrNull(shift.occurrence - 1) ?: return shift.label
        val template = when (shift.kind) {
            ShiftKind.SaturnReturn -> R.string.chapters_label_saturn_return
            ShiftKind.JupiterReturn -> R.string.chapters_label_jupiter_return
            else -> R.string.chapters_label_nodal_return
        }
        return words.string(template, ordinal)
    }

    /** The chapter's standing theme; a planet the engine has no theme for keeps its English one. */
    fun theme(shift: LifeShift, words: ChapterWords): String {
        val id = when (shift.kind) {
            ShiftKind.SaturnReturn -> R.string.chapters_theme_saturn_return
            ShiftKind.JupiterReturn -> R.string.chapters_theme_jupiter_return
            ShiftKind.NodalReturn -> R.string.chapters_theme_nodal_return
            ShiftKind.Mahadasha -> PLANET_THEMES[shift.planet] ?: return shift.theme
        }
        return words.string(id)
    }

    /** "Mahadasha lord: Saturn | Lasts ~19 years | Natal Saturn: Leo / H5"; the engine's own line when there are no facts. */
    fun evidence(shift: LifeShift, words: ChapterWords): String {
        if (shift.facts.isEmpty()) return shift.evidence
        return shift.facts.joinToString(" | ") { fact ->
            when (fact) {
                is ShiftFact.Lord -> words.string(R.string.chapters_fact_lord, words.planet(fact.planet))
                is ShiftFact.Lasts -> words.string(R.string.chapters_fact_lasts, words.number(fact.years))
                is ShiftFact.Cycle -> words.string(R.string.chapters_fact_cycle, words.number(fact.years))
                is ShiftFact.Natal -> words.string(R.string.chapters_fact_natal, words.planet(fact.planet), placement(fact.sign, fact.house, words))
                is ShiftFact.Node -> words.string(R.string.chapters_fact_node, words.planet(fact.planet), placement(fact.sign, fact.house, words))
            }
        }
    }

    private fun placement(sign: String, house: Int, words: ChapterWords): String =
        words.string(R.string.chapters_fact_placement, words.sign(sign), house)

    private val PLANET_THEMES = mapOf(
        "Sun" to R.string.chapters_theme_sun,
        "Moon" to R.string.chapters_theme_moon,
        "Mars" to R.string.chapters_theme_mars,
        "Mercury" to R.string.chapters_theme_mercury,
        "Jupiter" to R.string.chapters_theme_jupiter,
        "Venus" to R.string.chapters_theme_venus,
        "Saturn" to R.string.chapters_theme_saturn,
        "Rahu" to R.string.chapters_theme_rahu,
        "Ketu" to R.string.chapters_theme_ketu,
    )
}

/** The app's resources as [ChapterWords]. */
class ResourceChapterWords(private val resources: Resources, private val locale: Locale) : ChapterWords {
    override fun string(id: Int, vararg args: Any): String = resources.getString(id, *args)
    override val ordinals: List<String> = resources.getStringArray(R.array.chapters_ordinals).toList()
    override fun planet(name: String): String = PLANETS[name]?.let(resources::getString) ?: name
    override fun sign(name: String): String =
        Sign.entries.firstOrNull { it.label == name }?.let { resources.getString(signNameRes(it)) } ?: name
    override fun number(value: Double): String =
        NumberFormat.getInstance(locale).apply { maximumFractionDigits = 2 }.format(value)

    private companion object {
        /* ChartNames.planetName's, outside composition. */
        val PLANETS = mapOf(
            "Sun" to R.string.chart_planet_sun,
            "Moon" to R.string.chart_planet_moon,
            "Mars" to R.string.chart_planet_mars,
            "Mercury" to R.string.chart_planet_mercury,
            "Jupiter" to R.string.chart_planet_jupiter,
            "Venus" to R.string.chart_planet_venus,
            "Saturn" to R.string.chart_planet_saturn,
            "Rahu" to R.string.chart_planet_rahu,
            "Ketu" to R.string.chart_planet_ketu,
        )
    }
}

@Composable
internal fun rememberChapterWords(): ChapterWords {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { ResourceChapterWords(resources, AppLanguage.resolve(configuration)) }
}
