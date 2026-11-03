package site.lagnaatelier.app.data

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/*
 * A place's UTC offset at a given birth moment, from its IANA time zone. The
 * geocode route answers for the date and time the form had when the place
 * was chosen; when either changes afterwards (New York chosen for a June
 * date, then the date moved to January), the offset is worked out again here
 * from the zone the route named, so the form never shows, or sends, the
 * offset of another season.
 */
object PlaceOffsets {
    /**
     * Local minus UTC in minutes for [timeZoneId] at [date] and [time]
     * ("HH:MM"; noon when null or unreadable, today when [date] is null), or
     * null when the zone is not one java.time knows. A clock time skipped by a
     * spring-forward takes the offset in force before the change, as
     * java.time does.
     */
    fun offsetMinutes(timeZoneId: String, date: LocalDate?, time: String?): Int? {
        if (timeZoneId.isBlank()) return null
        val zone = try {
            ZoneId.of(timeZoneId)
        } catch (_: DateTimeException) {
            return null
        }
        val local = LocalDateTime.of(date ?: LocalDate.now(), parseTime(time) ?: LocalTime.NOON)
        val minutes = zone.rules.getOffset(local).totalSeconds / 60
        return minutes.takeIf { abs(it) <= LagnaApi.MAX_OFFSET_MINUTES }
    }

    /** [place] with its offset worked out again for [date] and [time]; unchanged if its zone is unknown. */
    fun reoffset(place: Place, date: LocalDate?, time: String?): Place {
        val minutes = offsetMinutes(place.timeZoneId, date, time) ?: return place
        return if (minutes == place.timezoneOffsetMinutes) place else place.copy(timezoneOffsetMinutes = minutes)
    }

    private val TIME = Regex("""([01]\d|2[0-3]):([0-5]\d)""")

    private fun parseTime(time: String?): LocalTime? =
        time?.let { TIME.matchEntire(it) }?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
}
