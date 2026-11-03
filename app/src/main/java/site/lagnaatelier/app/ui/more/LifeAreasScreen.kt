package site.lagnaatelier.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.lifeareas.ClassicalReading
import site.lagnaatelier.app.data.lifeareas.LifeArea
import site.lagnaatelier.app.data.lifeareas.LifeAreaKey
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted

/*
 * The website's /insights/life-areas as one screen: the seven areas as cards,
 * most active first. Each shows the engine's standing for the area and its
 * written brief; "Read more" opens what supports it, what to watch, timing,
 * what to do next and what the classics say.
 *
 * The briefs and the classical notes are each one call for all seven areas,
 * started when the screen first opens. Until a brief arrives the card shows
 * the engine's own paragraph, which is also what stays if it never does.
 */
@Composable
internal fun LifeAreasScreen(holder: MoreHolder, onBack: () -> Unit) {
    LaunchedEffect(holder) { holder.openLifeAreas() }

    MoreScaffold(stringResource(R.string.more_areas_title), stringResource(R.string.more_areas_lead), onBack) {
        when (val areas = holder.areas) {
            is Fetch.Ready -> items(areas.value, key = { it.key.name }) { area ->
                AreaCard(area, rank = areas.value.indexOf(area) + 1, total = areas.value.size, holder = holder)
            }
            else -> item {
                Panel(Modifier.fillMaxWidth()) {
                    FetchState(areas, stringResource(R.string.more_areas_loading), onRetry = { holder.openLifeAreas(force = true) }) {}
                }
            }
        }
    }
}

@Composable
private fun AreaCard(area: LifeArea, rank: Int, total: Int, holder: MoreHolder) {
    var open by rememberSaveable(area.key) { mutableStateOf(false) }
    val briefs = holder.briefs
    val brief = (briefs as? Fetch.Ready)?.value?.get(area.key)

    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                CardLabel(stringResource(R.string.more_areas_rank, rank, total))
                Text(
                    stringResource(area.key.label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            area.band?.let { GhostChip(stringResource(it.label)) }
        }
        Text(area.headline, style = MaterialTheme.typography.titleSmall)
        if (briefs == Fetch.Loading && brief == null) {
            CardLabel(stringResource(R.string.more_areas_writing))
        }
        Text(
            brief ?: area.body,
            style = MaterialTheme.typography.bodyMedium,
            color = if (brief == null && briefs == Fetch.Loading) Muted else Ink,
        )
        val standing = listOfNotNull(area.conclusion?.label, area.confirmation?.label).map { stringResource(it) }
        if (standing.isNotEmpty()) CardLabel(standing.joinToString(" · "))

        GhostChip(
            stringResource(if (open) R.string.more_areas_close else R.string.more_areas_open),
            onClick = { open = !open },
        )
        if (open) AreaDetail(area, holder)
    }
}

@Composable
private fun AreaDetail(area: LifeArea, holder: MoreHolder) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = Hairline)
        if (area.subthemes.isNotEmpty()) {
            Section(stringResource(R.string.more_areas_themes)) {
                area.subthemes.take(3).forEach { theme ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(theme.label, style = MaterialTheme.typography.bodyMedium, color = Ink)
                        if (theme.summary.isNotEmpty()) Text(theme.summary, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
        }
        if (area.strengths.isNotEmpty()) Section(stringResource(R.string.more_areas_support)) { Bullets(area.strengths) }
        if (area.watchouts.isNotEmpty()) Section(stringResource(R.string.more_areas_watch)) { Bullets(area.watchouts) }
        if (area.timing.isNotEmpty()) Section(stringResource(R.string.more_areas_timing)) { Bullets(area.timing) }
        if (area.guidance.isNotEmpty()) Section(stringResource(R.string.more_areas_guidance)) {
            Text(area.guidance, style = MaterialTheme.typography.bodyMedium)
        }
        if (area.longGame.isNotEmpty()) Section(stringResource(R.string.more_areas_long_game)) {
            Text(area.longGame, style = MaterialTheme.typography.bodyMedium)
        }
        Classics(holder.classics, area, onRetry = holder::retryClassics)
    }
}

/*
 * The area's classical note. It does not appear when the books say nothing
 * on this area for this chart, which is an answer rather than a failure.
 */
@Composable
private fun Classics(fetch: Fetch<Map<LifeAreaKey, ClassicalReading>>, area: LifeArea, onRetry: () -> Unit) {
    val reading = (fetch as? Fetch.Ready)?.value?.get(area.key)
    if (fetch is Fetch.Ready && reading == null) return
    HorizontalDivider(color = Hairline)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CardLabel(stringResource(R.string.more_classics_kicker))
        SubHead(stringResource(R.string.more_classics_heading))
        FetchState(fetch, stringResource(R.string.more_classics_pending), onRetry) {
            if (reading != null) ClassicalNoteBody(reading, stateKey = "area-${area.key.name}")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SubHead(title)
        content()
    }
}
