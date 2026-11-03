package site.lagnaatelier.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ChartParser
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.Placement
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.Traditions
import java.time.LocalDate

/*
 * The parser against a real response: GET https://lagnaatelier.site/api/chart
 * for the sample chart on 2026-11-02, saved as chart-ananya-mehra.json.
 */
class ChartParserTest {
    private val delhi = Place("New Delhi", "Delhi", "India", 28.6139, 77.209, "Asia/Kolkata", 330)
    private val details = BirthDetails("Ananya Mehra", LocalDate.of(1990, 6, 15), "14:30", TimeAccuracy.Exact, delhi)
    private val fixture = javaClass.classLoader!!.getResource("chart-ananya-mehra.json")!!.readText()

    @Test
    fun readsTheHeadlineFacts() {
        val chart = ChartParser.parse(fixture, details)
        assertEquals(Sign.Virgo, chart.ascendant)
        assertEquals(28.7507, chart.ascendantDegree, 1e-4)
        assertEquals(Sign.Aquarius, chart.moonSign)
        assertEquals("Shatabhisha", chart.nakshatra)
        assertEquals(4, chart.pada)
        assertEquals("Rahu", chart.nakshatraLord)
        assertEquals("Lahiri Classic", chart.tradition)
        assertEquals("New Delhi", chart.place)
        assertEquals(LocalDate.of(1990, 6, 15), chart.birthDate)
    }

    @Test
    fun readsTheCurrentPeriodsAndTheTimeline() {
        val chart = ChartParser.parse(fixture, details)
        assertEquals("Mercury", chart.mahadasha.lord)
        assertEquals(LocalDate.of(2042, 6, 29), chart.mahadasha.end)
        assertEquals("Mercury", chart.antardasha.lord)
        assertEquals(LocalDate.of(2027, 11, 25), chart.antardasha.end)
        assertEquals(listOf("Rahu", "Jupiter", "Saturn", "Mercury"), chart.mahadashas.take(4).map { it.lord })
        chart.mahadashas.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
    }

    @Test
    fun placesTheNineGrahasBySignInBothCharts() {
        val chart = ChartParser.parse(fixture, details)
        assertEquals(9, chart.placements.size)
        assertTrue(Placement("Su", Sign.Gemini) in chart.placements)
        assertTrue(Placement("Ke", Sign.Cancer) in chart.placements)
        assertEquals(9, chart.navamsa.size)
        assertTrue(Placement("Su", Sign.Libra) in chart.navamsa)
        assertEquals(Sign.Virgo, chart.navamsaAscendant)
    }

    @Test
    fun keepsTheEnginesChoiceOfFindingsInItsOrder() {
        val chart = ChartParser.parse(fixture, details)
        assertEquals(7, chart.findings.size)
        assertEquals("How your working life is built", chart.findings.first().headline)
        assertEquals("Foundation", chart.findings.first().category)
        assertTrue(chart.findings.first().body!!.startsWith("Communication-centered career path"))
    }

    @Test
    fun agreesWithTheBuiltInSample() {
        val chart = ChartParser.parse(fixture, details)
        val sample = SampleChart.chart
        assertEquals(sample.ascendant, chart.ascendant)
        assertEquals(sample.antardasha, chart.antardasha)
        assertEquals(sample.placements.toSet(), chart.placements.toSet())
        assertEquals(sample.navamsa.toSet(), chart.navamsa.toSet())
        // The sample keeps four of the seven findings, in the engine's order.
        val liveHeadlines = chart.findings.map { it.headline }
        val positions = sample.findings.map { liveHeadlines.indexOf(it.headline) }
        assertTrue(positions.none { it < 0 })
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun buildsTheQueryTheWebRouteExpects() {
        val query = details.copy(engineId = Traditions.engineId(Traditions.Ayanamsha.Krishnamurti, Traditions.HouseSystem.Placidus)).chartQuery()
        assertEquals("1990-06-15", query["birth_date"])
        assertEquals("14:30", query["birth_time"])
        assertEquals("330", query["timezone_offset_minutes"])
        assertEquals("krishnamurti_placidus", query["engine_id"])
        assertEquals("exact", query["birth_time_source"])
        assertEquals("false", query["birth_time_fallback"])

        val estimated = details.copy(exactTime = null, accuracy = TimeAccuracy.Evening).chartQuery()
        assertEquals("18:30", estimated["birth_time"])
        assertEquals("evening", estimated["birth_time_accuracy"])
        assertEquals("fallback", estimated["birth_time_source"])
        assertEquals("true", estimated["birth_time_fallback"])
    }

    @Test
    fun saysWhatTheSiteSaidWhenACallFails() {
        assertEquals(
            "birth_date: birth_date must match YYYY-MM-DD format",
            LagnaApi.errorMessage(400, """{"error":{"code":"VALIDATION_FAILED","message":"birth_date: birth_date must match YYYY-MM-DD format"}}"""),
        )
        assertEquals("Rate limit exceeded. Try again in 12 seconds.", LagnaApi.errorMessage(429, """{"detail":"Rate limit exceeded. Try again in 12 seconds."}"""))
        assertEquals("Too many requests just now. Try again in a minute.", LagnaApi.errorMessage(429, ""))
    }
}
