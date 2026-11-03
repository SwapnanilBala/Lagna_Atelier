package site.lagnaatelier.app.data.palm

import android.content.Context
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/*
 * Palm reading is capped at two a UTC week per caller, Monday to Sunday
 * (lib/llm-budget-tiers.ts PALM_READINGS_PER_WEEK on the web). The phone
 * counts the readings it sent this week so it can say how many are left
 * before the reader takes a photo; the server is the truth, so a 429 with
 * the weekly scope closes the week here whatever the count says.
 */
object PalmWeek {
    const val READINGS_PER_WEEK = 2

    /** The UTC Monday that starts [now]'s week. */
    fun weekStart(now: Instant): LocalDate =
        now.atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The Monday the next reading opens on. */
    fun nextWeekStart(now: Instant): LocalDate = weekStart(now).plusDays(7)

    /** How many of [sentAt] (epoch millis) fall in [now]'s UTC week. */
    fun usedThisWeek(sentAt: List<Long>, now: Instant): Int {
        val week = weekStart(now)
        return sentAt.count { weekStart(Instant.ofEpochMilli(it)) == week }
    }

    /** [sentAt] without the one send recorded at [token]: a refusal the server did not count. */
    fun withoutSend(sentAt: List<Long>, token: Long): List<Long> {
        val index = sentAt.lastIndexOf(token)
        return if (index < 0) sentAt else sentAt.filterIndexed { i, _ -> i != index }
    }

    /** Readings left this week: none once the server said so for this week. */
    fun remaining(sentAt: List<Long>, capHitWeek: LocalDate?, now: Instant): Int =
        if (capHitWeek == weekStart(now)) 0
        else (READINGS_PER_WEEK - usedThisWeek(sentAt, now)).coerceAtLeast(0)
}

/** The count, kept on the phone. */
class PalmAllowanceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun sent(): List<Long> =
        prefs.getString(KEY_SENT, "").orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }

    private fun capWeek(): LocalDate? =
        prefs.getString(KEY_CAP_WEEK, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun remaining(now: Instant = Instant.now()): Int = PalmWeek.remaining(sent(), capWeek(), now)

    /**
     * A reading about to be sent, counted before the request goes so that a
     * process killed mid-reading still counts it, as the server will have.
     * Returns the token [rollBack] takes for a refusal the server did not count.
     */
    fun recordSend(now: Instant = Instant.now()): Long {
        val token = now.toEpochMilli()
        val kept = (sent() + token).takeLast(KEEP)
        prefs.edit().putString(KEY_SENT, kept.joinToString(",")).apply()
        return token
    }

    /** Takes back the send recorded as [token]. */
    fun rollBack(token: Long) {
        prefs.edit().putString(KEY_SENT, PalmWeek.withoutSend(sent(), token).joinToString(",")).apply()
    }

    /** The server said this week's readings are used up. */
    fun recordWeeklyCap(now: Instant = Instant.now()) {
        prefs.edit().putString(KEY_CAP_WEEK, PalmWeek.weekStart(now).toString()).apply()
    }

    private companion object {
        const val PREFS = "palm_allowance"
        const val KEY_SENT = "sent_at"
        const val KEY_CAP_WEEK = "cap_week"
        const val KEEP = 8
    }
}
