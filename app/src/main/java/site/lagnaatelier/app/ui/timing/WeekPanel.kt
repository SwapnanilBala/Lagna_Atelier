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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.timing.EnergyBand
import site.lagnaatelier.app.data.timing.EnergyDay
import site.lagnaatelier.app.data.timing.EnergyWeek
import site.lagnaatelier.app.data.timing.TimingFormat
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.Segmented
import site.lagnaatelier.app.ui.components.TickBadge
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.GlowApricot
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted
import java.time.LocalDate

private const val SHOWN_CARDS = 2
private val BarSlot = 96.dp

@Composable
internal fun WeekPanel(holder: TimingHolder, onRetry: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.timing_week_title)) {
            Segmented(
                listOf(stringResource(R.string.timing_week_this), stringResource(R.string.timing_week_next)),
                selected = holder.weekIndex,
                onSelect = { holder.weekIndex = it },
            )
        }
        LoadState(holder.week(holder.weekIndex), stringResource(R.string.timing_week_loading), onRetry) { week ->
            Week(week, holder.today)
        }
    }
}

@Composable
private fun Week(week: EnergyWeek, today: LocalDate) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WeekBars(week, today)
        Key(week.average)
        Lead(week.headlineTitle, week.headlineParagraph)
        val cards = week.cards.take(SHOWN_CARDS)
        if (cards.isNotEmpty()) {
            HorizontalDivider(color = Hairline)
            cards.forEach { card ->
                Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 1.dp)) { TickBadge() }
                    Column(Modifier.weight(1f)) {
                        Text(
                            card.bestDay?.let { stringResource(R.string.timing_week_card_title, card.title, TimingFormat.weekdayDay(it)) }
                                ?: card.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (card.body.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(card.body, style = MaterialTheme.typography.bodyMedium, color = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun bandName(band: EnergyBand): String = stringResource(
    when (band) {
        EnergyBand.High -> R.string.timing_band_high
        EnergyBand.Balanced -> R.string.timing_band_balanced
        EnergyBand.Low -> R.string.timing_band_low
    },
)

/* Seven bars, Saturday to Friday, read out as one sentence. */
@Composable
private fun WeekBars(week: EnergyWeek, today: LocalDate) {
    val days = week.days.map { day ->
        val item = stringResource(
            R.string.timing_week_day_description,
            TimingFormat.longWeekday(day.date),
            day.date.dayOfMonth,
            day.score,
            bandName(day.band).lowercase(),
        )
        if (day.date == today) stringResource(R.string.timing_week_day_today, item) else item
    }
    val description = stringResource(
        R.string.timing_week_chart_description,
        TimingFormat.dayMonth(week.start),
        TimingFormat.dayMonth(week.end),
        days.joinToString("; "),
        week.average,
    )
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.days.forEach { day -> DayBar(day, isToday = day.date == today) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DayBar(day: EnergyDay, isToday: Boolean) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            day.score.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = if (isToday) Ink else Muted,
        )
        Box(Modifier.fillMaxWidth().height(BarSlot), contentAlignment = Alignment.BottomCenter) {
            val shape = RoundedCornerShape(8.dp)
            val height = BarSlot * (day.score.coerceIn(4, 100) / 100f)
            val bar = Modifier.width(22.dp).height(height)
            when (day.band) {
                EnergyBand.High -> Box(bar.background(Ink, shape))
                EnergyBand.Balanced -> Box(bar.background(BarGrey, shape))
                EnergyBand.Low -> Box(bar.background(CardWhite, shape).border(1.dp, BarGrey, shape))
            }
        }
        Text(
            "${TimingFormat.shortWeekday(day.date)}\n${day.date.dayOfMonth}",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5.sp, lineHeight = 13.sp),
            color = if (isToday) Ink else Muted,
            fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier
                .size(5.dp)
                .background(if (isToday) GlowApricot else Color.Transparent, CircleShape),
        )
    }
}

@Composable
private fun Key(average: Int) {
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatch(EnergyBand.High)
        Swatch(EnergyBand.Balanced)
        Swatch(EnergyBand.Low)
        Spacer(Modifier.weight(1f))
        Meta(stringResource(R.string.timing_week_average, average))
    }
}

@Composable
private fun Swatch(band: EnergyBand) {
    val shape = RoundedCornerShape(3.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        val box = Modifier.size(9.dp)
        when (band) {
            EnergyBand.High -> Box(box.background(Ink, shape))
            EnergyBand.Balanced -> Box(box.background(BarGrey, shape))
            EnergyBand.Low -> Box(box.background(CardWhite, shape).border(1.dp, BarGrey, shape))
        }
        Text(bandName(band), style = MaterialTheme.typography.labelMedium, color = Muted)
    }
}
