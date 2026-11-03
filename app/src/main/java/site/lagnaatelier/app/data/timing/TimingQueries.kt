package site.lagnaatelier.app.data.timing

import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.TimeAccuracy
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/*
 * The query strings and the date arithmetic the timing routes need. Pure, so
 * the tests can pin them.
 */

/** The muhurta activities the tab offers, by the route's ids. Medical ones are left out on purpose. */
enum class MuhurtaActivity(val apiValue: String) {
    Anything("general_auspicious"),
    Travel("travel"),
    Business("business_start"),
    Study("education"),
    Property("property_purchase"),
    Interview("job_interview"),
    Spiritual("spiritual_practice"),
}

object TimingQueries {
    /** How many days "Find a good time" searches, today included. */
    const val MUHURTA_DAYS = 14L

    /** The sample chart's birth details, so the sample shows timing too. */
    val sampleDetails = BirthDetails(
        name = "Ananya Mehra",
        date = LocalDate.of(1990, 6, 15),
        exactTime = "14:30",
        accuracy = TimeAccuracy.Exact,
        place = Place("New Delhi", "Delhi", "India", 28.6139, 77.209, "Asia/Kolkata", 330),
    )

    /** /api/chart/forecast reads snake_case birth details plus target_date. */
    fun forecast(details: BirthDetails, today: LocalDate): Map<String, String> =
        LinkedHashMap(details.chartQuery()).apply {
            remove("include_transits")
            put("target_date", today.toString())
        }

    /** /api/varshaphal reads snake_case birth details plus target_year. */
    fun varshaphal(details: BirthDetails, targetYear: Int): Map<String, String> =
        LinkedHashMap(details.chartQuery()).apply {
            remove("include_transits")
            put("target_year", targetYear.toString())
        }

    /**
     * /api/chart/weekly-energy reads the birth details camelCase (readChartParams
     * on the web) and wants country, state and city all non-blank; a place with
     * no state lends it the city's name, which the engine does not compute with.
     * [weekStart] is omitted for the current week; when given it must be a Saturday.
     */
    fun weeklyEnergy(details: BirthDetails, weekStart: LocalDate? = null): Map<String, String> {
        val place = details.place
        val city = place.city.ifBlank { place.state.ifBlank { place.country } }
        val state = place.state.ifBlank { city }
        val country = place.country.ifBlank { state }
        val params = linkedMapOf(
            "name" to details.name.trim(),
            "birthDate" to details.date.toString(),
            "birthTime" to details.time,
            "timezoneOffsetMinutes" to place.timezoneOffsetMinutes.toString(),
            "latitude" to place.latitude.toString(),
            "longitude" to place.longitude.toString(),
            "country" to country,
            "state" to state,
            "city" to city,
            "timeZoneId" to place.timeZoneId,
            "engineId" to details.engineId,
            "birthTimeAccuracy" to details.accuracy.apiValue,
            "birthTimeSource" to if (details.accuracy == TimeAccuracy.Exact) "exact" else "fallback",
            "birthTimeFallback" to (details.accuracy != TimeAccuracy.Exact).toString(),
        )
        if (weekStart != null) {
            require(weekStart.dayOfWeek == DayOfWeek.SATURDAY) { "week_start must be a Saturday" }
            params["week_start"] = weekStart.toString()
        }
        return params
    }

    /** /api/muhurta: the activity, an inclusive date range, and the place. */
    fun muhurta(place: Place, activity: MuhurtaActivity, start: LocalDate, offsetMinutes: Int): Map<String, String> =
        linkedMapOf(
            "activity" to activity.apiValue,
            "start_date" to start.toString(),
            "end_date" to start.plusDays(MUHURTA_DAYS - 1).toString(),
            "latitude" to place.latitude.toString(),
            "longitude" to place.longitude.toString(),
            "timezone_offset_minutes" to offsetMinutes.toString(),
        )

    /** The Saturday the week containing [today] starts on (the weekly-energy weeks run Sat to Fri). */
    fun weekStart(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))

    /** The Saturday after [today]: the start of "Next" week. */
    fun nextWeekStart(today: LocalDate): LocalDate = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))

    /**
     * The place's offset from UTC at [now], in minutes. The stored offset is the
     * one in force at birth, which can be an hour out today where clocks change,
     * so the zone's own rules win when the zone is known.
     */
    fun offsetNow(place: Place, now: Instant = Instant.now()): Int {
        val zone = place.timeZoneId.takeIf { it.isNotBlank() }?.let {
            try {
                ZoneId.of(it)
            } catch (e: DateTimeException) {
                null
            }
        } ?: return place.timezoneOffsetMinutes
        return zone.rules.getOffset(now).totalSeconds / 60
    }

    /** Today's date on the place's clock. */
    fun placeToday(offsetMinutes: Int, now: Instant = Instant.now()): LocalDate =
        now.atOffset(ZoneOffset.ofTotalSeconds(offsetMinutes * 60)).toLocalDate()

    /** A real instant as the place's clock shows it. */
    fun atPlace(instant: Instant, offsetMinutes: Int): LocalDateTime =
        instant.atOffset(ZoneOffset.ofTotalSeconds(offsetMinutes * 60)).toLocalDateTime()

    /**
     * The varshaphal year running on [today]: it begins on the birthday, so
     * before this year's birthday the running year is last year's.
     */
    fun varshaphalYear(birthDate: LocalDate, today: LocalDate): Int {
        /* withYear turns 29 Feb into 28 Feb in a common year. */
        val birthdayThisYear = birthDate.withYear(today.year)
        val year = if (today.isBefore(birthdayThisYear)) today.year - 1 else today.year
        return maxOf(year, birthDate.year)
    }

    /** A cache key for one chart's timing: the birth facts the routes compute from. */
    fun key(details: BirthDetails): String = details.chartQuery().entries.joinToString("&") { "${it.key}=${it.value}" }
}
