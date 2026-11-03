package site.lagnaatelier.app.data.timing

import org.json.JSONArray
import org.json.JSONObject
import site.lagnaatelier.app.data.Period
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/*
 * Pure readers for the four timing routes, on org.json so the unit tests can
 * run them against saved live responses (app/src/test/resources/timing).
 * Field names are the routes' own; see lib/astro-types.ts on the web.
 */
object TimingParser {

    /** GET /api/chart/forecast: dasha.current_*, pratyantar_periods, and the two transit lists. */
    fun parseForecast(body: String): Forecast {
        val json = JSONObject(body)
        val dasha = json.getJSONObject("dasha")
        val maha = Period(
            dasha.getString("current_dasha"),
            LocalDate.parse(dasha.getString("current_dasha_start")),
            LocalDate.parse(dasha.getString("current_dasha_end")),
        )
        val antar = Period(
            dasha.getString("current_antardasha"),
            LocalDate.parse(dasha.getString("current_antardasha_start")),
            LocalDate.parse(dasha.getString("current_antardasha_end")),
        )
        val pratyantar = dasha.optString("current_pratyantar").takeIf { it.isNotBlank() }?.let {
            Period(
                it,
                LocalDate.parse(dasha.getString("current_pratyantar_start")),
                LocalDate.parse(dasha.getString("current_pratyantar_end")),
            )
        }
        val pratyantars = dasha.optJSONArray("pratyantar_periods").objects().map {
            Period(it.getString("planet"), LocalDate.parse(it.getString("start_date")), LocalDate.parse(it.getString("end_date")))
        }

        val transits = transits(json.optJSONArray("supportive_transits"), TransitTone.Easy) +
            transits(json.optJSONArray("challenging_transits"), TransitTone.Testing)

        return Forecast(
            targetDate = LocalDate.parse(json.getString("target_date")),
            periods = RunningPeriods(maha, antar, pratyantar, pratyantars),
            /* Arriving soonest first; separating ones after, tightest first. */
            transits = transits
                .filter { ReaderCopy.isShowable(it.interpretation) }
                .sortedWith(compareBy<Transit>({ it.daysToExact == null }, { it.daysToExact ?: 0.0 }, { it.orb })),
        )
    }

    private fun transits(array: JSONArray?, tone: TransitTone): List<Transit> = array.objects().map {
        Transit(
            planet = it.getString("transit_planet"),
            natalPlanet = it.getString("natal_planet"),
            aspect = it.getString("aspect_type"),
            orb = it.optDouble("orb", 0.0),
            tone = tone,
            interpretation = it.optString("interpretation"),
            daysToExact = if (it.has("days_to_exact") && !it.isNull("days_to_exact")) it.getDouble("days_to_exact") else null,
        )
    }

    /** GET /api/chart/weekly-energy: seven scored days, a headline and the intent cards. */
    fun parseWeek(body: String): EnergyWeek {
        val json = JSONObject(body)
        val week = json.getJSONObject("week")
        val headline = json.optJSONObject("headline")
        val headlineTitle = headline?.optString("title").orEmpty()
        val headlineParagraph = headline?.optString("paragraph").orEmpty()
        val headlineShowable = ReaderCopy.isShowable(headlineTitle) && ReaderCopy.isShowable(headlineParagraph)
        return EnergyWeek(
            start = LocalDate.parse(week.getString("start_date")),
            end = LocalDate.parse(week.getString("end_date")),
            days = json.getJSONArray("days").objects().map {
                EnergyDay(LocalDate.parse(it.getString("date")), it.getInt("score"), EnergyBand.from(it.optString("band")))
            },
            average = json.optInt("average_score"),
            headlineTitle = if (headlineShowable) headlineTitle else "",
            headlineParagraph = if (headlineShowable) headlineParagraph else "",
            cards = json.optJSONArray("cards").objects()
                .map {
                    WeekCard(
                        title = it.optString("title"),
                        body = it.optString("body"),
                        bestDay = it.optString("best_day").takeIf { day -> day.isNotBlank() }?.let(LocalDate::parse),
                    )
                }
                .filter { it.title.isNotBlank() && ReaderCopy.isShowable(it.title) && ReaderCopy.isShowable(it.body) },
        )
    }

