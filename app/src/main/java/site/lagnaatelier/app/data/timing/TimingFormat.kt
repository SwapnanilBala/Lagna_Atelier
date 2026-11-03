package site.lagnaatelier.app.data.timing

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * Dates and clock times as the timing tab prints them. Locale.ENGLISH
 * throughout: the app's copy is English, and the phone's locale would
 * otherwise mix languages inside one line.
 */
object TimingFormat {
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val weekdayDay = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun clock(time: LocalTime): String = time.format(clock)

    /** "Wed 4", for a day inside the week on screen. */
    fun weekdayDay(date: LocalDate): String = date.format(weekdayDay)

    /** "Wed 4 Nov". */
    fun weekdayDayMonth(date: LocalDate): String = date.format(weekdayDayMonth)

    /** "4 Nov". */
    fun dayMonth(date: LocalDate): String = date.format(dayMonth)

    /** "Sat", the bar chart's axis. */
    fun shortWeekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    /** "Saturday", for the chart's spoken description. */
    fun longWeekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    /**
     * Where a muhurta window ends, at the place's clock: the time alone when it
     * ends the day it starts, else the day and the time ("Thu 5 Nov 12:30").
     */
    fun windowEnd(window: MuhurtaWindow, offsetMinutes: Int): String {
        val start = TimingQueries.atPlace(window.start, offsetMinutes)
        val end = TimingQueries.atPlace(window.end, offsetMinutes)
        return if (end.toLocalDate() == start.toLocalDate()) {
            clock(end.toLocalTime())
        } else {
            "${weekdayDayMonth(end.toLocalDate())} ${clock(end.toLocalTime())}"
        }
    }

    /** The window's start time at the place's clock: "15:30". */
    fun windowStart(window: MuhurtaWindow, offsetMinutes: Int): String =
        clock(TimingQueries.atPlace(window.start, offsetMinutes).toLocalTime())

    /** The day a window starts on, at the place's clock. */
    fun windowDay(window: MuhurtaWindow, offsetMinutes: Int): LocalDate =
        TimingQueries.atPlace(window.start, offsetMinutes).toLocalDate()

    /** The two-letter chart abbreviation: "Venus" -> "Ve". */
    fun planetAbbreviation(planet: String): String = when (planet) {
        "Sun" -> "Su"
        "Moon" -> "Mo"
        "Mars" -> "Ma"
        "Mercury" -> "Me"
        "Jupiter" -> "Ju"
        "Venus" -> "Ve"
        "Saturn" -> "Sa"
        "Rahu" -> "Ra"
        "Ketu" -> "Ke"
        else -> planet.take(2)
    }

    /**
     * The year lord's theme as a heading and a line under it: the first
     * sentence, without its full stop, and the rest.
     */
    fun splitSummary(summary: String): Pair<String, String> {
        val text = summary.trim()
        val cut = text.indexOf(". ")
        return if (cut < 0) text.removeSuffix(".") to "" else text.substring(0, cut) to text.substring(cut + 2).trim()
    }
}
