package site.lagnaatelier.app.look

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.AppLanguage
import site.lagnaatelier.app.data.Traditions
import site.lagnaatelier.app.data.periods.LifeShift
import site.lagnaatelier.app.data.periods.LifeShifts
import site.lagnaatelier.app.data.periods.NatalParser
import site.lagnaatelier.app.data.periods.ShiftKind
import site.lagnaatelier.app.data.periods.LifeShifts.pivotLabel
import site.lagnaatelier.app.ui.account.traditionLabel
import site.lagnaatelier.app.ui.timing.ChapterWords
import site.lagnaatelier.app.ui.timing.LifeChapterText
import java.io.File
import java.text.Normalizer
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/*
 * The life chapters' labels, themes and evidence, and the tradition names,
 * printed from the app's own catalogs (res/values*), as the phone prints
 * them: English must come out exactly as the engine words it, and each
 * translation must say the website's words with every slot filled.
 */
class LifeChapterTextTest {
    private val res = File("src/main/res")
    private val names: Map<Int, String> = R.string::class.java.fields.associate { it.getInt(null) to it.name }

    private class Catalog(val strings: Map<String, String>, val arrays: Map<String, List<String>>)

    private fun catalog(tag: String): Catalog {
        val folder = File(res, if (tag == "en") "values" else "values-$tag")
        val strings = mutableMapOf<String, String>()
        val arrays = mutableMapOf<String, List<String>>()
        folder.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty().forEach { file ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            for (i in 0 until root.childNodes.length) {
                val element = root.childNodes.item(i) as? Element ?: continue
                val name = element.getAttribute("name")
                when (element.tagName) {
                    "string" -> strings[name] = element.textContent.replace("\\'", "'")
                    "string-array" -> arrays[name] = (0 until element.childNodes.length)
                        .mapNotNull { element.childNodes.item(it) as? Element }.map { it.textContent }
                }
            }
        }
        return Catalog(strings, arrays)
    }

    private val english = catalog("en")

    /* The words as Resources would give them: the language's own, English where it has none. */
    private fun words(tag: String): ChapterWords {
        val own = if (tag == "en") english else catalog(tag)
        val locale = Locale.forLanguageTag(tag)
        fun text(name: String) = own.strings[name] ?: english.strings.getValue(name)
        return object : ChapterWords {
            override fun string(id: Int, vararg args: Any) = String.format(locale, text(names.getValue(id)), *args)
            override val ordinals = own.arrays["chapters_ordinals"] ?: english.arrays.getValue("chapters_ordinals")
            override fun planet(name: String) = text("chart_planet_${name.lowercase()}")
            override fun sign(name: String) = text("chart_sign_${name.lowercase()}")
            override fun number(value: Double): String = NumberFormat.getInstance(locale).apply { maximumFractionDigits = 2 }.format(value)
        }
    }

    private val natal = NatalParser.parse(javaClass.classLoader!!.getResource("chart-ananya-mehra.json")!!.readText())

    /* Every chapter the sample chart has over a long life: each kind, and returns up to their last. */
    private val shifts: List<LifeShift> = (1991..2085 step 2)
        .flatMap { year -> LifeShifts.compute(natal, Instant.parse("$year-06-01T00:00:00Z")) }
        .distinctBy { it.id }

    @Test
    fun theSampleCoversEveryKindAndLateReturns() {
        assertEquals(ShiftKind.entries.toSet(), shifts.map { it.kind }.toSet())
        assertTrue(shifts.any { it.occurrence >= 5 })
    }

    @Test
    fun englishIsTheEnginesOwnWords() {
        val en = words("en")
        shifts.forEach { shift ->
            assertEquals(shift.id, shift.label, LifeChapterText.label(shift, en))
            assertEquals(shift.id, shift.theme, LifeChapterText.theme(shift, en))
            assertEquals(shift.id, shift.evidence, LifeChapterText.evidence(shift, en))
        }
    }

