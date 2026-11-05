package site.lagnaatelier.app.ui.timing

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import site.lagnaatelier.app.PlaceStatus
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.ApiProblem
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ErrorText
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.periods.ClassicalReading
import site.lagnaatelier.app.data.periods.CompatibilityResult
import site.lagnaatelier.app.data.periods.CurrentPeriod
import site.lagnaatelier.app.data.periods.DashaReading
import site.lagnaatelier.app.data.periods.LifeShift
import site.lagnaatelier.app.data.periods.LifeShifts
import site.lagnaatelier.app.data.periods.NatalChart
import site.lagnaatelier.app.data.periods.PeriodChain
import site.lagnaatelier.app.data.periods.PeriodsApi
import java.time.LocalDate

/** Where a written reading stands, from asked to answered. */
sealed interface Written<out T> {
    data object Writing : Written<Nothing>
    data class Ready<T>(val value: T) : Written<T>

    /** The books have nothing for this period's planets: an answer, nothing to retry. */
    data object Empty : Written<Nothing>

    /** [retry] is false for today's allowance, which a second press cannot get past. */
    data class Failed(@param:StringRes @get:StringRes val message: Int, val retry: Boolean) : Written<Nothing>
}

/** Which screen the Timing tab shows: its own panels, or one of the two it leads on to. */
enum class TimingPage { Panels, Chapters, Compatibility }

/*
 * The written readings, the life chapters and compatibility for one chart on
 * one day, kept with the rest of the tab's state in its TimingHolder so going
 * back and forth never asks twice. Every reading here is written by a language
 * model against the reader's daily allowance:
 *
 *   the current-period paragraph is asked for once, when the tab first shows,
 *   as the web's card does on mount, and failing it is quiet (the card is
 *   complete without it);
 *   a period's reading only on "Read this period", one period at a time;
 *   the life chapters' readings once, when that screen is opened.
 *
 * Those calls run in the holder's own scope rather than a screen's, so a
 * reading already asked for (and counted against the allowance) still lands
 * when the reader switches tabs while it is written.
 *
 * Every reading is asked for in [language], and a holder is kept per language
 * (TimingHolders), so a switch of language asks again rather than showing the
 * old language's text.
 *
 * Read and written from the main thread only.
 */
class PeriodsHolder(val details: BirthDetails, val today: LocalDate, val language: String = "en") {
    private val scope = MainScope()

    var page by mutableStateOf(TimingPage.Panels)

    var natal by mutableStateOf<Load<NatalChart>>(Load.Idle)
        private set

    /** Ready(null) when the chart cannot supply the facts, or the route answered without a paragraph. */
    var currentPeriod by mutableStateOf<Written<String?>?>(null)
        private set

    val periodReadings = mutableStateMapOf<String, Written<ClassicalReading>>()

    /** The chapters as the web's engine computes them now; null until the natal facts are in. */
    var chapters by mutableStateOf<List<LifeShift>?>(null)
        private set
    val chapterReadings = mutableStateMapOf<String, ClassicalReading>()
    var chaptersWritten by mutableStateOf<Written<Unit>?>(null)
        private set
    var showPastChapters by mutableStateOf(false)

    val partner = PartnerDraft()
    var compatibility by mutableStateOf<Load<CompatibilityResult>>(Load.Idle)
        private set
    var compatTab by mutableStateOf(0)

    /* Callers that arrive while the chart is being fetched wait for that fetch rather than starting another. */
    private var natalWait: CompletableDeferred<NatalChart?>? = null

    /** The natal facts, fetched once; null when they could not be had. */
    suspend fun loadNatal(api: PeriodsApi, force: Boolean = false): NatalChart? {
        (natal as? Load.Ready)?.let { if (!force) return it.value }
        natalWait?.let { return it.await() }
        val wait = CompletableDeferred<NatalChart?>()
        natalWait = wait
        natal = Load.Loading
        return try {
            api.natal(details).also {
                natal = Load.Ready(it)
                chapters = LifeShifts.compute(it)
                wait.complete(it)
            }
        } catch (e: CancellationException) {
            natal = Load.Idle
            wait.complete(null)
            throw e
        } catch (e: Exception) {
            val message = ErrorText.of(e)
            natal = Load.Failed(message, offline = message == R.string.error_offline)
            wait.complete(null)
            null
        } finally {
            natalWait = null
        }
    }

    /** The paragraph on the periods running now: once per chart and day, and quiet on every failure. */
    fun startCurrentPeriod(api: PeriodsApi) {
        if (currentPeriod != null) return
        currentPeriod = Written.Writing
        scope.launch { loadCurrentPeriod(api) }
    }

