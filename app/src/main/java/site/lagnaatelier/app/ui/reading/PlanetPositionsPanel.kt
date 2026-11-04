package site.lagnaatelier.app.ui.reading

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.formatDegree
import site.lagnaatelier.app.data.reading.HouseSystemName
import site.lagnaatelier.app.data.reading.PlanetRow
import site.lagnaatelier.app.data.reading.PlanetTable
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Muted

/*
 * Every graha's sign, degree and house, as a table with mono figures: the
 * web's phone page (app/m/insights) does the same, because there is no hover
 * on a touch screen to hang a planet card on. The chart above answers "what
 * does my chart look like"; this answers "what exactly is where".
 *
 * The house column counts by sign, so it matches the number of the chart's
 * cell. Outside Whole Sign the house the readings use (the bhava) can differ,
 * so it gets a column of its own and a line saying why.
 */
@Composable
internal fun PlanetPositionsPanel(table: PlanetTable) {
    val system = table.houseSystem
    Panel {
        SectionHead(
            stringResource(R.string.positions_title),
            subtitle = stringResource(R.string.positions_count, table.rows.size),
        )
        Column {
            TableRow(header = true) {
                Cell(stringResource(R.string.positions_col_graha), Modifier.weight(1.5f), header = true)
                Cell(stringResource(R.string.positions_col_sign), Modifier.weight(1.3f), header = true)
                Cell(stringResource(R.string.positions_col_degree), Modifier.width(56.dp), header = true, numeric = true)
                Cell(stringResource(R.string.positions_col_house), Modifier.width(36.dp), header = true, numeric = true)
                if (system != null) {
                    Cell(stringResource(R.string.positions_col_bhava), Modifier.width(48.dp), header = true, numeric = true)
                }
            }
            table.rows.forEach { row ->
                HorizontalDivider(color = Hairline)
                PlanetTableRow(row, showBhava = system != null)
            }
        }
        if (table.anyFlags) Legend(stringResource(R.string.positions_flag_legend))
        if (system != null) Legend(stringResource(R.string.positions_bhava_legend, stringResource(system.label)))
    }
}

@Composable
private fun PlanetTableRow(row: PlanetRow, showBhava: Boolean) {
    val degree = formatDegree(row.degreeInSign)
    val name = planetName(row.name)
    val sign = signName(row.sign)
    val flags = listOfNotNull(
        stringResource(R.string.positions_retrograde_description).takeIf { row.retrograde },
        stringResource(R.string.positions_combust_description).takeIf { row.combust },
    )
    /* Read as one sentence, not five cells; the Rx and Cmb marks are spelled out. */
    val description = (
        if (showBhava) stringResource(R.string.positions_row_bhava_description, name, sign, degree, row.signHouse, row.bhava)
        else stringResource(R.string.positions_row_description, name, sign, degree, row.signHouse)
        ) + flags.joinToString("") { ", $it" }
    val retrograde = stringResource(R.string.positions_flag_retrograde)
    val combust = stringResource(R.string.positions_flag_combust)

    TableRow(Modifier.clearAndSetSemantics { contentDescription = description }) {
        Text(
            buildAnnotatedString {
                append(name)
                withStyle(SpanStyle(color = Muted, fontSize = MaterialTheme.typography.labelSmall.fontSize)) {
                    if (row.retrograde) append(" $retrograde")
                    if (row.combust) append(" $combust")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.5f),
        )
        Cell(sign, Modifier.weight(1.3f))
        Cell(degree, Modifier.width(56.dp), numeric = true)
        Cell("${row.signHouse}", Modifier.width(36.dp), numeric = true)
        if (showBhava) Cell("${row.bhava}", Modifier.width(48.dp), numeric = true)
    }
}

@Composable
private fun TableRow(modifier: Modifier = Modifier, header: Boolean = false, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(vertical = if (header) 4.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun Cell(text: String, modifier: Modifier, header: Boolean = false, numeric: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        style = when {
            header -> MaterialTheme.typography.labelMedium
            numeric -> MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
            else -> MaterialTheme.typography.bodyMedium
        },
        color = if (header) Muted else Color.Unspecified,
        textAlign = if (numeric) TextAlign.End else TextAlign.Start,
        maxLines = 1,
    )
}

/** The house system's name as the bhava legend says it. */
@get:StringRes
internal val HouseSystemName.label: Int
    get() = when (this) {
        HouseSystemName.Equal -> R.string.house_system_equal
        HouseSystemName.Placidus -> R.string.house_system_placidus
        HouseSystemName.Koch -> R.string.house_system_koch
        HouseSystemName.Campanus -> R.string.house_system_campanus
        HouseSystemName.Regiomontanus -> R.string.house_system_regiomontanus
        HouseSystemName.Porphyry -> R.string.house_system_porphyry
    }
