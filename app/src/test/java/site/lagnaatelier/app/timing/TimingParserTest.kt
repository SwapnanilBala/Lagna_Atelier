package site.lagnaatelier.app.timing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.progress
import site.lagnaatelier.app.data.timing.EnergyBand
import site.lagnaatelier.app.data.timing.ReaderCopy
import site.lagnaatelier.app.data.timing.TimingFormat
import site.lagnaatelier.app.data.timing.TimingParser
import site.lagnaatelier.app.data.timing.TransitTone
import site.lagnaatelier.app.data.timing.YearLordReason
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/*
 * The timing parsers against real responses from https://lagnaatelier.site for
 * the sample chart (Ananya Mehra, 15 June 1990, 14:30, New Delhi), captured on
 * 2026-11-03 and saved under app/src/test/resources/timing.
 */
class TimingParserTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("timing/$name")!!.readText()

    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun readsTheThreeRunningPeriods() {
        val periods = TimingParser.parseForecast(fixture("forecast-ananya-mehra.json")).periods
        assertEquals("Mercury", periods.maha.lord)
        assertEquals(LocalDate.of(2042, 6, 29), periods.maha.end)
        assertEquals("Mercury", periods.antar.lord)
        assertEquals(LocalDate.of(2027, 11, 25), periods.antar.end)
        val pratyantar = periods.pratyantar!!
        assertEquals("Rahu", pratyantar.lord)
        assertEquals(LocalDate.of(2026, 11, 1), pratyantar.start)
        assertEquals(LocalDate.of(2027, 3, 13), pratyantar.end)

        /* The mock's figures: 8%, 56% and 2% through on 3 November 2026. */
        assertEquals(8, (periods.maha.progress(today) * 100).roundToInt())
        assertEquals(56, (periods.antar.progress(today) * 100).roundToInt())
        assertEquals(2, (pratyantar.progress(today) * 100).roundToInt())
    }

    @Test
    fun findsTheNextPratyantarAndTheRemainingOnes() {
        val periods = TimingParser.parseForecast(fixture("forecast-ananya-mehra.json")).periods
        assertEquals(9, periods.pratyantars.size)
        periods.pratyantars.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
        val next = periods.next!!
        assertEquals("Jupiter", next.lord)
        assertEquals(LocalDate.of(2027, 3, 13), next.start)
        assertEquals("Rahu", periods.remaining.first().lord)
        assertEquals(periods.antar.end, periods.remaining.last().end)
    }

    @Test
    fun readsBothTransitListsWithTheirTone() {
        val transits = TimingParser.parseForecast(fixture("forecast-ananya-mehra.json")).transits
        assertEquals(2, transits.size)
        /* Sooner to exact first: the Moon (0.5 days) before Venus (1.6). */
        val moon = transits[0]
        assertEquals("Moon", moon.planet)
        assertEquals("Mercury", moon.natalPlanet)
        assertEquals("Square", moon.aspect)
        assertEquals(TransitTone.Testing, moon.tone)
        val venus = transits[1]
        assertEquals("Venus", venus.planet)
        assertEquals("Sun", venus.natalPlanet)
        assertEquals(TransitTone.Easy, venus.tone)
        assertEquals(1.6, venus.daysToExact!!, 1e-9)
        assertTrue(venus.interpretation.startsWith("Venus trine natal Sun"))
    }

    @Test
    fun aTransitPastExactHasNoDaysToExact() {
        val body = """
            {"target_date":"2026-11-03","dasha":{"current_dasha":"Mercury","current_antardasha":"Mercury",
             "current_dasha_start":"2025-06-29","current_dasha_end":"2042-06-29",
             "current_antardasha_start":"2025-06-29","current_antardasha_end":"2027-11-25"},
             "supportive_transits":[{"transit_planet":"Jupiter","natal_planet":"Sun","aspect_type":"Sextile",
               "orb":0.07,"tone":"supportive","interpretation":"Steady support.","applying":false}],
             "challenging_transits":[]}
        """.trimIndent()
        val forecast = TimingParser.parseForecast(body)
        assertNull(forecast.periods.pratyantar)
        assertNull(forecast.periods.next)
        assertNull(forecast.transits.single().daysToExact)
    }

    @Test
    fun readsTheWeekSaturdayToFriday() {
        val week = TimingParser.parseWeek(fixture("weekly-energy-this-week.json"))
        assertEquals(LocalDate.of(2026, 10, 31), week.start)
        assertEquals(LocalDate.of(2026, 11, 6), week.end)
        assertEquals(listOf(47, 60, 68, 41, 76, 50, 56), week.days.map { it.score })
        assertEquals(
            listOf(
                EnergyBand.Balanced, EnergyBand.Balanced, EnergyBand.High, EnergyBand.Low,
                EnergyBand.High, EnergyBand.Balanced, EnergyBand.Balanced,
            ),
            week.days.map { it.band },
        )
        assertEquals(57, week.average)
        assertEquals("Fullness Turns", week.headlineTitle)
        assertTrue(week.headlineParagraph.startsWith("A week of full days"))
        assertEquals(4, week.cards.size)
        assertEquals("New Opportunities", week.cards[0].title)
        assertEquals(LocalDate.of(2026, 11, 2), week.cards[0].bestDay)
    }

    @Test
    fun readsNextWeekFromTheFollowingSaturday() {
        val week = TimingParser.parseWeek(fixture("weekly-energy-next-week.json"))
        assertEquals(LocalDate.of(2026, 11, 7), week.start)
        assertEquals(java.time.DayOfWeek.SATURDAY, week.days.first().date.dayOfWeek)
        assertEquals(7, week.days.size)
    }

    @Test
    fun aWeekStartThatIsNotASaturdayCarriesTheSitesMessage() {
        val message = LagnaApi.errorMessage(400, fixture("weekly-energy-bad-week-start.json"))
        assertEquals("week_start: week_start must be a Saturday", message)
    }

    @Test
    fun muhurtaWindowsAreRealInstantsShownAtThePlacesClock() {
        val result = TimingParser.parseMuhurta(fixture("muhurta-travel.json"))
        assertEquals(330, result.offsetMinutes)
        assertEquals(10, result.windows.size)
        val best = result.best!!
        assertEquals(94, best.score)
        assertEquals(Instant.parse("2026-11-08T18:00:00Z"), best.start)
        /* 18:00 UTC is 23:30 in New Delhi, and the window runs into the next day. */
        assertEquals("23:30", TimingFormat.windowStart(best, 330))
        assertEquals(LocalDate.of(2026, 11, 8), TimingFormat.windowDay(best, 330))
        assertEquals("Mon 9 Nov 13:30", TimingFormat.windowEnd(best, 330))

        /* The mock's Wed 4 window: 10:00 UTC is 15:30 IST, ending Thu 5 Nov 12:30. */
        val wednesday = result.windows.first { it.start == Instant.parse("2026-11-04T10:00:00Z") }
        assertEquals("15:30", TimingFormat.windowStart(wednesday, 330))
        assertEquals("Thu 5 Nov 12:30", TimingFormat.windowEnd(wednesday, 330))

        /* A window inside one day shows its end as a time alone. */
        val sameDay = result.windows.first { it.start == Instant.parse("2026-11-06T04:00:00Z") }
        assertEquals("09:30", TimingFormat.windowStart(sameDay, 330))
        assertEquals("16:30", TimingFormat.windowEnd(sameDay, 330))
    }

    @Test
    fun windowsAreBestFirstThenEarliest() {
        val windows = TimingParser.parseMuhurta(fixture("muhurta-travel.json")).windows
        windows.zipWithNext().forEach { (a, b) ->
            assertTrue(a.score > b.score || (a.score == b.score && !a.start.isAfter(b.start)))
        }
        val nineties = windows.filter { it.score == 92 }.map { it.start }
        assertEquals(nineties.sorted(), nineties)
    }

    @Test
    fun rahukaalaIsReadAsWrittenNotConverted() {
        val result = TimingParser.parseMuhurta(fixture("muhurta-travel.json"))
        assertEquals(14, result.avoid.size)
        val rahu = result.rahukaalaOn(LocalDate.of(2026, 11, 3))!!
        /* "2026-11-03T14:42:08.404Z" is already New Delhi's clock: 14:42, not 20:12. */
        assertEquals("14:42", TimingFormat.clock(rahu.rahuStart))
        assertEquals("16:03", TimingFormat.clock(rahu.rahuEnd))
        assertEquals(LocalTime.of(12, 0), result.rahukaalaOn(LocalDate.of(2026, 11, 4))!!.rahuStart)
        assertNull(result.rahukaalaOn(LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun readsThisYearsChart() {
        val year = TimingParser.parseVarshaphal(fixture("varshaphal-2026.json"))
        assertEquals(2026, year.year)
        assertEquals("Sagittarius", year.rising)
        assertEquals(10.7789, year.risingDegree, 1e-4)
        assertEquals("Mercury", year.yearLord)
        assertEquals(YearLordReason.RulesMuntha, year.yearLordReason)
        assertEquals("Virgo", year.munthaSign)
        assertEquals(10, year.munthaHouse)
        assertEquals(37, year.profectionYear)
        assertEquals(1, year.profectionHouse)
        assertEquals("Virgo", year.profectionSign)
        assertNotNull(year.returnMoment)
        val (title, body) = TimingFormat.splitSummary(year.summary)
        assertEquals("A year of communication, learning, and commerce", title)
        assertEquals("Mental agility is the prime asset.", body)
    }

    @Test
    fun yearLordReasonsMapFromTheEnginesWording() {
        assertEquals(YearLordReason.RulesYearRising, TimingParser.yearLordReason("Lord of solar return ascendant (Leo)"))
        assertEquals(YearLordReason.RulesBirthRising, TimingParser.yearLordReason("Lord of natal ascendant (Virgo)"))
        assertEquals(YearLordReason.RulesYearRising, TimingParser.yearLordReason("Default to return ascendant lord"))
        assertEquals(YearLordReason.Other, TimingParser.yearLordReason(""))
    }

    @Test
    fun linesAboutDeathDiseaseOrDisabilityAreDropped() {
        assertFalse(ReaderCopy.isShowable("A risk of illness this week."))
        assertFalse(ReaderCopy.isShowable("Death of an elder"))
        assertTrue(ReaderCopy.isShowable("Words land well this week, Wed 4 especially."))
        assertTrue(ReaderCopy.isShowable("Studied patience pays.")) // "die" inside a word is fine

        val body = fixture("weekly-energy-this-week.json")
            .replace("Momentum is available if you start it.", "Watch for illness.")
        val week = TimingParser.parseWeek(body)
        assertEquals(3, week.cards.size)
        assertTrue(week.cards.none { it.title == "New Opportunities" })
    }

    @Test
    fun abbreviatesPlanetsAsTheChartDoes() {
        assertEquals("Ve", TimingFormat.planetAbbreviation("Venus"))
        assertEquals("Ma", TimingFormat.planetAbbreviation("Mars"))
        assertEquals("Ke", TimingFormat.planetAbbreviation("Ketu"))
    }
}
