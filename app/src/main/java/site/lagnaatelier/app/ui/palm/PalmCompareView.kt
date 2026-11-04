package site.lagnaatelier.app.ui.palm

import site.lagnaatelier.app.ui.components.DockRoom
import site.lagnaatelier.app.ui.components.NavRoom
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.palm.LineKey
import site.lagnaatelier.app.data.palm.PalmCopyGuard
import site.lagnaatelier.app.data.palm.PalmDiff
import site.lagnaatelier.app.data.palm.PalmDiffer
import site.lagnaatelier.app.data.palm.SavedPalmReading
import site.lagnaatelier.app.data.palm.StrengthDirection
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.components.TickBadge
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted

/*
 * Two saved readings side by side, after the web's
 * insights/palm-history/compare: pick two, then see what changed in the
 * lines, the mounts, the markings and the summary. Read only from the
 * phone's history (PalmDiffer); the site is never asked.
 */
@Composable
internal fun PalmCompareView(model: PalmModel) {
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showing by rememberSaveable { mutableStateOf(false) }
    val saved = model.saved
    val pair = picked.mapNotNull { id -> saved.firstOrNull { it.id == id } }

    if (showing && pair.size == 2) {
        BackHandler { showing = false }
        val diff = remember(pair[0].id, pair[1].id) { PalmDiffer.diff(pair[0], pair[1]) }
        CompareResult(model, diff, onBack = { showing = false })
    } else {
        BackHandler { model.view = PalmView.History }
        ComparePicker(
            model = model,
            saved = saved,
            picked = pair.map { it.id },
            onToggle = { id ->
                /* A third pick replaces the older of the two. */
                picked = when {
                    id in picked -> picked - id
                    picked.size < 2 -> picked + id
                    else -> picked.drop(1) + id
                }
            },
            onCompare = { showing = true },
        )
    }
}

@Composable
private fun ComparePicker(
    model: PalmModel,
    saved: List<SavedPalmReading>,
    picked: List<String>,
    onToggle: (String) -> Unit,
    onCompare: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = DockRoom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PalmHeader(
                    title = stringResource(R.string.palm_compare_pick_title),
                    subtitle = stringResource(R.string.palm_compare_picked, picked.size),
                    onBack = { model.view = PalmView.History },
                )
            }
            item {
                Text(
                    stringResource(R.string.palm_compare_pick_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            item {
                Panel(Modifier.fillMaxWidth()) {
                    saved.forEachIndexed { index, record ->
                        if (index > 0) HorizontalDivider(color = Hairline)
                        val on = record.id in picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .toggleable(value = on, role = Role.Checkbox) { onToggle(record.id) },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Thumb(rememberCompareThumb(model, record), 50.dp, 62.dp)
                            Column(Modifier.weight(1f)) {
                                Text(dayLabel(record.createdAtMillis), style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp))
                                Text(
                                    PalmCopyGuard.clean(record.reading.overallSummary),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                    color = Muted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (on) TickBadge() else EmptyDisc()
                        }
                    }
                }
            }
        }
        PalmDock {
            PillButton(stringResource(R.string.palm_compare_go), onClick = onCompare, enabled = picked.size == 2)
        }
    }
}

@Composable
private fun EmptyDisc() {
    Box(Modifier.size(22.dp).border(1.dp, Hairline, CircleShape))
}

@Composable
private fun CompareResult(model: PalmModel, diff: PalmDiff, onBack: () -> Unit) {
    val dates = stringResource(R.string.palm_compare_dates, dayLabel(diff.a.createdAtMillis), dayLabel(diff.b.createdAtMillis))
    val apart = if (diff.daysApart > 0) {
        pluralStringResource(R.plurals.palm_compare_days_apart, diff.daysApart, diff.daysApart)
    } else {
        stringResource(R.string.palm_compare_same_day)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = NavRoom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PalmHeader(title = stringResource(R.string.palm_compare_title), subtitle = "$dates · $apart", onBack = onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Side(model, diff.a, stringResource(R.string.palm_compare_a), Modifier.weight(1f))
                Side(model, diff.b, stringResource(R.string.palm_compare_b), Modifier.weight(1f))
            }
        }
        item { StrengthPanel(diff) }
        if (diff.lineVisibilityChanges.isNotEmpty()) item { VisibilityPanel(diff) }
        if (diff.markingsChanged) item {
            DeltaPanel(
                stringResource(R.string.palm_compare_markings),
                stringResource(R.string.palm_compare_newly_visible) to diff.newMarkings,
                stringResource(R.string.palm_compare_no_longer_visible) to diff.lostMarkings,
            )
        }
        if (diff.mountsChanged) item {
            DeltaPanel(
                stringResource(R.string.palm_compare_mounts),
                stringResource(R.string.palm_compare_newly_prominent) to diff.newMounts,
                stringResource(R.string.palm_compare_no_longer_prominent) to diff.lostMounts,
            )
        }
        item { SummaryPanel(diff) }
    }
}