    private suspend fun loadCurrentPeriod(api: PeriodsApi) {
        val natal = loadNatal(api)
        /* No chart, no paragraph: asked again next time the tab shows. */
        val running = natal?.stack?.getOrNull(1)
        if (natal == null || running == null) {
            currentPeriod = null
            return
        }
        val body = CurrentPeriod.requestBody(natal, CurrentPeriod.progressPercent(running, today), language)
        currentPeriod = if (body == null) {
            Written.Ready(null)
        } else {
            try {
                Written.Ready(api.currentPeriod(body))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }

    fun periodReading(chain: PeriodChain): Written<ClassicalReading>? = periodReadings[chain.key]

    /** "Read this period". An answered or running one is not asked again; a failed one is. */
    fun readPeriod(api: PeriodsApi, chain: PeriodChain) {
        when (periodReadings[chain.key]) {
            Written.Writing, is Written.Ready, Written.Empty -> return
            else -> Unit
        }
        periodReadings[chain.key] = Written.Writing
        scope.launch {
            periodReadings[chain.key] = try {
                when (val answer = api.dashaReading(details, chain, language)) {
                    is DashaReading.Answer.Written -> Written.Ready(answer.reading)
                    DashaReading.Answer.NothingInTheBooks -> Written.Empty
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }

    /**
     * The chapters shown on the life chapters screen, asked for once in the
     * order the page shows them; each batch the stream delivers shows at once.
     * [force] is "Try again", after a failure.
     */
    fun startChapters(api: PeriodsApi, force: Boolean = false) {
        when (chaptersWritten) {
            Written.Writing -> return
            null -> Unit
            else -> if (!force) return
        }
        chaptersWritten = Written.Writing
        scope.launch { loadChapters(api) }
    }

    private suspend fun loadChapters(api: PeriodsApi) {
        if (loadNatal(api) == null) {
            /* The screen says the chart could not be had and offers its own retry. */
            chaptersWritten = null
            return
        }
        val shown = LifeShifts.page(chapters.orEmpty()).shown
        if (shown.isEmpty()) {
            chaptersWritten = Written.Empty
            return
        }
        chaptersWritten = try {
            val readings = api.lifeShifts(details, shown.map { it.id }, language) { batch -> chapterReadings.putAll(batch) }
            chapterReadings.putAll(readings)
            Written.Ready(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(e)
        }
    }

    fun compare(api: PeriodsApi) {
        val partnerDetails = partner.details(details.engineId) ?: return
        if (compatibility is Load.Loading) return
        compatibility = Load.Loading
        scope.launch {
            compatibility = try {
                Load.Ready(api.compatibility(details, partnerDetails))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = ErrorText.of(e)
                Load.Failed(message, offline = message == R.string.error_offline)
            }
        }
    }

    /* A spent allowance is said as such and offers no retry; anything else can be tried again. */
    private fun failed(e: Exception): Written.Failed {
        val problem = (e as? LagnaApi.ApiException)?.problem
        val spent = problem == ApiProblem.DailyLimit || problem == ApiProblem.FullToday
        return Written.Failed(ErrorText.of(e), retry = !spent)
    }
}

/*
 * The partner's birth details, as the intake form collects them: a name, a
 * date, the exact time or the part of the day, and a place found through
 * /api/suggest and placed by /api/geocode. The searches run in the screen's
 * scope, so leaving the screen cancels them.
 */
class PartnerDraft {
    var name by mutableStateOf("")
    var date by mutableStateOf<LocalDate?>(null)
    var exactTime by mutableStateOf<String?>(null)
    var knowsTime by mutableStateOf(true)
    var estimate by mutableStateOf(TimeAccuracy.Unknown)

    var placeQuery by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<LagnaApi.PlaceSuggestion>>(emptyList())
        private set
    var place by mutableStateOf<Place?>(null)
        private set
    var placeStatus by mutableStateOf(PlaceStatus.Idle)
        private set

    private var searchJob: Job? = null

    val accuracy: TimeAccuracy get() = if (knowsTime) TimeAccuracy.Exact else estimate

    enum class Missing { Name, Date, Time, Place }

    val missing: Missing?
        get() = when {
            name.trim().length < 2 -> Missing.Name
            date == null -> Missing.Date
            knowsTime && exactTime == null -> Missing.Time
            place == null -> Missing.Place
            else -> null
        }

    fun onPlaceQuery(text: String, scope: CoroutineScope, api: LagnaApi) {
        placeQuery = text
        place = null
        searchJob?.cancel()
        if (text.trim().length < 2) {
            suggestions = emptyList()
            placeStatus = PlaceStatus.Idle
            return
        }
        searchJob = scope.launch {
            delay(250)
            placeStatus = PlaceStatus.Searching
            suggestions = try {
                api.suggest(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                placeStatus = PlaceStatus.Failed
                return@launch
            }
            placeStatus = if (suggestions.isEmpty()) PlaceStatus.NotFound else PlaceStatus.Idle
        }
    }

    fun choose(suggestion: LagnaApi.PlaceSuggestion, scope: CoroutineScope, api: LagnaApi) {
        searchJob?.cancel()
        placeQuery = suggestion.displayName
        suggestions = emptyList()
        placeStatus = PlaceStatus.Locating
        searchJob = scope.launch {
            place = try {
                api.geocode(suggestion, date, if (accuracy == TimeAccuracy.Exact) exactTime else accuracy.fallbackTime)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                placeStatus = PlaceStatus.Failed
                return@launch
            }
            placeStatus = if (place != null) PlaceStatus.Located else PlaceStatus.NotFound
        }
    }

    /** The partner's details, cast in the reader's own tradition so the two charts compare like with like. */
    fun details(engineId: String): BirthDetails? {
        if (missing != null) return null
        return BirthDetails(
            name = name.trim(),
            date = date!!,
            exactTime = exactTime,
            accuracy = accuracy,
            place = place!!,
            engineId = engineId,
        )
    }
}
