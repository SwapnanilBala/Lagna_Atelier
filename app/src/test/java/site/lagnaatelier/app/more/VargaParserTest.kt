package site.lagnaatelier.app.more

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ChartParser
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.Placement
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.vargas.KeyVarga
import site.lagnaatelier.app.data.vargas.VargaCommentary
import site.lagnaatelier.app.data.vargas.VargaParser
import java.time.LocalDate

/*
 * The vargas from the saved /api/chart answer for the sample chart
 * (chart-ananya-mehra.json), and the commentary route's real answer for them
 * (lifeareas/varga.json, 2026-11-03).
 */
class VargaParserTest {
    private val chartJson = javaClass.classLoader!!.getResource("chart-ananya-mehra.json")!!.readText()
    private val vargas = VargaParser.parse(JSONObject(chartJson).getJSONObject("chart").getJSONObject("divisional_charts"))

    @Test
    fun readsAllTwentyInDivisionOrder() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 16, 20, 24, 27, 30, 40, 45, 60), vargas.map { it.division })
        assertEquals(setOf(5, 6, 8, 11), vargas.filter { it.extended }.map { it.division }.toSet())
        assertEquals(KeyVarga.entries.map { it.division }, vargas.filter { it.key != null }.map { it.division })
        assertTrue(vargas.all { it.positions.size == 10 && it.ascendant != null })
    }

    @Test
    fun everyVargaTheEngineSendsHasATranslatedTheme() {
        // The engine's description is English only; each tile and detail prints the theme string instead.
        assertTrue(vargas.all { it.theme != null })
        assertEquals(vargas.size, vargas.mapNotNull { it.theme }.toSet().size)
    }

    @Test
    fun agreesWithTheNavamsaTheReadingAlreadyDraws() {
        val delhi = Place("New Delhi", "Delhi", "India", 28.6139, 77.209, "Asia/Kolkata", 330)
        val chart = ChartParser.parse(chartJson, BirthDetails("Ananya Mehra", LocalDate.of(1990, 6, 15), "14:30", TimeAccuracy.Exact, delhi))
        assertEquals(20, chart.vargas.size)
        val d9 = chart.vargas.first { it.division == 9 }
        assertEquals(chart.navamsaAscendant, d9.ascendant)
        assertEquals(chart.navamsa.toSet(), d9.placements.toSet())
        assertEquals("Marriage, dharma, and inner nature", d9.description)
        // The D1 is the rasi chart itself: every point repeats.
        val d1 = chart.vargas.first()
        assertTrue(d1.positions.all { it.repeats })
        assertEquals(chart.placements.toSet(), d1.placements.toSet())
    }

    @Test
    fun leavesOutAVargaItCannotReadRatherThanFailing() {
        val json = JSONObject(
            """{"9":{"division":9,"label":"D9","positions":[{"name":"Sun","rashi_sign":"Gemini","divisional_sign":"Libra"}]},
                "10":{"division":10,"label":"D10","positions":[{"name":"Sun","rashi_sign":"Gemini","divisional_sign":"Ophiuchus"}]},
                "12":{"division":12,"label":"D12","positions":[]}}""",
        )
        val parsed = VargaParser.parse(json)
        assertEquals(listOf(9), parsed.map { it.division })
        assertNull(parsed.single().ascendant)
        assertEquals(listOf(Placement("Su", Sign.Libra)), parsed.single().placements)
        assertEquals(emptyList<Any>(), VargaParser.parse(null))
    }

    @Test
    fun asksForTheTenKeyVargasOnlyWithClosedSetFields() {
        val body = JSONObject(VargaCommentary.requestBody(vargas)!!)
        assertEquals("en", body.getString("language"))
        val divisions = body.getJSONArray("divisions")
        assertEquals(listOf(1, 2, 4, 7, 9, 10, 12, 24, 30, 60), (0 until divisions.length()).map { divisions.getJSONObject(it).getInt("division") })
        val signs = Sign.entries.map { it.label }.toSet()
        for (i in 0 until divisions.length()) {
            val positions = divisions.getJSONObject(i).getJSONArray("positions")
            assertEquals(10, positions.length())
            for (j in 0 until positions.length()) {
                val p = positions.getJSONObject(j)
                assertTrue(p.getString("name") in VargaCommentary.POINTS)
                assertTrue(p.getString("rashi") in signs && p.getString("divisional") in signs)
            }
        }
        val d9 = divisions.getJSONObject(4).getJSONArray("positions").getJSONObject(1)
        assertEquals("Sun", d9.getString("name"))
        assertEquals("Gemini", d9.getString("rashi"))
        assertEquals("Libra", d9.getString("divisional"))
    }

    @Test
    fun asksForNothingWithoutAKeyVarga() {
        assertNull(VargaCommentary.requestBody(vargas.filter { it.key == null }))
    }

    @Test
    fun readsTheNotesByDivision() {
        val notes = VargaCommentary.parseNotes(javaClass.classLoader!!.getResource("lifeareas/varga.json")!!.readText())
        assertEquals(KeyVarga.entries.map { it.division }.toSet(), notes.keys)
        assertTrue(notes.getValue(1).startsWith("Your foundation is Virgo rising"))
        val odd = VargaCommentary.parseNotes("""{"notes":[{"division":40,"note":"x"},{"division":9,"note":"  "},{"division":10,"note":" Work. "}]}""")
        assertEquals(mapOf(10 to "Work."), odd)
    }
}
