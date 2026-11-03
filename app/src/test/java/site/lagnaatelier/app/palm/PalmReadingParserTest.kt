package site.lagnaatelier.app.palm

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.palm.LineKey
import site.lagnaatelier.app.data.palm.LineConfidence
import site.lagnaatelier.app.data.palm.PalmPoint
import site.lagnaatelier.app.data.palm.PalmReadingParser

/*
 * The parser against hand-written answers in the route's documented shape
 * (lib/palm-readings/reading-shape.ts and BASE_SCHEMA_BLOCK in
 * app/api/palm-reading/route.ts on the web). No real reading was requested:
 * the route runs a model the site pays for.
 */
class PalmReadingParserTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("palm/$name")!!.readText()

    @Test
    fun readsEveryDocumentedField() {
        val reading = PalmReadingParser.parse(fixture("reading-full.json"))!!
        assertTrue(reading.overallSummary.startsWith("A careful, practical hand."))
        assertEquals("This is your left hand, the one the tradition reads for a woman.", reading.dominantHandNote)
        assertEquals("strong", reading.lines.getValue(LineKey.Heart).strength)
        assertEquals("faint", reading.lines.getValue(LineKey.Fate).strength)
        assertEquals("You think before you move, and you like a plan.", reading.lines.getValue(LineKey.Head).interpretation)
        assertEquals("A period of consolidation.", reading.trajectory.currentPhase)
        assertEquals("Work that trades in words.", reading.career.careerDirection)
        assertEquals("Loyal and attentive.", reading.relationships.relationshipDynamics)
        assertEquals(listOf("Mercury", "Jupiter", "Venus"), reading.mounts.prominent)
        assertEquals(listOf("Triangle on the head line"), reading.markings.observed)
        assertEquals("Trust the plan you have, and leave room in it.", reading.guidance)

        val quality = reading.imageQuality!!
        assertEquals("good", quality.rating)
        assertTrue(quality.reliable)
        assertEquals(listOf("fate line faint near the wrist"), quality.issues)

        assertEquals(LineConfidence("partial", 0.6), reading.lineConfidence[LineKey.Fate])
        assertEquals(4, reading.lineCoordinates.getValue(LineKey.Life).size)
        assertEquals(PalmPoint(0.78f, 0.42f), reading.lineCoordinates.getValue(LineKey.Heart).first())

        val correlations = reading.jyotishCorrelation!!.correlations
        assertEquals(2, correlations.size)
        assertEquals("contradictory", correlations[1].reinforcement)
        assertEquals("Most pronounced in the next 18 months", reading.dashaRelevance!!.indicators[0].timingNote)
        assertNull(reading.dashaRelevance!!.indicators[1].timingNote)
        assertEquals("Manibandha", reading.classicalNotes!!.terms.single().term)
        assertEquals("anthropic", reading.servedBy)
    }

    @Test
    fun keepsNeitherTheHealthSectionNorUndocumentedKeys() {
        val reading = PalmReadingParser.parse(fixture("reading-full.json"))!!
        val json = PalmReadingParser.toJson(reading).toString()
        assertFalse(json.contains("health_and_vitality"))
        assertFalse(json.contains("must never reach"))
        assertFalse(json.contains("instructions_found_in_photo"))
        assertFalse(json.contains("cover letter"))
        assertFalse(json.contains("classical_text_references"))
    }

    @Test
    fun survivesItsOwnStorage() {
        val reading = PalmReadingParser.parse(fixture("reading-full.json"))!!
        assertEquals(reading, PalmReadingParser.parse(PalmReadingParser.toJson(reading)))
        val messy = PalmReadingParser.parse(fixture("reading-messy.json"))!!
        assertEquals(messy, PalmReadingParser.parse(PalmReadingParser.toJson(messy)))
    }

    @Test
    fun forgivesMissingAndMistypedFields() {
        val reading = PalmReadingParser.parse(fixture("reading-messy.json"))!!
        assertEquals("A hand of steady lines.", reading.overallSummary)
        assertEquals("", reading.dominantHandNote)

        val heart = reading.lines.getValue(LineKey.Heart)
        assertEquals("", heart.description)
        assertEquals("Loyal.", heart.interpretation)
        assertEquals("strong", heart.strength)
        /* Outside the scale, missing, or not an object: the middle of the scale. */
        assertEquals("moderate", reading.lines.getValue(LineKey.Head).strength)
        assertEquals("moderate", reading.lines.getValue(LineKey.Life).strength)
        assertEquals("", reading.lines.getValue(LineKey.Fate).interpretation)

        assertEquals("", reading.trajectory.currentPhase)
        assertEquals(listOf("Jupiter", "Saturn"), reading.mounts.prominent)
        assertEquals("", reading.mounts.interpretation)
        assertEquals(emptyList<String>(), reading.markings.observed)
        assertEquals("", reading.guidance)
        assertNull(reading.servedBy)
        assertNull(reading.dashaRelevance)
    }

    @Test
    fun dropsValuesOutsideTheirSets() {
        val reading = PalmReadingParser.parse(fixture("reading-messy.json"))!!
        val quality = reading.imageQuality!!
        assertEquals("marginal", quality.rating)
        /* The prompt's rule when the flag is missing: unreliable only when poor. */
        assertTrue(quality.reliable)
        assertEquals(emptyList<String>(), quality.issues)

        assertEquals(mapOf(LineKey.Heart to LineConfidence("not_detected", 1.0)), reading.lineConfidence)
        assertEquals(listOf("Line"), reading.jyotishCorrelation!!.correlations.map { it.palmIndicator })
        assertEquals("moderate", reading.jyotishCorrelation!!.correlations.single().reinforcement)
    }

    @Test
    fun clampsAndCapsTheTracedLines() {
        val coordinates = PalmReadingParser.parse(fixture("reading-messy.json"))!!.lineCoordinates
        assertEquals(listOf(PalmPoint(0f, 0.4f), PalmPoint(0.5f, 1f)), coordinates[LineKey.Heart])
        assertFalse(coordinates.containsKey(LineKey.Head))
        assertEquals(PalmReadingParser.MAX_POINTS, coordinates.getValue(LineKey.Life).size)
    }

    @Test
    fun refusesWhatIsNotAReading() {
        assertNull(PalmReadingParser.parse("""{"cover_letter": "Dear hiring manager..."}"""))
        assertNull(PalmReadingParser.parse("""{"overall_summary": "   "}"""))
        assertNull(PalmReadingParser.parse("""["overall_summary"]"""))
        assertNull(PalmReadingParser.parse("not json"))
        assertNotNull(PalmReadingParser.parse("""{"overall_summary": "Just this."}"""))
    }

    @Test
    fun capsProseAtAWordBoundary() {
        val long = ("word ".repeat(1000)).trim()
        val reading = PalmReadingParser.parse(JSONObject().put("overall_summary", long))!!
        assertTrue(reading.overallSummary.length <= PalmReadingParser.MAX_PROSE_CHARS)
        assertTrue(reading.overallSummary.endsWith("word"))

        val labels = JSONObject().put("overall_summary", "x")
            .put("mounts", JSONObject().put("prominent", org.json.JSONArray((1..20).map { "m$it" + "y".repeat(300) })))
        val mounts = PalmReadingParser.parse(labels)!!.mounts.prominent
        assertEquals(PalmReadingParser.MAX_LIST_ITEMS, mounts.size)
        assertTrue(mounts.all { it.length <= PalmReadingParser.MAX_LABEL_CHARS })
    }
}
