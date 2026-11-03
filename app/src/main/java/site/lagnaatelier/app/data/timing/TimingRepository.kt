package site.lagnaatelier.app.data.timing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.LagnaApi
import java.time.LocalDate

/*
 * The timing routes as typed calls. All four are plain GETs with no language
 * model behind them; the network runs on IO inside LagnaApi.getJson and the
 * parsing on Default, so nothing here touches the main thread.
 */
class TimingRepository(private val api: LagnaApi) {

    suspend fun forecast(details: BirthDetails, today: LocalDate): Forecast {
        val body = api.getJson("/api/chart/forecast", TimingQueries.forecast(details, today))
        return withContext(Dispatchers.Default) { TimingParser.parseForecast(body) }
    }

    /** The current week when [weekStart] is null, else the week from that Saturday. */
    suspend fun week(details: BirthDetails, weekStart: LocalDate?): EnergyWeek {
        val body = api.getJson("/api/chart/weekly-energy", TimingQueries.weeklyEnergy(details, weekStart))
        return withContext(Dispatchers.Default) { TimingParser.parseWeek(body) }
    }

    /** The search can take a while on a long range, so it gets a longer read timeout. */
    suspend fun muhurta(details: BirthDetails, activity: MuhurtaActivity, start: LocalDate, offsetMinutes: Int): MuhurtaResult {
        val body = api.getJson(
            "/api/muhurta",
            TimingQueries.muhurta(details.place, activity, start, offsetMinutes),
            readTimeoutMs = 60_000,
        )
        return withContext(Dispatchers.Default) { TimingParser.parseMuhurta(body) }
    }

    suspend fun year(details: BirthDetails, targetYear: Int): YearChart {
        val body = api.getJson("/api/varshaphal", TimingQueries.varshaphal(details, targetYear))
        return withContext(Dispatchers.Default) { TimingParser.parseVarshaphal(body) }
    }
}
