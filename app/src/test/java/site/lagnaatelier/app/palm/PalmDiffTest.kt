package site.lagnaatelier.app.palm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.palm.LineKey
import site.lagnaatelier.app.data.palm.LineStrengthChange
import site.lagnaatelier.app.data.palm.LineVisibilityChange
import site.lagnaatelier.app.data.palm.PalmDiffer
import site.lagnaatelier.app.data.palm.PalmReader
import site.lagnaatelier.app.data.palm.PalmReadingParser
import site.lagnaatelier.app.data.palm.SavedPalmReading
import site.lagnaatelier.app.data.palm.StrengthDirection

/*
 * The compare against two hand-written readings: reading-full.json, and
 * reading-later.json, the same hand eleven days on with a stronger fate
 * line, a fainter life line, Saturn come up and Venus gone, and a new mark.
 */
class PalmDiffTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("palm/$name")!!.readText()

    private fun saved(id: String, name: String, createdAt: Long) = SavedPalmReading(
        id = id,
        createdAtMillis = createdAt,
        reader = PalmReader.Woman,
        classicalMode = false,
        jyotishContext = null,
        reading = PalmReadingParser.parse(fixture(name))!!,
    )

    private val day = 24 * 60 * 60 * 1000L
    private val earlier = saved("a", "reading-full.json", 1_793_000_000_000L)
    private val later = saved("b", "reading-later.json", 1_793_000_000_000L + 11 * day + 3 * 60 * 60 * 1000L)

    @Test
    fun theEarlierReadingIsAlwaysA() {
        val forwards = PalmDiffer.diff(earlier, later)
        val backwards = PalmDiffer.diff(later, earlier)
        assertEquals("a", forwards.a.id)
        assertEquals("a", backwards.a.id)
        assertEquals(forwards.lineStrengthChanges, backwards.lineStrengthChanges)
        assertEquals(11, forwards.daysApart)
    }

    @Test
    fun linesReportStrongerWeakerOrUnchanged() {
        val diff = PalmDiffer.diff(earlier, later)
        assertEquals(
            listOf(
                LineStrengthChange(LineKey.Heart, "strong", "strong", StrengthDirection.Unchanged),
                LineStrengthChange(LineKey.Head, "strong", "strong", StrengthDirection.Unchanged),
                LineStrengthChange(LineKey.Life, "moderate", "faint", StrengthDirection.Weaker),
                LineStrengthChange(LineKey.Fate, "faint", "moderate", StrengthDirection.Stronger),
            ),
            diff.lineStrengthChanges,
        )
        assertFalse(diff.allLinesUnchanged)
    }

    @Test
    fun visibilityListsOnlyTheLinesThatChanged() {
        val diff = PalmDiffer.diff(earlier, later)
        assertEquals(
            listOf(
                /* Gone from the later reading's confidences: reported, with nothing to show for B. */
                LineVisibilityChange(LineKey.Life, "clear", null),
                LineVisibilityChange(LineKey.Fate, "partial", "clear"),
            ),
            diff.lineVisibilityChanges,
        )
    }

    @Test
    fun mountsAndMarkingsMatchIgnoringCaseAndSpacing() {
        val diff = PalmDiffer.diff(earlier, later)
        /* "  jupiter " is the earlier "Jupiter"; Saturn is listed twice but new once. */
        assertEquals(listOf("Saturn"), diff.newMounts)
        assertEquals(listOf("Venus"), diff.lostMounts)
        assertEquals(listOf("Star on the mount of Jupiter"), diff.newMarkings)
        assertEquals(listOf("Triangle on the head line"), diff.lostMarkings)
        assertTrue(diff.mountsChanged && diff.markingsChanged)
    }

    @Test
    fun summariesComeThroughSideBySide() {
        val diff = PalmDiffer.diff(later, earlier)
        assertTrue(diff.aSummary.startsWith("A careful, practical hand."))
        assertTrue(diff.bSummary.startsWith("A steadier hand than before."))
    }

    @Test
    fun aReadingAgainstItselfHasNothingToReport() {
        val diff = PalmDiffer.diff(earlier, earlier.copy(id = "c"))
        assertTrue(diff.allLinesUnchanged)
        assertTrue(diff.lineVisibilityChanges.isEmpty())
        assertFalse(diff.mountsChanged || diff.markingsChanged)
        assertEquals(0, diff.daysApart)
    }

    @Test
    fun unknownStrengthsCountAsUnchanged() {
        assertEquals(StrengthDirection.Unchanged, PalmDiffer.direction(null, "strong"))
        assertEquals(StrengthDirection.Unchanged, PalmDiffer.direction("strong", "bright"))
        assertEquals(StrengthDirection.Stronger, PalmDiffer.direction("absent", "faint"))
        assertEquals(StrengthDirection.Weaker, PalmDiffer.direction("strong", "absent"))
    }
}