/* One reading's card: its tag and date, the photo, and a way into the whole reading. */
@Composable
private fun Side(model: PalmModel, record: SavedPalmReading, tag: String, modifier: Modifier) {
    FloatCard(modifier, spacing = 8.dp, padding = 10.dp) {
        CardLabel(tag)
        Text(dayLabel(record.createdAtMillis), style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Thumb(rememberCompareThumb(model, record), 120.dp, 140.dp)
        }
        Text(
            stringResource(R.string.palm_compare_open_full),
            style = MaterialTheme.typography.labelMedium,
            color = Ink,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button) { model.openSaved(record) }
                .padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun StrengthPanel(diff: PalmDiff) {
    Panel(Modifier.fillMaxWidth()) {
        CompareHead(stringResource(R.string.palm_compare_line_strength))
        if (diff.allLinesUnchanged) {
            Text(stringResource(R.string.palm_compare_no_strength), style = MaterialTheme.typography.bodyMedium, color = Muted)
        } else {
            diff.lineStrengthChanges.forEachIndexed { index, change ->
                if (index > 0) HorizontalDivider(color = Hairline)
                ChangeRow(
                    line = change.line,
                    fromTo = stringResource(R.string.palm_compare_from_to, strengthWord(change.from), strengthWord(change.to)),
                    verdict = directionWord(change.direction),
                    arrow = when (change.direction) {
                        StrengthDirection.Stronger -> "↑"
                        StrengthDirection.Weaker -> "↓"
                        StrengthDirection.Unchanged -> "→"
                    },
                )
            }
        }
    }
}

@Composable
private fun VisibilityPanel(diff: PalmDiff) {
    Panel(Modifier.fillMaxWidth()) {
        CompareHead(stringResource(R.string.palm_compare_line_visibility))
        diff.lineVisibilityChanges.forEachIndexed { index, change ->
            if (index > 0) HorizontalDivider(color = Hairline)
            ChangeRow(
                line = change.line,
                fromTo = stringResource(R.string.palm_compare_from_to, visibilityWord(change.from), visibilityWord(change.to)),
                verdict = null,
                arrow = null,
            )
        }
    }
}

/* A line's name, what it was and is now, and the one-word verdict when there is one. */
@Composable
private fun ChangeRow(line: LineKey, fromTo: String, verdict: String?, arrow: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(lineName(line)), style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp))
            Text(fromTo, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        if (verdict != null && arrow != null) {
            InkChip("$arrow $verdict", on = arrow != "→")
        }
    }
}

@Composable
private fun DeltaPanel(title: String, gained: Pair<String, List<String>>, lost: Pair<String, List<String>>) {
    Panel(Modifier.fillMaxWidth()) {
        CompareHead(title)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(gained, lost).forEach { (heading, items) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardLabel(heading)
                    if (items.isEmpty()) {
                        Text(stringResource(R.string.palm_compare_none), style = MaterialTheme.typography.bodyMedium, color = Muted)
                    }
                    items.forEach { Text("· ${PalmCopyGuard.clean(it)}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun SummaryPanel(diff: PalmDiff) {
    Panel(Modifier.fillMaxWidth()) {
        CompareHead(stringResource(R.string.palm_compare_summary))
        listOf(stringResource(R.string.palm_compare_a) to diff.aSummary, stringResource(R.string.palm_compare_b) to diff.bSummary)
            .forEachIndexed { index, (tag, summary) ->
                if (index > 0) HorizontalDivider(color = Hairline)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardLabel(tag)
                    Text(
                        PalmCopyGuard.clean(summary).ifEmpty { stringResource(R.string.palm_compare_no_summary) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
    }
}

@Composable
private fun CompareHead(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun strengthWord(value: String?): String = stringResource(
    when (value) {
        "strong" -> R.string.palm_strength_strong
        "moderate" -> R.string.palm_strength_moderate
        "faint" -> R.string.palm_strength_faint
        "absent" -> R.string.palm_strength_absent
        else -> R.string.palm_compare_not_read
    },
)

@Composable
private fun visibilityWord(value: String?): String = stringResource(
    when (value) {
        "clear" -> R.string.palm_visibility_clear
        "partial" -> R.string.palm_visibility_partial
        "faint" -> R.string.palm_visibility_faint
        "not_detected" -> R.string.palm_visibility_not_detected
        else -> R.string.palm_compare_not_read
    },
)

@Composable
private fun directionWord(direction: StrengthDirection): String = stringResource(
    when (direction) {
        StrengthDirection.Stronger -> R.string.palm_compare_stronger
        StrengthDirection.Weaker -> R.string.palm_compare_weaker
        StrengthDirection.Unchanged -> R.string.palm_compare_unchanged
    },
)

@Composable
private fun rememberCompareThumb(model: PalmModel, record: SavedPalmReading): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, record.id) { value = model.thumbnail(record) }
    return bitmap
}
