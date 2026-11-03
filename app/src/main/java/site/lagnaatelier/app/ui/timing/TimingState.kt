package site.lagnaatelier.app.ui.timing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.timing.EnergyWeek
import site.lagnaatelier.app.data.timing.Forecast
import site.lagnaatelier.app.data.timing.MuhurtaActivity
import site.lagnaatelier.app.data.timing.MuhurtaResult
import site.lagnaatelier.app.data.timing.TimingQueries
import site.lagnaatelier.app.data.timing.TimingRepository
import site.lagnaatelier.app.data.timing.YearChart
import java.io.IOException
import java.time.LocalDate

/** One panel's data, from asking to answered. */
sealed interface Load<out T> {
    data object Idle : Load<Nothing>
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>

    /** [message] is the site's own when it gave one; [offline] when the call never reached it. */
    data class Failed(val message: String?, val offline: Boolean) : Load<Nothing>
}

/*
 * Everything the tab has fetched for one chart on one day, and the choices
 * the reader made on it (week, activity, what is expanded). Compose state, so
 * the panels read it directly; kept in [TimingHolders] rather than in the
 * composition, so switching tabs and back does not fetch again.
 */
class TimingHolder(val details: BirthDetails, val today: LocalDate) {
    /* The place's offset now, not at birth, and its own today: muhurta times are shown on that clock. */
    val placeOffsetMinutes = TimingQueries.offsetNow(details.place)
    val placeToday: LocalDate = TimingQueries.placeToday(placeOffsetMinutes)
    val targetYear = TimingQueries.varshaphalYear(details.date, today)

    var forecast by mutableStateOf<Load<Forecast>>(Load.Idle)
        private set
    var year by mutableStateOf<Load<YearChart>>(Load.Idle)
        private set
    val weeks = mutableStateMapOf<Int, Load<EnergyWeek>>()
    val muhurta = mutableStateMapOf<MuhurtaActivity, Load<MuhurtaResult>>()

    /** 0 = this week, 1 = next. */
    var weekIndex by mutableIntStateOf(0)
    var activity by mutableStateOf(MuhurtaActivity.Anything)
    var showAllPeriods by mutableStateOf(false)
    var showAllTransits by mutableStateOf(false)
    var showAllWindows by mutableStateOf(false)

    fun week(index: Int): Load<EnergyWeek> = weeks[index] ?: Load.Idle
    fun muhurta(activity: MuhurtaActivity): Load<MuhurtaResult> = muhurta[activity] ?: Load.Idle

    suspend fun loadForecast(repo: TimingRepository, force: Boolean = false) =
        load({ forecast }, { forecast = it }, force) { repo.forecast(details, today) }

    suspend fun loadYear(repo: TimingRepository, force: Boolean = false) =
        load({ year }, { year = it }, force) { repo.year(details, targetYear) }

    suspend fun loadWeek(repo: TimingRepository, index: Int, force: Boolean = false) =
        load({ week(index) }, { weeks[index] = it }, force) {
            /* The current week goes without week_start, as the route prefers; next week names its Saturday. */
            repo.week(details, if (index == 0) null else TimingQueries.nextWeekStart(today))
        }

    suspend fun loadMuhurta(repo: TimingRepository, activity: MuhurtaActivity, force: Boolean = false) =
        load({ muhurta(activity) }, { muhurta[activity] = it }, force) {
            repo.muhurta(details, activity, placeToday, placeOffsetMinutes)
        }

    /*
     * Answered and in-flight calls are left alone; a failed one is asked again
     * when its panel comes back, or at once on "Try again". A call cancelled
     * because the panel left the screen goes back to Idle, so it starts again
     * next time rather than staying Loading for ever.
     */
    private suspend fun <T> load(get: () -> Load<T>, set: (Load<T>) -> Unit, force: Boolean, fetch: suspend () -> T) {
        val current = get()
        if (current is Load.Loading) return
        if (current is Load.Ready && !force) return
        set(Load.Loading)
        try {
            set(Load.Ready(fetch()))
        } catch (e: CancellationException) {
            set(Load.Idle)
            throw e
        } catch (e: LagnaApi.ApiException) {
            set(Load.Failed(e.message?.ifBlank { null }, offline = false))
        } catch (e: IOException) {
            set(Load.Failed(null, offline = true))
        } catch (e: Exception) {
            // A response the parser did not expect: say so rather than crash.
            set(Load.Failed(null, offline = false))
        }
    }
}

/*
 * A few charts' worth of timing, for the life of the process. Read and written
 * from the main thread only (composition and its effects).
 */
object TimingHolders {
    private const val KEEP = 4
    private val holders = object : LinkedHashMap<String, TimingHolder>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TimingHolder>?) = size > KEEP
    }

    fun get(details: BirthDetails, today: LocalDate): TimingHolder =
        holders.getOrPut("${TimingQueries.key(details)}|$today") { TimingHolder(details, today) }
}
