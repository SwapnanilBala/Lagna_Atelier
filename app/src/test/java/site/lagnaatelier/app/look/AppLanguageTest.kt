package site.lagnaatelier.app.look

import org.junit.Assert.assertEquals
import org.junit.Test
import site.lagnaatelier.app.data.AppDates
import site.lagnaatelier.app.data.AppLanguage
import site.lagnaatelier.app.data.ChartStyle
import site.lagnaatelier.app.data.ThemeChoice
import java.io.File
import java.time.LocalDate
import java.util.Locale

class AppLanguageTest {
    @Test
    fun datesFollowTheFirstLanguageTheAppHasStringsFor() {
        assertEquals("hi", AppLanguage.resolve(listOf(Locale.forLanguageTag("hi-IN"), Locale.US)).language)
        /* Japanese text falls back to English, so the dates do too -- unless a later locale has strings. */
        assertEquals(Locale.ENGLISH, AppLanguage.resolve(listOf(Locale.JAPAN)))
        assertEquals("de", AppLanguage.resolve(listOf(Locale.JAPAN, Locale.GERMANY)).language)
        assertEquals(Locale.ENGLISH, AppLanguage.resolve(emptyList()))
    }

    @Test
    fun englishKeepsTheScreensOwnPatterns() {
        val date = LocalDate.of(2026, 11, 3)
        assertEquals("Tue 3 Nov", date.format(AppDates.formatter("EEE d MMM", "EEEdMMM", Locale.ENGLISH)))
        assertEquals("3 Nov 2026", date.format(AppDates.formatter("d MMM yyyy", "dMMMyyyy", Locale.forLanguageTag("en-IN"))))
    }

    @Test
    fun anotherLanguageGetsItsOwnWords() {
        /* On the JVM Android's pattern is a stub, so the fields stay in the English order; the words change. */
        val date = LocalDate.of(2026, 11, 3)
        assertEquals("mar. 3 nov.", date.format(AppDates.formatter("EEE d MMM", "EEEdMMM", Locale.FRENCH)))
    }

    @Test
    fun theLocaleConfigListsEverySupportedLanguage() {
        val config = File("src/main/res/xml/locales_config.xml").readText()
        val listed = Regex("""android:name="([a-z]{2})"""").findAll(config).map { it.groupValues[1] }.toList()
        assertEquals(AppLanguage.SUPPORTED, listed)
    }

    @Test
    fun storedChoicesReadBackAndUnknownOnesFallBack() {
        ThemeChoice.entries.forEach { assertEquals(it, ThemeChoice.from(it.stored)) }
        assertEquals(ThemeChoice.System, ThemeChoice.from(null))
        assertEquals(ThemeChoice.System, ThemeChoice.from("sepia"))
        assertEquals(true, ThemeChoice.System.isDark(systemDark = true))
        assertEquals(false, ThemeChoice.Light.isDark(systemDark = true))
        assertEquals(true, ThemeChoice.Dark.isDark(systemDark = false))
        ChartStyle.entries.forEach { assertEquals(it, ChartStyle.from(it.stored)) }
        assertEquals(ChartStyle.SouthIndian, ChartStyle.from("constellation"))
    }
}
