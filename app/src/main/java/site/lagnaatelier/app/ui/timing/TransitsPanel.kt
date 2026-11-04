package site.lagnaatelier.app.ui.timing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.formatDegree
import site.lagnaatelier.app.data.timing.TimingFormat
import site.lagnaatelier.app.data.timing.Transit
import site.lagnaatelier.app.data.timing.TransitTone
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.reading.planetAbbreviation
import site.lagnaatelier.app.ui.reading.planetName
import site.lagnaatelier.app.ui.reading.pointName
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted
import kotlin.math.roundToInt

private const val FIRST_TRANSITS = 3

@Composable
internal fun TransitsPanel(holder: TimingHolder, onRetry: () -> Unit) {
    val transits = (holder.forecast as? Load.Ready)?.value?.transits.orEmpty()
    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.timing_transits_title)) {
            if (transits.size > FIRST_TRANSITS) {
                TextLink(
                    if (holder.showAllTransits) stringResource(R.string.timing_see_fewer)
                    else stringResource(R.string.timing_see_all_count, transits.size),
                ) { holder.showAllTransits = !holder.showAllTransits }
            }
        }
        LoadState(holder.forecast, stringResource(R.string.timing_transits_loading), onRetry) { forecast ->
            val all = forecast.transits
            if (all.isEmpty()) {
                Text(stringResource(R.string.timing_transits_empty), style = MaterialTheme.typography.bodyMedium, color = Muted)
            } else {
                val shown = if (holder.showAllTransits) all else all.take(FIRST_TRANSITS)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    shown.forEachIndexed { index, transit ->
                        if (index > 0) HorizontalDivider(color = Hairline)
                        TransitRow(transit)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransitRow(transit: Transit) {
    val easy = transit.tone == TransitTone.Easy
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(if (easy) Ink else Canvas, CircleShape)
                .border(1.dp, if (easy) Ink else Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                planetAbbreviation(transit.planet),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = if (easy) CardWhite else Ink,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.timing_transit_title, planetName(transit.planet), aspectWord(transit.aspect), pointName(transit.natalPlanet)),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            )
            Spacer(Modifier.height(2.dp))
            Meta(stringResource(R.string.timing_transit_meta, exactness(transit), formatDegree(transit.orb)))
            if (transit.interpretation.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(transit.interpretation, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp), color = Muted)
            }
        }
        TonePill(easy)
    }
}

/* "Venus trine your Sun", "Mars opposite your Moon": the engine's aspect names as a reader says them. */
@Composable
private fun aspectWord(aspect: String): String = when (aspect) {
    "Conjunction" -> stringResource(R.string.timing_aspect_conjunction)
    "Opposition" -> stringResource(R.string.timing_aspect_opposition)
    "Trine" -> stringResource(R.string.timing_aspect_trine)
    "Square" -> stringResource(R.string.timing_aspect_square)
    "Sextile" -> stringResource(R.string.timing_aspect_sextile)
    else -> aspect.lowercase()
}

@Composable
private fun exactness(transit: Transit): String {
    val days = transit.daysToExact ?: return stringResource(R.string.timing_transit_separating)
    if (days < 1.0) return stringResource(R.string.timing_transit_exact_today)
    val whole = days.roundToInt()
    return pluralStringResource(R.plurals.timing_transit_exact_in, whole, whole)
}

@Composable
private fun TonePill(easy: Boolean) {
    Text(
        stringResource(if (easy) R.string.timing_tone_easy else R.string.timing_tone_testing),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5.sp),
        color = if (easy) CardWhite else Ink,
        modifier = Modifier
            .background(if (easy) Ink else CardWhite, CircleShape)
            .border(1.dp, if (easy) Ink else Hairline, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
