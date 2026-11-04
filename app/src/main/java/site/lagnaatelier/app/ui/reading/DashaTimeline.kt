package site.lagnaatelier.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.data.Period
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.GlowApricot
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * The mahadashas laid end to end on one scale, like a price chart over a life.
 * Every position comes from the dates, so a bar's width is its length in years.
 *
 * The engine lists the whole 120-year Vimshottari cycle; the bar shows the
 * first [YEARS_SHOWN] years from birth, and a period too short to draw (the
 * fortnight of Rahu the sample chart is born into) is left out.
 */
private const val YEARS_SHOWN = 80

@Composable
fun DashaTimeline(
    periods: List<Period>,
    current: Period,
    today: LocalDate,
    modifier: Modifier = Modifier,
    from: LocalDate = LocalDate.of(periods.first().start.year, 1, 1),
    to: LocalDate = LocalDate.of(((from.year + YEARS_SHOWN + 9) / 10) * 10, 1, 1),
) {
    val span = ChronoUnit.DAYS.between(from, to).toFloat()
    fun fraction(date: LocalDate) = (ChronoUnit.DAYS.between(from, date) / span).coerceIn(0f, 1f)

    val visible = periods.filter { it.end > from && it.start < to }
    val description = visible.joinToString(", ") { "${it.lord} ${it.start.year} to ${it.end.year}" } +
        ". Now in ${current.lord}."

    Column(modifier.semantics { contentDescription = description }) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(34.dp)) {
            visible.forEach { period ->
                val left = fraction(period.start)
                val width = fraction(period.end) - left
                val isCurrent = period == current
                val barWidth = maxWidth * width - 3.dp
                if (barWidth < 8.dp) return@forEach
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier
                        .offset(x = maxWidth * left)
                        .width(barWidth)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(if (isCurrent) Ink else Canvas)
                        .border(1.dp, if (isCurrent) Ink else Hairline, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (barWidth >= 20.dp) {
                        Text(
                            if (barWidth < 44.dp) planetAbbreviation(period.lord) else planetName(period.lord),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) CardWhite else Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
            // Today, as a thin glow-coloured mark across the bars.
            if (today > from && today < to) {
                Box(
                    Modifier
                        .offset(x = maxWidth * fraction(today) - 1.dp, y = (-5).dp)
                        .width(2.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(GlowApricot),
                )
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            /* Round years every twenty, whatever year the reader was born in. */
            val firstLabel = ((from.year + 19) / 20) * 20
            val years = (firstLabel..to.year step 20).toList()
            years.forEach { year ->
                val at = fraction(LocalDate.of(year, 1, 1))
                val x = maxWidth * at
                // Centred under its tick, but kept inside the panel at either end.
                val shift = when {
                    at < 0.06f -> 0.dp
                    at > 0.94f -> 26.dp
                    else -> 13.dp
                }
                Text(
                    "$year",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f),
                    color = Muted,
                    modifier = Modifier.offset(x = x - shift, y = 4.dp),
                )
            }
        }
    }
}
