package site.lagnaatelier.app.data.periods

import org.json.JSONArray
import org.json.JSONObject
import site.lagnaatelier.app.data.Period
import site.lagnaatelier.app.data.progress
import java.time.LocalDate

/*
 * The written paragraph for the periods running now, from
 * POST /api/chart/current-period. The body is the web panel's
 * (lib/current-period-reading.ts buildCurrentPeriodFacts): the stack from the
 * Maha Dasha down, each lord's sign and house, the rising sign and the nine
 * planets' signs, the birth nakshatra, and how far through the Antardasha the
 * reader is. Every field comes from a closed set, which is what the route
 * validates against, so a chart that cannot supply them all sends nothing.
 */
object CurrentPeriod {
    /* In the web's order: the route files the reading under a key built in this order too. */
    val LORDS = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu")

    val SIGNS = setOf(
        "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
        "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces",
    )

    val NAKSHATRAS = setOf(
        "Ashwini", "Bharani", "Krittika", "Rohini", "Mrigashira", "Ardra",
        "Punarvasu", "Pushya", "Ashlesha", "Magha", "Purva Phalguni",
        "Uttara Phalguni", "Hasta", "Chitra", "Swati", "Vishakha", "Anuradha",
        "Jyeshtha", "Moola", "Purva Ashadha", "Uttara Ashadha", "Shravana",
        "Dhanishta", "Shatabhisha", "Purva Bhadrapada", "Uttara Bhadrapada", "Revati",
    )

    /** The route reads two or three levels: Maha Dasha, Antardasha, Pratyantardasha. */
    private const val MIN_STEPS = 2
    private const val MAX_STEPS = 3

    /**
     * The request body, or null when the chart cannot supply the facts (fewer
     * than two lords, or a nakshatra outside the 27), which the card answers
     * by showing no paragraph rather than asking.
     */
    fun requestBody(natal: NatalChart, antarProgressPercent: Double, language: String = "en"): String? {
        val stack = mutableListOf<Period>()
        for (period in natal.stack.take(MAX_STEPS)) {
            /* Stop at the first gap rather than skipping it, as the web does. */
            if (period.lord !in LORDS) break
            stack.add(period)
        }
        if (stack.size < MIN_STEPS) return null
        if (natal.nakshatraName !in NAKSHATRAS || natal.nakshatraLord !in LORDS) return null

        val body = JSONObject()
        body.put("stack", JSONArray().apply {
            stack.forEach { period ->
                put(JSONObject().apply {
                    put("lord", period.lord)
                    put("startDate", period.start.toString())
                    put("endDate", period.end.toString())
                    natal.planet(period.lord)
                        ?.takeIf { it.sign in SIGNS && it.house in 1..12 }
                        ?.let {
                            put("sign", it.sign)
                            put("house", it.house)
                        }
                })
            }
        })
        chart(natal)?.let { body.put("chart", it) }
        body.put("nakshatra", JSONObject().apply {
            put("name", natal.nakshatraName)
            put("lord", natal.nakshatraLord)
            put("pada", natal.nakshatraPada)
        })
        body.put("progressPercent", antarProgressPercent.coerceIn(0.0, 100.0))
        body.put("language", language)
        return body.toString()
    }

    /** The rising sign and all nine planets' signs, or null unless every one is known. */
    private fun chart(natal: NatalChart): JSONObject? {
        if (natal.ascendantSign !in SIGNS) return null
        val placed = LORDS.map { natal.planet(it)?.takeIf { planet -> planet.sign in SIGNS } ?: return null }
        return JSONObject().apply {
            put("ascendantSign", natal.ascendantSign)
            put("planets", JSONArray().apply {
                placed.forEach { put(JSONObject().put("name", it.name).put("sign", it.sign)) }
            })
        }
    }

    /**
     * How far through the Antardasha [today] is, 0 to 100: the level the
     * paragraph's phase describes, however deep the stack (PHASE_LEVEL).
     */
    fun progressPercent(antar: Period, today: LocalDate): Double = antar.progress(today) * 100.0

    /** The paragraph from the route's {reading, cached}, or null when there is none to show. */
    fun parseReading(body: String): String? =
        (JSONObject(body).opt("reading") as? String)?.trim()?.takeIf { it.isNotEmpty() }
}
