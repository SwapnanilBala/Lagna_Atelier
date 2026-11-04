package site.lagnaatelier.app.ui.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.ChartStyle
import site.lagnaatelier.app.data.LookPreferences
import site.lagnaatelier.app.data.Placement
import site.lagnaatelier.app.data.Sign

/*
 * Chart vocabulary in the app's language, and the one entry point every
 * screen that draws a chart goes through, so the style chosen in Account ->
 * Preferences is used wherever a chart is drawn.
 *
 * The data keeps English names and two-letter abbreviations ("Su"), the
 * API's own; only what is printed is translated, as the web's
 * lib/chart-labels.ts does.
 */

internal fun signNameRes(sign: Sign): Int = when (sign) {
    Sign.Aries -> R.string.chart_sign_aries
    Sign.Taurus -> R.string.chart_sign_taurus
    Sign.Gemini -> R.string.chart_sign_gemini
    Sign.Cancer -> R.string.chart_sign_cancer
    Sign.Leo -> R.string.chart_sign_leo
    Sign.Virgo -> R.string.chart_sign_virgo
    Sign.Libra -> R.string.chart_sign_libra
    Sign.Scorpio -> R.string.chart_sign_scorpio
    Sign.Sagittarius -> R.string.chart_sign_sagittarius
    Sign.Capricorn -> R.string.chart_sign_capricorn
    Sign.Aquarius -> R.string.chart_sign_aquarius
    Sign.Pisces -> R.string.chart_sign_pisces
}

/** A sign's name in the app's language: "Taurus", "वृषभ". */
@Composable
fun signName(sign: Sign): String = stringResource(signNameRes(sign))

/** A body's chart abbreviation in the app's language: "Su" -> "सू". Unknown ones print as given. */
@Composable
fun bodyAbbreviation(abbreviation: String): String = when (abbreviation) {
    "Su" -> stringResource(R.string.chart_abbrev_su)
    "Mo" -> stringResource(R.string.chart_abbrev_mo)
    "Ma" -> stringResource(R.string.chart_abbrev_ma)
    "Me" -> stringResource(R.string.chart_abbrev_me)
    "Ju" -> stringResource(R.string.chart_abbrev_ju)
    "Ve" -> stringResource(R.string.chart_abbrev_ve)
    "Sa" -> stringResource(R.string.chart_abbrev_sa)
    "Ra" -> stringResource(R.string.chart_abbrev_ra)
    "Ke" -> stringResource(R.string.chart_abbrev_ke)
    else -> abbreviation
}

/** A planet's name in the app's language, from the API's English one: "Mercury" -> "बुध". Unknown ones print as given. */
@Composable
fun planetName(name: String): String = when (name) {
    "Sun" -> stringResource(R.string.chart_planet_sun)
    "Moon" -> stringResource(R.string.chart_planet_moon)
    "Mars" -> stringResource(R.string.chart_planet_mars)
    "Mercury" -> stringResource(R.string.chart_planet_mercury)
    "Jupiter" -> stringResource(R.string.chart_planet_jupiter)
    "Venus" -> stringResource(R.string.chart_planet_venus)
    "Saturn" -> stringResource(R.string.chart_planet_saturn)
    "Rahu" -> stringResource(R.string.chart_planet_rahu)
    "Ketu" -> stringResource(R.string.chart_planet_ketu)
    else -> name
}

/** A planet's chart abbreviation in the app's language, from its English name: "Ketu" -> "Ke". */
@Composable
fun planetAbbreviation(name: String): String = when (name) {
    "Sun" -> bodyAbbreviation("Su")
    "Moon" -> bodyAbbreviation("Mo")
    "Mars" -> bodyAbbreviation("Ma")
    "Mercury" -> bodyAbbreviation("Me")
    "Jupiter" -> bodyAbbreviation("Ju")
    "Venus" -> bodyAbbreviation("Ve")
    "Saturn" -> bodyAbbreviation("Sa")
    "Rahu" -> bodyAbbreviation("Ra")
    "Ketu" -> bodyAbbreviation("Ke")
    else -> name.take(2)
}

/** A sign given by its English name, as the timing and compatibility routes send it: "Gemini" -> "मिथुन". Unknown ones print as given. */
@Composable
fun signName(name: String): String = ChartVocabulary.sign(name)?.let { signName(it) } ?: name

/**
 * A sign as a narrow cell labels it: a Latin name cut to three letters (Gem,
 * Gém), a Devanagari or Bengali one written whole, since cutting it by
 * character would split a conjunct. The web's signAbbreviation.
 */
@Composable
fun signAbbreviation(sign: Sign): String = ChartVocabulary.abbreviate(signName(sign))

/** A row of a positions table: a planet, or the ascendant. */
@Composable
fun pointName(name: String): String =
    if (name == ChartVocabulary.ASCENDANT) stringResource(R.string.chart_point_ascendant) else planetName(name)

/** A nakshatra's name in the app's language, from the API's English one: "Shatabhisha" -> "शतभिषा". Unknown ones print as given. */
@Composable
fun nakshatraName(name: String): String = ChartVocabulary.nakshatraRes(name)?.let { stringResource(it) } ?: name