    /**
     * GET /api/muhurta. `windows[].start/end` are true UTC instants; the
     * `avoid_periods` strings were shifted to the place's clock on the server
     * and still carry a "Z", so they are read as written, not converted.
     */
    fun parseMuhurta(body: String): MuhurtaResult {
        val json = JSONObject(body)
        val windows = json.optJSONArray("windows").objects().map {
            MuhurtaWindow(
                start = Instant.parse(it.getString("start")),
                end = Instant.parse(it.getString("end")),
                score = it.optInt("score"),
                quality = it.optString("quality"),
            )
        }
        val avoid = json.optJSONArray("avoid_periods").objects().mapNotNull {
            val rahu = it.optJSONObject("rahukaala") ?: return@mapNotNull null
            AvoidPeriod(
                date = LocalDate.parse(it.getString("date")),
                rahuStart = clockAsWritten(rahu.getString("start")),
                rahuEnd = clockAsWritten(rahu.getString("end")),
            )
        }
        return MuhurtaResult(
            activity = json.optString("activity"),
            offsetMinutes = json.optInt("timezone_offset_minutes"),
            windows = windows.sortedWith(compareByDescending<MuhurtaWindow> { it.score }.thenBy { it.start }),
            avoid = avoid,
        )
    }

    /** "2026-11-03T14:42:08.404Z" -> 14:42:08.404, the clock time as written. */
    internal fun clockAsWritten(value: String) = Instant.parse(value).atOffset(ZoneOffset.UTC).toLocalTime()

    /** GET /api/varshaphal: the return chart's rising sign, the year lord, Muntha and the profection. */
    fun parseVarshaphal(body: String): YearChart {
        val json = JSONObject(body)
        val ascendant = json.getJSONObject("returnChart").getJSONObject("ascendant")
        val varshesh = json.getJSONObject("varshesh")
        val muntha = json.getJSONObject("muntha")
        val profection = json.getJSONObject("profection")
        val summary = json.optJSONObject("yearSummary")?.optString("yearLordInterpretation").orEmpty()
        return YearChart(
            year = json.getInt("year"),
            returnMoment = json.optString("solarReturnMoment").takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it) }.getOrNull() },
            rising = ascendant.getString("sign"),
            risingDegree = ascendant.getDouble("degree_in_sign"),
            yearLord = varshesh.getString("planet"),
            yearLordReason = yearLordReason(varshesh.optString("reason")),
            munthaSign = muntha.getString("sign"),
            munthaHouse = muntha.getInt("house"),
            profectionYear = profection.getInt("year"),
            profectionHouse = profection.getInt("activatedHouse"),
            profectionSign = profection.getString("activatedSign"),
            summary = summary.takeIf { ReaderCopy.isShowable(it) }.orEmpty(),
        )
    }

    /* The engine's reasons (lib/engines/varshaphal-engine.ts selectVarshesh). */
    internal fun yearLordReason(reason: String): YearLordReason = when {
        reason.startsWith("Lord of Muntha") -> YearLordReason.RulesMuntha
        reason.startsWith("Lord of solar return ascendant") -> YearLordReason.RulesYearRising
        reason.startsWith("Default to return ascendant") -> YearLordReason.RulesYearRising
        reason.startsWith("Lord of natal ascendant") -> YearLordReason.RulesBirthRising
        else -> YearLordReason.Other
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

/*
 * The site's copy rule for this app: nothing about death, disease or
 * disability. The timing routes are template text and should never say it,
 * but a line that does is dropped rather than shown.
 */
object ReaderCopy {
    private val blocked = Regex(
        "\\b(death|deaths|die|dies|dying|dead|fatal|disease|diseases|illness|illnesses|sickness|" +
            "disability|disabled|blind|blindness|surgery|surgical|hospital|accident|accidents|injury|injuries)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun isShowable(text: String): Boolean = !blocked.containsMatchIn(text)
}
