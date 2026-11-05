package site.lagnaatelier.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.lifeareas.ClassicalReading
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.CitedParagraph
import site.lagnaatelier.app.ui.components.CitedRun
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Muted

/*
 * "From the classics", the web's ClassicalNote: the note with each statement
 * numbered against the passage it rests on, and the passages behind a toggle
 * as the books print them, with a credit for each book quoted.
 */
@Composable
internal fun ClassicalNoteBody(reading: ClassicalReading, stateKey: String) {
    var open by rememberSaveable(stateKey) { mutableStateOf(false) }
    val markStyle = SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.7.em, color = Muted)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        reading.paragraphs().forEach { pieces ->
            CitedParagraph(pieces.map { CitedRun(it.text, it.sources) }, markStyle, MaterialTheme.typography.bodyMedium)
        }
        GhostChip(
            if (open) stringResource(R.string.more_classics_hide) else stringResource(R.string.more_classics_show, reading.sources.size),
            onClick = { open = !open },
        )
        if (open) {
            reading.sources.forEachIndexed { index, source ->
                if (index > 0) HorizontalDivider(color = Hairline)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${source.number}", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val ref = if (source.fromWomensBook) {
                            stringResource(R.string.more_classics_ref_women, source.ref)
                        } else {
                            stringResource(R.string.more_classics_ref, source.ref)
                        }
                        val note = stringResource(R.string.more_classics_note)
                        CardLabel(if (source.isNote) "$ref · $note" else ref)
                        /* Quoted in the books' English, as printed. */
                        Text(
                            "“${source.text}”",
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        )
                    }
                }
            }
            if (reading.sources.any { !it.fromWomensBook }) {
                Text(stringResource(R.string.more_classics_credit), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (reading.sources.any { it.fromWomensBook }) {
                Text(stringResource(R.string.more_classics_credit_women), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}
