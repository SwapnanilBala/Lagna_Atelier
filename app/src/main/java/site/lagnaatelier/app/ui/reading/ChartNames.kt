package site.lagnaatelier.app.ui.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
