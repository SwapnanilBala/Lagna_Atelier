package site.lagnaatelier.app.reading

import org.junit.Assert.assertEquals
import org.junit.Test
import site.lagnaatelier.app.ui.components.CitedRun
import site.lagnaatelier.app.ui.components.spokenCitations

/* What a screen reader hears for a cited paragraph: marks as words, never run into the word before. */
class SpokenCitationsTest {
    private val mark: (List<Int>) -> String = { if (it.size == 1) "(source ${it[0]})" else "(sources ${it.joinToString(", ")})" }

    @Test
    fun spellsOutEachMarkAfterItsWords() {
        val runs = listOf(
            CitedRun("Such men", listOf(1)),
            CitedRun(" are wealthy and learned.", listOf(2, 3)),
            CitedRun(" They travel far.", emptyList()),
        )
        assertEquals(
            "Such men (source 1) are wealthy and learned. (sources 2, 3) They travel far.",
            spokenCitations(runs, mark),
        )
    }

    @Test
    fun keepsPunctuationAgainstTheMarkAndTouchingWordsApart() {
        val runs = listOf(CitedRun("Such men", listOf(1)), CitedRun(", the books say", listOf(4)), CitedRun("and more", emptyList()))
        assertEquals("Such men (source 1), the books say (source 4) and more", spokenCitations(runs, mark))
    }
}
