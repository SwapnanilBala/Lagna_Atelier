package site.lagnaatelier.app.data

import site.lagnaatelier.app.data.reading.ReadingSections
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/*
 * What the reading screen needs from a chart. Shaped after the web app's
 * /api/chart payload (chart.ascendant, chart.nakshatra, chart.dasha,
 * chart.planets), so swapping the sample for a network call later changes
 * where the values come from, not what the screens take.
 */

enum class Sign(val label: String) {
    Aries("Aries"), Taurus("Taurus"), Gemini("Gemini"), Cancer("Cancer"),
    Leo("Leo"), Virgo("Virgo"), Libra("Libra"), Scorpio("Scorpio"),
    Sagittarius("Sagittarius"), Capricorn("Capricorn"), Aquarius("Aquarius"), Pisces("Pisces"),
}

/** One body in a sign, by its two-letter chart abbreviation. */
data class Placement(val abbreviation: String, val sign: Sign)

/**
 * A dasha period. `end` is exclusive, as the engine reports it. The sequence
 * dates are the whole period before it was clipped to the reader's life (the
 * mahadasha running at birth began before it); sub-periods are proportioned
 * from them.
 */
data class Period(
    val lord: String,
    val start: LocalDate,
    val end: LocalDate,
    val sequenceStart: LocalDate = start,
    val sequenceEnd: LocalDate = end,
) {
    /** The same period of life, however it was reported (with or without its sequence dates). */
    fun sameAs(other: Period): Boolean = lord == other.lord && start == other.start
}

/**
 * One of the readings the engine chose for the page. [key] is the rule's
 * instance key, which finds the same reading's evidence in the full reading
 * (ReadingSections.findings); blank for a reading built by hand.
 */
data class Finding(val category: String, val headline: String, val body: String? = null, val key: String = "")

data class ChartSummary(
    val name: String,
    val place: String,
    val birthDate: LocalDate,
    val birthTime: String,
    val tradition: String,
    val ascendant: Sign,
    val ascendantDegree: Double,
    val moonSign: Sign,
    val moonDegree: Double,
    val nakshatra: String,
    val pada: Int,
    val nakshatraLord: String,
    val mahadasha: Period,
    val antardasha: Period,
    /** Every mahadasha the timeline draws, in order. */
    val mahadashas: List<Period>,
    val placements: List<Placement>,
    val findings: List<Finding>,
    /** D9 placements by navamsa sign, and the navamsa's rising sign. */
    val navamsa: List<Placement> = emptyList(),
    val navamsaAscendant: Sign? = null,
    /** True when the reader did not know the exact time and the chart stands in one. */
    val timeIsEstimated: Boolean = false,
    /** The reading tab's longer sections: planet table, house support, every finding, yogas. */
    val sections: ReadingSections = ReadingSections(),
    /** Every divisional chart the engine cast, D1 to D60 (data/vargas). Empty for the built-in sample. */
    val vargas: List<site.lagnaatelier.app.data.vargas.Varga> = emptyList(),
)

/** The antardashas of the mahadasha running now, from the chart's own dates. */
val ChartSummary.antardashas: List<Period>
    get() = Vimshottari.subPeriods(mahadashas.firstOrNull { it.sameAs(mahadasha) } ?: mahadasha)

data class TodaySky(val date: LocalDate, val moonSign: Sign, val moonNakshatra: String, val sunSign: Sign)

/* In the app's language: "15 Jun 1990" in English, the language's own order otherwise. */
fun LocalDate.dayMonthYear(): String = format(AppDates.formatter("d MMM yyyy", "dMMMyyyy"))
fun LocalDate.monthYear(): String = format(AppDates.formatter("MMM yyyy", "MMMyyyy"))
fun LocalDate.weekdayDayMonth(): String = format(AppDates.formatter("EEE d MMM", "EEEdMMM"))

/** Degrees within a sign as the web app prints them: 28.7507 -> 28°45′. */
fun formatDegree(degreeInSign: Double): String {
    var whole = degreeInSign.toInt()
    var minutes = ((degreeInSign - whole) * 60).roundToInt()
    if (minutes == 60) {
        whole += 1
        minutes = 0
    }
    return "$whole°${minutes.toString().padStart(2, '0')}′"
}

/** How far through a period `today` is, from 0 to 1. */
fun Period.progress(today: LocalDate): Float {
    val total = ChronoUnit.DAYS.between(start, end).toFloat()
    if (total <= 0f) return 0f
    val done = ChronoUnit.DAYS.between(start, today).toFloat()
    return (done / total).coerceIn(0f, 1f)
}
