package site.lagnaatelier.app.report

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.report.ProseChapter
import site.lagnaatelier.app.data.report.ProseProgress
import site.lagnaatelier.app.data.report.ReportApi
import site.lagnaatelier.app.data.report.StoryParser
import site.lagnaatelier.app.data.report.StoryProse
import site.lagnaatelier.app.data.report.StoryProseCodec

/*
 * Fixtures:
 *   report/story-prose-facts-ananya-mehra.json  lib/story-prose.ts buildStoryProseFacts run
 *       on the story fixture with the web's own code (tsx), through JSON.stringify.
 *   report/story-prose-ananya-mehra.ndjson      the one real POST /api/chart/story-prose
 *       answer, streamed (Accept: application/x-ndjson), 2026-11-03.
 */
class StoryProseTest {
    private val story = StoryParser.parseReport(resource("report/story-report-ananya-mehra.json")).story
    private val stream = resource("report/story-prose-ananya-mehra.ndjson")

    private fun written(): StoryProse {
        val result = ReportApi.readStream(stream.lineSequence()) {}
        return (result as ReportApi.ProseResult.Written).prose
    }

    @Test
    fun theFactsMatchTheWebsByteForByte() {
        val expected = resource("report/story-prose-facts-ananya-mehra.json").trimEnd()
        assertEquals(expected, StoryProseCodec.factsJson(story))
        assertEquals("{\"facts\":$expected}", StoryProseCodec.requestBody(story))
    }

    @Test
    fun theFactsCarryNoNameAndNoTitle() {
        val body = StoryProseCodec.requestBody(story)
        val facts = JSONObject(body).getJSONObject("facts")
        assertEquals(setOf("subtitle", "atAGlance", "centralThemes", "chapters"), facts.keys().asSequence().toSet())
        assertFalse(body.contains("clientName"))
        assertFalse(body.contains("headline"))
        assertFalse(body.contains("Ananya"))
        assertEquals(
            setOf("id", "title", "eyebrow", "support", "signals", "draft"),
            facts.getJSONArray("chapters").getJSONObject(0).keys().asSequence().toSet(),
        )
    }

    @Test
    fun theDraftSendsTheOpeningOnce() {
        val essence = story.chapters.first()
        val draft = JSONObject(StoryProseCodec.factsJson(story)).getJSONArray("chapters").getJSONObject(0).getString("draft")
        assertEquals("${essence.opening} ${essence.narrative[1]}", draft)
    }

    @Test
    fun aChapterWithNoNarrativeDraftsFromItsBodyAndDefaultsItsSupport() {
        val bare = story.chapters.first().copy(opening = null, narrative = emptyList(), body = "  Body text.  ", support = null)
        val json = JSONObject(StoryProseCodec.factsJson(story.copy(chapters = listOf(bare))))
        val chapter = json.getJSONArray("chapters").getJSONObject(0)
        assertEquals("Body text.", chapter.getString("draft"))
        assertEquals("supported", chapter.getString("support"))
    }

    @Test
    fun escapingFollowsJsonStringify() {
        val odd = story.copy(subtitle = "Quote \" slash \\ / tab\t line\n bell\u0007 é – 🌙 </script>")
        val json = StoryProseCodec.factsJson(odd)
        assertTrue(json.startsWith("{\"subtitle\":\"Quote \\\" slash \\\\ / tab\\t line\\n bell\\u0007 é – 🌙 </script>\","))
        assertEquals(odd.subtitle, JSONObject(json).getString("subtitle"))
    }

    @Test
    fun theStreamHandsOnProgressThenTheProse() {
        val progress = mutableListOf<ProseProgress>()
        val result = ReportApi.readStream(stream.lineSequence()) { progress += it }
        assertTrue(result is ReportApi.ProseResult.Written)
        val prose = (result as ReportApi.ProseResult.Written).prose
        assertEquals(story.chapters.map { it.id }, prose.chapters.map { it.id })
        assertTrue(prose.chapters.all { it.narrative.size == 2 })
        assertEquals(2, prose.preface.size)

        assertTrue(progress.first().planning)
        assertFalse(progress.last().planning)
        assertEquals(9, progress.last().chapters)
        assertEquals(9, progress.last().chaptersStarted)
        assertTrue(progress.zipWithNext().all { (a, b) -> b.fraction >= a.fraction && b.chaptersStarted >= a.chaptersStarted })
        assertTrue(progress.all { it.fraction in 0.0..1.0 })
    }

    @Test
    fun anErrorLineOrABrokenStreamLeavesTheEngineText() {
        val error = ReportApi.readStream(
            sequenceOf(
                """{"type":"progress","phase":"planning","fraction":0,"chaptersStarted":0,"chapters":9}""",
                """{"type":"error","message":"The written report was declined."}""",
            ),
        ) {}
        assertEquals(
            ReportApi.ProseResult.Unavailable(ReportApi.ProseFailure.Declined, "The written report was declined."),
            error,
        )
        val broken = ReportApi.readStream(stream.lineSequence().take(20)) {}
        assertEquals(ReportApi.ProseFailure.Offline, (broken as ReportApi.ProseResult.Unavailable).reason)
        val wrongShape = ReportApi.readStream(sequenceOf("""{"type":"done","prose":{"introduction":"x","preface":[],"chapters":[]}}""")) {}
        assertEquals(ReportApi.ProseFailure.Declined, (wrongShape as ReportApi.ProseResult.Unavailable).reason)
    }

