package site.lagnaatelier.app.ui.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.reading.ClassicalReading
import site.lagnaatelier.app.data.reading.ClassicalSegment
import site.lagnaatelier.app.data.reading.ClassicalSource
import site.lagnaatelier.app.data.reading.NoteState
import site.lagnaatelier.app.data.reading.Yoga
import site.lagnaatelier.app.data.reading.YogaClassics
import site.lagnaatelier.app.data.reading.YogaClassicsNote
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Muted

/*
 * A few charts' notes, for the life of the process, keyed by what is sent:
 * two charts whose yogas agree share a note, as they do in the route's own
 * cache. The scope is the process's, not the screen's, so a note being
 * written when the reader scrolls on still lands, rather than being paid for
 * and thrown away. Read and written from the main thread only.
 */
object YogaClassicsNotes {
    private const val KEEP = 6
    private val notes = object : LinkedHashMap<String, YogaClassicsNote>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, YogaClassicsNote>?) = size > KEEP
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun get(requestBody: String): YogaClassicsNote = notes.getOrPut(requestBody) { YogaClassicsNote(requestBody) }
}

/*
 * What the Brihat Jataka says about this chart's yogas, every statement
 * numbered against the verse it rests on and the verses printed underneath
 * as the 1885 translation has them (the web's ClassicalNote card).
 *
 * Asked for on a tap, never on sight: the note is written by a model on the
 * site's allowance, and most readers scroll past the yogas.
 */
@Composable
internal fun YogaClassicsCard(yogas: List<Yoga>, api: LagnaApi) {
    val body = remember(yogas) { YogaClassics.requestBody(yogas) } ?: return
    val note = remember(body) { YogaClassicsNotes.get(body) }
    val ask = { YogaClassicsNotes.scope.launch { note.ask { YogaClassics.fetch(api, it) } }; Unit }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        CardLabel(stringResource(R.string.classics_kicker))
        Text(stringResource(R.string.classics_heading), style = MaterialTheme.typography.titleMedium)
        when (val state = note.state) {
            NoteState.NotAsked -> GhostChip(stringResource(R.string.classics_ask), onClick = ask)
            NoteState.Asking -> Text(stringResource(R.string.classics_pending), style = MaterialTheme.typography.bodyMedium, color = Muted)
            NoteState.Empty -> Text(stringResource(R.string.classics_empty), style = MaterialTheme.typography.bodyMedium, color = Muted)
            is NoteState.Failed -> {
                Text(stringResource(state.message), style = MaterialTheme.typography.bodyMedium, color = Muted)
                GhostChip(stringResource(R.string.sections_retry), onClick = ask)
            }
            is NoteState.Ready -> ClassicalNoteBody(state.reading)
        }
    }
}

@Composable
private fun ClassicalNoteBody(reading: ClassicalReading) {
    var versesOpen by rememberSaveable { mutableStateOf(false) }
    val marks = SpanStyle(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f, baselineShift = BaselineShift.Superscript, color = Muted)
    reading.paragraphs().forEach { pieces ->
        Text(citedParagraph(pieces, marks), style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider(color = Hairline)
    DisclosureRow(expanded = versesOpen, onToggle = { versesOpen = !versesOpen }) {
        Text(
            stringResource(R.string.classics_sources, reading.sources.size),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
    }
    if (versesOpen) {
        reading.sources.forEach { SourceRow(it) }
    }
    Legend(stringResource(R.string.classics_credit))
}

/** A paragraph with each run's verse numbers raised after it, as the web prints them. */
internal fun citedParagraph(pieces: List<ClassicalSegment>, marks: SpanStyle): AnnotatedString = buildAnnotatedString {
    pieces.forEach { piece ->
        append(piece.text)
        if (piece.sources.isNotEmpty()) withStyle(marks) { append(piece.sources.joinToString(",")) }
    }
}

@Composable
private fun SourceRow(source: ClassicalSource) {
    val ref = when (source.book) {
        "brihat-jataka-1885" -> stringResource(R.string.classics_ref_brihat_jataka, source.ref)
        "brihat-samhita-1884" -> stringResource(R.string.classics_ref_brihat_samhita, source.ref)
        "strijataka-1931" -> stringResource(R.string.classics_ref_strijataka, source.ref)
        else -> stringResource(R.string.classics_ref_other, source.ref)
    }
    val label = if (source.isNote) "$ref · ${stringResource(R.string.classics_translator_note)}" else ref
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${source.number}", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.width(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
            Text("“${source.text}”", style = MaterialTheme.typography.bodySmall)
        }
    }
}
