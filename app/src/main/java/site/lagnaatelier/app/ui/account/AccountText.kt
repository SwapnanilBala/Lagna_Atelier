package site.lagnaatelier.app.ui.account

import site.lagnaatelier.app.data.Traditions
import java.text.BreakIterator

/*
 * Plain functions the account screens print with, kept free of Android so the
 * unit tests can call them.
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

/**
 * "Lahiri · whole sign" for an engine id such as `lahiri_classic`, or null
 * when the id is not one of the 36 the app knows.
 */
fun traditionLabel(engineId: String): String? {
    val ayanamsha = Traditions.Ayanamsha.entries
        .sortedByDescending { it.key.length }
        .firstOrNull { engineId.startsWith(it.key + "_") } ?: return null
    val houses = Traditions.HouseSystem.entries
        .firstOrNull { it.code == engineId.removePrefix(ayanamsha.key + "_") } ?: return null
    /* "whole sign" reads lower-case after the dot, as on the mock; named systems keep their capital. */
    val housesLabel = if (houses == Traditions.HouseSystem.WholeSign) houses.label.lowercase() else houses.label
    return "${ayanamsha.label} · $housesLabel"
}
