package site.lagnaatelier.app.periods

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.periods.LifeShifts
import site.lagnaatelier.app.data.periods.NatalParser
import site.lagnaatelier.app.data.periods.PeriodsApi
import site.lagnaatelier.app.data.periods.ShiftKind
import site.lagnaatelier.app.data.periods.ShiftStatus
import java.time.Instant

/*
 * The port of lib/engines/major-shifts-engine.ts. The expected ids are what
 * the web's own engine printed for the sample chart at 2026-11-03T12:00Z
 * (run with tsx against the saved chart): the route drops an id its engine
 * does not produce, so they must match to the millisecond.
 */
class LifeShiftsTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val natal = NatalParser.parse(resource("chart-ananya-mehra.json"))
    private val now = Instant.parse("2026-11-03T12:00:00Z")
    private val shifts = LifeShifts.compute(natal, now)

    @Test
    fun picksTheFiveChaptersNearestNowWithTheWebsIds() {
        assertEquals(
            listOf(
                "saturn-return-2019-11-29T13:56:18.960Z",
                "mahadasha-2025-06-29T00:00:00.000Z",
                "jupiter-return-2026-01-15T03:52:33.600Z",
                "nodal-return-2027-09-06T02:21:46.080Z",
                "jupiter-return-2037-11-25T18:10:04.800Z",
            ),
            shifts.map { it.id },
        )
        assertEquals(listOf(1, 2, 3, 4, 5), shifts.map { it.index })
        assertEquals(
            listOf(ShiftStatus.Past, ShiftStatus.Past, ShiftStatus.Past, ShiftStatus.Upcoming, ShiftStatus.Upcoming),
            shifts.map { it.status },
        )
        assertEquals(listOf(29, 35, 36, 37, 47), shifts.map { it.ageAtPivot })
    }

    @Test
    fun writesTheEnginesWindowsLabelsAndTemplates() {
        val saturn = shifts[0]
        assertEquals(ShiftKind.SaturnReturn, saturn.kind)
        assertEquals("Saturn return (first)", saturn.label)
        assertEquals("2018-11-29T13:56:18.960Z", LifeShifts.isoString(saturn.windowStart))
        assertEquals("2020-11-28T13:56:18.960Z", LifeShifts.isoString(saturn.windowEnd))
        assertEquals(
            "Saturn comes back to its birth position around age 29. This is the classic life pivot: commitments " +
                "harden, false structures crack, and the area where Saturn sits in your chart (Capricorn / H5) demands " +
                "a more honest version. Expect a 9 to 18 month rebuild, not an event.",
            saturn.narrative,
        )

        val mercury = shifts[1]
        assertEquals("Mercury mahadasha begins", mercury.label)
        assertEquals("communication, study, and adaptive thinking", mercury.theme)
        assertEquals("2024-10-02T00:00:00.000Z", LifeShifts.isoString(mercury.windowStart))
        assertEquals("Mahadasha lord: Mercury | Lasts ~17 years | Natal Mercury: Taurus / H9", mercury.evidence)
        assertTrue(mercury.narrative.startsWith("The Mercury mahadasha opens around age 35 — a communication"))

        val nodes = shifts[3]
        assertEquals("Rahu / Ketu", nodes.planet)
        assertEquals("Nodal return (second)", nodes.label)
        assertEquals("Cycle: ~18.6 years | Rahu: Capricorn / H5 | Ketu: Cancer / H11", nodes.evidence)
        assertEquals("September 2027", LifeShifts.pivotLabel(nodes))
        assertEquals("Dec 2026 → Jun 2028", LifeShifts.windowLabel(nodes))
        assertEquals("Jupiter return (fourth)", shifts[4].label)
    }

    @Test
    fun putsTheChaptersAheadFirstAndThePastAfter() {
        val page = LifeShifts.page(shifts)
        assertEquals(listOf(4, 5), page.featured.map { it.index })
        assertEquals(listOf(1, 2, 3), page.past.map { it.index })
        /* Asked for in the order the page shows them, as the web asks. */
        val body = JSONObject(LifeShifts.requestBody(page.shown.map { it.id }))
        assertEquals(5, body.getJSONArray("ids").length())
        assertEquals("nodal-return-2027-09-06T02:21:46.080Z", body.getJSONArray("ids").getString(0))
        assertEquals("compact", body.getString("depth"))
    }

    @Test
    fun withNothingAheadTheLastChapterLeads() {
        val late = LifeShifts.compute(natal, Instant.parse("2120-01-01T00:00:00Z"))
        val page = LifeShifts.page(late)
        assertEquals(listOf(late.last().id), page.featured.map { it.id })
        assertEquals(late.size - 1, page.past.size)
    }

    @Test
    fun countsFromTheFirstPeriodWhenTheAuditHasNoBirthInstant() {
        val bare = natal.copy(birthUtcIso = null, birthLocalIso = null)
        /* 1972-06-28, the partial Rahu period's true start: the returns move, the dasha openings do not. */
        val ids = LifeShifts.compute(bare, now).map { it.id }
        assertTrue(ids.contains("mahadasha-2025-06-29T00:00:00.000Z"))
        assertTrue(ids.none { it.startsWith("saturn-return-2019-11-29") })
        assertTrue(LifeShifts.compute(bare.copy(mahaPeriods = emptyList()), now).isEmpty())
    }

    /* The live stream: the chapters ahead in one batch, the past in another, then `done`. */
    @Test
    fun readsTheStreamBatchByBatch() {
        val batches = mutableListOf<Set<String>>()
        val readings = PeriodsApi.readStream(resource("periods/life-shifts-ananya-mehra.ndjson").lineSequence()) {
            batches.add(it.keys)
        }
        assertEquals(5, readings.size)
        assertEquals(2, batches.size)
        assertEquals(
            setOf("nodal-return-2027-09-06T02:21:46.080Z", "jupiter-return-2037-11-25T18:10:04.800Z"),
            batches[0],
        )
        assertEquals(5, batches[1].size)
        val mahadasha = readings.getValue("mahadasha-2025-06-29T00:00:00.000Z")
        assertEquals(listOf(1, 2, 3), mahadasha.sources.map { it.number })
        assertTrue(mahadasha.paragraphs().isNotEmpty())
        /* Every id the stream answered is one the port computes. */
        assertTrue(shifts.map { it.id }.containsAll(readings.keys))
    }

    @Test
    fun readsThePlainAnswerAndSkipsBrokenLines() {
        val plain = """{"readings":[{"id":"a","reading":{"segments":[{"text":"Cited.","sources":[1]}],
            "sources":[{"number":1,"book":"brihat-jataka-1885","ref":"1.1","kind":"verse","text":"v"}]}},
            {"id":"b","reading":{"segments":[],"sources":[]}},{"reading":{}}],"cached":true}"""
        assertEquals(setOf("a"), LifeShifts.parseReadings(JSONObject(plain)).keys)

        val lines = sequenceOf("not json", "", """{"type":"error","message":"x"}""", """{"type":"readings","readings":[]}""")
        assertTrue(PeriodsApi.readStream(lines) { throw AssertionError("no batch") }.isEmpty())
    }
}
