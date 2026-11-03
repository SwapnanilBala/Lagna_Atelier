package site.lagnaatelier.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.lifeareas.AskAnswer
import site.lagnaatelier.app.data.lifeareas.AskClassicsParser
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.LiquidChip
import site.lagnaatelier.app.ui.components.LiquidTextField
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.theme.Muted

/*
 * "Ask the classics", the life-areas page's AskClassicsPanel on the web: the
 * ready-made questions the books can answer for this chart, a box for the
 * reader's own, and the answer to whichever they asked last, cited passage by
 * passage. Only the questions the books can answer for this chart are offered.
 *
 * Every answer counts toward the day's questions, so an answered question is
 * never asked again (MoreHolder keeps it), and a typed one goes one at a time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AskClassicsScreen(holder: MoreHolder, onBack: () -> Unit) {
    LaunchedEffect(holder) { holder.openAsk() }
    var draft by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val typing = holder.typed?.second == AskAnswer.Pending
    val canSend = AskClassicsParser.canAsk(draft) && !typing
    val send = {
        if (holder.askTyped(draft)) focus.clearFocus()
    }

    MoreScaffold(stringResource(R.string.more_ask_title), stringResource(R.string.more_ask_lead), onBack) {
        item {
            Panel(Modifier.fillMaxWidth()) {
                CardLabel(stringResource(R.string.more_ask_choose))
                FetchState(holder.questions, stringResource(R.string.more_ask_loading), onRetry = { holder.openAsk(force = true) }) { questions ->
                    if (questions.isEmpty()) {
                        Text(stringResource(R.string.more_ask_none), style = MaterialTheme.typography.bodyMedium, color = Muted)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            questions.forEach { question ->
                                LiquidChip(
                                    stringResource(question.label),
                                    selected = holder.showing == question,
                                    onClick = { holder.ask(question) },
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                LiquidTextField(
                    value = draft,
                    onValueChange = { draft = it.take(AskClassicsParser.MAX_LENGTH) },
                    label = stringResource(R.string.more_ask_own_label),
                    placeholder = stringResource(R.string.more_ask_own_placeholder),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) send() }),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.more_ask_own_hint, AskClassicsParser.MAX_LENGTH),
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(stringResource(R.string.more_ask_own_submit), onClick = send, enabled = canSend)
                }
                CardLabel(stringResource(R.string.more_ask_count, draft.length, AskClassicsParser.MAX_LENGTH))
            }
        }
        val fixed = holder.showing
        val typed = holder.typed
        val answer = if (fixed != null) holder.answers[fixed] else typed?.second
        if (answer != null) item {
            val heading = if (fixed != null) stringResource(fixed.label) else typed?.first.orEmpty()
            Panel(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CardLabel(stringResource(if (fixed != null) R.string.more_classics_kicker else R.string.more_ask_you_asked))
                    SubHead(heading)
                }
                Answer(answer, stateKey = fixed?.id ?: "typed:$heading", onRetry = fixed?.let { { holder.ask(it) } })
            }
        }
    }
}

@Composable
private fun Answer(answer: AskAnswer, stateKey: String, onRetry: (() -> Unit)?) {
    when (answer) {
        AskAnswer.Pending -> CardLabel(stringResource(R.string.more_ask_pending))
        is AskAnswer.Ready -> ClassicalNoteBody(answer.reading, stateKey)
        AskAnswer.Empty -> Quiet(stringResource(R.string.more_ask_empty))
        is AskAnswer.Refused -> Quiet(stringResource(answer.reason.message))
        is AskAnswer.Failed -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Quiet(stringResource(answer.message))
            /* A typed question is sent again from the box, where it still is. */
            if (onRetry != null && answer.message != R.string.more_ask_limited) {
                GhostChip(stringResource(R.string.more_retry), onClick = onRetry)
            }
        }
    }
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted)
}
