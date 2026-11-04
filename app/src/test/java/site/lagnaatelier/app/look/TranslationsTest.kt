package site.lagnaatelier.app.look

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import site.lagnaatelier.app.data.AppLanguage
import java.io.File
import java.text.Normalizer
import javax.xml.parsers.DocumentBuilderFactory

/*
 * The translations against English, for what would break at run time: a
 * placeholder English passes that a translation lacks (or one it invents)
 * throws or prints garbage in String.format. A key not translated yet is
 * fine here -- it falls back to English -- and is what
 * tools/i18n/check_translations.py lists.
 */
class TranslationsTest {
    private val res = File("src/main/res")
    private val placeholder = Regex("""%(\d+\$)?[sdf]""")

    /* key -> every text it carries (a string, each plural quantity, each array item). */
    private fun load(folder: File): Map<String, List<String>> {
        val out = mutableMapOf<String, List<String>>()
        folder.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }.orEmpty().forEach { file ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val children = root.childNodes
            for (i in 0 until children.length) {
                val element = children.item(i) as? Element ?: continue
                if (element.getAttribute("translatable") == "false") continue
                val name = element.getAttribute("name")
                out[name] = when (element.tagName) {
                    "string" -> listOf(element.textContent)
                    else -> (0 until element.childNodes.length).mapNotNull { element.childNodes.item(it) as? Element }.map { it.textContent }
                }
            }
        }
        return out
    }

    private fun placeholders(texts: List<String>) = texts.flatMap { text -> placeholder.findAll(text).map { it.value } }.toSet()

    @Test
    fun everyLanguageHasItsFolder() {
        AppLanguage.SUPPORTED.drop(1).forEach { tag ->
            assertTrue("no values-$tag", File(res, "values-$tag").isDirectory)
        }
    }

    @Test
    fun translationsKeepEnglishPlaceholders() {
        val english = load(File(res, "values"))
        AppLanguage.SUPPORTED.drop(1).forEach { tag ->
            load(File(res, "values-$tag")).forEach { (key, texts) ->
                val source = english[key] ?: return@forEach
                assertEquals("values-$tag $key", placeholders(source), placeholders(texts))
            }
        }
    }

    @Test
    fun hindiAndBengaliAreNfc() {
        listOf("hi", "bn").forEach { tag ->
            load(File(res, "values-$tag")).forEach { (key, texts) ->
                texts.forEach { assertTrue("values-$tag $key", Normalizer.isNormalized(it, Normalizer.Form.NFC)) }
            }
        }
    }
}
