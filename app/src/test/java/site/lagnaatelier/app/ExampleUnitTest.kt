package site.lagnaatelier.app

import org.junit.Assert.assertEquals
import org.junit.Test
import site.lagnaatelier.app.data.Period
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.data.formatDegree
import site.lagnaatelier.app.data.progress
import site.lagnaatelier.app.ui.reading.southIndianCell
import java.time.LocalDate

/* The arithmetic the reading screen prints, checked against the web app's figures. */
class ChartMathTest {
    @Test
    fun degreesPrintAsTheWebAppPrintsThem() {
        assertEquals("28°45′", formatDegree(28.7507))
        assertEquals("19°58′", formatDegree(19.97))
        assertEquals("1°00′", formatDegree(0.9999))
        assertEquals("0°00′", formatDegree(0.0))
    }

    @Test
    fun periodProgressIsTheShareOfDaysElapsed() {
        val antardasha = SampleChart.chart.antardasha
        assertEquals(0.56f, antardasha.progress(LocalDate.of(2026, 11, 2)), 0.01f)
        assertEquals(0f, antardasha.progress(LocalDate.of(2020, 1, 1)), 0f)
        assertEquals(1f, antardasha.progress(LocalDate.of(2030, 1, 1)), 0f)
        assertEquals(0f, Period("Sun", LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 1)).progress(LocalDate.of(2000, 1, 1)), 0f)
    }

    @Test
    fun southIndianSquaresRunClockwiseFromPisces() {
        assertEquals(0 to 0, southIndianCell(Sign.Pisces))
        assertEquals(3 to 0, southIndianCell(Sign.Gemini))
        assertEquals(3 to 3, southIndianCell(Sign.Virgo))
        assertEquals(0 to 3, southIndianCell(Sign.Sagittarius))
        assertEquals(0 to 1, southIndianCell(Sign.Aquarius))
        // Every sign has its own square, and none falls in the centre.
        val cells = Sign.entries.map(::southIndianCell)
        assertEquals(12, cells.toSet().size)
        assertEquals(false, cells.any { (c, r) -> c in 1..2 && r in 1..2 })
    }

    @Test
    fun theSampleTimelineIsContinuous() {
        SampleChart.chart.mahadashas.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
    }
}
