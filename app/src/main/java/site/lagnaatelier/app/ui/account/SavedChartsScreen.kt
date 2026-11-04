package site.lagnaatelier.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.ChartStore
import site.lagnaatelier.app.data.SavedChart
import site.lagnaatelier.app.data.dayMonthYear
import site.lagnaatelier.app.data.formatDegree
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.Glow
import site.lagnaatelier.app.ui.components.LiquidIcons
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Muted

/*
 * Opened from the landing page's "Saved charts" (mock screen 06). The chart
 * opened last is lifted over the glow with its rising sign and Moon; the rest
 * sit in a flat list. Each has a bin button, and a long-press does the same,
 * opening an inline check before anything is deleted. [onSample] opens the
 * built-in sample chart.
 */
@Composable
fun SavedChartsScreen(
    store: ChartStore,
    /** Signed in with sync on: each chart says whether it is on the account. */
    syncing: Boolean,
    onOpen: (SavedChart) -> Unit,
    onStartNew: () -> Unit,
    onSample: () -> Unit,
    onBack: () -> Unit,
) {
    val charts = store.saved
    /* The key of the chart whose delete check is open, if any. */
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    val askDelete = { chart: SavedChart -> confirming = chart.key }
    val delete = { chart: SavedChart ->
        confirming = null
        store.delete(chart)
    }

    Box(Modifier.fillMaxSize().background(Canvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(count = charts.size, onBack = onBack)
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (charts.isEmpty()) {
                    item { EmptyState(onSample) }
                } else {
                    val first = charts.first()
                    item(key = "front") {
                        Box {
                            Glow(
                                Modifier
                                    .matchParentSize()
                                    .offset(x = (-40).dp, y = 0.dp)
                                    .requiredSize(width = 400.dp, height = 280.dp),
                            )
                            FrontCard(
                                chart = first,
                                syncing = syncing,
                                confirming = confirming == first.key,
                                onOpen = { onOpen(first) },
                                onAskDelete = { askDelete(first) },
                                onCancel = { confirming = null },
                                onDelete = { delete(first) },
                            )
                        }
                    }
                    val rest = charts.drop(1)
                    if (rest.isNotEmpty()) item(key = "rest") {
                        Panel(Modifier.fillMaxWidth()) {
                            rest.forEachIndexed { index, chart ->
                                if (index > 0) HorizontalDivider(color = Hairline)
                                SavedChartRow(
                                    chart = chart,
                                    onOpen = { onOpen(chart) },
                                    onDelete = { askDelete(chart) },
                                    syncing = syncing,
                                )
                                if (confirming == chart.key) {
                                    DeleteConfirm(
                                        name = chart.details.name,
                                        onAccount = chart.remoteId != null,
                                        onCancel = { confirming = null },
                                        onDelete = { delete(chart) },
                                    )
                                }
                            }
                        }
                    }
                    item(key = "note") {
                        Text(
                            stringResource(R.string.saved_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            PillButton(stringResource(R.string.saved_add), onClick = onStartNew, icon = AccountIcons.Plus)
        }
    }
}

/* The back circle and the title, as the reading screen's top bar. */
@Composable
private fun Header(count: Int, onBack: () -> Unit) {
    val backLabel = stringResource(R.string.reading_back)
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CardWhite)
                .border(1.dp, Hairline, CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = backLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(LiquidIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(stringResource(R.string.saved_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.saved_count, count, ChartStore.MAX_SAVED),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun FrontCard(
    chart: SavedChart,
    syncing: Boolean,
    confirming: Boolean,
    onOpen: () -> Unit,
    onAskDelete: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val details = chart.details
    FloatCard(Modifier.fillMaxWidth(), spacing = 12.dp, padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                CardLabel(
                    if (syncing) "${stringResource(R.string.saved_opened_last)} · ${chart.syncLabel()}"
                    else stringResource(R.string.saved_opened_last),
                )
                Text(
                    details.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${details.placeShort()} · ${details.date.dayMonthYear()} · ${details.timeLabel()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconCircle(AccountIcons.Bin, stringResource(R.string.saved_delete_button, details.name), onAskDelete)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(stringResource(R.string.saved_rising), chart.ascendant.label, chart.risingDegree())
            Tile(
                stringResource(R.string.saved_moon),
                chart.moonSign?.label ?: stringResource(R.string.saved_missing_value),
                chart.moonDegree?.let(::formatDegree) ?: stringResource(R.string.saved_missing_value),
            )
        }
        if (confirming) {
            DeleteConfirm(details.name, onCancel = onCancel, onDelete = onDelete, onAccount = chart.remoteId != null)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardLabel(chart.traditionText(), Modifier.weight(1f).padding(end = 8.dp))
            PillButton(stringResource(R.string.saved_open), onClick = onOpen)
        }
    }
}

/* Flat, as the mock's "tiles flat": hairline on white inside the lifted card. */
@Composable
private fun RowScope.Tile(label: String, value: String, meta: String) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .weight(1f)
            .background(CardWhite, shape)
            .border(1.dp, Hairline, shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CardLabel(label)
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(meta, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun EmptyState(onSample: () -> Unit) {
    Box {
        Glow(
            Modifier
                .matchParentSize()
                .offset(x = (-40).dp, y = (-20).dp)
                .requiredSize(width = 400.dp, height = 260.dp),
        )
        FloatCard(Modifier.fillMaxWidth(), spacing = 14.dp, padding = 16.dp) {
            Text(
                stringResource(R.string.saved_empty),
                style = MaterialTheme.typography.titleSmall,
            )
            GhostChip(stringResource(R.string.saved_sample), onClick = onSample)
        }
    }
}
