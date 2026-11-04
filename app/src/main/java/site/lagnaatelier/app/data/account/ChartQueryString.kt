package site.lagnaatelier.app.data.account

import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.BirthSex
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.Traditions
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate

/*
 * A chart as the account stores it: the query string the website files a
 * chart under and reopens /insights from (buildChartHistoryQuery in
 * lib/chart-params-url.ts). camelCase, optional fields left out rather than
 * sent blank. The server derives the birth facts from it
 * (lib/sync/facts.ts), so it is the whole of what a synced chart is.
 *
 * [decode] reverses [encode] exactly, so a chart sent from this phone comes
 * back with the same key and is never mistaken for a second chart. A chart
 * the website saved decodes the same way; one the app could not cast again
 * (no time zone, no coordinates) is null and stays on the account only.
 */
object ChartQueryString {
    fun encode(details: BirthDetails): String {
        val place = details.place
        val exact = details.accuracy == TimeAccuracy.Exact
        val params = linkedMapOf(
            "name" to details.name.trim(),
            "birthDate" to details.date.toString(),
            "birthTime" to details.time,
            "timezoneOffsetMinutes" to place.timezoneOffsetMinutes.toString(),
            "latitude" to place.latitude.toString(),
            "longitude" to place.longitude.toString(),
            "country" to place.country,
            "state" to place.state,
            "city" to place.city,
            "engineId" to details.engineId,
        )
        if (place.timeZoneId.isNotBlank()) params["timeZoneId"] = place.timeZoneId
        params["birthTimeAccuracy"] = details.accuracy.apiValue
        params["birthTimeSource"] = if (exact) "exact" else "fallback"
        params["birthTimeFallback"] = (!exact).toString()
        details.sex?.let { params["birthSex"] = it.apiValue }
        return params.entries.joinToString("&") { (key, value) -> "${encodePart(key)}=${encodePart(value)}" }
    }

    fun decode(queryString: String): BirthDetails? {
        val params = parse(queryString)
        val name = params["name"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val date = params["birthDate"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val latitude = params["latitude"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
        val longitude = params["longitude"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
        val offset = params["timezoneOffsetMinutes"]?.toIntOrNull()
            ?.takeIf { kotlin.math.abs(it) <= LagnaApi.MAX_OFFSET_MINUTES } ?: return null
        /* The saved list needs a zone to cast again; a chart without one stays on the account. */
        val zone = params["timeZoneId"]?.takeIf { it.isNotBlank() } ?: return null
        val accuracy = TimeAccuracy.entries.firstOrNull { it.apiValue == params["birthTimeAccuracy"] }
            ?: TimeAccuracy.Exact
        val time = params["birthTime"]?.let { TIME.matchEntire(it)?.groupValues?.get(1) }
        if (accuracy == TimeAccuracy.Exact && time == null) return null
        return BirthDetails(
            name = name,
            date = date,
            exactTime = if (accuracy == TimeAccuracy.Exact) time else null,
            accuracy = accuracy,
            place = Place(
                city = params["city"].orEmpty(),
                state = params["state"].orEmpty(),
                country = params["country"].orEmpty(),
                latitude = latitude,
                longitude = longitude,
                timeZoneId = zone,
                timezoneOffsetMinutes = offset,
            ),
            engineId = params["engineId"]?.takeIf { ENGINE.matches(it) } ?: Traditions.DEFAULT_ENGINE_ID,
            sex = BirthSex.from(params["birthSex"]),
        )
    }

    /* URLSearchParams on the web writes a space as "+"; URLEncoder does the same. */
    private fun encodePart(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun parse(queryString: String): Map<String, String> =
        queryString.removePrefix("?").split("&").filter { it.isNotEmpty() }.mapNotNull { part ->
            val separator = part.indexOf('=')
            val key = if (separator < 0) part else part.substring(0, separator)
            val value = if (separator < 0) "" else part.substring(separator + 1)
            runCatching { URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8") }.getOrNull()
        }.reversed().toMap() // the first of a repeated key wins, as URLSearchParams.get does

    /* "14:30" or "14:30:00"; the saved list keeps minutes. */
    private val TIME = Regex("""(([01]\d|2[0-3]):[0-5]\d)(:[0-5]\d)?""")
    private val ENGINE = Regex("""[a-z_]{3,40}""")
}
