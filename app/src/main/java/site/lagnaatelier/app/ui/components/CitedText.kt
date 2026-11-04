package site.lagnaatelier.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import site.lagnaatelier.app.R

/** One run of a cited paragraph: its words, and the numbers of the passages they rest on. */
data class CitedRun(val text: String, val sources: List<Int>)

/*
 * A paragraph from the classics: each run followed by its passage numbers,
 * raised. A raised "1" is only a size and a baseline on screen; a screen
 * reader would run it into the word before it ("men1"), so the paragraph is
 * read aloud with each mark spelled out instead ("men (source 1)").
 */
@Composable
fun CitedParagraph(
    runs: List<CitedRun>,
    markStyle: SpanStyle,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val marks = HashMap<List<Int>, String>()
    for (run in runs) {
        if (run.sources.isNotEmpty() && run.sources !in marks) {
            marks[run.sources] = pluralStringResource(R.plurals.classics_mark_spoken, run.sources.size, run.sources.joinToString(", "))
        }
    }
    val spoken = spokenCitations(runs) { marks.getValue(it) }
    Text(
        buildAnnotatedString {
            runs.forEach { run ->
                append(run.text)
                if (run.sources.isNotEmpty()) withStyle(markStyle) { append(run.sources.joinToString(",")) }
            }
        },
        style = style,
        color = color,
        modifier = modifier.semantics { contentDescription = spoken },
    )
}

/** The paragraph as it is read aloud: each run's marks as words after it, spaced so they never touch a word. */
fun spokenCitations(runs: List<CitedRun>, mark: (List<Int>) -> String): String = buildString {
    runs.forEach { run ->
        append(run.text)
        if (run.sources.isNotEmpty()) append(' ').append(mark(run.sources)).append(' ')
    }
}.replace(Regex("\\s+"), " ").replace(Regex(" ([,.;:!?।])"), "$1").trim()
