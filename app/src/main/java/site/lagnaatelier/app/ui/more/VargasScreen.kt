package site.lagnaatelier.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.vargas.KeyVarga
import site.lagnaatelier.app.data.vargas.Varga
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.LiquidChip
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.reading.SouthIndianChart
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted

/*
 * The website's varga atlas (/insights/divisional-charts) and a varga's own
 * page (/insights/divisional-charts/[division]): every chart the engine casts,
 * the ten key ones first, each drawn as the app's South Indian chart with the
 * site's guidance and, for the key ten, a note written for these placements.
 *
 * A loaded chart already carries its vargas; the sample is cast again for
 * them. The notes for all ten key vargas are one call, made when the atlas
 * first opens, as the website makes it.
 */
@Composable
internal fun VargasScreen(
    holder: MoreHolder,
    cast: List<Varga>,
    timeIsEstimated: Boolean,
    tradition: String,
    division: Int?,
    onDivision: (Int?) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(holder) { holder.openVargas(cast) }
    val vargas = (holder.vargas as? Fetch.Ready)?.value
    val selected = vargas?.firstOrNull { it.division == division }

    if (selected != null && vargas != null) {
        VargaDetail(selected, vargas, holder, timeIsEstimated, tradition, onDivision, onBack)
        return
    }
    MoreScaffold(stringResource(R.string.more_varga_title), stringResource(R.string.more_varga_lead), onBack) {
        if (timeIsEstimated) item { CardLabel(stringResource(R.string.more_varga_time_caution), Modifier.padding(horizontal = 4.dp)) }
        val fetch = holder.vargas
        if (fetch !is Fetch.Ready) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    FetchState(fetch, stringResource(R.string.more_varga_loading), onRetry = { holder.openVargas(cast, force = true) }) {}
                }
            }
            return@MoreScaffold
        }
        if (fetch.value.isEmpty()) {
            item { Panel(Modifier.fillMaxWidth()) { CardLabel(stringResource(R.string.more_varga_missing)) } }
            return@MoreScaffold
        }
        val (key, supporting) = fetch.value.partition { it.key != null }
        if (key.isNotEmpty()) {
            item { GroupHead(stringResource(R.string.more_varga_key_heading), stringResource(R.string.more_varga_key_lead)) }
            key.chunked(2).forEach { pair -> item { TileRow(pair, onDivision) } }
        }
        if (supporting.isNotEmpty()) {
            item { GroupHead(stringResource(R.string.more_varga_support_heading), stringResource(R.string.more_varga_support_lead)) }
            supporting.chunked(2).forEach { pair -> item { TileRow(pair, onDivision) } }
        }
    }
}

