package site.lagnaatelier.app.ui.palm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.palm.Correlation
import site.lagnaatelier.app.data.palm.LineKey
import site.lagnaatelier.app.data.palm.PalmCopyGuard
import site.lagnaatelier.app.data.palm.PalmQa
import site.lagnaatelier.app.data.palm.PalmReading
import site.lagnaatelier.app.data.palm.QaRole
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.Glow
import site.lagnaatelier.app.ui.components.LiquidIcons
import site.lagnaatelier.app.ui.components.LiquidTextField
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.components.TickBadge
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted
import kotlin.math.roundToInt

/*
 * A palm reading: photo quality first, then the traced photo and the short
 * version over the glow, the four lines, the mounts, and the follow-up
 * questions (the mock's screen 03). Every string in the reading is the
 * model's, shown as plain text and only after PalmCopyGuard; the health
 * section is never shown.
 */
@Composable
internal fun PalmReadingView(model: PalmModel, shown: ShownReading, canAsk: Boolean, onAsk: (String) -> Unit) {
    val reading = shown.reading
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = DockRoom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PalmHeader(
                    title = stringResource(R.string.palm_reading_title),
                    subtitle = stringResource(R.string.palm_history_meta, dayLabel(shown.createdAtMillis), handLabel(shown.reader)),
                    onBack = { model.view = PalmView.Capture },
                )
            }
            reading.imageQuality?.let { quality -> item { QualityPanel(quality.rating, quality.reliable, quality.notes) } }
            item { SummaryCard(shown, reading) }
            item { LinesPanel(reading) }
            if (reading.mounts.prominent.isNotEmpty() || reading.mounts.interpretation.isNotEmpty()) {
                item { MountsPanel(reading) }
            }
            item { MorePanel(reading) }
            item { AskPanel(model, canAsk, onAsk) }
            model.saveError?.let { item { Note(it, error = true) } }
        }
        PalmDock {
            GhostChip(stringResource(R.string.palm_new_reading), onClick = model::newReading)
            val saved = shown.savedId != null
            PillButton(
                stringResource(
                    when {
                        saved -> R.string.palm_saved
                        model.saving -> R.string.palm_saving
                        else -> R.string.palm_save
                    },
                ),
                onClick = model::save,
                icon = if (saved) LiquidIcons.Check else LiquidIcons.ArrowForward,
                enabled = !saved && !model.saving,
            )
        }
    }
}

@Composable
private fun QualityPanel(rating: String, reliable: Boolean, notes: String) {
    val cleaned = PalmCopyGuard.clean(notes)
    Panel(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 1.dp)) { TickBadge() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.palm_quality_title, qualityLabel(rating)), style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp))
                when {
                    !reliable || rating == "poor" -> Note(stringResource(R.string.palm_quality_hard), error = true)
                    rating == "marginal" -> Note(stringResource(R.string.palm_quality_soft), error = true)
                }
                if (cleaned.isNotEmpty()) Note(cleaned)
            }
        }
    }
}

@Composable
private fun SummaryCard(shown: ShownReading, reading: PalmReading) {
    Box {
        Glow(Modifier.offset(x = (-56).dp, y = (-10).dp).requiredSize(width = 410.dp, height = 330.dp))
        FloatCard(Modifier.fillMaxWidth(), spacing = 10.dp) {
            if (shown.photo != null) TracedPhoto(shown.photo, reading)
            CardLabel(stringResource(R.string.palm_in_short))
            Text(
                PalmCopyGuard.clean(reading.overallSummary),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 23.sp),
            )
            val hand = PalmCopyGuard.clean(reading.dominantHandNote)
            if (hand.isNotEmpty()) CardLabel(hand)
        }
    }
}

@Composable
private fun LinesPanel(reading: PalmReading) {
    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.palm_lines_title)) { CardLabel(stringResource(R.string.palm_lines_label)) }
        LineKey.entries.forEachIndexed { index, key ->
            val line = reading.lines[key] ?: return@forEachIndexed
            if (index > 0) HorizontalDivider(color = Hairline)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(lineName(key)), style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp))
                    Text(lineMeta(reading, key, line.strength), style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                val body = PalmCopyGuard.clean(line.interpretation.ifEmpty { line.description })
                if (body.isNotEmpty()) {
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun lineMeta(reading: PalmReading, key: LineKey, strength: String): String {
    val confidence = reading.lineConfidence[key]
    if (confidence != null) {
        val visibility = stringResource(
            when (confidence.visibility) {
                "clear" -> R.string.palm_visibility_clear
                "partial" -> R.string.palm_visibility_partial
                "faint" -> R.string.palm_visibility_faint
                else -> R.string.palm_visibility_not_detected
            },
        )
        return stringResource(R.string.palm_line_confidence, visibility, (confidence.confidence * 100).roundToInt())
    }
    return stringResource(
        when (strength) {
            "strong" -> R.string.palm_strength_strong
            "faint" -> R.string.palm_strength_faint
            "absent" -> R.string.palm_strength_absent
            else -> R.string.palm_strength_moderate
        },
    )
}

@Composable
private fun MountsPanel(reading: PalmReading) {
    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.palm_mounts_title)) { CardLabel(stringResource(R.string.palm_mounts_label)) }
        if (reading.mounts.prominent.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reading.mounts.prominent.forEach { InkChip(it.take(40)) }
            }
        }
        val body = PalmCopyGuard.clean(reading.mounts.interpretation)
        if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
        /* The one correlation the mock shows: the strongest the chart gave. */
        val correlation = reading.jyotishCorrelation?.correlations?.let { list ->
            list.firstOrNull { it.reinforcement == "strong" } ?: list.firstOrNull()
        }
        if (correlation != null) CorrelationRow(correlation)
    }
}

