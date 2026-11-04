package site.lagnaatelier.app.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.BirthSex
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.account.ChartQueryString
import site.lagnaatelier.app.data.chartKey
import java.time.LocalDate

class ChartQueryStringTest {
    private val delhi = Place("New Delhi", "Delhi", "India", 28.6139, 77.209, "Asia/Kolkata", 330)

    private fun details(accuracy: TimeAccuracy = TimeAccuracy.Exact, sex: BirthSex? = null, place: Place = delhi) =
        BirthDetails(
            name = "Ananya Mehra",
            date = LocalDate.of(1990, 6, 15),
            exactTime = if (accuracy == TimeAccuracy.Exact) "14:30" else null,
            accuracy = accuracy,
            place = place,
            engineId = "raman_placidus",
            sex = sex,
        )

    @Test
    fun roundTripsEveryAccuracyAndSexToTheSameChart() {
        for (accuracy in TimeAccuracy.entries) {
            for (sex in listOf(null, BirthSex.Female, BirthSex.Male)) {
                val original = details(accuracy, sex)
                val back = ChartQueryString.decode(ChartQueryString.encode(original))
                assertEquals(original, back)
                assertEquals(chartKey(original), chartKey(back!!))
            }
        }
    }

    @Test
    fun writesTheWebsitesCamelCaseNamesAndLeavesSexOutWhenNotGiven() {
        val plain = ChartQueryString.encode(details())
        assertTrue(plain.startsWith("name=Ananya+Mehra&birthDate=1990-06-15&birthTime=14%3A30&timezoneOffsetMinutes=330"))
        assertTrue(plain.contains("&timeZoneId=Asia%2FKolkata"))
        assertTrue(plain.contains("&engineId=raman_placidus"))
        assertTrue(plain.contains("&birthTimeAccuracy=exact&birthTimeSource=exact&birthTimeFallback=false"))
        assertFalse(plain.contains("birthSex"))

        assertTrue(ChartQueryString.encode(details(sex = BirthSex.Female)).endsWith("&birthSex=female"))
        val estimate = ChartQueryString.encode(details(TimeAccuracy.Evening))
        assertTrue(estimate.contains("birthTime=18%3A30"))
        assertTrue(estimate.contains("birthTimeSource=fallback&birthTimeFallback=true"))
    }

    @Test
    fun readsAChartTheWebsiteSaved() {
        /* buildChartHistoryQuery's shape: seconds on the time, %20 or + for spaces. */
        val web = "name=Ravi%20Kumar&birthDate=1985-01-02&birthTime=06:05:00&timezoneOffsetMinutes=330" +
            "&latitude=12.9716&longitude=77.5946&country=India&state=Karnataka&city=Bengaluru" +
            "&engineId=lahiri_classic&timeZoneId=Asia/Kolkata&birthSex=male"
        val chart = ChartQueryString.decode(web)!!
        assertEquals("Ravi Kumar", chart.name)
        assertEquals("06:05", chart.exactTime)
        assertEquals(TimeAccuracy.Exact, chart.accuracy)
        assertEquals("Bengaluru", chart.place.city)
        assertEquals(BirthSex.Male, chart.sex)
    }

    @Test
    fun leavesOnTheAccountAChartTheAppCouldNotCastAgain() {
        val full = ChartQueryString.encode(details())
        assertNull(ChartQueryString.decode(full.replace("&timeZoneId=Asia%2FKolkata", "")))
        assertNull(ChartQueryString.decode(full.replace("latitude=28.6139", "latitude=")))
        assertNull(ChartQueryString.decode(full.replace("birthTime=14%3A30", "birthTime=25%3A00")))
        assertNull(ChartQueryString.decode(full.replace("birthDate=1990-06-15", "birthDate=June")))
        assertNull(ChartQueryString.decode(""))
    }

    @Test
    fun ignoresAnUnknownSexAndAStrangeEngine() {
        val odd = ChartQueryString.encode(details()) + "&birthSex=other"
        assertNull(ChartQueryString.decode(odd)!!.sex)
        val engine = ChartQueryString.encode(details()).replace("raman_placidus", "%3Cscript%3E")
        assertEquals("lahiri_classic", ChartQueryString.decode(engine)!!.engineId)
    }
}
