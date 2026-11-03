package site.lagnaatelier.app.data

import java.net.URLEncoder
import site.lagnaatelier.app.data.report.ReportApi

/**
 * What the reading's Share button sends: a line about the chart and a link that
 * opens the same chart on the website.
 *
 * The link is the website's reading page with the page's own camelCase
 * parameters, the ones a shared link from the site carries
 * (lib/chart-params-url.ts), so it opens the reading rather than the intake
 * form. The sample chart has no birth details of anyone's, so it links to the
 * home page instead.
 */
object ShareReading {
    fun link(details: BirthDetails?, baseUrl: String = LagnaApi.BASE_URL): String {
        if (details == null) return baseUrl
        val query = ReportApi.storyReportQuery(details).entries
            .joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return "$baseUrl/insights?$query"
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
