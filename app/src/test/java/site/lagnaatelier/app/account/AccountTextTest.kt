package site.lagnaatelier.app.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import site.lagnaatelier.app.data.Traditions
import site.lagnaatelier.app.ui.account.initials
import site.lagnaatelier.app.ui.account.traditionLabel

class AccountTextTest {
    @Test
    fun initialsTakeTheFirstAndLastWords() {
        assertEquals("AM", initials("Ananya Mehra"))
        assertEquals("JL", initials("Jordan Lee"))
        assertEquals("M", initials("Maya"))
        assertEquals("AM", initials("ananya priya mehra"))
        assertEquals("AM", initials("  Ananya \t  Mehra  "))
    }

    @Test
    fun initialsSkipPunctuationAndKeepWholeLetters() {
        assertEquals("ZO", initials("Zoë O’Brien"))
        assertEquals("JS", initials("(Jo) \"Smith\""))
        assertEquals("ÉL", initials("élodie le\u0301ger".let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFC) }))
        // A decomposed accent stays with its letter.
        assertEquals("E\u0301", initials("e\u0301mile"))
        // Devanagari: the first letter with its vowel sign, as one character on screen.
        assertEquals("अमे", initials("अनन्या मेहरा"))
    }

    @Test
    fun initialsOfNothingAreAQuestionMark() {
        assertEquals("?", initials(""))
        assertEquals("?", initials("   "))
        assertEquals("?", initials("— —"))
    }

    @Test
    fun traditionLabelsReadAsTheMockDoes() {
        assertEquals("Lahiri · whole sign", traditionLabel(Traditions.DEFAULT_ENGINE_ID))
        assertEquals("Fagan-Bradley · Placidus", traditionLabel("fagan_bradley_placidus"))
        assertEquals("Krishnamurti · Equal", traditionLabel("krishnamurti_equal"))
        assertNull(traditionLabel("lahiri"))
        assertNull(traditionLabel("unknown_classic"))
        // Every one of the 36 has a label.
        Traditions.Ayanamsha.entries.forEach { ayanamsha ->
            Traditions.HouseSystem.entries.forEach { houses ->
                assertEquals(
                    "${ayanamsha.label} · ",
                    traditionLabel(Traditions.engineId(ayanamsha, houses))!!.substringBefore(" · ") + " · ",
                )
            }
        }
    }
}