    @Test
    fun hindiReadsTheWebsitesLabels() {
        val hi = words("hi")
        val saturn = shifts.first { it.kind == ShiftKind.SaturnReturn && it.occurrence == 1 }
        assertEquals(nfc("शनि वापसी (पहली)"), LifeChapterText.label(saturn, hi))
        assertEquals(nfc("ढाँचा, ज़िम्मेदारी और निर्णयों की कीमत"), LifeChapterText.theme(saturn, hi))
        val mercury = shifts.first { it.kind == ShiftKind.Mahadasha && it.planet == "Mercury" }
        assertEquals(nfc("बुध महादशा का आरंभ"), LifeChapterText.label(mercury, hi))
        val evidence = LifeChapterText.evidence(mercury, hi)
        assertTrue(evidence, evidence.startsWith(nfc("महादशा स्वामी: बुध | अवधि लगभग 17 वर्ष | जन्म कुंडली में बुध: ")))
        assertTrue(evidence, Regex("""/ भाव \d+$""").containsMatchIn(evidence))
    }

    @Test
    fun germanPutsTheOrdinalFirstAsTheWebsiteDoes() {
        val de = words("de")
        val jupiter = shifts.first { it.kind == ShiftKind.JupiterReturn && it.occurrence == 2 }
        assertEquals("Zweite Jupiterrückkehr", LifeChapterText.label(jupiter, de))
        val nodes = shifts.first { it.kind == ShiftKind.NodalReturn }
        assertTrue(LifeChapterText.evidence(nodes, de).startsWith("Zyklus: etwa 18,6 Jahre | Rahu: "))
        val late = shifts.first { it.occurrence == 5 }
        assertTrue(LifeChapterText.label(late, de).startsWith("Fünfte "))
    }

    @Test
    fun everyLanguageHasEveryTemplateAndEightOrdinals() {
        val keys = english.strings.keys.filter { it.startsWith("chapters_label_") || it.startsWith("chapters_theme_") || it.startsWith("chapters_fact_") }
        assertEquals(4 + 12 + 6, keys.size)
        AppLanguage.SUPPORTED.drop(1).forEach { tag ->
            val own = catalog(tag)
            keys.forEach { key -> assertTrue("values-$tag $key", key in own.strings) }
            assertEquals("values-$tag", 8, own.arrays.getValue("chapters_ordinals").size)
            /* No language prints a label that still carries a slot or the engine's English. */
            shifts.forEach { shift ->
                val label = LifeChapterText.label(shift, words(tag))
                assertTrue("$tag $label", "%" !in label && label != shift.label)
            }
        }
    }

    @Test
    fun pivotDatesAreInTheLanguage() {
        val shift = shifts.first()
        assertEquals(
            java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC).format(shift.pivot),
            pivotLabel(shift, Locale.ENGLISH),
        )
    }

    @Test
    fun traditionNamesAreTheWebsites() {
        fun word(tag: String): (Int) -> String {
            val own = catalog(tag)
            return { id -> names.getValue(id).let { own.strings[it] ?: english.strings.getValue(it) } }
        }
        assertEquals(nfc("लाहिड़ी · पूर्ण-राशि"), traditionLabel(Traditions.DEFAULT_ENGINE_ID, word("hi")))
        assertEquals(nfc("লাহিড়ী · পূর্ণ-রাশি ভাব"), traditionLabel(Traditions.DEFAULT_ENGINE_ID, word("bn")))
        assertEquals("Krishnamurti · Äquale Häuser", traditionLabel("krishnamurti_equal", word("de")))
        assertEquals("Lahiri · signo completo", traditionLabel(Traditions.DEFAULT_ENGINE_ID, word("es")))
        /* English from the catalog is what the app printed before it had one. */
        Traditions.Ayanamsha.entries.forEach { ayanamsha ->
            Traditions.HouseSystem.entries.forEach { houses ->
                val id = Traditions.engineId(ayanamsha, houses)
                assertEquals(traditionLabel(id), traditionLabel(id, word("en")))
            }
        }
    }
}

private fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
