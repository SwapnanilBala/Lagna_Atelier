package site.lagnaatelier.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ChartStore
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.TodaySky
import site.lagnaatelier.app.data.Traditions
import java.io.IOException
import java.time.LocalDate

/** What the reading screen is showing. */
sealed interface ChartState {
    data object Sample : ChartState
    data class Loading(val details: BirthDetails) : ChartState
    data class Loaded(val chart: ChartSummary, val details: BirthDetails) : ChartState
    data class Failed(val details: BirthDetails, val message: String) : ChartState
}

/** Where the place field is, from typing to a place on the map. */
enum class PlaceStatus { Idle, Searching, Locating, Located, NotFound, Failed }

/*
 * The app's state, held across rotation. Compose state throughout, so the
 * screens read it directly.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    /* Shared with the feature screens (timing, palm, report) for their own calls. */
    val api = LagnaApi()

    /* Charts saved on this phone; see data/ChartStore.kt. */
    val store = ChartStore(application)

    var today by mutableStateOf<TodaySky?>(null)
        private set
    var todayUnavailable by mutableStateOf(false)
        private set

    var chartState by mutableStateOf<ChartState>(ChartState.Sample)
        private set

    val intake = IntakeDraft()

    init {
        loadToday()
    }

    fun loadToday() {
        todayUnavailable = false
        viewModelScope.launch {
            try {
                today = api.todaySky()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                todayUnavailable = true
            }
        }
    }

    fun showSample() {
        chartState = ChartState.Sample
    }

    fun castChart(details: BirthDetails) {
        chartState = ChartState.Loading(details)
        viewModelScope.launch {
            chartState = try {
                api.chart(details).let { chart ->
                    store.save(details, chart)
                    ChartState.Loaded(chart, details)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LagnaApi.ApiException) {
                ChartState.Failed(details, e.message ?: "")
            } catch (e: IOException) {
                ChartState.Failed(details, "")
            } catch (e: Exception) {
                // A response the parser did not expect: say so rather than crash.
                ChartState.Failed(details, e.message ?: "")
            }
        }
    }

    fun retry() {
        val details = when (val state = chartState) {
            is ChartState.Failed -> state.details
            is ChartState.Loaded -> state.details
            else -> return
        }
        castChart(details)
    }

    /*
     * The form. Typing in the place field searches after a short pause, the
     * way the web form debounces; choosing a suggestion asks the site where it
     * is and which time zone it keeps.
     */
    inner class IntakeDraft {
        var name by mutableStateOf("")
        var date by mutableStateOf<LocalDate?>(null)
        var exactTime by mutableStateOf<String?>(null)
        var knowsTime by mutableStateOf(true)
        var estimate by mutableStateOf(TimeAccuracy.Unknown)
        var ayanamsha by mutableStateOf(Traditions.Ayanamsha.Lahiri)
        var houses by mutableStateOf(Traditions.HouseSystem.WholeSign)

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

        fun onPlaceQuery(text: String) {
            placeQuery = text
            place = null
            searchJob?.cancel()
            if (text.trim().length < 2) {
                suggestions = emptyList()
                placeStatus = PlaceStatus.Idle
                return
            }
            searchJob = viewModelScope.launch {
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

        fun choose(suggestion: LagnaApi.PlaceSuggestion) {
            searchJob?.cancel()
            placeQuery = suggestion.displayName
            suggestions = emptyList()
            placeStatus = PlaceStatus.Locating
            searchJob = viewModelScope.launch {
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

        /** What still stands between the form and a chart, first gap first. */
        val missing: Missing?
            get() = when {
                name.trim().length < 2 -> Missing.Name
                date == null -> Missing.Date
                knowsTime && exactTime == null -> Missing.Time
                place == null -> Missing.Place
                else -> null
            }

        fun details(): BirthDetails? {
            if (missing != null) return null
            return BirthDetails(
                name = name.trim(),
                date = date!!,
                exactTime = exactTime,
                accuracy = accuracy,
                place = place!!,
                engineId = Traditions.engineId(ayanamsha, houses),
            )
        }
    }

    enum class Missing { Name, Date, Time, Place }
}
