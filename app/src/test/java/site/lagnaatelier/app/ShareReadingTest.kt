package site.lagnaatelier.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.ShareReading
import site.lagnaatelier.app.data.TimeAccuracy

/** The link the reading's Share button sends. */
class ShareReadingTest {
    private val delhi = Place("New Delhi", "Delhi", "India", 28.6139, 77.209, "Asia/Kolkata", 330)

    @Test
    fun `the sample chart links to the home page`() {
        assertEquals("https://lagnaatelier.site", ShareReading.link(null))
    }

    @Test
    fun `a cast chart opens its own reading on the website`() {
        val link = ShareReading.link(BirthDetails("Ananya Mehra", LocalDate.of(1990, 6, 15), "14:30", TimeAccuracy.Exact, delhi))
        assertTrue(link.startsWith("https://lagnaatelier.site/insights?name=Ananya+Mehra&birthDate=1990-06-15&birthTime=14%3A30"))
        /* All nine the page needs, or the link dead-ends at the intake form. */
        for (key in listOf("timezoneOffsetMinutes=330", "latitude=28.6139", "longitude=77.209", "country=India", "state=Delhi", "city=New+Delhi")) {
            assertTrue(key, link.contains("&$key"))
        }
        assertTrue(link.contains("&timeZoneId=Asia%2FKolkata"))
    }
}
