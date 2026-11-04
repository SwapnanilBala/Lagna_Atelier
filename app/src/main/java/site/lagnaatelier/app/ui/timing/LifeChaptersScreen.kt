package site.lagnaatelier.app.ui.timing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.periods.ClassicalReading
import site.lagnaatelier.app.data.periods.LifeShift
import site.lagnaatelier.app.data.periods.LifeShifts
import site.lagnaatelier.app.data.periods.PeriodsApi
import site.lagnaatelier.app.data.periods.ShiftStatus
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.GlowBehind
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted

/*
 * The website's /insights/life-shifts: the chapter that is active now and
 * the next transition on top, every past chapter folded away beneath, each
 * with its written reading from /api/chart/life-shifts when it lands and the
 * engine's short version until then (and instead, if none comes).
 *
 * The readings are asked for once, when this page first opens, for every
 * chapter on it, as the web page does; the route streams the chapters ahead
 * first, so the cards at the top usually fill in before the past ones.
 */
@Composable
internal fun LifeChaptersScreen(readings: PeriodsHolder, api: PeriodsApi, onBack: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(readings) { readings.startChapters(api) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SubPageHeader(stringResource(R.string.chapters_title), stringResource(R.string.chapters_lead), onBack)

        val chapters = readings.chapters
        if (chapters == null) {
            /* The chart's facts are still coming, or could not be had. */
            Panel(Modifier.fillMaxWidth()) {
                LoadState(readings.natal, stringResource(R.string.chapters_loading), onRetry = { readings.startChapters(api, force = true) }) {}
            }
            return@Column
        }
        if (chapters.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.chapters_empty), style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            return@Column
        }

        val page = LifeShifts.page(chapters)
        val written = readings.chaptersWritten
        val writing = written == Written.Writing
        (written as? Written.Failed)?.let { failure ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (failure.retry) R.string.chapters_failed else failure.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                if (failure.retry) GhostChip(stringResource(R.string.timing_retry), onClick = { readings.startChapters(api, force = true) })
            }
        }

        page.featured.forEachIndexed { index, shift ->
            val card = @Composable { ChapterCard(shift, readings.chapterReadings[shift.id], writing) }
            if (index == 0) {
                Box {
                    GlowBehind()
                    card()
                }
            } else {
                card()
            }
        }

        if (page.past.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                GhostChip(
                    if (readings.showPastChapters) stringResource(R.string.chapters_hide_past)
                    else pluralStringResource(R.plurals.chapters_show_past, page.past.size, page.past.size),
                    onClick = { readings.showPastChapters = !readings.showPastChapters },
                )
            }
            if (readings.showPastChapters) {
                page.past.forEach { shift -> ChapterCard(shift, readings.chapterReadings[shift.id], writing) }
            }
        }
    }
}

@Composable
private fun ChapterCard(shift: LifeShift, reading: ClassicalReading?, writing: Boolean) {
    FloatCard(Modifier.fillMaxWidth(), spacing = 10.dp, padding = 16.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Meta(stringResource(R.string.chapters_index, shift.index), color = Ink)
            GhostChip(
                stringResource(
                    when (shift.status) {
                        ShiftStatus.Past -> R.string.chapters_status_past
                        ShiftStatus.Active -> R.string.chapters_status_active
                        ShiftStatus.Upcoming -> R.string.chapters_status_upcoming
                    },
                ),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CardLabel(shift.label)
            Text(shift.theme.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Meta(stringResource(R.string.chapters_pivot, LifeShifts.pivotLabel(shift), shift.ageAtPivot), color = Ink)
            Meta(stringResource(R.string.chapters_window, LifeShifts.windowLabel(shift)))
        }
        when {
            reading != null -> ClassicalReadingView(reading)
            writing -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CardLabel(stringResource(R.string.chapters_writing))
                /* The short version, dimmed, until the reading lands. */
                Text(shift.narrative, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.alpha(0.7f))
            }
            else -> Text(shift.narrative, style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
        if (shift.evidence.isNotBlank()) Meta(shift.evidence)
    }
}
