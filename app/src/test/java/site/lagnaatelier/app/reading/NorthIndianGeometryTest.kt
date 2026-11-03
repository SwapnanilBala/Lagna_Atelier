package site.lagnaatelier.app.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.Placement
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.ui.reading.ChartPoint
import site.lagnaatelier.app.ui.reading.NorthIndianGeometry
import kotlin.math.abs

/* After the web's lib/__tests__/north-indian-chart.test.ts: everything lands inside its own house. */
class NorthIndianGeometryTest {
    private val geo = NorthIndianGeometry

    @Test
    fun housesCountWholeSignsFromTheAscendant() {
        assertEquals(1, geo.houseOfSign(Sign.Leo, ascendant = Sign.Leo))
        assertEquals(2, geo.houseOfSign(Sign.Virgo, ascendant = Sign.Leo))
        assertEquals(8, geo.houseOfSign(Sign.Pisces, ascendant = Sign.Leo))
        assertEquals(12, geo.houseOfSign(Sign.Cancer, ascendant = Sign.Leo))
        assertEquals(12, geo.houseOfSign(Sign.Pisces, ascendant = Sign.Aries))
        assertEquals(Sign.Leo, geo.signForHouse(Sign.Leo, 1))
        assertEquals(Sign.Cancer, geo.signForHouse(Sign.Leo, 12))
        assertEquals(Sign.Aries, geo.signForHouse(Sign.Pisces, 2))
    }

    @Test
    fun signForHouseAndHouseOfSignAreInverses() {
        for (ascendant in Sign.entries) {
            for (house in geo.HOUSES) {
                assertEquals(house, geo.houseOfSign(geo.signForHouse(ascendant, house), ascendant))
            }
            assertEquals(Sign.entries.toSet(), geo.HOUSES.map { geo.signForHouse(ascendant, it) }.toSet())
        }
    }

    @Test
    fun signNumbersCountFromAries() {
        assertEquals(1, geo.signNumber(Sign.Aries))
        assertEquals(5, geo.signNumber(Sign.Leo))
        assertEquals(12, geo.signNumber(Sign.Pisces))
    }

    @Test
    fun theTwelveRegionsTileTheSquare() {
        val area = geo.HOUSE_POLYGONS.values.sumOf { polygonArea(it) }
        assertEquals(1.0, area, 1e-6)
        geo.HOUSE_POLYGONS.values.flatten().forEach { assertTrue(it.x in 0f..1f && it.y in 0f..1f) }
        /* The angles are kites twice the size of the triangles. */
        assertEquals(0.125, polygonArea(geo.HOUSE_POLYGONS.getValue(1)), 1e-6)
        assertEquals(0.0625, polygonArea(geo.HOUSE_POLYGONS.getValue(2)), 1e-6)
    }

    @Test
    fun theTopKiteIsTheFirstHouseAndHousesRunAnticlockwise() {
        fun centroid(house: Int) = geo.HOUSE_POLYGONS.getValue(house).let { p ->
            ChartPoint(p.map { it.x }.average().toFloat(), p.map { it.y }.average().toFloat())
        }
        assertTrue(centroid(1).y < 0.3f && abs(centroid(1).x - 0.5f) < 1e-6f)
        /* Anticlockwise from the top: 2 is upper left, 4 the left kite, 7 the bottom, 10 the right. */
        assertTrue(centroid(2).x < 0.5f && centroid(2).y < 0.25f)
        assertTrue(centroid(4).x < 0.3f)
        assertTrue(centroid(7).y > 0.7f)
        assertTrue(centroid(10).x > 0.7f)
        assertTrue(centroid(12).x > 0.5f && centroid(12).y < 0.25f)
    }

    @Test
    fun signNumbersAndLabelBoxesSitInTheirOwnHouse() {
        for (house in geo.HOUSES) {
            val polygon = geo.HOUSE_POLYGONS.getValue(house)
            assertTrue("number of house $house", geo.contains(polygon, geo.SIGN_NUMBER_POINTS.getValue(house)))
            val box = geo.LABEL_BOXES.getValue(house)
            val corners = listOf(
                ChartPoint(box.x, box.y), ChartPoint(box.x + box.w, box.y),
                ChartPoint(box.x, box.y + box.h), ChartPoint(box.x + box.w, box.y + box.h),
            )
            corners.forEach { assertTrue("label box of house $house at $it", geo.contains(polygon, it)) }
            /* No other house's number inside this house's label box. */
            geo.SIGN_NUMBER_POINTS.forEach { (other, point) ->
                if (other != house) {
                    val inside = point.x > box.x && point.x < box.x + box.w && point.y > box.y && point.y < box.y + box.h
                    assertTrue("number of $other inside the box of $house", !inside)
                }
            }
        }
    }

    @Test
    fun placementsGoByTheirSignFromTheAscendant() {
        val chart = SampleChart.chart
        val byHouse = geo.placementsByHouse(chart.placements, chart.ascendant)
        assertEquals(chart.placements.size, byHouse.values.sumOf { it.size })
        byHouse.forEach { (house, bodies) ->
            bodies.forEach { assertEquals(geo.signForHouse(chart.ascendant, house), it.sign) }
        }
        val leoRising = geo.placementsByHouse(listOf(Placement("Sa", Sign.Capricorn), Placement("Mo", Sign.Leo)), Sign.Leo)
        assertEquals(listOf("Mo"), leoRising.getValue(1).map { it.abbreviation })
        assertEquals(listOf("Sa"), leoRising.getValue(6).map { it.abbreviation })
    }

    private fun polygonArea(polygon: List<ChartPoint>): Double {
        var twice = 0.0
        var j = polygon.size - 1
        for (i in polygon.indices) {
            twice += polygon[j].x.toDouble() * polygon[i].y - polygon[i].x.toDouble() * polygon[j].y
            j = i
        }
        return abs(twice) / 2
    }
}
