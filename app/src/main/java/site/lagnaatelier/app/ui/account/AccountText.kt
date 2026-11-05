package site.lagnaatelier.app.ui.account

import androidx.annotation.StringRes
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.Traditions
import java.text.BreakIterator

/*
 * Plain functions the account screens print with, kept free of Android so the
 * unit tests can call them (string ids are plain numbers; the words for them
 * are passed in).
 */

/**
 * Up to two initials for the disc beside a saved chart: the first letter of
 * the first and last words, "Ananya Mehra" -> "AM", "Maya" -> "M".
 *
 * Letters are taken whole, as the reader sees them, so a Devanagari or
 * accented initial keeps its marks. Leading punctuation is skipped.
 */
fun initials(name: String): String {
    val words = name.trim().split(Regex("""\s+""")).mapNotNull(::firstLetter)
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words.first()
        else -> words.first() + words.last()
    }.uppercase()
}

/** The first user-perceived character of [word] that starts with a letter or digit. */
private fun firstLetter(word: String): String? {
    if (word.isEmpty()) return null
    val breaks = BreakIterator.getCharacterInstance()
    breaks.setText(word)
    var start = breaks.first()
    var end = breaks.next()
    while (end != BreakIterator.DONE) {
        if (Character.isLetterOrDigit(word.codePointAt(start))) return word.substring(start, end)
        start = end
        end = breaks.next()
    }
    return null
}

/** The ayanamsha and house system an engine id such as `lahiri_classic` names, or null when it is not one of the 36. */
fun traditionOf(engineId: String): Pair<Traditions.Ayanamsha, Traditions.HouseSystem>? {
    val ayanamsha = Traditions.Ayanamsha.entries
        .sortedByDescending { it.key.length }
        .firstOrNull { engineId.startsWith(it.key + "_") } ?: return null
    val houses = Traditions.HouseSystem.entries
        .firstOrNull { it.code == engineId.removePrefix(ayanamsha.key + "_") } ?: return null
    return ayanamsha to houses
}

/**
 * "Lahiri · whole sign" for an engine id such as `lahiri_classic`, or null
 * when the id is not one of the 36 the app knows. In English from the enums
 * when [word] is null; otherwise each name is [word] of its string
 * (res/values/strings_traditions.xml), so "लाहिड़ी · पूर्ण-राशि" in Hindi.
 */
fun traditionLabel(engineId: String, word: ((Int) -> String)? = null): String? {
    val (ayanamsha, houses) = traditionOf(engineId) ?: return null
    if (word != null) return "${word(ayanamsha.nameRes)} · ${word(houses.inLabelRes)}"
    /* "whole sign" reads lower-case after the dot, as on the mock; named systems keep their capital. */
    val housesLabel = if (houses == Traditions.HouseSystem.WholeSign) houses.label.lowercase() else houses.label
    return "${ayanamsha.label} · $housesLabel"
}

/** An ayanamsha's name in the app's language: the website's engineSelect.groups label. */
@get:StringRes
val Traditions.Ayanamsha.nameRes: Int
    get() = when (this) {
        Traditions.Ayanamsha.Lahiri -> R.string.tradition_ayanamsha_lahiri
        Traditions.Ayanamsha.Raman -> R.string.tradition_ayanamsha_raman
        Traditions.Ayanamsha.Krishnamurti -> R.string.tradition_ayanamsha_krishnamurti
        Traditions.Ayanamsha.FaganBradley -> R.string.tradition_ayanamsha_fagan_bradley
        Traditions.Ayanamsha.Pushyapaksha -> R.string.tradition_ayanamsha_pushyapaksha
        Traditions.Ayanamsha.Yukteshwar -> R.string.tradition_ayanamsha_yukteshwar
    }

/** A house system's name in the app's language, as the intake form's chip says it: "Whole sign". */
@get:StringRes
val Traditions.HouseSystem.nameRes: Int
    get() = when (this) {
        Traditions.HouseSystem.WholeSign -> R.string.tradition_houses_whole_sign
        Traditions.HouseSystem.Equal -> R.string.tradition_houses_equal
        Traditions.HouseSystem.Placidus -> R.string.tradition_houses_placidus
        Traditions.HouseSystem.Koch -> R.string.tradition_houses_koch
        Traditions.HouseSystem.Campanus -> R.string.tradition_houses_campanus
        Traditions.HouseSystem.Regiomontanus -> R.string.tradition_houses_regiomontanus
    }

/** The same, after the dot of a tradition label: "whole sign" lower-case where the language does that. */
@get:StringRes
val Traditions.HouseSystem.inLabelRes: Int
    get() = if (this == Traditions.HouseSystem.WholeSign) R.string.tradition_houses_whole_sign_in_label else nameRes
