package site.lagnaatelier.app.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.report.ReportApi
import site.lagnaatelier.app.data.report.ReportFiles
import site.lagnaatelier.app.data.report.ReportPercent
import site.lagnaatelier.app.data.report.ProseProgress
import site.lagnaatelier.app.data.report.ReportStage
import site.lagnaatelier.app.data.report.StoryParser
import java.time.LocalDate

/*
 * GET https://lagnaatelier.site/api/chart/story-report for the sample chart
 * (Ananya Mehra, 15 June 1990, 14:30, New Delhi, Lahiri whole signs), read on
 * 2026-11-03 and saved as report/story-report-ananya-mehra.json.
 */
class StoryReportTest {
    private val fixture = resource("report/story-report-ananya-mehra.json")

    @Test
    fun readsTheStory() {
        val report = StoryParser.parseReport(fixture)
        val story = report.story
        assertEquals("2026-11-03T05:45:53.332Z", report.generatedAtUtc)
        assertEquals("Ananya Mehra story", story.title)
        assertEquals("A verified, client-focused astrological portrait", story.subtitle)
        assertEquals(2, story.preface.size)
        assertEquals(4, story.atAGlance.size)
        assertEquals("Virgo rising", story.atAGlance[0].value)
        assertEquals(3, story.centralThemes.size)
        assertEquals(2, story.timeline.size)
        assertEquals("Dec 2026 – Jun 2028", story.timeline[0].window)
        assertEquals(
            listOf("essence", "marriage", "family", "career", "wealth", "strengths", "emotional-orientation", "timing", "grounding"),
            story.chapters.map { it.id },
        )
        assertTrue(story.reflectionNote.startsWith("Use this as a reflective planning tool"))
    }

    @Test
    fun readsAChapterWhole() {
        val essence = StoryParser.parseReport(fixture).story.chapters.first()
        assertEquals("Essence & life approach", essence.eyebrow)
        assertEquals("Lead with your Virgo approach", essence.title)
        assertEquals("well-supported", essence.support)
        assertEquals(3, essence.signals.size)
        assertEquals("Mercury in Taurus, house 9", essence.signals[1].value)
        assertEquals(essence.opening, essence.narrative[0])
        assertEquals(2, essence.practices.size)
        assertEquals("Where do you feel most like yourself without needing to perform or explain?", essence.reflectionPrompt)
    }

    @Test
    fun readsTheVerification() {
        val verification = StoryParser.parseReport(fixture).story.verification
        assertTrue(verification.isVerified)
        assertFalse(verification.isFailed)
        assertEquals(11, verification.passedCount)
        assertEquals(11, verification.totalCount)
        assertEquals(11, verification.checks.size)
        assertEquals("Core coordinates", verification.checks[0].label)
        assertTrue(verification.checks.all { it.status == "passed" })
        assertEquals("Lahiri", verification.profile.ayanamsha)
        assertEquals("Whole Sign", verification.profile.houseSystem)
    }

    @Test
    fun absentAndNullOptionalFieldsReadAsNull() {
        val body = """
            {"generated_at_utc":"x","story":{"title":"T","chapters":[{"id":"essence","opening":null}],
             "verification":{"status":"verified","passedCount":1}}}
        """.trimIndent()
        val chapter = StoryParser.parseReport(body).story.chapters.single()
        assertNull(chapter.opening)
        assertNull(chapter.support)
        assertNull(chapter.reflectionPrompt)
        assertTrue(chapter.narrative.isEmpty())
    }

    @Test
    fun theStoryRouteTakesCamelCaseParameters() {
        val delhi = Place("New Delhi", "Delhi", "India", 28.6139, 77.209, "Asia/Kolkata", 330)
        val query = ReportApi.storyReportQuery(BirthDetails("Ananya Mehra", LocalDate.of(1990, 6, 15), "14:30", TimeAccuracy.Exact, delhi))
        assertEquals(
            linkedMapOf(
                "name" to "Ananya Mehra",
                "birthDate" to "1990-06-15",
                "birthTime" to "14:30",
                "timezoneOffsetMinutes" to "330",
                "latitude" to "28.6139",
                "longitude" to "77.209",
                "country" to "India",
                "state" to "Delhi",
                "city" to "New Delhi",
                "timeZoneId" to "Asia/Kolkata",
                "engineId" to "lahiri_classic",
                "birthTimeAccuracy" to "exact",
                "birthTimeSource" to "exact",
                "birthTimeFallback" to "false",
            ),
            query,
        )
    }

    @Test
    fun anEstimatedTimeAndAStatelessPlaceStillMakeACompleteQuery() {
        val singapore = Place("Singapore", "", "Singapore", 1.29, 103.85, "Asia/Singapore", 480)
        val query = ReportApi.storyReportQuery(BirthDetails("Mei", LocalDate.of(2001, 2, 3), null, TimeAccuracy.Evening, singapore))
        assertEquals("18:30", query["birthTime"])
        assertEquals("evening", query["birthTimeAccuracy"])
        assertEquals("fallback", query["birthTimeSource"])
        assertEquals("true", query["birthTimeFallback"])
        /* The route requires a state; the country stands in and moves nothing. */
        assertEquals("Singapore", query["state"])
    }

    @Test
    fun fileNamesAreTheTitleSluggedAsTheWebSlugsIt() {
        val story = StoryParser.parseReport(fixture).story
        assertEquals("ananya-mehra-story.pdf", ReportFiles.fileName(story))
        assertEquals("zo-o-brien-story", ReportFiles.slug("  Zoë O'Brien story "))
        assertEquals("a-b-c", ReportFiles.slug("--A  b__C--"))
        assertEquals("personal-story", ReportFiles.slug("  ·  "))
        assertEquals("personal-story", ReportFiles.slug(""))
    }

    @Test
    fun theBarFollowsTheWebsRules() {
        assertEquals(2, ReportPercent.of(ReportStage.Calculating, null))
        assertEquals(6, ReportPercent.of(ReportStage.Verifying, null))
        assertEquals(8, ReportPercent.of(ReportStage.Writing, null))
        assertEquals(8, ReportPercent.of(ReportStage.Writing, ProseProgress("planning", 0.0, 0, 9)))
        assertEquals(50, ReportPercent.of(ReportStage.Writing, ProseProgress("writing", 0.5, 4, 9)))
        assertEquals(89, ReportPercent.of(ReportStage.Writing, ProseProgress("writing", 0.97, 9, 9)))
        assertEquals(95, ReportPercent.of(ReportStage.Typesetting, null))
    }
}

internal fun Any.resource(path: String): String =
    javaClass.classLoader!!.getResource(path)!!.readText(Charsets.UTF_8)
