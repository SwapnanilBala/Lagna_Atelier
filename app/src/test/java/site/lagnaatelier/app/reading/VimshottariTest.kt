package site.lagnaatelier.app.reading

import org.junit.Assert.assertEquals
import org.junit.Test
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ChartParser
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.Vimshottari
import site.lagnaatelier.app.data.antardashas
import java.time.LocalDate

class VimshottariTest {
    private val fixture = javaClass.classLoader!!.getResource("chart-ananya-mehra.json")!!.readText()
    private val details = BirthDetails(
        name = "Ananya Mehra",
        date = LocalDate.of(1990, 6, 15),
        exactTime = "14:30",
        accuracy = TimeAccuracy.Exact,
        place = Place("New Delhi", "Delhi", "India", 28.6139, 77.209, "Asia/Kolkata", 330),
    )

    @Test
    fun worksOutTheAntardashasTheChartReports() {
        val chart = ChartParser.parse(fixture, details)
        val antars = chart.antardashas
        assertEquals(9, antars.size)
        assertEquals("Mercury", antars.first().lord)
        assertEquals(chart.mahadasha.start, antars.first().start)
        assertEquals(chart.mahadasha.end, antars.last().end)
        antars.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
        /* The one running now is the chart's own current_antardasha, to the day. */
        val now = antars.single { it.sameAs(chart.antardasha) }
        assertEquals(chart.antardasha.end, now.end)
    }

    @Test
    fun clipsTheMahadashaRunningAtBirthToTheLife() {
        val chart = ChartParser.parse(fixture, details)
        val atBirth = Vimshottari.subPeriods(chart.mahadashas.first())
        assertEquals(details.date, atBirth.first().start)
        assertEquals(chart.mahadashas.first().end, atBirth.last().end)
    }
}
