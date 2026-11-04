package site.lagnaatelier.app.ui.timing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.periods.ClassicalReading
import site.lagnaatelier.app.data.periods.ClassicalSource
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.CitedParagraph
import site.lagnaatelier.app.ui.components.CitedRun
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted

/*
 * A reading cited to the classical books, as the web's "From the classics"
 * card prints it (classical-note.tsx): each statement followed by the small
 * numbers of the passages it rests on, then the passages themselves behind a
 * link, closed at first, and the credit for each book the reading quotes.
 */
@Composable
internal fun ClassicalReadingView(reading: ClassicalReading, modifier: Modifier = Modifier) {
    var showSources by rememberSaveable(reading) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        reading.paragraphs().forEach { pieces ->
            CitedParagraph(
                pieces.map { CitedRun(it.text, it.sources) },
                markStyle = SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em, color = Muted),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
        }
        if (reading.sources.isNotEmpty()) {
            TextLink(
                if (showSources) stringResource(R.string.periods_sources_hide)
                else stringResource(R.string.periods_sources_show, reading.sources.size),
                onClick = { showSources = !showSources },
            )
            if (showSources) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    reading.sources.forEach { SourceRow(it) }
                }
                if (reading.quotesOtherBooks) Meta(stringResource(R.string.periods_credit_bj))
                if (reading.quotesStrijataka) Meta(stringResource(R.string.periods_credit_strijataka))
            }
        }
    }
}

@Composable
private fun SourceRow(source: ClassicalSource) {
    val ref = if (source.book == ClassicalReading.STRIJATAKA) {
        stringResource(R.string.periods_source_strijataka, source.ref)
    } else {
        stringResource(R.string.periods_source_bj, source.ref)
    }
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Meta(
            "${source.number}. " + if (source.isNote) stringResource(R.string.periods_source_note, ref) else ref,
            color = Ink,
        )
        /* The books' own English, as printed. */
        Text(
            stringResource(R.string.periods_source_text, source.text),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
}

/** A written reading's place on a card: its progress line, the reading, or why there is none. */
@Composable
internal fun WrittenState(
    state: Written<ClassicalReading>,
    pendingLabel: String,
    onRetry: () -> Unit,
) {
    when (state) {
        Written.Writing -> CardLabel(pendingLabel)
        is Written.Ready -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardLabel(stringResource(R.string.periods_reading_kicker))
            ClassicalReadingView(state.value)
        }
        Written.Empty -> Text(
            stringResource(R.string.periods_reading_empty),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        is Written.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(state.message), style = MaterialTheme.typography.bodySmall, color = Muted)
            if (state.retry) TextLink(stringResource(R.string.timing_retry), onClick = onRetry)
        }
    }
}