@Composable
private fun GroupHead(title: String, lead: String) {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(lead, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun TileRow(pair: List<Varga>, onDivision: (Int?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pair.forEach { VargaTile(it, onClick = { onDivision(it.division) }) }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun RowScope.VargaTile(varga: Varga, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val key = varga.key
    Column(
        Modifier
            .weight(1f)
            .background(CardWhite, shape)
            .border(1.dp, Hairline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(varga.label, style = MaterialTheme.typography.titleSmall)
        Text(
            if (key != null) stringResource(key.title) else varga.description,
            style = MaterialTheme.typography.bodySmall,
            color = Ink,
            maxLines = 2,
        )
        if (key != null) Text(stringResource(key.sensitivity.label), style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun VargaDetail(
    varga: Varga,
    all: List<Varga>,
    holder: MoreHolder,
    timeIsEstimated: Boolean,
    tradition: String,
    onDivision: (Int?) -> Unit,
    onBack: () -> Unit,
) {
    val key = varga.key
    val name = key?.let { stringResource(it.title) }
    val title = if (name != null) "${varga.label} · $name" else varga.label
    MoreScaffold(title, lead = null, onBack = { onDivision(null) }) {
        item {
            /* Opens with the chosen chart in view, a couple of its neighbours before it. */
            val chips = rememberLazyListState(initialFirstVisibleItemIndex = (all.indexOf(varga) - 2).coerceAtLeast(0))
            LazyRow(state = chips, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(all, key = { it.division }) { other ->
                    LiquidChip(other.label, selected = other.division == varga.division, onClick = { onDivision(other.division) })
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                CardLabel(if (key != null) stringResource(key.focus) else varga.description)
                val ascendant = varga.ascendant
                if (ascendant != null) {
                    SouthIndianChart(
                        varga.placements,
                        ascendant = ascendant,
                        title = name ?: varga.label,
                        subtitle = stringResource(R.string.more_varga_subtitle, varga.label, tradition),
                    )
                } else {
                    CardLabel(stringResource(R.string.more_varga_no_ascendant))
                }
                if (varga.extended) Text(stringResource(R.string.more_varga_extended), style = MaterialTheme.typography.bodySmall, color = Muted)
                if (key != null) {
                    Text(stringResource(key.summary), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(stringResource(R.string.more_varga_supporting_note), style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                if (timeIsEstimated && varga.division != 1) {
                    CardLabel(stringResource(R.string.more_varga_time_caution))
                }
            }
        }
        if (key != null) {
            item { NotePanel(varga.division, holder) }
            item { GuidancePanel(key) }
        }
        item { PositionsPanel(varga) }
    }
}

/* The note written for this chart's placements, for a key varga only: the route writes no other. */
@Composable
private fun NotePanel(division: Int, holder: MoreHolder) {
    Panel(Modifier.fillMaxWidth()) {
        CardLabel(stringResource(R.string.more_varga_note_kicker))
        when (val notes = holder.vargaNotes) {
            is Fetch.Ready -> {
                val note = notes.value[division]
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.more_varga_note_after), style = MaterialTheme.typography.bodySmall, color = Muted)
                } else {
                    Text(stringResource(R.string.more_varga_note_missing), style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
            }
            is Fetch.Failed -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(notes.message), style = MaterialTheme.typography.bodyMedium, color = Muted)
                GhostChip(stringResource(R.string.more_retry), onClick = { holder.retryNotes() })
            }
            Fetch.Idle, Fetch.Loading -> CardLabel(stringResource(R.string.more_varga_note_pending))
        }
    }
}

@Composable
private fun GuidancePanel(key: KeyVarga) {
    Panel(Modifier.fillMaxWidth()) {
        listOf(
            R.string.more_varga_read_with to key.readWith,
            R.string.more_varga_question to key.question,
            R.string.more_varga_reliability to key.timeNote,
        ).forEachIndexed { index, (heading, body) ->
            if (index > 0) HorizontalDivider(color = Hairline)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SubHead(stringResource(heading))
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PositionsPanel(varga: Varga) {
    Panel(Modifier.fillMaxWidth()) {
        SubHead(stringResource(R.string.more_varga_positions))
        Row(Modifier.fillMaxWidth()) {
            Cell(stringResource(R.string.more_varga_col_point), header = true)
            Cell(stringResource(R.string.more_varga_col_d1), header = true)
            Cell(varga.label, header = true)
        }
        varga.positions.forEach { position ->
            HorizontalDivider(color = Hairline)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Cell(position.name)
                Cell(position.rashi.label)
                Box(Modifier.weight(1f)) {
                    Column {
                        Text(position.sign.label, style = MaterialTheme.typography.bodyMedium)
                        if (position.repeats) CardLabel(stringResource(R.string.more_varga_repeats))
                    }
                }
            }
        }
        CardLabel(stringResource(R.string.more_varga_repeat_count, varga.positions.count { it.repeats }, varga.positions.size))
        Text(stringResource(R.string.more_varga_positions_note), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun RowScope.Cell(text: String, header: Boolean = false) {
    Text(
        text,
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        color = if (header) Muted else Ink,
        modifier = Modifier.weight(1f),
    )
}