/**
 * A value from a finding's evidence (the engine's claims): a sign, a planet,
 * the ascendant, a nakshatra, an "Nth house", or a comma list of those.
 * Anything else (degrees, counts, a dignity word, a phrase) prints as the
 * engine wrote it. The web's chartValue.
 */
@Composable
fun chartValue(value: String): String {
    val parts = ChartVocabulary.parts(value)
    if (parts.size > 1) return parts.map { chartValue(it) }.joinToString(", ")
    ChartVocabulary.house(value)?.let { house ->
        return stringArrayResource(R.array.chart_house_names).getOrNull(house - 1) ?: value
    }
    ChartVocabulary.sign(value)?.let { return signName(it) }
    ChartVocabulary.nakshatraRes(value)?.let { return stringResource(it) }
    return pointName(value)
}

/** The lookups behind the names above, free of Compose so they can be tested. */
internal object ChartVocabulary {
    const val ASCENDANT = "Ascendant"

    private val signs = Sign.entries.associateBy { it.label.lowercase() }

    fun sign(name: String): Sign? = signs[name.trim().lowercase()]

    /* The twenty-seven as the engine names them, in order (the web's NAKSHATRA_NAMES). */
    val NAKSHATRAS: Map<String, Int> = linkedMapOf(
        "Ashwini" to R.string.chart_nakshatra_ashwini,
        "Bharani" to R.string.chart_nakshatra_bharani,
        "Krittika" to R.string.chart_nakshatra_krittika,
        "Rohini" to R.string.chart_nakshatra_rohini,
        "Mrigashira" to R.string.chart_nakshatra_mrigashira,
        "Ardra" to R.string.chart_nakshatra_ardra,
        "Punarvasu" to R.string.chart_nakshatra_punarvasu,
        "Pushya" to R.string.chart_nakshatra_pushya,
        "Ashlesha" to R.string.chart_nakshatra_ashlesha,
        "Magha" to R.string.chart_nakshatra_magha,
        "Purva Phalguni" to R.string.chart_nakshatra_purva_phalguni,
        "Uttara Phalguni" to R.string.chart_nakshatra_uttara_phalguni,
        "Hasta" to R.string.chart_nakshatra_hasta,
        "Chitra" to R.string.chart_nakshatra_chitra,
        "Swati" to R.string.chart_nakshatra_swati,
        "Vishakha" to R.string.chart_nakshatra_vishakha,
        "Anuradha" to R.string.chart_nakshatra_anuradha,
        "Jyeshtha" to R.string.chart_nakshatra_jyeshtha,
        "Moola" to R.string.chart_nakshatra_moola,
        "Purva Ashadha" to R.string.chart_nakshatra_purva_ashadha,
        "Uttara Ashadha" to R.string.chart_nakshatra_uttara_ashadha,
        "Shravana" to R.string.chart_nakshatra_shravana,
        "Dhanishta" to R.string.chart_nakshatra_dhanishta,
        "Shatabhisha" to R.string.chart_nakshatra_shatabhisha,
        "Purva Bhadrapada" to R.string.chart_nakshatra_purva_bhadrapada,
        "Uttara Bhadrapada" to R.string.chart_nakshatra_uttara_bhadrapada,
        "Revati" to R.string.chart_nakshatra_revati,
    )
    private val nakshatrasByLowerCase = NAKSHATRAS.mapKeys { it.key.lowercase() }

    fun nakshatraRes(name: String): Int? = nakshatrasByLowerCase[name.trim().lowercase()]

    private val HOUSE = Regex("""^(\d{1,2})(?:st|nd|rd|th) house$""")

    /** 7 for "7th house"; null for anything else, or a house past the twelfth. */
    fun house(value: String): Int? = HOUSE.matchEntire(value)?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..12 }

    fun parts(value: String): List<String> = if (", " in value) value.split(", ") else listOf(value)

    private val LATIN_START = Regex("""^\p{IsLatin}""")

    fun abbreviate(name: String): String {
        if (!LATIN_START.containsMatchIn(name)) return name
        val end = if (name.codePointCount(0, name.length) > 3) name.offsetByCodePoints(0, 3) else name.length
        return name.substring(0, end)
    }
}

/** Every sign's name, for building a chart's spoken description in one pass. */
@Composable
internal fun signNames(): Map<Sign, String> = Sign.entries.associateWith { signName(it) }

/** Every abbreviation [placements] use, translated. */
@Composable
internal fun abbreviations(placements: List<Placement>): Map<String, String> =
    placements.map { it.abbreviation }.distinct().associateWith { bodyAbbreviation(it) }

/** A rasi or navamsa chart, drawn in the style chosen in Account -> Preferences. */
@Composable
fun RasiChart(
    placements: List<Placement>,
    ascendant: Sign,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    when (LookPreferences.chartStyle) {
        ChartStyle.SouthIndian -> SouthIndianChart(placements, ascendant, title, subtitle, modifier)
        ChartStyle.NorthIndian -> Column(modifier) {
            NorthIndianChart(placements, ascendant, title, subtitle)
        }
    }
}
