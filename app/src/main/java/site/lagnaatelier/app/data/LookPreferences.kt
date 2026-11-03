package site.lagnaatelier.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * How the app looks, remembered on this phone: light or dark, and which way
 * charts are drawn. Account -> Preferences sets both. The language is not
 * here: Android keeps the per-app language itself (see AppLanguage).
 *
 * Read once, as the activity starts, so the first frame is already in the
 * right theme; a few bytes of SharedPreferences, so the read is not worth a
 * background thread.
 */

enum class ThemeChoice(val stored: String) {
    /** Follow the phone's dark theme setting. The default. */
    System("system"),
    Light("light"),
    Dark("dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        fun from(stored: String?): ThemeChoice = entries.firstOrNull { it.stored == stored } ?: System
    }
}

/* The web's names: astro_chart_style is "constellation" or "north-indian" there. */
enum class ChartStyle(val stored: String) {
    SouthIndian("south-indian"),
    NorthIndian("north-indian");

    companion object {
        fun from(stored: String?): ChartStyle = entries.firstOrNull { it.stored == stored } ?: SouthIndian
    }
}

object LookPreferences {
    private const val FILE = "look"
    private const val THEME = "theme"
    private const val CHART_STYLE = "chart_style"

    private var prefs: SharedPreferences? = null

    var theme by mutableStateOf(ThemeChoice.System)
        private set
    var chartStyle by mutableStateOf(ChartStyle.SouthIndian)
        private set

    fun load(context: Context) {
        val stored = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = stored
        theme = ThemeChoice.from(stored.getString(THEME, null))
        chartStyle = ChartStyle.from(stored.getString(CHART_STYLE, null))
    }

    fun choose(choice: ThemeChoice) {
        theme = choice
        prefs?.edit()?.putString(THEME, choice.stored)?.apply()
    }

    fun choose(style: ChartStyle) {
        chartStyle = style
        prefs?.edit()?.putString(CHART_STYLE, style.stored)?.apply()
    }
}
