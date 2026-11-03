package site.lagnaatelier.app.ui.reading

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.Placement
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.LiquidType
import site.lagnaatelier.app.ui.theme.MidTone
import site.lagnaatelier.app.ui.theme.Muted

/*
 * The North Indian chart: houses fixed, the ascendant the top diamond, filled
 * as the South Indian chart fills its rising square, the signs rotating and
 * each house carrying its sign's number. The geometry is NorthIndianGeometry,
 * which is tested; this file only draws it.
 *
 * The diamond has no middle to write in, so the chart's name and tradition go
 * under it rather than inside.
 */
@Composable
fun NorthIndianChart(
    placements: List<Placement>,
    ascendant: Sign,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val ascLabel = stringResource(R.string.reading_chart_ascendant)
    val names = signNames()
    val abbreviations = abbreviations(placements)
    val byHouse = remember(placements, ascendant) { NorthIndianGeometry.placementsByHouse(placements, ascendant) }
    val description = buildString {
        append(stringResource(R.string.chart_north_description, names.getValue(ascendant)))
        byHouse.forEach { (house, bodies) ->
            if (bodies.isEmpty()) return@forEach
            val sign = NorthIndianGeometry.signForHouse(ascendant, house)
            append(' ')
            append(stringResource(R.string.chart_house_holds, house, names.getValue(sign), bodies.joinToString(", ") { abbreviations.getValue(it.abbreviation) }))
        }
    }
    val shape = RoundedCornerShape(12.dp)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .border(1.dp, Hairline, shape)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            val side = maxWidth
            val fill = Ink
            val line = MidTone
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width
                fun at(p: ChartPoint) = Offset(p.x * s, p.y * s)
                val rising = NorthIndianGeometry.HOUSE_POLYGONS.getValue(1)
                drawPath(
                    Path().apply {
                        moveTo(at(rising[0]).x, at(rising[0]).y)
                        rising.drop(1).forEach { lineTo(at(it).x, at(it).y) }
                        close()
                    },
                    fill,
                )
                val stroke = 1.dp.toPx()
                drawLine(line, Offset(0f, 0f), Offset(s, s), stroke)
                drawLine(line, Offset(s, 0f), Offset(0f, s), stroke)
                val diamond = listOf(Offset(s / 2, 0f), Offset(s, s / 2), Offset(s / 2, s), Offset(0f, s / 2))
                diamond.forEachIndexed { index, from -> drawLine(line, from, diamond[(index + 1) % 4], stroke) }
            }

            NorthIndianGeometry.HOUSES.forEach { house ->
                val sign = NorthIndianGeometry.signForHouse(ascendant, house)
                val isAsc = house == 1
                val point = NorthIndianGeometry.SIGN_NUMBER_POINTS.getValue(house)
                Box(
                    Modifier
                        .offset(x = side * point.x - 12.dp, y = side * point.y - 8.dp)
                        .size(width = 24.dp, height = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${NorthIndianGeometry.signNumber(sign)}",
                        style = LiquidType.gridLabel,
                        color = if (isAsc) CardWhite else Muted,
                    )
                }

                val bodies = byHouse.getValue(house).map { abbreviations.getValue(it.abbreviation) }
                val labels = if (isAsc) listOf(ascLabel) + bodies else bodies
                if (labels.isEmpty()) return@forEach
                val box = NorthIndianGeometry.LABEL_BOXES.getValue(house)
                val base = MaterialTheme.typography.titleMedium
                /* A crowded triangle drops a size rather than spill over its lines. */
                val scale = if (labels.size > 4) 0.7f else 0.84f
                Box(
                    Modifier
                        .offset(x = side * box.x, y = side * box.y)
                        .size(width = side * box.w, height = side * box.h),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        labels.joinToString(" "),
                        style = base.copy(fontSize = base.fontSize * scale, lineHeight = base.lineHeight * scale),
                        color = if (isAsc) CardWhite else Ink,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(
            "$title · $subtitle",
            style = LiquidType.gridLabel.copy(fontSize = LiquidType.gridLabel.fontSize * 1.1f),
            color = Muted,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
