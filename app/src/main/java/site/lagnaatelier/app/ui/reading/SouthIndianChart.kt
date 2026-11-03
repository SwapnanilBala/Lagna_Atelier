package site.lagnaatelier.app.ui.reading

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.Placement
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.ui.components.Glow
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.LiquidType
import site.lagnaatelier.app.ui.theme.Muted

/*
 * The South Indian chart, one of the two styles (RasiChart picks): the signs
 * sit in fixed squares, Pisces top left and running clockwise, and the
 * planets are placed by sign. Never by house -- in this app a planet's
 * `house` is the bhava, which can differ from its sign square (the web app's
 * rule, lib/chart-style.ts and wheelPlacement).
 */

/** Column and row of each sign's square, Pisces at (0, 0). */
internal fun southIndianCell(sign: Sign): Pair<Int, Int> = when (sign) {
    Sign.Pisces -> 0 to 0
    Sign.Aries -> 1 to 0
    Sign.Taurus -> 2 to 0
    Sign.Gemini -> 3 to 0
    Sign.Cancer -> 3 to 1
    Sign.Leo -> 3 to 2
    Sign.Virgo -> 3 to 3
    Sign.Libra -> 2 to 3
    Sign.Scorpio -> 1 to 3
    Sign.Sagittarius -> 0 to 3
    Sign.Capricorn -> 0 to 2
    Sign.Aquarius -> 0 to 1
}

@Composable
fun SouthIndianChart(
    placements: List<Placement>,
    ascendant: Sign,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val ascLabel = stringResource(R.string.reading_chart_ascendant)
    val names = signNames()
    val abbreviations = abbreviations(placements)
    val description = buildString {
        append(stringResource(R.string.chart_south_description, names.getValue(ascendant)))
        Sign.entries.forEach { sign ->
            val here = placements.filter { it.sign == sign }.map { abbreviations.getValue(it.abbreviation) }
            if (here.isNotEmpty()) append(' ').append(stringResource(R.string.chart_sign_holds, names.getValue(sign), here.joinToString(", ")))
        }
    }
    val shape = RoundedCornerShape(12.dp)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .border(1.dp, Hairline, shape)
            .semantics { contentDescription = description },
    ) {
        val cell = maxWidth / 4

        Sign.entries.forEach { sign ->
            val (col, row) = southIndianCell(sign)
            val isAsc = sign == ascendant
            val bodies = placements.filter { it.sign == sign }.joinToString(" ") { abbreviations.getValue(it.abbreviation) }
            Column(
                Modifier
                    .offset(x = cell * col, y = cell * row)
                    .size(cell)
                    .background(if (isAsc) Ink else Color.Transparent)
                    .border(0.5.dp, Hairline)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(names.getValue(sign), style = LiquidType.gridLabel, color = if (isAsc) CardWhite else Muted, maxLines = 1)
                Text(
                    if (isAsc) listOf(ascLabel, bodies).filter { it.isNotEmpty() }.joinToString(" ") else bodies,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.84f),
                    color = if (isAsc) CardWhite else Ink,
                )
            }
        }

        Box(
            Modifier
                .offset(x = cell, y = cell)
                .size(cell * 2)
                .border(0.5.dp, Hairline),
            contentAlignment = Alignment.Center,
        ) {
            Glow(Modifier.fillMaxSize().padding(cell * 0.35f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = LiquidType.gridLabel.copy(fontSize = LiquidType.gridLabel.fontSize * 1.05f), color = Muted, textAlign = TextAlign.Center)
            }
        }
    }
}
