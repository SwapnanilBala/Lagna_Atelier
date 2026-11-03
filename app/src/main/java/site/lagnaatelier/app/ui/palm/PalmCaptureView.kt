package site.lagnaatelier.app.ui.palm

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.palm.PalmReader
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.Glow
import site.lagnaatelier.app.ui.components.LiquidChip
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.components.TickBadge
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted

/*
 * The web's stage lines, paced for a reading of about a minute (Opus 5 at
 * low effort, measured ~65 s): an honest description of the work in the
 * order the prompt asks for it, not instrumentation.
 */
private val STAGES = listOf(
    0L to R.string.palm_stage_start,
    8_000L to R.string.palm_stage_lines,
    20_000L to R.string.palm_stage_mounts,
    34_000L to R.string.palm_stage_weighing,
    48_000L to R.string.palm_stage_writing,
    62_000L to R.string.palm_stage_finishing,
)
private const val EXPECTED_MS = 70_000f

@Composable
internal fun PalmCaptureView(
    model: PalmModel,
    hasChart: Boolean,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onAnalyse: () -> Unit,
) {
    val analysing = model.analysingSince != null
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = NavRoom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.palm_title),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 38.sp),
                )
                Text(
                    stringResource(if (hasChart) R.string.palm_lead_chart else R.string.palm_lead_plain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
        }
        item {
            Box {
                Glow(Modifier.offset(x = (-40).dp, y = (-10).dp).requiredSize(width = 400.dp, height = 320.dp))
                FloatCard(Modifier.fillMaxWidth(), spacing = 14.dp) {
                    Finder(model.photoBitmap, model.reader, model.preparing)
                    WhoseHand(model.reader, enabled = !analysing) { model.reader = it }
                    if (analysing) {
                        Analysing(model.analysingSince ?: 0L)
                    } else {
                        Actions(model, onTakePhoto, onChoosePhoto, onAnalyse)
                    }
                    model.captureError?.let { Note(it, error = true) }
                    if (model.shown != null && !analysing) {
                        GhostChip(stringResource(R.string.palm_back_to_reading), onClick = { model.view = PalmView.Reading })
                    }
                }
            }
        }
        item { Tips() }
        item { ClassicalPanel(model.classicalMode, enabled = !analysing) { model.classicalMode = it } }
        item {
            AllowancePanel(
                remaining = model.remaining,
                nextOpening = model.nextOpening(),
                onHistory = model::openHistory,
                historyCount = model.saved.size,
            )
        }
    }
}

@Composable
private fun Finder(photo: ImageBitmap?, reader: PalmReader, preparing: Boolean) {
    val description = stringResource(R.string.palm_photo_description)
    Box(
        Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(14.dp)).background(Canvas),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(
                photo,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            HandOutline(Modifier.size(width = 130.dp, height = 162.dp))
            FinderCorners(Modifier.fillMaxSize())
        }
        val hint = when {
            preparing -> stringResource(R.string.palm_preparing)
            photo != null -> null
            else -> stringResource(
                when (reader) {
                    PalmReader.Woman -> R.string.palm_finder_left
                    PalmReader.Man -> R.string.palm_finder_right
                    PalmReader.Unspecified -> R.string.palm_finder_writing
                },
            )
        }
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = Muted,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(CircleShape)
                    .background(CardWhite)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun WhoseHand(reader: PalmReader, enabled: Boolean, onSelect: (PalmReader) -> Unit) {
    val legend = stringResource(R.string.palm_reader_legend)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CardLabel(legend)
        FlowRow(
            Modifier.semantics { contentDescription = legend },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                PalmReader.Woman to R.string.palm_reader_woman,
                PalmReader.Man to R.string.palm_reader_man,
                PalmReader.Unspecified to R.string.palm_reader_unspecified,
            ).forEach { (option, label) ->
                LiquidChip(stringResource(label), selected = reader == option, onClick = { if (enabled) onSelect(option) })
            }
        }
        Text(
            stringResource(
                when (reader) {
                    PalmReader.Woman -> R.string.palm_reader_hint_woman
                    PalmReader.Man -> R.string.palm_reader_hint_man
                    PalmReader.Unspecified -> R.string.palm_reader_hint_unspecified
                },
            ),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            color = Muted,
        )
    }
}

@Composable
private fun Actions(model: PalmModel, onTakePhoto: () -> Unit, onChoosePhoto: () -> Unit, onAnalyse: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (model.photo == null) {
            PillButton(stringResource(R.string.palm_take_photo), onClick = onTakePhoto, enabled = !model.preparing)
            GhostChip(stringResource(R.string.palm_choose_photo), onClick = if (model.preparing) null else onChoosePhoto)
        } else {
            PillButton(
                stringResource(R.string.palm_analyze),
                onClick = onAnalyse,
                enabled = !model.preparing && model.remaining > 0,
            )
            GhostChip(stringResource(R.string.palm_retake), onClick = if (model.preparing) null else onTakePhoto)
            GhostChip(stringResource(R.string.palm_choose_another), onClick = if (model.preparing) null else onChoosePhoto)
        }
    }
}

@Composable
private fun Analysing(since: Long) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(since) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val elapsed = (now - since).coerceAtLeast(0)
    val stage = STAGES.last { elapsed >= it.first }.second
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(stage), style = MaterialTheme.typography.titleMedium)
        ProgressLine((elapsed / EXPECTED_MS).coerceAtMost(0.95f))
        Note(stringResource(R.string.palm_patience))
    }
}

@Composable
private fun Tips() {
    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.palm_tips_title))
        TipRow(stringResource(R.string.palm_tip_flat))
        TipRow(stringResource(R.string.palm_tip_light), stringResource(R.string.palm_tip_light_detail))
        TipRow(stringResource(R.string.palm_tip_fingers))
        TipRow(stringResource(R.string.palm_tip_frame))
    }
}

@Composable
private fun TipRow(title: String, detail: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 1.dp)) { TickBadge() }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp))
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun ClassicalPanel(on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val title = stringResource(R.string.palm_classical_title)
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp))
                Text(stringResource(R.string.palm_classical_subtitle), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Switch(
                checked = on,
                onCheckedChange = onChange,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = title },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CardWhite,
                    checkedTrackColor = Ink,
                    checkedBorderColor = Ink,
                    uncheckedThumbColor = CardWhite,
                    uncheckedTrackColor = Hairline,
                    uncheckedBorderColor = Hairline,
                ),
            )
        }
    }
}
