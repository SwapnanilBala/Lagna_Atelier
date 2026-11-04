package site.lagnaatelier.app.data.periods

import org.json.JSONObject
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.timing.ReaderCopy

/*
 * POST /api/compatibility {primary, partner}: two sets of birth details in
 * /api/chart's snake_case (lib/schemas.ts CompatibilityInputSchema), with
 * numbers as numbers, and back the score, the themes, every aspect between
 * the two charts and the married-life read across them
 * (lib/engines/compatibility-service.ts). The engine's own text, with no
 * language model behind it.
 */

data class SynastryAspect(
    val primaryPlanet: String,
    val partnerPlanet: String,
    /** "Trine", "Square"... */
    val aspectType: String,
    val orb: Double,
    val harmonious: Boolean,
)

data class CompatibilityTheme(val title: String, val insight: String)

enum class FindingPolarity { Clear, Caution, Context }

data class SynastryFinding(val text: String, val basis: String, val polarity: FindingPolarity)

data class SynastryFacet(
    val label: String,
    val verdict: String,
    val sourcing: String,
    val findings: List<SynastryFinding>,
)

data class CompatibilityResult(
    val primaryName: String,
    val partnerName: String,
    /** 0 to 100. */
    val score: Double,
    val summary: String,
    val themes: List<CompatibilityTheme>,
    /** Tightest first. */
    val aspects: List<SynastryAspect>,
    /** The married-life read, or empty when the engine declined it. */
    val facets: List<SynastryFacet>,
    val method: String,
) {
    val supportive: Int get() = aspects.count { it.harmonious }
    val friction: Int get() = aspects.size - supportive
    val tightest: SynastryAspect? get() = aspects.minByOrNull { it.orb }
    private val findings get() = facets.flatMap { it.findings }
    val clearFindings: Int get() = findings.count { it.polarity == FindingPolarity.Clear }
    val cautionFindings: Int get() = findings.count { it.polarity == FindingPolarity.Caution }

    /* The web's ring: 70 and over reads as harmonious, 45 and over as compatible. */
    enum class Band { Harmonious, Compatible, Challenging }

    val band: Band get() = when {
        score >= 70 -> Band.Harmonious
        score >= 45 -> Band.Compatible
        else -> Band.Challenging
    }
}

object Compatibility {
    const val PATH = "/api/compatibility"

    fun requestBody(primary: BirthDetails, partner: BirthDetails): String = JSONObject()
        .put("primary", person(primary))
        .put("partner", person(partner))
        .put("save_result", false)
        .toString()

    /* What buildBirthDetailsPayload sends on the web, plus how sure each time is. */
    internal fun person(details: BirthDetails): JSONObject {
        val place = details.place
        return JSONObject()
            .put("name", details.name.trim())
            .put("birth_date", details.date.toString())
            .put("birth_time", details.time)
            .put("engine_id", details.engineId)
            .put("timezone_offset_minutes", place.timezoneOffsetMinutes)
            .put("latitude", place.latitude)
            .put("longitude", place.longitude)
            .put("country", place.country)
            .put("state", place.state)
            .put("city", place.city)
            .put("town", "")
            .put("time_zone_id", place.timeZoneId)
            .put("birth_time_accuracy", details.accuracy.apiValue)
            .put("birth_time_source", if (details.accuracy == TimeAccuracy.Exact) "exact" else "fallback")
            .put("birth_time_fallback", details.accuracy != TimeAccuracy.Exact)
    }

    /*
     * The app's copy line holds here as on the timing routes: a theme or a
     * finding that speaks of death, disease or disability is dropped rather
     * than shown. The engine's templates should never say it.
     */
    fun parse(body: String): CompatibilityResult {
        val json = JSONObject(body)
        val kalatra = json.optJSONObject("kalatra_synastry")
        val summary = json.optString("summary")
        return CompatibilityResult(
            primaryName = json.optJSONObject("primary_client")?.optString("name").orEmpty(),
            partnerName = json.optJSONObject("partner_client")?.optString("name").orEmpty(),
            score = json.getDouble("compatibility_score").coerceIn(0.0, 100.0),
            summary = summary.takeIf { ReaderCopy.isShowable(it) }.orEmpty(),
            themes = json.optJSONArray("themes").objects()
                .map { CompatibilityTheme(it.optString("title"), it.optString("insight")) }
                .filter { it.title.isNotBlank() && ReaderCopy.isShowable(it.title) && ReaderCopy.isShowable(it.insight) },
            aspects = json.optJSONArray("synastry_aspects").objects()
                .map {
                    SynastryAspect(
                        primaryPlanet = it.getString("primary_planet"),
                        partnerPlanet = it.getString("partner_planet"),
                        aspectType = it.getString("aspect_type"),
                        orb = it.optDouble("orb", 0.0),
                        harmonious = it.optBoolean("harmonious"),
                    )
                }
                .sortedBy { it.orb },
            facets = kalatra?.optJSONArray("facets").objects().map { facet ->
                SynastryFacet(
                    label = facet.optString("label"),
                    verdict = facet.optString("verdict"),
                    sourcing = facet.optString("sourcing"),
                    findings = facet.optJSONArray("findings").objects()
                        .map {
                            SynastryFinding(
                                text = it.optString("text"),
                                basis = it.optString("basis"),
                                polarity = when (it.optString("polarity")) {
                                    "clear" -> FindingPolarity.Clear
                                    "caution" -> FindingPolarity.Caution
                                    else -> FindingPolarity.Context
                                },
                            )
                        }
                        .filter { it.text.isNotBlank() && ReaderCopy.isShowable(it.text) && ReaderCopy.isShowable(it.basis) },
                )
            }.filter { it.label.isNotBlank() && ReaderCopy.isShowable(it.verdict) },
            method = kalatra?.optString("method").orEmpty(),
        )
    }
}
