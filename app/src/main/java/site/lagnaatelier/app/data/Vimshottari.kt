package site.lagnaatelier.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.max
import kotlin.math.min

/*
 * The antardashas of a mahadasha, worked out on the phone the way the web's
 * dasha panel does (lib/dasha-periods.ts childSpans, over the engine's
 * computeSubPeriods in lib/engines/nakshatra-engine.ts): the nine lords in
 * Vimshottari order from the mahadasha's own, each taking its share of the
 * whole period (its years over 120), measured in milliseconds from midnight
 * UTC of the period's dates, then clipped to the part of the period that
 * falls in the reader's life. Pure arithmetic on the chart's own dates, so it
 * agrees with the chart's current_antardasha to the day.
 */
object Vimshottari {
    val LORDS = listOf("Ketu", "Venus", "Sun", "Moon", "Mars", "Rahu", "Jupiter", "Saturn", "Mercury")

    private val YEARS = mapOf(
        "Ketu" to 7, "Venus" to 20, "Sun" to 6, "Moon" to 10, "Mars" to 7,
        "Rahu" to 18, "Jupiter" to 16, "Saturn" to 19, "Mercury" to 17,
    )

    /** The sub-periods of [parent], clipped to its visible start and end; empty for an unknown lord. */
    fun subPeriods(parent: Period): List<Period> {
        val visibleStart = ms(parent.start)
        val visibleEnd = ms(parent.end)
        val fullStart = ms(parent.sequenceStart)
        val fullEnd = ms(parent.sequenceEnd)
        val total = fullEnd - fullStart
        val first = LORDS.indexOf(parent.lord)
        if (total <= 0 || first < 0) return emptyList()

        val out = mutableListOf<Period>()
        var cursor = fullStart
        for (i in 0 until 9) {
            val lord = LORDS[(first + i) % 9]
            var rawEnd = cursor + total * YEARS.getValue(lord) / 120.0
            if (i == 8 || rawEnd > fullEnd) rawEnd = fullEnd
            val clippedStart = max(cursor, visibleStart)
            val clippedEnd = min(rawEnd, visibleEnd)
            if (clippedStart < clippedEnd) {
                out += Period(lord, date(clippedStart), date(clippedEnd), sequenceStart = date(cursor), sequenceEnd = date(rawEnd))
            }
            cursor = rawEnd
        }
        return out
    }

    private fun ms(date: LocalDate): Double = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli().toDouble()

    /* As JavaScript's Date does with a fractional time: toward zero, then the UTC day. */
    private fun date(ms: Double): LocalDate = Instant.ofEpochMilli(ms.toLong()).atZone(ZoneOffset.UTC).toLocalDate()
}
