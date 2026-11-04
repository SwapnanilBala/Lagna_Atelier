package site.lagnaatelier.app.data.lifeareas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.report.ReportApi
import site.lagnaatelier.app.data.vargas.Varga
import site.lagnaatelier.app.data.vargas.VargaCommentary
import java.net.URLEncoder

/*
 * The calls behind More readings, typed. All but life-domains and the
 * ask-classics availability call a model and spend the day's allowance
 * (ten a route signed out, lib/llm-budget-tiers.ts), so the screens ask each
 * once per chart and keep the answer (see ui/more/MoreHolder.kt). The site
 * caches each answer too, and charges nothing for a cached one.
 *
 * The reading routes take the page's camelCase birth parameters
 * (lib/chart-params-url.ts readChartParams), the same set the report sends.
 * The classical notes, the answers and the varga notes are written in
 * [language] (ReadingLanguage); the briefs are English only, as on the web:
 * domain-brief takes no language.
 */
class MoreReadingsApi(private val api: LagnaApi, private val language: String = "en") {

    suspend fun lifeAreas(details: BirthDetails): List<LifeArea> {
        val body = api.getJson(LIFE_DOMAINS, query(details))
        return withContext(Dispatchers.Default) { LifeAreasParser.parseAreas(body) }
    }

    /** All seven briefs come back from one call, whichever [domain] it names. */
    suspend fun briefs(details: BirthDetails, domain: LifeAreaKey): Map<LifeAreaKey, String> {
        val body = api.getJson(DOMAIN_BRIEF, query(details) + ("domain" to domain.apiKey), readTimeoutMs = MODEL_TIMEOUT_MS)
        return withContext(Dispatchers.Default) { LifeAreasParser.parseBriefs(body) }
    }

    suspend fun areaClassics(details: BirthDetails): Map<LifeAreaKey, ClassicalReading> {
        val body = api.getJson(AREA_CLASSICS, query(details) + ("language" to language), readTimeoutMs = MODEL_TIMEOUT_MS)
        return withContext(Dispatchers.Default) { LifeAreasParser.parseAreaClassics(body) }
    }

    suspend fun askAvailable(details: BirthDetails): List<AskQuestion> {
        val body = api.getJson(ASK_CLASSICS, query(details))
        return withContext(Dispatchers.Default) { AskClassicsParser.parseAvailable(body) }
    }

    suspend fun ask(details: BirthDetails, question: AskQuestion): AskAnswer {
        val params = query(details) + mapOf("question" to question.id, "language" to language)
        val body = api.getJson(ASK_CLASSICS, params, readTimeoutMs = MODEL_TIMEOUT_MS)
        return withContext(Dispatchers.Default) { AskClassicsParser.parseAnswer(body) }
    }

    /** A typed question goes in the body; the birth details stay in the query, as on GET. */
    suspend fun askTyped(details: BirthDetails, text: String): AskAnswer {
        val path = "$ASK_CLASSICS?${queryString(query(details))}"
        val body = api.postJson(path, AskClassicsParser.typedBody(text, language), readTimeoutMs = MODEL_TIMEOUT_MS)
        return withContext(Dispatchers.Default) { AskClassicsParser.parseAnswer(body) }
    }

    /** The ten key vargas' notes in one call; empty when none of them is in [vargas]. */
    suspend fun vargaNotes(vargas: List<Varga>): Map<Int, String> {
        val request = VargaCommentary.requestBody(vargas, language) ?: return emptyMap()
        val body = api.postJson(VargaCommentary.PATH, request, readTimeoutMs = MODEL_TIMEOUT_MS)
        return withContext(Dispatchers.Default) { VargaCommentary.parseNotes(body) }
    }

    companion object {
        const val LIFE_DOMAINS = "/api/chart/life-domains"
        const val DOMAIN_BRIEF = "/api/chart/domain-brief"
        const val AREA_CLASSICS = "/api/chart/area-classics"
        const val ASK_CLASSICS = "/api/chart/ask-classics"

        /* The routes allow themselves 60 s (maxDuration); a read past that is a lost answer. */
        const val MODEL_TIMEOUT_MS = 70_000

        fun query(details: BirthDetails): Map<String, String> = ReportApi.storyReportQuery(details)

        /* As LagnaApi.getJson encodes its query, for the one POST that carries one. */
        fun queryString(params: Map<String, String>): String =
            params.entries.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
    }
}
