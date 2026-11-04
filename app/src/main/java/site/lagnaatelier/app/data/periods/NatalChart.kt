package site.lagnaatelier.app.data.periods

import org.json.JSONArray
import org.json.JSONObject
import site.lagnaatelier.app.data.Period
import java.time.LocalDate

/*
 * The parts of GET /api/chart the period readings and the life chapters are
 * built from, which the reading screen's ChartSummary does not keep: each
 * planet's house, the running stack down to the pratyantar, the whole Maha
 * Dasha list with its partial first period, and the birth instant the
 * returns are counted from. Read straight from the payload (lib/astro-types.ts)
 * so this feature never edits the shared chart parser.
 */

/** A body in the birth chart: its sign and its house in the chart's own house system. */
data class NatalPlanet(val name: String, val sign: String, val house: Int)

/** One Maha Dasha as the engine lists it. [isPartial] is the one running at birth. */
data class MahaPeriod(
    val planet: String,
    val start: LocalDate,
    val end: LocalDate,
    val years: Double,
    val isPartial: Boolean,
    /** The whole period's first day, before birth for the partial one. */
    val sequenceStart: LocalDate? = null,
)

data class NatalChart(
    val ascendantSign: String,
    val planets: List<NatalPlanet>,
    val nakshatraName: String,
    val nakshatraLord: String,
    val nakshatraPada: Int,
    /** Maha Dasha, Antardasha and, when the engine gives one, the Pratyantardasha running today. */
    val stack: List<Period>,
    val mahaPeriods: List<MahaPeriod>,
    /** "1990-06-15T09:00": the audit's birth instant in UTC, written without an offset. */
    val birthUtcIso: String?,
    val birthLocalIso: String?,
) {
    fun planet(name: String): NatalPlanet? = planets.firstOrNull { it.name == name }
}

object NatalParser {
    fun parse(body: String): NatalChart {
        val chart = JSONObject(body).getJSONObject("chart")
        val nakshatra = chart.getJSONObject("nakshatra")
        val dasha = chart.getJSONObject("dasha")
        val audit = chart.optJSONObject("calculation_audit")

        val stack = buildList {
            for (level in listOf("dasha", "antardasha", "pratyantar")) {
                val lord = dasha.optString("current_$level").takeIf { it.isNotBlank() } ?: break
                val start = dasha.optString("current_${level}_start").takeIf { it.isNotBlank() } ?: break
                val end = dasha.optString("current_${level}_end").takeIf { it.isNotBlank() } ?: break
                add(Period(lord, date(start), date(end)))
            }
        }

        return NatalChart(
            ascendantSign = chart.getJSONObject("ascendant").getString("sign"),
            planets = chart.getJSONArray("planets").objects().map {
                NatalPlanet(it.getString("name"), it.getString("sign"), it.optInt("house"))
            },
            nakshatraName = nakshatra.getString("name"),
            nakshatraLord = nakshatra.getString("lord"),
            nakshatraPada = nakshatra.getInt("pada"),
            stack = stack,
            mahaPeriods = dasha.optJSONArray("periods").objects().map {
                MahaPeriod(
                    planet = it.getString("planet"),
                    start = date(it.getString("start_date")),
                    end = date(it.getString("end_date")),
                    years = it.optDouble("years", 0.0),
                    isPartial = it.optBoolean("is_partial"),
                    sequenceStart = it.optString("sequence_start_date").takeIf { day -> day.isNotBlank() }?.let(::date),
                )
            },
            birthUtcIso = audit?.optString("birth_utc_iso")?.takeIf { it.isNotBlank() },
            birthLocalIso = audit?.optString("birth_local_iso")?.takeIf { it.isNotBlank() },
        )
    }

    private fun date(value: String): LocalDate = LocalDate.parse(value.take(10))
}

/** The objects in an array, skipping anything else; none for a missing array. */
internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
