package site.lagnaatelier.app.data.timing

import site.lagnaatelier.app.data.Period
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/*
 * What the Timing tab shows, read from four of the site's GET routes:
 *
 *   /api/chart/forecast        the running periods and today's transits
 *   /api/chart/weekly-energy   seven days, scored
 *   /api/muhurta               good windows for an activity, and Rahukaala
 *   /api/varshaphal            this year's chart (the solar return)
 */

/** The three running periods and the sub-periods of the current antardasha. */
data class RunningPeriods(
    val maha: Period,
    val antar: Period,
    /** Null when the engine gives no pratyantar for the date. */
    val pratyantar: Period?,
    /** Every pratyantar in the current antardasha, in order. */
    val pratyantars: List<Period>,
) {
    /** The pratyantar after the running one, if it falls inside this antardasha. */
    val next: Period?
        get() {
            val current = pratyantar ?: return null
            return pratyantars.firstOrNull { it.start == current.end }
        }

    /** The running pratyantar and the ones after it. */
    val remaining: List<Period>
        get() {
            val current = pratyantar ?: return pratyantars
            return pratyantars.filter { it.start >= current.start }
        }
}

enum class TransitTone { Easy, Testing }

data class Transit(
    val planet: String,
    val natalPlanet: String,
    val aspect: String,
    val orb: Double,
    val tone: TransitTone,
    val interpretation: String,
    /** Null once the aspect is separating, or when the engine cannot tell. */
    val daysToExact: Double?,
)

data class Forecast(val targetDate: LocalDate, val periods: RunningPeriods, val transits: List<Transit>)

enum class EnergyBand(val apiValue: String) {
    High("high"), Balanced("balanced"), Low("low");

    companion object {
        fun from(value: String): EnergyBand = entries.firstOrNull { it.apiValue == value } ?: Balanced
    }
}

data class EnergyDay(val date: LocalDate, val score: Int, val band: EnergyBand)

data class WeekCard(val title: String, val body: String, val bestDay: LocalDate?)

data class EnergyWeek(
    val start: LocalDate,
    val end: LocalDate,
    val days: List<EnergyDay>,
    val average: Int,
    val headlineTitle: String,
    val headlineParagraph: String,
    val cards: List<WeekCard>,
)

/** A good window. [start] and [end] are real instants; show them at the place's offset. */
data class MuhurtaWindow(val start: Instant, val end: Instant, val score: Int, val quality: String)

/**
 * Rahukaala on one day. The route sends these already shifted to the place's
 * clock, though the strings end in "Z", so they are kept as clock times.
 */
data class AvoidPeriod(val date: LocalDate, val rahuStart: LocalTime, val rahuEnd: LocalTime)

data class MuhurtaResult(
    val activity: String,
    val offsetMinutes: Int,
    /** Best first: highest score, then earliest. */
    val windows: List<MuhurtaWindow>,
    val avoid: List<AvoidPeriod>,
) {
    val best: MuhurtaWindow? get() = windows.firstOrNull()

    fun rahukaalaOn(date: LocalDate): AvoidPeriod? = avoid.firstOrNull { it.date == date }
}

/** Why the engine chose the year lord, as the varshaphal route reports it. */
enum class YearLordReason { RulesYearRising, RulesMuntha, RulesBirthRising, Other }

data class YearChart(
    val year: Int,
    val returnMoment: Instant?,
    val rising: String,
    val risingDegree: Double,
    val yearLord: String,
    val yearLordReason: YearLordReason,
    val munthaSign: String,
    val munthaHouse: Int,
    val profectionYear: Int,
    val profectionHouse: Int,
    val profectionSign: String,
    /** The year lord's theme, or blank when it was left out. */
    val summary: String,
)
