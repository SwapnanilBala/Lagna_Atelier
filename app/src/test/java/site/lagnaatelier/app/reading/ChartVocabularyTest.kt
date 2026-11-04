package site.lagnaatelier.app.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import site.lagnaatelier.app.data.AppLanguage
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.data.periods.CurrentPeriod
import site.lagnaatelier.app.ui.reading.ChartVocabulary
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/* The lookups that turn the engine's English chart names into the app's resources. */
class ChartVocabularyTest {
    @Test
    fun knowsTheTwentySevenTheEngineNames() {
        assertEquals(27, ChartVocabulary.NAKSHATRAS.size)
        assertEquals(CurrentPeriod.NAKSHATRAS, ChartVocabulary.NAKSHATRAS.keys)
        ChartVocabulary.NAKSHATRAS.keys.forEach { name ->
            assertEquals(name, ChartVocabulary.NAKSHATRAS[name], ChartVocabulary.nakshatraRes(name.uppercase()))
        }
        assertNull(ChartVocabulary.nakshatraRes("Abhijit"))
    }

    @Test
    fun readsSignsByTheirEnglishName() {
        assertEquals(Sign.Gemini, ChartVocabulary.sign("Gemini"))
        assertEquals(Sign.Pisces, ChartVocabulary.sign(" pisces "))
        assertNull(ChartVocabulary.sign("Ophiuchus"))
    }

    @Test
    fun readsHousesAndListsAsTheEngineWritesThem() {
        assertEquals(7, ChartVocabulary.house("7th house"))
        assertEquals(1, ChartVocabulary.house("1st house"))
        assertEquals(12, ChartVocabulary.house("12th house"))
        assertNull(ChartVocabulary.house("13th house"))
        assertNull(ChartVocabulary.house("7th"))
        assertEquals(listOf("Sun", "Moon"), ChartVocabulary.parts("Sun, Moon"))
        assertEquals(listOf("28°45′"), ChartVocabulary.parts("28°45′"))
    }

    @Test
    fun cutsLatinSignNamesOnlyAndByCodePoint() {
        assertEquals("Gem", ChartVocabulary.abbreviate("Gemini"))
        assertEquals("Gém", ChartVocabulary.abbreviate("Gémeaux"))
        assertEquals("Leo", ChartVocabulary.abbreviate("Leo"))
        assertEquals("वृश्चिक", ChartVocabulary.abbreviate("वृश्चिक"))
        assertEquals("বৃশ্চিক", ChartVocabulary.abbreviate("বৃশ্চিক"))
    }

    /* Every language carries all 27, the ascendant and the twelve houses; Hindi and Bengali in their own script. */
    @Test
    fun everyLanguageNamesEveryNakshatra() {
        val res = File("src/main/res")
        val keys = ChartVocabulary.NAKSHATRAS.keys.map { "chart_nakshatra_" + it.lowercase().replace(' ', '_') } + "chart_point_ascendant"
        AppLanguage.SUPPORTED.forEach { tag ->
            val folder = File(res, if (tag == "en") "values" else "values-$tag")
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(folder, "strings_chart_names.xml")).documentElement
            val strings = (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }
                .filter { it.tagName == "string" }.associate { it.getAttribute("name") to it.textContent }
            keys.forEach { key -> assertTrue("$tag $key", strings[key].orEmpty().isNotBlank()) }
            if (tag == "hi" || tag == "bn") {
                keys.forEach { key -> assertTrue("$tag $key is still Latin", strings.getValue(key).none { it in 'A'..'z' }) }
            }
            val houses = root.getElementsByTagName("string-array").item(0) as Element
            assertEquals("$tag houses", 12, houses.getElementsByTagName("item").length)
        }
    }
}
