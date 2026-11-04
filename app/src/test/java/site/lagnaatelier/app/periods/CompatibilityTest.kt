package site.lagnaatelier.app.periods

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.periods.Compatibility
import site.lagnaatelier.app.data.periods.CompatibilityResult
import site.lagnaatelier.app.data.periods.FindingPolarity
import site.lagnaatelier.app.data.timing.TimingQueries
import java.time.LocalDate

/*
 * POST /api/compatibility, against a live answer for the sample chart and a
 * partner born 11 February 1988, 07:15, in Mumbai, captured on 2026-11-03.
 */
class CompatibilityTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val partner = BirthDetails(
        name = "Rohan Kapoor",
        date = LocalDate.of(1988, 2, 11),
        exactTime = null,
        accuracy = TimeAccuracy.Morning,
        place = Place("Mumbai", "", "India", 19.054999, 72.8692035, "Asia/Kolkata", 330),
    )

    @Test
    fun sendsBothChartsInSnakeCaseWithNumbersAsNumbers() {
        val body = JSONObject(Compatibility.requestBody(TimingQueries.sampleDetails, partner))
        val primary = body.getJSONObject("primary")
        assertEquals("Ananya Mehra", primary.getString("name"))
        assertEquals("1990-06-15", primary.getString("birth_date"))
        assertEquals("14:30", primary.getString("birth_time"))
        assertEquals("lahiri_classic", primary.getString("engine_id"))
        assertEquals(330, (primary.get("timezone_offset_minutes") as Number).toInt())
        assertEquals(28.6139, (primary.get("latitude") as Number).toDouble(), 1e-9)
        assertEquals("exact", primary.getString("birth_time_accuracy"))
        assertEquals(false, primary.get("birth_time_fallback"))

        val them = body.getJSONObject("partner")
        /* Not sure of the time: the morning's stand-in, said so. */
        assertEquals("08:00", them.getString("birth_time"))
        assertEquals("morning", them.getString("birth_time_accuracy"))
        assertEquals("fallback", them.getString("birth_time_source"))
        assertEquals(true, them.get("birth_time_fallback"))
        assertEquals("", them.getString("state"))
        assertEquals(false, body.get("save_result"))
    }

    @Test
    fun readsTheScoreThemesAspectsAndMarriedLife() {
        val result = Compatibility.parse(resource("periods/compatibility-ananya-rohan.json"))
        assertEquals("Ananya Mehra", result.primaryName)
        assertEquals("Rohan Kapoor", result.partnerName)
        assertEquals(81.8, result.score, 1e-9)
        assertEquals(CompatibilityResult.Band.Harmonious, result.band)
        assertTrue(result.summary.startsWith("Ananya Mehra and Rohan Kapoor show a compatibility score"))
        assertEquals(listOf("Emotional Rhythm", "Attraction Pattern", "Communication Style", "Long-Term Potential"), result.themes.map { it.title })

        assertEquals(12, result.aspects.size)
        assertEquals(6, result.supportive)
        assertEquals(6, result.friction)
        val tightest = result.tightest!!
        assertEquals("Saturn", tightest.primaryPlanet)
        assertEquals("Jupiter", tightest.partnerPlanet)
        assertEquals("Square", tightest.aspectType)
        assertFalse(tightest.harmonious)
        assertEquals(result.aspects.sortedBy { it.orb }, result.aspects)

        assertEquals(5, result.facets.size)
        val mangal = result.facets[0]
        assertEquals("Mangal dosha, matched", mangal.label)
        assertEquals("Carried by one side", mangal.verdict)
        assertEquals(FindingPolarity.Caution, mangal.findings[0].polarity)
        assertEquals("Mars in the 7th from the ascendant", mangal.findings[0].basis)
        assertTrue(result.facets[3].findings.isEmpty())
        assertTrue(result.clearFindings > 0)
        assertTrue(result.method.startsWith("Read between the two charts"))
    }

    @Test
    fun dropsTextOutsideTheCopyLineAndSurvivesNoMarriedLife() {
        val body = """{"primary_client":{"name":"A"},"partner_client":{"name":"B"},"compatibility_score":40,
            "summary":"A long illness ahead.","themes":[{"title":"Health","insight":"Watch for disease."},
            {"title":"Warmth","insight":"Easy talk."}],"synastry_aspects":[],"kalatra_synastry":null}"""
        val result = Compatibility.parse(body)
        assertEquals("", result.summary)
        assertEquals(listOf("Warmth"), result.themes.map { it.title })
        assertTrue(result.facets.isEmpty())
        assertEquals(null, result.tightest)
        assertEquals(CompatibilityResult.Band.Challenging, result.band)
    }
}