    @Test
    fun onlyTheRoutesOwn429IsTheDaysAllowance() {
        val budget = """{"error":{"code":"RATE_LIMITED","message":"Sign in to download another written report today.",
            "details":{"retryAfterSeconds":3600,"scope":"anonymous"}}}"""
        assertEquals(ReportApi.ProseFailure.DailyLimit, ReportApi.failureFor(429, budget))
        assertEquals(ReportApi.ProseFailure.Declined, ReportApi.failureFor(429, ""))
        assertEquals(ReportApi.ProseFailure.Declined, ReportApi.failureFor(429, """{"detail":"Too many requests"}"""))
        assertEquals(ReportApi.ProseFailure.Declined, ReportApi.failureFor(503, """{"error":{"message":"Written prose is unavailable"}}"""))
    }

    @Test
    fun progressLinesAreClamped() {
        val line = StoryProseCodec.parseLine("""{"type":"progress","phase":"odd","fraction":3,"chaptersStarted":12.7,"chapters":9}""")
        val progress = (line as StoryProseCodec.StreamLine.Progress).progress
        assertEquals(ProseProgress("planning", 1.0, 9, 9), progress)
        assertNull(StoryProseCodec.parseLine("not json"))
        assertNull(StoryProseCodec.parseLine("   "))
    }

    @Test
    fun aCacheHitArrivesAsPlainJson() {
        val done = JSONObject(stream.lineSequence().last { it.isNotBlank() })
        val plain = JSONObject().put("prose", done.getJSONObject("prose")).put("cached", true).toString()
        assertEquals(written(), StoryProseCodec.parsePlain(plain))
        assertNull(StoryProseCodec.parsePlain("""{"error":{"message":"Sign in to download another written report today."}}"""))
    }

    @Test
    fun parsingDropsChaptersThatDoNotHold() {
        val raw = JSONObject(
            """{"introduction":"Intro","preface":["P1", 3],"chapters":[
                {"id":"essence","opening":"O","narrative":["A","  "]},
                {"id":"marriage","opening":"O","narrative":["  "]},
                {"id":7,"opening":"O","narrative":["A"]},
                {"id":"family","narrative":["A"]}
            ]}""",
        )
        val prose = StoryProseCodec.parseProse(raw)!!
        assertEquals(listOf("P1"), prose.preface)
        assertEquals(listOf(ProseChapter("essence", "O", listOf("A"))), prose.chapters)
        assertNull(StoryProseCodec.parseProse(JSONObject("""{"introduction":"x","preface":[]}""")))
        assertNull(StoryProseCodec.parseProse(null))
    }

    @Test
    fun theMergeRewritesOnlyTheRunningProse() {
        val prose = written()
        val merged = StoryProseCodec.apply(story, prose)
        assertEquals(prose.introduction.trim(), merged.introduction)
        assertEquals(prose.preface, merged.preface)
        merged.chapters.zip(story.chapters).forEach { (after, before) ->
            val writtenChapter = prose.chapters.first { it.id == before.id }
            assertEquals(writtenChapter.narrative, after.narrative)
            assertEquals(writtenChapter.opening.trim(), after.opening)
            /* Everything the engine computed stays the engine's. */
            assertEquals(before.title, after.title)
            assertEquals(before.eyebrow, after.eyebrow)
            assertEquals(before.support, after.support)
            assertEquals(before.signals, after.signals)
            assertEquals(before.practices, after.practices)
            assertEquals(before.reflectionPrompt, after.reflectionPrompt)
        }
        assertEquals(story.atAGlance, merged.atAGlance)
        assertEquals(story.verification, merged.verification)
        assertEquals(story.title, merged.title)
    }

    @Test
    fun aChapterTheModelSkippedKeepsTheEnginesText() {
        val prose = written().let { it.copy(chapters = it.chapters.filter { chapter -> chapter.id != "career" }) }
        val merged = StoryProseCodec.apply(story, prose)
        val career = merged.chapters.first { it.id == "career" }
        val engineCareer = story.chapters.first { it.id == "career" }
        assertEquals(engineCareer, career)
        assertNotEquals(story.chapters.first { it.id == "wealth" }, merged.chapters.first { it.id == "wealth" })
    }

    @Test
    fun blankWrittenTextFallsBackToTheEngines() {
        val prose = StoryProse(
            introduction = "   ",
            preface = emptyList(),
            chapters = listOf(ProseChapter("essence", "  ", listOf("New paragraph."))),
        )
        val merged = StoryProseCodec.apply(story, prose)
        assertEquals(story.introduction, merged.introduction)
        assertEquals(story.preface, merged.preface)
        val essence = merged.chapters.first()
        assertEquals(story.chapters.first().opening, essence.opening)
        assertEquals(listOf("New paragraph."), essence.narrative)
    }
}
