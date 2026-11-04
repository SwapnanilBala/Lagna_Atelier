package site.lagnaatelier.app.ui.timing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.Period
import site.lagnaatelier.app.data.dayMonthYear
import site.lagnaatelier.app.data.progress
import site.lagnaatelier.app.data.periods.ClassicalReading
import site.lagnaatelier.app.data.periods.DashaReading
import site.lagnaatelier.app.data.periods.PeriodChain
import site.lagnaatelier.app.data.timing.Forecast
import site.lagnaatelier.app.data.timing.TimingFormat
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * The three running periods stacked as one headline, the written paragraph on
 * what they mean together (/api/chart/current-period), then one line each
 * with how far through it is and its own reading on request. While the
 * forecast loads, the chart's own Maha and Antar stand in the headline, so
 * the card is never empty.
 */
@Composable
internal fun PeriodsCard(chart: ChartSummary, holder: TimingHolder, onRetry: () -> Unit, onRead: (PeriodChain) -> Unit) {
    val today = holder.today
    FloatCard(Modifier.fillMaxWidth(), spacing = 14.dp, padding = 16.dp) {
        when (val state = holder.forecast) {
            is Load.Ready -> Periods(state.value, today, holder, onRead)
            else -> {
                Column {
                    CardLabel(stringResource(R.string.timing_running_now, TimingFormat.weekdayDayMonth(today)))
                    Lords(listOf(chart.mahadasha.lord, chart.antardasha.lord))
                }
                NowParagraph(holder.readings.currentPeriod)
                LoadState(state, stringResource(R.string.timing_periods_loading), onRetry) {}
            }
        }
    }
}

/* Quiet while it is written and on any failure: the card is complete without it. */
@Composable
private fun NowParagraph(state: Written<String?>?) {
    when (state) {
        Written.Writing -> CardLabel(stringResource(R.string.periods_now_writing))
        is Written.Ready -> state.value?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
        else -> Unit
    }
}

@Composable
private fun Periods(forecast: Forecast, today: LocalDate, holder: TimingHolder, onRead: (PeriodChain) -> Unit) {
    val periods = forecast.periods
    val readings = holder.readings
    Column {
        CardLabel(stringResource(R.string.timing_running_now, TimingFormat.weekdayDayMonth(today)))
        Lords(listOfNotNull(periods.maha.lord, periods.antar.lord, periods.pratyantar?.lord))
    }
    NowParagraph(readings.currentPeriod)
    val labels = listOf(R.string.timing_level_maha, R.string.timing_level_antar, R.string.timing_level_pratyantar)
    DashaReading.runningChains(periods).forEachIndexed { index, chain ->
        Level(stringResource(labels[index]), chain.period, today)
        PeriodReading(chain, readings.periodReading(chain), onRead)
    }

    if (periods.pratyantars.isNotEmpty()) {
        HorizontalDivider(color = Hairline)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val next = periods.next
            Text(
                if (next != null) stringResource(R.string.timing_next_period, next.lord, next.start.dayMonthYear()) else "",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier.weight(1f),
            )
            GhostChip(
                stringResource(if (holder.showAllPeriods) R.string.timing_fewer_periods else R.string.timing_all_periods),
                onClick = { holder.showAllPeriods = !holder.showAllPeriods },
            )
        }
        if (holder.showAllPeriods) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardLabel(stringResource(R.string.timing_periods_remaining))
                periods.remaining.forEach { period ->
                    val chain = DashaReading.pratyantarChain(periods, period)
                    /* The running one is read from its own line above. */
                    val isCurrent = period == periods.pratyantar
                    PeriodRow(period, isCurrent, if (isCurrent) null else chain, readings.periodReading(chain), onRead)
                }
            }
        }
    }
}

@Composable
private fun Lords(lords: List<String>) {
    Text(
        buildAnnotatedString {
            lords.forEachIndexed { index, lord ->
                if (index > 0) withStyle(SpanStyle(color = Muted)) { append(" · ") }
                append(lord)
            }
        },
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp, lineHeight = 27.sp),
    )
}

@Composable
private fun Level(label: String, period: Period, today: LocalDate) {
    val fraction = period.progress(today)
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = Muted)) { append(label) }
                    append("  ")
                    append(period.lord)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Meta("${(fraction * 100).roundToInt()}%", color = Ink)
        }
        ProgressLine(fraction)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Meta(period.start.dayMonthYear())
            Meta(stringResource(R.string.timing_range_to, period.end.dayMonthYear()))
        }
    }
}

/** A period's own reading under its line: the button until it is asked for, then the reading. */
@Composable
private fun PeriodReading(chain: PeriodChain, state: Written<ClassicalReading>?, onRead: (PeriodChain) -> Unit) {
    if (state == null) {
        GhostChip(stringResource(R.string.periods_read_this), onClick = { onRead(chain) })
    } else {
        WrittenState(state, stringResource(R.string.periods_reading_pending), onRetry = { onRead(chain) })
    }
}

/* [chain] is null for a row that offers no reading of its own. */
@Composable
private fun PeriodRow(
    period: Period,
    isCurrent: Boolean,
    chain: PeriodChain?,
    reading: Written<ClassicalReading>?,
    onRead: (PeriodChain) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    period.lord,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) Ink else Muted,
                    fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (isCurrent) Meta(stringResource(R.string.timing_period_now), color = Ink)
                Meta(stringResource(R.string.timing_period_row, period.start.dayMonthYear(), period.end.dayMonthYear()))
            }
            if (chain != null && reading == null) {
                TextLink(stringResource(R.string.periods_read_short), onClick = { onRead(chain) })
            }
        }
        if (chain != null && reading != null) {
            WrittenState(reading, stringResource(R.string.periods_reading_pending), onRetry = { onRead(chain) })
        }
    }
}
