package site.lagnaatelier.app.ui.palm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.palm.PalmCopyGuard
import site.lagnaatelier.app.data.palm.SavedPalmReading
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.Glow
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Muted

/*
 * Readings saved on this phone (the mock's screen 04): the latest lifted,
 * the rest in a flat list, each with an inline delete confirm.
 */
@Composable
internal fun PalmHistoryView(model: PalmModel) {
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    val saved = model.saved
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = DockRoom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PalmHeader(
                    title = stringResource(R.string.palm_history_title),
                    subtitle = if (saved.isEmpty()) null else pluralStringResource(R.plurals.palm_history_count, saved.size, saved.size),
                    onBack = { model.view = PalmView.Capture },
                )
            }
            if (model.historyLoaded && saved.isEmpty()) {
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.palm_history_empty), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            saved.firstOrNull()?.let { latest ->
                item(key = latest.id) {
                    Box {
                        Glow(Modifier.offset(x = (-40).dp, y = (-30).dp).requiredSize(width = 400.dp, height = 260.dp))
                        FloatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { model.openSaved(latest) },
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Thumb(rememberThumb(model, latest), 70.dp, 86.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    CardLabel(stringResource(R.string.palm_history_latest))
                                    val (headline, rest) = splitSummary(latest)
                                    Text(headline, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(meta(latest), style = MaterialTheme.typography.labelSmall, color = Muted)
                                    if (rest.isNotEmpty()) {
                                        Text(rest, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            DeleteRow(confirming == latest.id, onAsk = { confirming = latest.id }, onCancel = { confirming = null }) {
                                confirming = null
                                model.delete(latest)
                            }
                        }
                    }
                }
            }
            if (saved.size > 1) {
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        PanelHead(stringResource(R.string.palm_history_earlier))
                        saved.drop(1).forEachIndexed { index, record ->
                            if (index > 0) HorizontalDivider(color = Hairline)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { model.openSaved(record) },
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Thumb(rememberThumb(model, record), 50.dp, 62.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(dayLabel(record.createdAtMillis), style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp))
                                        Text(meta(record, withDate = false), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = Muted)
                                    }
                                }
                                DeleteRow(confirming == record.id, onAsk = { confirming = record.id }, onCancel = { confirming = null }) {
                                    confirming = null
                                    model.delete(record)
                                }
                            }
                        }
                    }
                }
            }
            item { AllowancePanel(model.remaining, model.nextOpening(), onHistory = null, historyCount = saved.size) }
            item {
                Text(
                    stringResource(R.string.palm_history_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        PalmDock {
            PillButton(stringResource(R.string.palm_title), onClick = { model.view = PalmView.Capture })
        }
    }
}

@Composable
private fun rememberThumb(model: PalmModel, record: SavedPalmReading): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, record.id) { value = model.thumbnail(record) }
    return bitmap
}

/* The summary's first sentence as the headline, the rest under it. */
private fun splitSummary(record: SavedPalmReading): Pair<String, String> {
    val summary = PalmCopyGuard.clean(record.reading.overallSummary)
    val end = Regex("""[.!?।](\s|$)""").find(summary)?.range?.first
    if (end == null || end >= summary.length - 1) return summary to ""
    return summary.substring(0, end + 1).trim() to summary.substring(end + 1).trim()
}

@Composable
private fun meta(record: SavedPalmReading, withDate: Boolean = true): String {
    val hand = handLabel(record.reader)
    val quality = record.reading.imageQuality?.rating
    val first = if (withDate) dayLabel(record.createdAtMillis) else hand
    return when {
        withDate && quality != null -> stringResource(R.string.palm_history_meta_quality, first, hand, qualityLabel(quality))
        withDate -> stringResource(R.string.palm_history_meta, first, hand)
        quality != null -> stringResource(R.string.palm_history_meta_hand_quality, hand, qualityLabel(quality))
        else -> hand
    }
}

@Composable
private fun DeleteRow(confirming: Boolean, onAsk: () -> Unit, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (confirming) {
            Text(
                stringResource(R.string.palm_delete_confirm),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                modifier = Modifier.weight(1f),
            )
            GhostChip(stringResource(R.string.palm_cancel), onClick = onCancel)
            PillButton(stringResource(R.string.palm_delete_yes), onClick = onConfirm)
        } else {
            GhostChip(stringResource(R.string.palm_delete), onClick = onAsk)
        }
    }
}
