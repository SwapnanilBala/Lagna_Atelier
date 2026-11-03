package site.lagnaatelier.app.more

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Test
import site.lagnaatelier.app.data.lifeareas.ActivityBand
import site.lagnaatelier.app.data.lifeareas.ClassicalReading
import site.lagnaatelier.app.data.lifeareas.ClassicalSegment
import site.lagnaatelier.app.data.lifeareas.ClassicalSource
import site.lagnaatelier.app.data.lifeareas.Conclusion
import site.lagnaatelier.app.data.lifeareas.Confirmation
import site.lagnaatelier.app.data.lifeareas.LifeAreaKey
import site.lagnaatelier.app.data.lifeareas.LifeAreasParser

/*
 * Against real answers for the sample chart (Ananya Mehra), fetched from
 * lagnaatelier.site on 2026-11-03: life-domains.json, domain-brief.json and
 * area-classics.json in test resources/lifeareas.
 */
class LifeAreasParserTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("lifeareas/$name")!!.readText()

    @Test
    fun readsAllSevenAreasMostActiveFirst() {
        val areas = LifeAreasParser.parseAreas(fixture("life-domains.json"))
        assertEquals(7, areas.size)
        assertEquals(LifeAreaKey.entries.toSet(), areas.map { it.key }.toSet())
        // Love life and inheritance tie at 0.94; the engine's order breaks the tie.
        assertEquals(
            listOf(
                LifeAreaKey.Love, LifeAreaKey.Inheritance, LifeAreaKey.Career, LifeAreaKey.Family,
                LifeAreaKey.Influence, LifeAreaKey.LifeCycle, LifeAreaKey.Travel,
            ),
            areas.map { it.key },
        )
        areas.zipWithNext().forEach { (a, b) -> assertTrue(a.activityScore >= b.activityScore) }
    }

    @Test
    fun keepsTheEnginesStandingAndText() {
        val love = LifeAreasParser.parseAreas(fixture("life-domains.json")).first { it.key == LifeAreaKey.Love }
        assertEquals("What partnership actually asks of you", love.headline)
        assertEquals(ActivityBand.Prominent, love.band)
        assertEquals(Conclusion.Cautious, love.conclusion)
        assertEquals(Confirmation.Contradictory, love.confirmation)
        assertEquals(3, love.strengths.size)
        assertEquals(2, love.watchouts.size)
        assertEquals(2, love.timing.size)
        assertTrue(love.body.startsWith("Partnership starts through"))
        assertTrue(love.guidance.startsWith("In love, name the terms"))
        assertEquals("Commitment", love.subthemes.first().label)
    }

    @Test
    fun skipsAnAreaItCannotRead() {
        val json = """{"insights":[
            {"key":"career","display":{"headline":"Work","body":"b","strengths":[],"watchouts":[],"timing":[]},"signal_profile":{"activity_score":0.5,"activity_band":"active"}},
            {"key":"astral_travel","display":{"headline":"No"}},
            {"key":"family"}
        ]}"""
        val areas = LifeAreasParser.parseAreas(json)
        assertEquals(listOf(LifeAreaKey.Career), areas.map { it.key })
        assertNull(areas.first().conclusion)
    }

    @Test
    fun readsEveryBriefFromOneAnswer() {
        val briefs = LifeAreasParser.parseBriefs(fixture("domain-brief.json"))
        assertEquals(LifeAreaKey.entries.toSet(), briefs.keys)
        assertTrue(briefs.getValue(LifeAreaKey.Career).startsWith("Your working life"))
    }

    @Test
    fun readsTheAreasClassicalNotesWithTheirSources() {
        val notes = LifeAreasParser.parseAreaClassics(fixture("area-classics.json"))
        // The books had nothing on travel for this chart, so it is absent: an answer, not a failure.
        assertEquals(
            setOf(LifeAreaKey.Love, LifeAreaKey.Career, LifeAreaKey.Family, LifeAreaKey.Inheritance, LifeAreaKey.Influence, LifeAreaKey.LifeCycle),
            notes.keys,
        )
        val love = notes.getValue(LifeAreaKey.Love)
        assertEquals(3, love.sources.size)
        assertEquals(ClassicalSource(1, "brihat-jataka-1885", "17.11", isNote = false, text = love.sources[0].text), love.sources[0])
        assertFalse(love.sources[0].fromWomensBook)
        // Every mark points at a passage the note prints.
        val numbers = love.sources.map { it.number }.toSet()
        assertTrue(love.segments.flatMap { it.sources }.all { it in numbers })
    }

    @Test
    fun cutsParagraphsAtBlankLinesAndKeepsMarksAfterTheirWords() {
        val reading = ClassicalReading(
            segments = listOf(
                ClassicalSegment(" With the Moon in Aquarius you will be ", emptyList()),
                ClassicalSegment("fond of friends", listOf(1)),
                ClassicalSegment(".\n\nWith Venus in Aries, ", emptyList()),
                ClassicalSegment("you will love art\n\n", listOf(2)),
            ),
            sources = listOf(
                ClassicalSource(1, "brihat-jataka-1885", "17.11", false, "a"),
                ClassicalSource(2, "brihat-jataka-1885", "18.14", false, "b"),
            ),
        )
        val paragraphs = reading.paragraphs()
        assertEquals(2, paragraphs.size)
        assertEquals("With the Moon in Aquarius you will be ", paragraphs[0][0].text)
        assertEquals(listOf(1), paragraphs[0][1].sources)
        assertEquals(".", paragraphs[0].last().text)
        // The mark stays on the last piece of its segment, after the paragraph break is cut away.
        assertEquals("you will love art", paragraphs[1][1].text)
        assertEquals(listOf(2), paragraphs[1][1].sources)
    }

    @Test
    fun treatsAReadingWithNothingToShowAsNone() {
        assertNull(LifeAreasParser.reading(JSONObject("""{"segments":[{"text":"x","sources":[]}],"sources":[]}""")))
        assertNull(LifeAreasParser.reading(JSONObject("""{"segments":[],"sources":[{"number":1,"book":"b","ref":"1","kind":"verse","text":"t"}]}""")))
        assertNull(LifeAreasParser.reading(null))
    }

    @Test
    fun dropsAMarkWithNoPassageAndKnowsTheWomensBook() {
        val reading = LifeAreasParser.reading(
            JSONObject(
                """{"segments":[{"text":"A note","sources":[1,7]}],
                   "sources":[{"number":1,"book":"strijataka-1931","ref":"4","kind":"note","text":"t"}]}""",
            ),
        )!!
        assertEquals(listOf(1), reading.segments.single().sources)
        assertTrue(reading.sources.single().fromWomensBook)
        assertTrue(reading.sources.single().isNote)
    }
}
