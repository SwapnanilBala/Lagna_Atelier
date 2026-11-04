package site.lagnaatelier.app.periods

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.Period
import site.lagnaatelier.app.data.periods.ClassicalReading
import site.lagnaatelier.app.data.periods.CurrentPeriod
import site.lagnaatelier.app.data.periods.DashaReading
import site.lagnaatelier.app.data.periods.NatalParser
import site.lagnaatelier.app.data.periods.PeriodsApi
import site.lagnaatelier.app.data.timing.TimingParser
import site.lagnaatelier.app.data.timing.TimingQueries
import java.time.LocalDate

/*
 * The current-period paragraph and "Read this period", against live answers
 * from https://lagnaatelier.site for the sample chart (Ananya Mehra, 15 June
 * 1990, 14:30, New Delhi), captured on 2026-11-03 under
 * app/src/test/resources/periods. The chart is the one saved for the chart
 * parser's tests.
 */
class PeriodReadingsTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val natal = NatalParser.parse(resource("chart-ananya-mehra.json"))

    @Test
    fun readsTheNatalFactsTheReadingsNeed() {
        assertEquals("Virgo", natal.ascendantSign)
        assertEquals(9, natal.planets.size)
        assertEquals("Taurus", natal.planet("Mercury")!!.sign)
        assertEquals(9, natal.planet("Mercury")!!.house)
        assertEquals("Shatabhisha", natal.nakshatraName)
        assertEquals("Rahu", natal.nakshatraLord)
        assertEquals(4, natal.nakshatraPada)
        assertEquals(listOf("Mercury", "Mercury", "Rahu"), natal.stack.map { it.lord })
        assertEquals(LocalDate.of(2026, 11, 1), natal.stack[2].start)
        assertEquals(LocalDate.of(2027, 3, 13), natal.stack[2].end)
        assertEquals("1990-06-15T09:00", natal.birthUtcIso)
        assertEquals(10, natal.mahaPeriods.size)
        assertTrue(natal.mahaPeriods.first().isPartial)
        assertEquals(LocalDate.of(1972, 6, 28), natal.mahaPeriods.first().sequenceStart)
        assertEquals(17.0, natal.mahaPeriods.first { it.planet == "Mercury" }.years, 0.0)
    }

    /* The body the web panel sends for this chart, field for field: the route's cache key is built from it. */
    @Test
    fun buildsTheCurrentPeriodBodyAsTheWebDoes() {
        val body = JSONObject(CurrentPeriod.requestBody(natal, 56.0)!!)
        val stack = body.getJSONArray("stack")
        assertEquals(3, stack.length())
        val maha = stack.getJSONObject(0)
        assertEquals("Mercury", maha.getString("lord"))
        assertEquals("2025-06-29", maha.getString("startDate"))
        assertEquals("2042-06-29", maha.getString("endDate"))
        assertEquals("Taurus", maha.getString("sign"))
        assertEquals(9, maha.getInt("house"))
        val pratyantar = stack.getJSONObject(2)
        assertEquals("Rahu", pratyantar.getString("lord"))
        assertEquals("Capricorn", pratyantar.getString("sign"))
        assertEquals(5, pratyantar.getInt("house"))

        val chart = body.getJSONObject("chart")
        assertEquals("Virgo", chart.getString("ascendantSign"))
        val planets = chart.getJSONArray("planets")
        assertEquals(CurrentPeriod.LORDS, (0 until planets.length()).map { planets.getJSONObject(it).getString("name") })
        assertEquals("Pisces", planets.getJSONObject(2).getString("sign"))

        assertEquals("Shatabhisha", body.getJSONObject("nakshatra").getString("name"))
        assertEquals(4, body.getJSONObject("nakshatra").getInt("pada"))
        assertEquals(56.0, body.getDouble("progressPercent"), 0.0)
        assertEquals("en", body.getString("language"))
    }

    @Test
    fun sendsNothingWithoutTwoLordsOrAKnownNakshatra() {
        assertNull(CurrentPeriod.requestBody(natal.copy(stack = natal.stack.take(1)), 10.0))
        assertNull(CurrentPeriod.requestBody(natal.copy(nakshatraName = "Abhijit"), 10.0))
        /* A lord outside the nine stops the stack there. */
        val odd = natal.copy(stack = listOf(natal.stack[0], natal.stack[1].copy(lord = "Pluto"), natal.stack[2]))
        assertNull(CurrentPeriod.requestBody(odd, 10.0))
    }

    @Test
    fun leavesTheChartOutUnlessAllNinePlanetsAreKnown() {
        val body = JSONObject(CurrentPeriod.requestBody(natal.copy(planets = natal.planets.dropLast(1)), 120.0)!!)
        assertFalse(body.has("chart"))
        /* Ketu is gone, the stack's lords are not: their placements stay. */
        assertEquals("Taurus", body.getJSONArray("stack").getJSONObject(0).getString("sign"))
        assertEquals(100.0, body.getDouble("progressPercent"), 0.0)
    }

    @Test
    fun readsTheParagraph() {
        val reading = CurrentPeriod.parseReading(resource("periods/current-period-ananya-mehra.json"))!!
        assertTrue(reading.startsWith("With Mercury running both the Maha Dasha"))
        assertNull(CurrentPeriod.parseReading("""{"reading":"   ","cached":true}"""))
        assertNull(CurrentPeriod.parseReading("""{"cached":true}"""))
    }

    /* The chains the route walks to: the dates are the forecast's, which agree with the web's own spans to the day. */
    @Test
    fun namesEachPeriodByItsChainAndFirstDay() {
        val periods = TimingParser.parseForecast(resource("timing/forecast-ananya-mehra.json")).periods
        val chains = DashaReading.runningChains(periods)
        assertEquals(listOf(listOf("Mercury"), listOf("Mercury", "Mercury"), listOf("Mercury", "Mercury", "Rahu")), chains.map { it.lords })
        assertEquals(LocalDate.of(2026, 11, 1), chains[2].period.start)

        val jupiter = periods.pratyantars.first { it.lord == "Jupiter" }
        val chain = DashaReading.pratyantarChain(periods, jupiter)
        val body = JSONObject(DashaReading.requestBody(chain))
        assertEquals("""["Mercury","Mercury","Jupiter"]""", body.getJSONArray("lords").toString())
        assertEquals("2027-03-13", body.getString("start"))
        assertEquals("en", body.getString("language"))
        assertEquals("Mercury>Mercury>Jupiter@2027-03-13", chain.key)
    }

    @Test
    fun readsAPeriodReadingWithItsSources() {
        val answer = DashaReading.parse(resource("periods/dasha-reading-mercury-mercury-rahu.json"))
        val reading = (answer as DashaReading.Answer.Written).reading
        assertEquals(listOf(1, 2, 3, 4), reading.sources.map { it.number })
        assertEquals("brihat-jataka-1885", reading.sources[0].book)
        assertEquals("18.19", reading.sources[0].ref)
        assertFalse(reading.sources[0].isNote)
        assertTrue(reading.quotesOtherBooks)
        assertFalse(reading.quotesStrijataka)

        /* The second segment holds a blank line: it ends the first paragraph unmarked, and its mark goes to the second. */
        val paragraphs = reading.paragraphs()
        assertEquals(2, paragraphs.size)
        assertTrue(paragraphs[0][0].text.endsWith("head of a town, village or army"))
        assertEquals(listOf(1), paragraphs[0][0].sources)
        assertEquals(", so public responsibility may come alongside the personal matters.", paragraphs[0][1].text)
        assertTrue(paragraphs[0][1].sources.isEmpty())
        assertTrue(paragraphs[1][0].text.startsWith("Rahu sits in a trine"))
        assertEquals(listOf(2), paragraphs[1][0].sources)
        /* The closing full stop, which carries no mark, is kept. */
        assertEquals(".", paragraphs[1].last().text)
    }

    @Test
    fun treatsAnEmptyLibraryAsAnAnswer() {
        assertEquals(DashaReading.Answer.NothingInTheBooks, DashaReading.parse("""{"reading":null,"cached":false}"""))
        assertEquals(
            DashaReading.Answer.NothingInTheBooks,
            DashaReading.parse("""{"reading":{"segments":[{"text":"Uncited.","sources":[]}],"sources":[]}}"""),
        )
    }

    @Test
    fun paragraphsTrimAndDropEmptyPieces() {
        val reading = ClassicalReading.parse(
            JSONObject(
                """{"segments":[{"text":"  First.\n\n","sources":[]},{"text":"Second","sources":[2]},{"text":"\n\n  ","sources":[]}],
                   "sources":[{"number":2,"book":"strijataka-1931","ref":"3","kind":"note","text":"x"}]}""",
            ),
        )!!
        val paragraphs = reading.paragraphs()
        assertEquals(listOf("First."), paragraphs[0].map { it.text })
        assertEquals(listOf("Second"), paragraphs[1].map { it.text })
        assertEquals(2, paragraphs.size)
        assertTrue(reading.sources[0].isNote)
        assertTrue(reading.quotesStrijataka)
    }

    /* The reading routes read the page's own camelCase birth details. */
    @Test
    fun asksWithTheCamelCaseChartQuery() {
        val query = PeriodsApi.query(TimingQueries.sampleDetails)
        assertTrue(query.startsWith("name=Ananya+Mehra&birthDate=1990-06-15&birthTime=14%3A30&timezoneOffsetMinutes=330"))
        assertTrue(query.contains("&timeZoneId=Asia%2FKolkata&engineId=lahiri_classic&birthTimeAccuracy=exact"))
    }

    @Test
    fun pratyantarProgressDoesNotDecideThePhase() {
        /* The phase is the Antardasha's: 56% through on 3 November 2026, whatever the Pratyantar's. */
        val antar = Period("Mercury", LocalDate.of(2025, 6, 29), LocalDate.of(2027, 11, 25))
        val percent = CurrentPeriod.progressPercent(antar, LocalDate.of(2026, 11, 3))
        assertEquals(56.0, percent, 0.5)
    }
}