@Composable
private fun CorrelationRow(correlation: Correlation) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Canvas).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(PalmCopyGuard.clean(correlation.palmIndicator), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), modifier = Modifier.weight(1f))
        Text(
            stringResource(
                when (correlation.reinforcement) {
                    "strong" -> R.string.palm_reinforcement_strong
                    "moderate" -> R.string.palm_reinforcement_moderate
                    "contradictory" -> R.string.palm_reinforcement_contradictory
                    else -> R.string.palm_reinforcement_neutral
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
        Text(
            PalmCopyGuard.clean(correlation.chartFactor),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/* The rest of the reading, folded: trajectory, work, relationships, fingers, markings, the dasha lens and guidance. */
@Composable
private fun MorePanel(reading: PalmReading) {
    val sections = listOf(
        R.string.palm_more_current to reading.trajectory.currentPhase,
        R.string.palm_more_near to reading.trajectory.nearFuture,
        R.string.palm_more_long to reading.trajectory.longTermPath,
        R.string.palm_more_challenges to reading.trajectory.challenges,
        R.string.palm_more_opportunities to reading.trajectory.opportunities,
        R.string.palm_more_talents to reading.career.naturalTalents,
        R.string.palm_more_career to reading.career.careerDirection,
        R.string.palm_more_purpose to reading.career.purposeAlignment,
        R.string.palm_more_emotional to reading.relationships.emotionalState,
        R.string.palm_more_relationships to reading.relationships.relationshipDynamics,
        R.string.palm_more_connection to reading.relationships.connectionStyle,
        R.string.palm_more_fingers to listOf(reading.fingers.observation, reading.fingers.interpretation).filter { it.isNotEmpty() }.joinToString(" "),
        R.string.palm_more_markings to reading.markings.interpretation,
        R.string.palm_more_dasha to (reading.dashaRelevance?.summary ?: ""),
        R.string.palm_more_guidance to reading.guidance,
    ).map { (label, body) -> label to PalmCopyGuard.clean(body) }.filter { it.second.isNotEmpty() }
    if (sections.isEmpty()) return

    var open by rememberSaveable { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.palm_more_title)) {
            Text(
                stringResource(if (open) R.string.palm_more_hide else R.string.palm_more_show),
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { open = !open }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        val visible = if (open) sections else sections.take(1)
        visible.forEachIndexed { index, (label, body) ->
            if (index > 0) HorizontalDivider(color = Hairline)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(label), style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
    }
}

@Composable
private fun AskPanel(model: PalmModel, canAsk: Boolean, onAsk: (String) -> Unit) {
    val turns = model.turns
    val full = PalmQa.atLimit(turns)
    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.palm_qa_title)) {
            val left = PalmQa.questionsLeft(turns)
            Text(pluralStringResource(R.plurals.palm_qa_left, left, left), style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        Note(stringResource(R.string.palm_qa_lead))

        turns.forEach { turn ->
            val mine = turn.role == QaRole.User
            val shownText = if (mine) turn.content else PalmCopyGuard.clean(turn.content).ifEmpty { stringResource(R.string.palm_qa_error) }
            Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                Text(
                    shownText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mine || !turn.offTopic) Ink else Muted,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (mine) Canvas else CardWhite)
                        .border(1.dp, if (mine) Canvas else Hairline, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
        if (model.asking) Note(stringResource(R.string.palm_qa_thinking))

        if (!canAsk) {
            /* A saved reading opens signed out; asking about it needs the account. */
            if (model.qaError == null) Note(stringResource(R.string.palm_sign_in_ask))
        } else if (turns.isEmpty() && !model.asking) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    R.string.palm_qa_starter_career,
                    R.string.palm_qa_starter_timing,
                    R.string.palm_qa_starter_strength,
                ).forEach { id ->
                    val question = stringResource(id)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { onAsk(question) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(question, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                        Icon(LiquidIcons.ArrowForward, contentDescription = null, tint = Muted, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        if (!canAsk) {
            /* Nothing to type into: the note above says why. */
        } else if (full) {
            Note(stringResource(R.string.palm_qa_full))
        } else {
            val sendLabel = stringResource(R.string.palm_qa_send)
            val canSend = !model.asking && model.draft.isNotBlank()
            LiquidTextField(
                value = model.draft,
                onValueChange = { model.draft = it.take(PalmQa.MAX_QUESTION_CHARS) },
                label = stringResource(R.string.palm_qa_label),
                placeholder = stringResource(R.string.palm_qa_placeholder),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onAsk(model.draft) }),
                trailing = {
                    Box(
                        Modifier
                            .padding(end = 4.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (canSend) Ink else Hairline)
                            .clickable(enabled = canSend, role = Role.Button) { onAsk(model.draft) }
                            .semantics { contentDescription = sendLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(LiquidIcons.ArrowForward, contentDescription = null, tint = CardWhite, modifier = Modifier.size(17.dp))
                    }
                },
            )
        }
        model.qaError?.let { Note(it, error = true) }
        Note(stringResource(R.string.palm_qa_footnote))
    }
}
