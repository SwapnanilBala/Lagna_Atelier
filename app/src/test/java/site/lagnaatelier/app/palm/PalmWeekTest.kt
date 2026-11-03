package site.lagnaatelier.app.palm

import org.junit.Assert.assertEquals
import org.junit.Test
import site.lagnaatelier.app.data.palm.PalmWeek
import java.time.Instant
import java.time.LocalDate

/* Two readings a UTC week, Monday to Sunday, as lib/llm-budget.ts counts them. */
class PalmWeekTest {
    private fun at(iso: String) = Instant.parse(iso)

    @Test
    fun weeksStartOnUtcMonday() {
        val monday = LocalDate.of(2026, 11, 2)
        assertEquals(monday, PalmWeek.weekStart(at("2026-11-02T00:00:00Z")))
        assertEquals(monday, PalmWeek.weekStart(at("2026-11-03T05:46:00Z")))
        assertEquals(monday, PalmWeek.weekStart(at("2026-11-08T23:59:59Z")))
        assertEquals(LocalDate.of(2026, 11, 9), PalmWeek.weekStart(at("2026-11-09T00:00:00Z")))
        assertEquals(LocalDate.of(2026, 10, 26), PalmWeek.weekStart(at("2026-11-01T23:59:59Z")))
    }

    @Test
    fun aMondayMorningInIndiaCanStillBeLastWeek() {
        /* 05:00 on Monday in New Delhi is 23:30 on Sunday in UTC. */
        assertEquals(LocalDate.of(2026, 11, 2), PalmWeek.weekStart(at("2026-11-08T23:30:00Z")))
        assertEquals(LocalDate.of(2026, 11, 9), PalmWeek.nextWeekStart(at("2026-11-08T23:30:00Z")))
    }

    @Test
    fun countsOnlyThisWeeksReadings() {
        val sent = listOf(
            at("2026-11-01T23:59:59Z"), // last week
            at("2026-11-02T00:00:00Z"), // this week
            at("2026-11-04T12:00:00Z"), // this week
        ).map { it.toEpochMilli() }
        val now = at("2026-11-05T09:00:00Z")
        assertEquals(2, PalmWeek.usedThisWeek(sent, now))
        assertEquals(0, PalmWeek.remaining(sent, null, now))
        assertEquals(1, PalmWeek.remaining(sent.take(2), null, now))
        assertEquals(2, PalmWeek.remaining(emptyList(), null, now))
        /* Next Monday the count starts again. */
        assertEquals(2, PalmWeek.remaining(sent, null, at("2026-11-09T00:00:01Z")))
    }

    @Test
    fun theServersRefusalClosesTheWeekWhateverTheCount() {
        val now = at("2026-11-05T09:00:00Z")
        assertEquals(0, PalmWeek.remaining(emptyList(), LocalDate.of(2026, 11, 2), now))
        /* A refusal from an earlier week says nothing about this one. */
        assertEquals(2, PalmWeek.remaining(emptyList(), LocalDate.of(2026, 10, 26), now))
    }

    @Test
    fun neverGoesBelowNone() {
        val now = at("2026-11-05T09:00:00Z")
        val many = List(5) { now.toEpochMilli() - it * 1000L }
        assertEquals(0, PalmWeek.remaining(many, null, now))
    }
}
