package site.lagnaatelier.app.timing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.timing.MuhurtaActivity
import site.lagnaatelier.app.data.timing.TimingQueries
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class TimingQueriesTest {
    private val sample = TimingQueries.sampleDetails

    @Test
    fun weekStartsAreSaturdays() {
        val tuesday = LocalDate.of(2026, 11, 3)
        assertEquals(LocalDate.of(2026, 10, 31), TimingQueries.weekStart(tuesday))
        assertEquals(LocalDate.of(2026, 11, 7), TimingQueries.nextWeekStart(tuesday))

        /* On a Saturday this week starts today and next week a week on. */
        val saturday = LocalDate.of(2026, 11, 7)
        assertEquals(saturday, TimingQueries.weekStart(saturday))
        assertEquals(LocalDate.of(2026, 11, 14), TimingQueries.nextWeekStart(saturday))

        /* A Friday is the last day of its week. */
        val friday = LocalDate.of(2026, 11, 6)
        assertEquals(LocalDate.of(2026, 10, 31), TimingQueries.weekStart(friday))
        assertEquals(LocalDate.of(2026, 11, 7), TimingQueries.nextWeekStart(friday))

        var day = LocalDate.of(2026, 1, 1)
        repeat(400) {
            assertEquals(DayOfWeek.SATURDAY, TimingQueries.nextWeekStart(day).dayOfWeek)
            assertEquals(DayOfWeek.SATURDAY, TimingQueries.weekStart(day).dayOfWeek)
            day = day.plusDays(1)
        }
    }

    @Test
    fun weeklyEnergyReadsCamelCaseAndOmitsWeekStartForThisWeek() {
        val params = TimingQueries.weeklyEnergy(sample)
        assertEquals("1990-06-15", params["birthDate"])
        assertEquals("14:30", params["birthTime"])
        assertEquals("330", params["timezoneOffsetMinutes"])
        assertEquals("Asia/Kolkata", params["timeZoneId"])
        assertEquals("lahiri_classic", params["engineId"])
        assertEquals("Delhi", params["state"])
        assertFalse(params.containsKey("week_start"))
        assertFalse(params.keys.any { it.contains('_') })

        val next = TimingQueries.weeklyEnergy(sample, LocalDate.of(2026, 11, 7))
        assertEquals("2026-11-07", next["week_start"])
        assertThrows(IllegalArgumentException::class.java) {
            TimingQueries.weeklyEnergy(sample, LocalDate.of(2026, 11, 3))
        }
    }

    @Test
    fun weeklyEnergyFillsABlankStateSoTheRouteAccepts() {
        val singapore = sample.copy(place = Place("Singapore", "", "Singapore", 1.29, 103.85, "Asia/Singapore", 480))
        val params = TimingQueries.weeklyEnergy(singapore)
        listOf("country", "state", "city").forEach { assertTrue(params[it]!!.isNotBlank()) }
    }

    @Test
    fun forecastAndVarshaphalReadSnakeCase() {
        val forecast = TimingQueries.forecast(sample, LocalDate.of(2026, 11, 3))
        assertEquals("2026-11-03", forecast["target_date"])
        assertEquals("1990-06-15", forecast["birth_date"])
        assertEquals("330", forecast["timezone_offset_minutes"])
        assertEquals("2026", TimingQueries.varshaphal(sample, 2026)["target_year"])
    }

    @Test
    fun muhurtaSearchesFourteenDaysIncludingToday() {
        val params = TimingQueries.muhurta(sample.place, MuhurtaActivity.Travel, LocalDate.of(2026, 11, 3), 330)
        assertEquals("travel", params["activity"])
        assertEquals("2026-11-03", params["start_date"])
        assertEquals("2026-11-16", params["end_date"])
        assertEquals("28.6139", params["latitude"])
        assertEquals("330", params["timezone_offset_minutes"])
    }

    @Test
    fun noMedicalActivityIsOffered() {
        assertTrue(MuhurtaActivity.entries.none { it.apiValue == "medical_procedure" })
    }

    @Test
    fun theOffsetNowFollowsTheZoneNotTheBirthDate() {
        /* Born in New York in July (EDT, -240); in November the city keeps EST, -300. */
        val newYork = Place("New York", "New York", "United States", 40.71, -74.0, "America/New_York", -240)
        assertEquals(-300, TimingQueries.offsetNow(newYork, Instant.parse("2026-11-03T12:00:00Z")))
        assertEquals(-240, TimingQueries.offsetNow(newYork, Instant.parse("2026-07-03T12:00:00Z")))
        assertEquals(330, TimingQueries.offsetNow(sample.place, Instant.parse("2026-11-03T12:00:00Z")))
        /* No zone, or one Java does not know: the stored offset stands. */
        assertEquals(-240, TimingQueries.offsetNow(newYork.copy(timeZoneId = ""), Instant.now()))
        assertEquals(-240, TimingQueries.offsetNow(newYork.copy(timeZoneId = "Nowhere/Here"), Instant.now()))
    }

    @Test
    fun theDayAtThePlaceCanDifferFromUtc() {
        /* 20:00 UTC on the 2nd is already 01:30 on the 3rd in New Delhi. */
        val instant = Instant.parse("2026-11-02T20:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 3), TimingQueries.placeToday(330, instant))
        assertEquals(LocalDate.of(2026, 11, 2), TimingQueries.placeToday(0, instant))
    }

    @Test
    fun theVarshaphalYearTurnsOnTheBirthday() {
        val birth = LocalDate.of(1990, 6, 15)
        assertEquals(2026, TimingQueries.varshaphalYear(birth, LocalDate.of(2026, 11, 3)))
        assertEquals(2026, TimingQueries.varshaphalYear(birth, LocalDate.of(2026, 6, 15)))
        assertEquals(2025, TimingQueries.varshaphalYear(birth, LocalDate.of(2026, 6, 14)))
        /* Never before the birth year, which the route refuses. */
        assertEquals(1990, TimingQueries.varshaphalYear(birth, LocalDate.of(1990, 1, 1)))
        /* A leap-day birthday turns on 28 February in a common year. */
        val leap = LocalDate.of(1992, 2, 29)
        assertEquals(2026, TimingQueries.varshaphalYear(leap, LocalDate.of(2026, 2, 28)))
        assertEquals(2025, TimingQueries.varshaphalYear(leap, LocalDate.of(2026, 2, 27)))
    }

    @Test
    fun theCacheKeyChangesWithTheBirthFacts() {
        val other = BirthDetails("Ananya Mehra", LocalDate.of(1990, 6, 15), "14:31", TimeAccuracy.Exact, sample.place)
        assertEquals(TimingQueries.key(sample), TimingQueries.key(sample.copy()))
        assertFalse(TimingQueries.key(sample) == TimingQueries.key(other))
    }
}
