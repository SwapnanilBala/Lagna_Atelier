package site.lagnaatelier.app.reading

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.reading.ClassicalReading
import site.lagnaatelier.app.data.reading.ClassicalSegment
import site.lagnaatelier.app.data.reading.ReadingSectionsParser
import site.lagnaatelier.app.data.reading.Yoga
import site.lagnaatelier.app.data.reading.YogaClassics
import site.lagnaatelier.app.data.reading.YogaFamily
import site.lagnaatelier.app.data.reading.YogaStrength
import site.lagnaatelier.app.data.reading.NoteState
import site.lagnaatelier.app.data.reading.YogaClassicsNote
import java.io.IOException

/*
 * The yogas' classical note: what is sent to POST /api/chart/yoga-classics
 * and how its answer is read. reading/yoga-classics-ananya-mehra.json is the
 * live route's answer for the sample chart on 2026-11-03.
 */
class YogaClassicsTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val yogas = ReadingSectionsParser.parse(resource("chart-ananya-mehra.json")).yogas

    @Test
    fun theRequestIsEveryYogaInRankOrderAsIdsPlanetsAndStrengths() {
        val body = JSONObject(YogaClassics.requestBody(yogas)!!)
        assertEquals("en", body.getString("language"))
        val rows = body.getJSONArray("yogas")
        assertEquals(yogas.size, rows.length())
        val first = rows.getJSONObject(0)
        assertEquals(setOf("id", "planets", "strength"), first.keys().asSequence().toSet())
        assertEquals("pushkala", first.getString("id"))
        assertEquals("strong", first.getString("strength"))
        assertEquals(listOf("Moon", "Saturn", "Mercury"), (0 until first.getJSONArray("planets").length()).map { first.getJSONArray("planets").getString(it) })
        assertEquals(yogas.map { it.id }, (0 until rows.length()).map { rows.getJSONObject(it).getString("id") })
    }

    @Test
    fun theRequestStaysWithinWhatTheRouteTakes() {
        val many = (1..75).map { yoga("y$it", listOf("Moon", "Lagna lord", "Moon", "Ascendant")) }
        val rows = JSONObject(YogaClassics.requestBody(many + many.first())!!).getJSONArray("yogas")
        assertEquals(YogaClassics.MAX_YOGAS, rows.length())
        val planets = rows.getJSONObject(0).getJSONArray("planets")
        /* Unknown names dropped and repeats folded, as the route would. */
        assertEquals(listOf("Moon", "Ascendant"), (0 until planets.length()).map { planets.getString(it) })
        assertNull(YogaClassics.requestBody(emptyList()))
    }

    @Test
    fun readsTheNoteAndItsVerses() {
        val reading = YogaClassics.parse(resource("reading/yoga-classics-ananya-mehra.json"))!!
        assertEquals(7, reading.sources.size)
        assertEquals((1..7).toList(), reading.sources.map { it.number })
        val first = reading.sources.first()
        assertEquals("brihat-jataka-1885", first.book)
        assertEquals("12.18", first.ref)
        assertFalse(first.isNote)
        assertTrue(first.text.startsWith("A person born under a Damini yoga"))
        /* Every number the note cites is a verse it prints. */
        val cited = reading.segments.flatMap { it.sources }.toSet()
        assertTrue(cited.isNotEmpty())
        assertTrue(cited.all { number -> reading.sources.any { it.number == number } })
    }

    @Test
    fun theNoteSplitsIntoItsParagraphs() {
        val reading = YogaClassics.parse(resource("reading/yoga-classics-ananya-mehra.json"))!!
        val paragraphs = reading.paragraphs()
        assertEquals(5, paragraphs.size)
        assertTrue(paragraphs.first().first().text.startsWith("The Brihat Jataka holds that a Damini yoga"))
        assertTrue(paragraphs[1].first().text.startsWith("Varahamihira ties Vasumati to wealth."))
        paragraphs.flatten().forEach { assertFalse(it.text.contains("\n\n")) }
    }

    @Test
    fun aBlocksNumbersStayAfterItsLastPiece() {
        val reading = ClassicalReading(
            segments = listOf(
                ClassicalSegment("  Opening words ", emptyList()),
                ClassicalSegment("cited first.\n\nThen a second paragraph", listOf(1, 2)),
                ClassicalSegment(".\n\n  \n\n", emptyList()),
            ),
            sources = emptyList(),
        )
        val paragraphs = reading.paragraphs()
        assertEquals(2, paragraphs.size)
        assertEquals(
            listOf(ClassicalSegment("Opening words ", emptyList()), ClassicalSegment("cited first.", emptyList())),
            paragraphs[0],
        )
        assertEquals(
            listOf(ClassicalSegment("Then a second paragraph", listOf(1, 2)), ClassicalSegment(".", emptyList())),
            paragraphs[1],
        )
    }

    @Test
    fun aChartTheBookDoesNotCoverHasNoNote() {
        assertNull(YogaClassics.parse("""{"reading":null,"cached":false}"""))
        assertNull(YogaClassics.parse("""{"cached":false}"""))
    }

    @Test
    fun anUncitedNoteIsRefused() {
        try {
            YogaClassics.parse("""{"reading":{"segments":[{"text":"Words","sources":[]}],"sources":[]},"cached":false}""")
            fail("An uncited note must not be shown")
        } catch (expected: IOException) {
        }
    }

    // --- The note's state ---

    private val sample = ClassicalReading(listOf(ClassicalSegment("Words", listOf(1))), emptyList())

    @Test
    fun anAnsweredNoteIsNotAskedForAgain() = runBlocking {
        val note = YogaClassicsNote("{}")
        var calls = 0
        note.ask { calls++; sample }
        assertEquals(NoteState.Ready(sample), note.state)
        note.ask { calls++; sample }
        assertEquals(1, calls)
    }

    @Test
    fun anEmptyAnswerIsKeptAsAnAnswer() = runBlocking {
        val note = YogaClassicsNote("{}")
        var calls = 0
        note.ask { calls++; null }
        assertEquals(NoteState.Empty, note.state)
        note.ask { calls++; null }
        assertEquals(1, calls)
    }

    @Test
    fun aFailedNoteIsWordedByTheAppAndCanBeAskedAgain() = runBlocking {
        val note = YogaClassicsNote("{}")
        note.ask { throw LagnaApi.ApiException("Sign in to keep reading classical notes today.", 429, "RATE_LIMITED", "anonymous") }
        assertEquals(NoteState.Failed(R.string.error_daily), note.state)
        note.ask { throw IOException("unreachable") }
        assertEquals(NoteState.Failed(R.string.error_offline), note.state)
        note.ask { sample }
        assertEquals(NoteState.Ready(sample), note.state)
    }

    @Test
    fun theRequestBodyIsWhatIsSent() = runBlocking {
        val body = YogaClassics.requestBody(yogas)!!
        val note = YogaClassicsNote(body)
        var sent: String? = null
        note.ask { sent = it; null }
        assertEquals(body, sent)
    }

    private fun yoga(id: String, planets: List<String>) = Yoga(
        id = id, name = id, sanskrit = null, family = YogaFamily.Benefic, strength = YogaStrength.Moderate,
        weight = 69, planets = planets, description = "", effects = "",
    )
}
