package site.lagnaatelier.app.data

import java.time.LocalDate

/*
 * What the intake form collects, and the query string the web app's
 * GET /api/chart expects for it (snake_case, lib/chart-query.ts on the web).
 */

/** A place as the user picked it, once /api/geocode has placed it on the map. */
data class Place(
    val city: String,
    val state: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    /** Local minus UTC, IST = +330. The server recomputes it from the coordinates. */
    val timezoneOffsetMinutes: Int,
) {
    val label: String get() = listOf(city, state, country).filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * How sure the reader is of the birth time. When they are not, the web app
 * stands in a fixed time for the part of the day they remember
 * (lib/birth-time.ts), and so does this.
 */
enum class TimeAccuracy(val apiValue: String, val fallbackTime: String?) {
    Exact("exact", null),
    Morning("morning", "08:00"),
    Afternoon("afternoon", "13:30"),
    Evening("evening", "18:30"),
    Unknown("unknown", "12:00"),
}

/**
 * Sex at birth, as the website's intake asks it (lib/birth-sex.ts there):
 * Woman, Man, or Prefer not to say, which is null. Optional, and used only
 * where the tradition reads the sexes differently: the partner's planet
 * (Jupiter for a woman, Venus for a man) and which hand a palm reading reads.
 */
enum class BirthSex(val apiValue: String) {
    Female("female"),
    Male("male");

    companion object {
        /** Anything but the two values is "not given", as on the web. */
        fun from(value: String?): BirthSex? = entries.firstOrNull { it.apiValue == value }
    }
}

data class BirthDetails(
    val name: String,
    val date: LocalDate,
    /** "HH:MM", 24-hour. Ignored unless [accuracy] is [TimeAccuracy.Exact]. */
    val exactTime: String?,
    val accuracy: TimeAccuracy,
    val place: Place,
    val engineId: String = Traditions.DEFAULT_ENGINE_ID,
    /** Null when the reader preferred not to say. Not part of [chartQuery]: GET /api/chart ignores it. */
    val sex: BirthSex? = null,
) {
    /** The time the chart is cast for. */
    val time: String get() = if (accuracy == TimeAccuracy.Exact) exactTime.orEmpty() else accuracy.fallbackTime!!

    fun chartQuery(): Map<String, String> = linkedMapOf(
        "name" to name.trim(),
        "birth_date" to date.toString(),
        "birth_time" to time,
        "timezone_offset_minutes" to place.timezoneOffsetMinutes.toString(),
        "latitude" to place.latitude.toString(),
        "longitude" to place.longitude.toString(),
        "country" to place.country,
        "state" to place.state,
        "city" to place.city,
        "time_zone_id" to place.timeZoneId,
        "engine_id" to engineId,
        "birth_time_accuracy" to accuracy.apiValue,
        "birth_time_source" to if (accuracy == TimeAccuracy.Exact) "exact" else "fallback",
        "birth_time_fallback" to (accuracy != TimeAccuracy.Exact).toString(),
        "include_transits" to "true",
    )

    /**
     * The camelCase routes that read sex at birth (story-report,
     * weekly-energy: readChartParams on the web) take it as `birthSex`, and
     * only when it was given.
     */
    fun withBirthSex(params: MutableMap<String, String>): MutableMap<String, String> {
        sex?.let { params["birthSex"] = it.apiValue }
        return params
    }
}

/** The 36 traditions: six ayanamshas, each with six house systems (lib/engines/engine-registry.ts). */
object Traditions {
    enum class Ayanamsha(val key: String, val label: String) {
        Lahiri("lahiri", "Lahiri"),
        Raman("raman", "Raman"),
        Krishnamurti("krishnamurti", "Krishnamurti"),
        FaganBradley("fagan_bradley", "Fagan-Bradley"),
        Pushyapaksha("pushyapaksha", "Pushyapaksha"),
        Yukteshwar("yukteshwar", "Yukteshwar"),
    }

    enum class HouseSystem(val code: String, val label: String) {
        WholeSign("classic", "Whole sign"),
        Equal("equal", "Equal"),
        Placidus("placidus", "Placidus"),
        Koch("koch", "Koch"),
        Campanus("campanus", "Campanus"),
        Regiomontanus("regiomontanus", "Regiomontanus"),
    }

    const val DEFAULT_ENGINE_ID = "lahiri_classic"

    fun engineId(ayanamsha: Ayanamsha, houses: HouseSystem) = "${ayanamsha.key}_${houses.code}"
}
