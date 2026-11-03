package site.lagnaatelier.app.reading

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ChartParser
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.reading.FindingTheme
import site.lagnaatelier.app.data.reading.HouseGroup
import site.lagnaatelier.app.data.reading.HouseSupport
import site.lagnaatelier.app.data.reading.HouseSystemName
import site.lagnaatelier.app.data.reading.ReadingSectionsParser
import site.lagnaatelier.app.data.reading.SupportBand
import site.lagnaatelier.app.data.reading.YogaFamily
import site.lagnaatelier.app.data.reading.YogaStrength
import site.lagnaatelier.app.data.reading.FindingFilter
import java.time.LocalDate

/*
 * The reading tab's longer sections against real responses from
 * GET https://lagnaatelier.site/api/chart for the sample chart (Ananya
 * Mehra, 15 June 1990, 14:30, New Delhi): chart-ananya-mehra.json under
 * Lahiri whole signs (2026-11-02), and reading/chart-ananya-mehra-placidus.json
 * under Lahiri Placidus (2026-11-03), where sign houses and bhavas part.
 */
class ReadingSectionsParserTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    private val wholeSign = ReadingSectionsParser.parse(resource("chart-ananya-mehra.json"))
    private val placidus = ReadingSectionsParser.parse(resource("reading/chart-ananya-mehra-placidus.json"))

    @Test
    fun theChartParserCarriesTheSections() {
        val delhi = Place("New Delhi", "Delhi", "India", 28.6139, 77.209, "Asia/Kolkata", 330)
        val details = BirthDetails("Ananya Mehra", LocalDate.of(1990, 6, 15), "14:30", TimeAccuracy.Exact, delhi)
        val chart = ChartParser.parse(resource("chart-ananya-mehra.json"), details)
        assertEquals(wholeSign, chart.sections)
    }

    // --- Planet positions ---

    @Test
    fun everyGrahaInOrderWithSignDegreeAndHouse() {
        val table = wholeSign.positions!!
        assertEquals(
            listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu"),
            table.rows.map { it.name },
        )
        val sun = table.rows.first()
        assertEquals(Sign.Gemini, sun.sign)
        assertEquals(0.2907, sun.degreeInSign, 1e-4)
        assertEquals(10, sun.signHouse)
        val moon = table.rows[1]
        assertEquals(Sign.Aquarius, moon.sign)
        assertEquals(6, moon.signHouse)
    }

    @Test
    fun wholeSignNeedsNoBhavaColumn() {
        val table = wholeSign.positions!!
        assertNull(table.houseSystem)
        /* Under whole signs the engine's house and the sign house are the same number. */
        table.rows.forEach { assertEquals(it.name, it.signHouse, it.bhava) }
    }

    @Test
    fun retrogradeAndCombustMarksAreRead() {
        val table = wholeSign.positions!!
        assertEquals(listOf("Saturn", "Rahu", "Ketu"), table.rows.filter { it.retrograde }.map { it.name })
        assertTrue(table.rows.none { it.combust })
        assertTrue(table.anyFlags)
    }

    @Test
    fun placidusKeepsTheBhavaApartFromTheSignHouse() {
        val table = placidus.positions!!
        assertEquals(HouseSystemName.Placidus, table.houseSystem)
        val sun = table.rows.first { it.name == "Sun" }
        assertEquals(10, sun.signHouse)
        assertEquals(9, sun.bhava)
    }

    @Test
    fun signHouseCountsFromTheRisingSign() {
        assertEquals(1, ReadingSectionsParser.signHouse(Sign.Virgo, Sign.Virgo))
        assertEquals(12, ReadingSectionsParser.signHouse(Sign.Leo, Sign.Virgo))
        assertEquals(8, ReadingSectionsParser.signHouse(Sign.Aries, Sign.Virgo))
        assertEquals(1, ReadingSectionsParser.signHouse(Sign.Aries, Sign.Aries))
        assertEquals(12, ReadingSectionsParser.signHouse(Sign.Pisces, Sign.Aries))
    }

    // --- House support ---

    @Test
    fun eachHouseTakesTheTotalOfItsSign() {
        val support = wholeSign.houseSupport!!
        assertEquals(12, support.houses.size)
        val first = support.houses.first()
        assertEquals(Sign.Virgo, first.sign)
        assertEquals(30, first.bindus)
        /* 30 / 28.083 = 106.8% of average. */
        assertEquals(107, first.percent)
        assertEquals(SupportBand.Strong, first.band)
        assertEquals(337, support.totalBindus)
        assertTrue(support.totalIsExact)
    }

    @Test
    fun bestAndLeastSupportedHouses() {
        val support = wholeSign.houseSupport!!
        assertEquals(8, support.strongest.house)
        assertEquals(Sign.Aries, support.strongest.sign)
        assertEquals(35, support.strongest.bindus)
        assertEquals(12, support.weakest.house)
        assertEquals(20, support.weakest.bindus)
        assertEquals(SupportBand.Weak, support.weakest.band)
        /* 30, 29, 31, 30, 31, 35 and 30 clear the 28.08 average; Aquarius's 28 does not. */
        assertEquals(7, support.aboveAverage)
    }

    @Test
    fun aQuadrantSystemThatRepeatsASignIsNotAnExactTotal() {
        val support = placidus.houseSupport!!
        /* Virgo on the 1st and the 12th, Pisces on the 6th and the 7th; Gemini and Sagittarius on none. */
        assertFalse(support.totalIsExact)
        assertEquals(support.houses.first().bindus, support.houses.last().bindus)
        assertEquals(337 + 30 + 31 - 24 - 31, support.totalBindus)
    }

    @Test
    fun bandsUseTheWebThresholds() {
        assertEquals(SupportBand.Strong, HouseSupport.bandFor(28))
        assertEquals(SupportBand.Neutral, HouseSupport.bandFor(27))
        assertEquals(SupportBand.Neutral, HouseSupport.bandFor(26))
        assertEquals(SupportBand.Weak, HouseSupport.bandFor(25))
    }

    @Test
    fun everyHouseHasAnAngularGroupAndTheOverlapsHold() {
        val angular = setOf(HouseGroup.Kendra, HouseGroup.Panaphara, HouseGroup.Apoklima)
        (1..12).forEach { house -> assertEquals(1, HouseSupport.GROUPS.getValue(house).count { it in angular }) }
        assertEquals(listOf(HouseGroup.Apoklima, HouseGroup.Upachaya, HouseGroup.Dusthana), HouseSupport.GROUPS[6])
        assertEquals(listOf(HouseGroup.Kendra, HouseGroup.Trikona), HouseSupport.GROUPS[1])
    }

    @Test
    fun noAshtakavargaMeansNoHouseSupportButTheRestStays() {
        val root = JSONObject(resource("chart-ananya-mehra.json"))
        root.remove("ashtakavarga")
        val sections = ReadingSectionsParser.parse(root)
        assertNull(sections.houseSupport)
        assertNotNull(sections.positions)
        assertEquals(19, sections.findings.size)
    }

    // --- Findings ---

    @Test
    fun everyFindingTheChosenFewFirst() {
        val findings = wholeSign.findings
        assertEquals(19, findings.size)
        assertEquals((1..7).toList(), findings.take(7).map { it.rank })
        assertEquals("career.tenth_house_axis:Gemini", findings.first().key)
        /* The rest by score, highest first. */
        val rest = findings.drop(7)
        assertTrue(rest.all { it.rank == 0 })
        assertEquals(rest.sortedByDescending { it.score }, rest)
        assertEquals("dignity.own_sign_career:Saturn", rest.first().key)
        assertEquals("yoga.fortune_house:Mercury", rest.last().key)
    }

    @Test
    fun aFindingCarriesItsEvidenceAndHowCommonItIs() {
        val lagna = wholeSign.findings.first { it.key == "core.lagna_signature:Virgo" }
        assertEquals(6, lagna.rank)
        assertEquals(FindingTheme.Core, lagna.theme)
        assertEquals("How you come across before you say anything", lagna.headline)
        assertEquals("Shows up in about 9 of every 100 charts", lagna.rarityLabel)
        assertEquals("Ascendant computed in Virgo.", lagna.technicalNote)
        assertEquals("Rising sign", lagna.claims.first().label)
        assertEquals("Virgo", lagna.claims.first().value)
        assertFalse(lagna.rare)
        assertTrue(wholeSign.findings.all { it.rarityLabel.isNotBlank() })
    }

    @Test
    fun rareBandsAndTheOtherSide() {
        assertTrue(wholeSign.findings.first { it.key == "core.ascendant_lord:Mercury:9" }.rare)
        val nodes = wholeSign.findings.first { it.key == "core.nodal_axis:5:11" }
        assertNotNull(nodes.tension)
        assertNull(wholeSign.findings.first().tension)
    }

    @Test
    fun theRarityLabelIsWorkedOutWhenTheResponseLeavesItOut() {
        fun label(rate: Double, lowConfidence: Boolean = false) = ReadingSectionsParser.rarityLabel(
            JSONObject().put("fire_rate", rate).put("low_confidence", lowConfidence),
        )
        assertEquals("Shows up in about 9 of every 100 charts", label(0.092055))
        assertEquals("Present in every chart", label(0.999))
        assertEquals("Shows up in about 4 of every 1,000 charts", label(0.004))
        assertEquals("Shows up in fewer than 1 in 1,000 charts", label(0.0001))
        assertEquals("An unusual combination", label(0.2, lowConfidence = true))
    }

    @Test
    fun filtersOfferOnlyWhatListsSomething() {
        val findings = wholeSign.findings
        assertEquals(FindingFilter.entries, FindingFilter.offered(findings))
        assertEquals(7, findings.count(FindingFilter.Top::matches))
        assertEquals(findings.size, findings.count(FindingFilter.All::matches))
        val loveOnly = findings.filter { it.theme == FindingTheme.Love && !it.isTop }
        assertEquals(listOf(FindingFilter.All, FindingFilter.Love), FindingFilter.offered(loveOnly))
    }

    // --- Yogas ---

    @Test
    fun yogasStrongestFirst() {
        val yogas = wholeSign.yogas
        assertEquals(35, yogas.size)
        assertEquals("pushkala", yogas.first().id)
        assertEquals(listOf("pushkala", "dhanakaraka", "vidya_lord_trikona"), yogas.take(3).map { it.id })
        /* By the engine's weighting, then by strength. */
        yogas.zipWithNext().forEach { (a, b) ->
            assertTrue(a.weight > b.weight || (a.weight == b.weight && a.strength.ordinal <= b.strength.ordinal))
        }
        assertEquals("vargottama", yogas.last().id)
    }

    @Test
    fun aYogaSaysWhatItMeansAndWhatFormsIt() {
        val pushkala = wholeSign.yogas.first()
        assertEquals("Pushkala Yoga", pushkala.name)
        assertEquals("पुष्कल योग", pushkala.sanskrit)
        assertEquals(YogaFamily.Wealth, pushkala.family)
        assertEquals(YogaStrength.Strong, pushkala.strength)
        assertEquals(listOf("Moon", "Saturn", "Mercury"), pushkala.planets)
        assertEquals("Indicates social support, comfort, recognition, and material sufficiency.", pushkala.effects)
        assertEquals("Moon sign lord Saturn connects with ascendant lord Mercury.", pushkala.description)
    }

    @Test
    fun aSanskritLineThatRepeatsTheNameIsDropped() {
        val root = JSONObject().put(
            "chart",
            JSONObject().put(
                "yogas",
                org.json.JSONArray()
                    .put(yoga("a", "Alpha Yoga", sanskrit = "Alpha Yoga"))
                    .put(yoga("b", "Beta Yoga", present = false))
                    .put(yoga("c", "Gamma Yoga", category = "unheard_of")),
            ),
        )
        val yogas = ReadingSectionsParser.parse(root).yogas
        assertEquals(listOf("a"), yogas.map { it.id })
        assertNull(yogas.single().sanskrit)
    }

    @Test
    fun aMalformedSectionIsLeftOutWithoutTakingTheOthers() {
        val root = JSONObject(resource("chart-ananya-mehra.json"))
        root.getJSONObject("chart").put("yogas", "not a list").put("planets", JSONObject())
        val sections = ReadingSectionsParser.parse(root)
        assertTrue(sections.yogas.isEmpty())
        assertNull(sections.positions)
        assertEquals(19, sections.findings.size)
        assertNotNull(sections.houseSupport)
    }

    @Test
    fun aResponseWithoutAChartHasNoSections() {
        val sections = ReadingSectionsParser.parse(JSONObject())
        assertNull(sections.positions)
        assertNull(sections.houseSupport)
        assertTrue(sections.findings.isEmpty())
        assertTrue(sections.yogas.isEmpty())
    }

    private fun yoga(id: String, name: String, sanskrit: String? = null, present: Boolean = true, category: String = "benefic") =
        JSONObject()
            .put("yoga_id", id)
            .put("name", name)
            .put("sanskrit", sanskrit ?: JSONObject.NULL)
            .put("category", category)
            .put("present", present)
            .put("strength", "moderate")
            .put("involved_planets", org.json.JSONArray().put("Moon"))
            .put("description", "d")
            .put("effects", "e")
            .put("occurrence_chance", 69)
}
