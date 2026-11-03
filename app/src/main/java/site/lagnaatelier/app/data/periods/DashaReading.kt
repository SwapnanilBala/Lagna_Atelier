package site.lagnaatelier.app.data.periods

import org.json.JSONArray
import org.json.JSONObject
import site.lagnaatelier.app.data.Period
import site.lagnaatelier.app.data.timing.RunningPeriods

/*
 * "Read this period": POST /api/chart/dasha-reading, one period at a time and
 * only when the reader asks (use-dasha-reading.ts on the web). The birth
 * details go in the query, camelCase; the body names the period by its chain
 * of lords from the Maha Dasha down and the deepest one's first day. The route
 * walks the chart's own periods to that chain and refuses one it does not
 * have, before anything is spent, so the dates must be the engine's to the day.
 */

/** A period the reader can ask about: its lords from the Maha Dasha down, and its own dates. */
data class PeriodChain(val lords: List<String>, val period: Period) {
    /** One reading per period; the key the tab files it under. */
    val key: String get() = "${lords.joinToString(">")}@${period.start}"
}

object DashaReading {
    /** The Maha Dasha, the Antardasha, the running Pratyantardasha. */
    fun runningChains(periods: RunningPeriods): List<PeriodChain> = buildList {
        add(PeriodChain(listOf(periods.maha.lord), periods.maha))
        add(PeriodChain(listOf(periods.maha.lord, periods.antar.lord), periods.antar))
        periods.pratyantar?.let { add(pratyantarChain(periods, it)) }
    }

    /** A Pratyantardasha inside the running Antardasha. */
    fun pratyantarChain(periods: RunningPeriods, pratyantar: Period): PeriodChain =
        PeriodChain(listOf(periods.maha.lord, periods.antar.lord, pratyantar.lord), pratyantar)

    fun requestBody(chain: PeriodChain, language: String = "en"): String = JSONObject()
        .put("lords", JSONArray(chain.lords))
        .put("start", chain.period.start.toString())
        .put("language", language)
        .toString()

    /** What the route answered: a reading, or none when the books have nothing for this period's planets. */
    sealed interface Answer {
        data class Written(val reading: ClassicalReading) : Answer
        data object NothingInTheBooks : Answer
    }

    /* `reading: null` is an answer, not a failure; a reading with no sources is treated the same, as the web does. */
    fun parse(body: String): Answer {
        val reading = ClassicalReading.parse(JSONObject(body).optJSONObject("reading"))
        return if (reading != null && reading.isUsable) Answer.Written(reading) else Answer.NothingInTheBooks
    }
}
