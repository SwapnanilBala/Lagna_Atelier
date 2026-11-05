package site.lagnaatelier.app.data

import org.json.JSONArray
import org.json.JSONObject
import site.lagnaatelier.app.data.reading.ReadingSectionsParser
import java.time.LocalDate

/*
 * Reads the web app's chart payload (GET /api/chart) into what the screens use.
 *
 * The paths are the ones lib/astro-types.ts on the web declares. The birth
 * date and time are not echoed back by the API, so they come from what the
 * reader entered.
 */
object ChartParser {
    private val grahaAbbreviations = linkedMapOf(
        "Sun" to "Su", "Moon" to "Mo", "Mars" to "Ma", "Mercury" to "Me", "Jupiter" to "Ju",
        "Venus" to "Ve", "Saturn" to "Sa", "Rahu" to "Ra", "Ketu" to "Ke",
    )

    private val tierLabels = mapOf("foundation" to "Foundation", "signature" to "Signature")

    fun parse(json: String, details: BirthDetails): ChartSummary {
        val root = JSONObject(json)
        val client = root.getJSONObject("client")
        val chart = root.getJSONObject("chart")
        val ascendant = chart.getJSONObject("ascendant")
        val nakshatra = chart.getJSONObject("nakshatra")
        val dasha = chart.getJSONObject("dasha")
        val planets = chart.getJSONArray("planets").objects()
        val moon = planets.first { it.getString("name") == "Moon" }

        val placements = planets.mapNotNull { planet ->
            grahaAbbreviations[planet.getString("name")]?.let { Placement(it, sign(planet.getString("sign"))) }
        }

        val navamsa = chart.optJSONArray("navamsa")?.objects().orEmpty().mapNotNull { row ->
            grahaAbbreviations[row.getString("name")]?.let { Placement(it, sign(row.getString("navamsa_sign"))) }
        }
        val navamsaAscendant = chart.optJSONObject("divisional_charts")
            ?.optJSONObject("9")
            ?.optJSONArray("positions")
            ?.objects()
            ?.firstOrNull { it.optString("name") == "Ascendant" }
            ?.let { sign(it.getString("divisional_sign")) }

        return ChartSummary(
            name = client.optString("name").ifBlank { details.name },
            place = client.optString("city").ifBlank { details.place.city },
            birthDate = details.date,
            birthTime = details.time,
            tradition = root.optJSONObject("engine")?.optString("engine_label").orEmpty().ifBlank { details.engineId },
            ascendant = sign(ascendant.getString("sign")),
            ascendantDegree = ascendant.getDouble("degree_in_sign"),
            moonSign = sign(moon.getString("sign")),
            moonDegree = moon.getDouble("degree_in_sign"),
            nakshatra = nakshatra.getString("name"),
            pada = nakshatra.getInt("pada"),
            nakshatraLord = nakshatra.getString("lord"),
            mahadasha = Period(
                dasha.getString("current_dasha"),
                date(dasha.getString("current_dasha_start")),
                date(dasha.getString("current_dasha_end")),
            ),
            antardasha = Period(
                dasha.getString("current_antardasha"),
                date(dasha.getString("current_antardasha_start")),
                date(dasha.getString("current_antardasha_end")),
            ),
            mahadashas = dasha.getJSONArray("periods").objects().map {
                val start = date(it.getString("start_date"))
                val end = date(it.getString("end_date"))
                Period(
                    it.getString("planet"),
                    start,
                    end,
                    sequenceStart = it.optString("sequence_start_date").takeIf { s -> s.length >= 10 }?.let(::date) ?: start,
                    sequenceEnd = it.optString("sequence_end_date").takeIf { s -> s.length >= 10 }?.let(::date) ?: end,
                )
            },
            placements = placements,
            navamsa = navamsa,
            navamsaAscendant = navamsaAscendant,
            findings = findings(chart),
            timeIsEstimated = details.accuracy != TimeAccuracy.Exact,
            sections = ReadingSectionsParser.parse(root),
            vargas = site.lagnaatelier.app.data.vargas.VargaParser.parse(chart.optJSONObject("divisional_charts")),
            engineId = details.engineId,
        )
    }

    /* The readings the engine chose for the page, in the order it chose them. */
    private fun findings(chart: JSONObject): List<Finding> {
        val rules = chart.optJSONArray("deterministic_rules")?.objects().orEmpty()
        val byKey = rules.associateBy { it.optString("instance_key") }
        val chosen = chart.optJSONArray("selected_rule_ids")?.strings().orEmpty().mapNotNull { byKey[it] }
        val ordered = chosen.ifEmpty { rules.filter { it.optJSONObject("selection")?.optBoolean("selected") == true } }
        return ordered.map { rule ->
            val display = rule.getJSONObject("display")
            Finding(
                category = tierLabels[rule.optString("tier")] ?: rule.optString("category").replaceFirstChar { it.uppercase() },
                headline = display.getString("headline"),
                body = display.optString("body").ifBlank { null },
                key = rule.optString("instance_key"),
            )
        }
    }

    /** Today's Moon and Sun from a chart cast for the present moment. */
    fun parseTodaySky(json: String, today: LocalDate): TodaySky {
        val chart = JSONObject(json).getJSONObject("chart")
        val planets = chart.getJSONArray("planets").objects()
        return TodaySky(
            date = today,
            moonSign = sign(planets.first { it.getString("name") == "Moon" }.getString("sign")),
            moonNakshatra = chart.getJSONObject("nakshatra").getString("name"),
            sunSign = sign(planets.first { it.getString("name") == "Sun" }.getString("sign")),
        )
    }

    private fun sign(name: String): Sign = Sign.entries.firstOrNull { it.label == name }
        ?: throw IllegalArgumentException("Unknown sign: $name")

    private fun date(value: String): LocalDate = LocalDate.parse(value.take(10))

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}
