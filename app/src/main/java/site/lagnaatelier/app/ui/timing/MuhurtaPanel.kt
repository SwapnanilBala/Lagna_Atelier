package site.lagnaatelier.app.ui.timing

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.timing.MuhurtaActivity
import site.lagnaatelier.app.data.timing.MuhurtaResult
import site.lagnaatelier.app.data.timing.MuhurtaWindow
import site.lagnaatelier.app.data.timing.TimingFormat
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.LiquidChip
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.LiquidType
import site.lagnaatelier.app.ui.theme.Muted

@Composable
internal fun MuhurtaPanel(holder: TimingHolder, onRetry: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.timing_muhurta_title)) {
            CardLabel(stringResource(R.string.timing_muhurta_range))
        }
        ActivityChips(holder.activity) {
            holder.activity = it
            holder.showAllWindows = false
        }
        LoadState(holder.muhurta(holder.activity), stringResource(R.string.timing_muhurta_loading), onRetry) { result ->
            Windows(result, holder)
        }
    }
}

@Composable
private fun ActivityChips(selected: MuhurtaActivity, onSelect: (MuhurtaActivity) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MuhurtaActivity.entries.forEach { activity ->
            val on = activity == selected
            LiquidChip(
                stringResource(activity.label),
                selected = on,
                onClick = { onSelect(activity) },
                modifier = Modifier.semantics { this.selected = on },
            )
        }
    }
}

private val MuhurtaActivity.label: Int
    get() = when (this) {
        MuhurtaActivity.Anything -> R.string.timing_activity_anything
        MuhurtaActivity.Travel -> R.string.timing_activity_travel
        MuhurtaActivity.Business -> R.string.timing_activity_business
        MuhurtaActivity.Study -> R.string.timing_activity_study
        MuhurtaActivity.Property -> R.string.timing_activity_property
        MuhurtaActivity.Interview -> R.string.timing_activity_interview
        MuhurtaActivity.Spiritual -> R.string.timing_activity_spiritual
    }

private val MuhurtaActivity.bestLabel: Int
    get() = when (this) {
        MuhurtaActivity.Anything -> R.string.timing_best_anything
        MuhurtaActivity.Travel -> R.string.timing_best_travel
        MuhurtaActivity.Business -> R.string.timing_best_business
        MuhurtaActivity.Study -> R.string.timing_best_study
        MuhurtaActivity.Property -> R.string.timing_best_property
        MuhurtaActivity.Interview -> R.string.timing_best_interview
        MuhurtaActivity.Spiritual -> R.string.timing_best_spiritual
    }

@Composable
private fun Windows(result: MuhurtaResult, holder: TimingHolder) {
    val offset = result.offsetMinutes
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val best = result.best
        if (best == null) {
            Text(stringResource(R.string.timing_no_windows), style = MaterialTheme.typography.bodyMedium, color = Muted)
        } else {
            BestWindow(best, offset, stringResource(holder.activity.bestLabel))
        }

        result.rahukaalaOn(holder.placeToday)?.let { rahu ->
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.timing_rahukaala_today), style = MaterialTheme.typography.bodySmall, color = Muted)
                Meta(
                    stringResource(R.string.timing_clock_range, TimingFormat.clock(rahu.rahuStart), TimingFormat.clock(rahu.rahuEnd)),
                    color = Ink,
                )
            }
        }

        if (result.windows.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardLabel(pluralStringResource(R.plurals.timing_windows_found, result.windows.size, result.windows.size))
                if (result.windows.size > 1) {
                    PillButton(
                        stringResource(if (holder.showAllWindows) R.string.timing_show_fewer else R.string.timing_see_all),
                        onClick = { holder.showAllWindows = !holder.showAllWindows },
                    )
                }
            }
        }

        if (holder.showAllWindows) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                result.windows.forEachIndexed { index, window ->
                    if (index > 0) HorizontalDivider(color = Hairline)
                    WindowRow(window, offset)
                }
            }
        }
    }
}

@Composable
private fun BestWindow(window: MuhurtaWindow, offset: Int, label: String) {
    val day = TimingFormat.weekdayDayMonth(TimingFormat.windowDay(window, offset))
    val range = stringResource(R.string.timing_window_range, TimingFormat.windowStart(window, offset), TimingFormat.windowEnd(window, offset))
    val description = stringResource(
        R.string.timing_window_best_description,
        label,
        stringResource(R.string.timing_window_description, day, range, window.score),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(Canvas, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardLabel(label)
            Text(day, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 24.sp))
            Meta(range)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(window.score.toString(), style = LiquidType.figure.copy(fontSize = 34.sp, lineHeight = 34.sp))
            CardLabel(stringResource(R.string.timing_score_of))
        }
    }
}

@Composable
private fun WindowRow(window: MuhurtaWindow, offset: Int) {
    val day = TimingFormat.weekdayDayMonth(TimingFormat.windowDay(window, offset))
    val range = stringResource(R.string.timing_window_range, TimingFormat.windowStart(window, offset), TimingFormat.windowEnd(window, offset))
    val description = stringResource(R.string.timing_window_description, day, range, window.score)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(day, style = MaterialTheme.typography.titleMedium)
            Meta(range)
        }
        Text(window.score.toString(), style = MaterialTheme.typography.titleSmall)
    }
}
