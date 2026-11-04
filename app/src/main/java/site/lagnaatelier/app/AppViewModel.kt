package site.lagnaatelier.app

import android.app.Application
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.BirthSex
import site.lagnaatelier.app.data.account.AccountApi
import site.lagnaatelier.app.data.account.AccountSession
import site.lagnaatelier.app.data.account.ChartSync
import site.lagnaatelier.app.data.account.GoogleIdTokens
import site.lagnaatelier.app.data.account.SessionVault
import site.lagnaatelier.app.data.ChartStore
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.ErrorText
import site.lagnaatelier.app.data.LagnaApi
import org.json.JSONObject
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.PlaceOffsets
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.data.SavedChartsCodec
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.TodaySky
import site.lagnaatelier.app.data.Traditions
import java.time.LocalDate

/** What the reading screen is showing. */
sealed interface ChartState {
    data object Sample : ChartState
    data class Loading(val details: BirthDetails) : ChartState
    data class Loaded(val chart: ChartSummary, val details: BirthDetails) : ChartState
    data class Failed(val details: BirthDetails, @param:StringRes @get:StringRes val message: Int) : ChartState
}

/** Where the place field is, from typing to a place on the map. */
enum class PlaceStatus { Idle, Searching, Locating, Located, NotFound, Failed }

/*
 * The app's state, held across rotation. Compose state throughout, so the
 * screens read it directly.
 *
 * Across the process being let go in the background it keeps, through
 * [SavedStateHandle], the chart on screen (its birth details, or "the
 * sample") and the form as it was. A chart comes back by being cast again,
 * behind the casting screen, since the chart itself is far too large for
 * the saved state.
 */
class AppViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    /* Shared with the feature screens (timing, palm, report) for their own calls. */
    val api = LagnaApi()

    /* Charts saved on this phone; see data/ChartStore.kt. */
    val store = ChartStore(application)

    /* Sign in with Google, and the saved charts kept in step with the account; see data/account. */
    private val accountApi = AccountApi(api)

    val account: AccountSession = AccountSession(
        api = accountApi,
        vault = SessionVault(application),
        tokens = GoogleIdTokens(),
        serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
        scope = viewModelScope,
        onSignedIn = { sync.request() },
        onSignedOut = { store.forgetAccount() },
    )

    val sync: ChartSync = ChartSync(
        store = store,
        api = accountApi,
        session = account,
        cast = api::chart,
        consentPrompt = { application.getString(R.string.accounts_sync_prompt) },
        scope = viewModelScope,
    )

    var today by mutableStateOf<TodaySky?>(null)
        private set
    var todayUnavailable by mutableStateOf(false)
        private set

    var chartState by mutableStateOf<ChartState>(ChartState.Sample)
        private set

    val intake = IntakeDraft()

    /* The birth details of the chart on screen; null for the sample. */
    private var shownDetails: BirthDetails? = null

    init {
        api.sessionToken = { account.token }
        api.onSessionRejected = { account.sessionRejected() }
        store.onChanged = { sync.request() }
        account.load()
        loadToday()
        val restored = savedState.get<Bundle>(STATE_KEY)
        restored?.getString(KEY_DRAFT)?.let { runCatching { intake.restore(JSONObject(it)) } }
        restored?.getString(KEY_CHART)
            ?.let { runCatching { SavedChartsCodec.decodeDetails(JSONObject(it)) }.getOrNull() }
            ?.let(::castChart)
        savedState.setSavedStateProvider(STATE_KEY) {
            Bundle().apply {
                putString(KEY_DRAFT, intake.snapshot().toString())
                shownDetails?.let { putString(KEY_CHART, SavedChartsCodec.encodeDetails(it).toString()) }
            }
        }
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
        shownDetails = null
        chartState = ChartState.Sample
    }

    fun castChart(details: BirthDetails) {
        shownDetails = details
        chartState = ChartState.Loading(details)
        viewModelScope.launch {
            chartState = try {
                api.chart(details).let { chart ->
                    store.save(details, chart)
                    ChartState.Loaded(chart, details)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Including a response the parser did not expect: said plainly, never crashed on.
                ChartState.Failed(details, ErrorText.of(e))
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

        /*
         * The date and the time decide the place's UTC offset (summer or
         * winter time, or a zone that changed its rules), so changing any of
         * them after a place was chosen works the offset out again.
         */
        private var dateValue by mutableStateOf<LocalDate?>(null)
        private var exactTimeValue by mutableStateOf<String?>(null)
        private var knowsTimeValue by mutableStateOf(true)
        private var estimateValue by mutableStateOf(TimeAccuracy.Unknown)

        var date: LocalDate?
            get() = dateValue
            set(value) {
                dateValue = value
                reoffset()
            }
        var exactTime: String?
            get() = exactTimeValue
            set(value) {
                exactTimeValue = value
                reoffset()
            }
        var knowsTime: Boolean
            get() = knowsTimeValue
            set(value) {
                knowsTimeValue = value
                reoffset()
            }
        var estimate: TimeAccuracy
            get() = estimateValue
            set(value) {
                estimateValue = value
                reoffset()
            }

        /* The suggestion the place came from, to ask the site again if the zone is not one the phone knows. */
        private var chosen: LagnaApi.PlaceSuggestion? = null

        var ayanamsha by mutableStateOf(Traditions.Ayanamsha.Lahiri)
        var houses by mutableStateOf(Traditions.HouseSystem.WholeSign)
        /** Sex at birth; null is "Prefer not to say", where the choice starts. */
        var sex by mutableStateOf<BirthSex?>(null)

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

        /* The time the chart would be cast for, as the geocode route is asked. */
        private val castTime: String? get() = if (accuracy == TimeAccuracy.Exact) exactTime else accuracy.fallbackTime

        private fun reoffset() {
            val current = place ?: return
            val minutes = PlaceOffsets.offsetMinutes(current.timeZoneId, date, castTime)
            if (minutes != null) {
                if (minutes != current.timezoneOffsetMinutes) place = current.copy(timezoneOffsetMinutes = minutes)
            } else {
                /* A zone the phone cannot read: ask the site again for the new moment. */
                chosen?.let(::choose)
            }
        }

        /** A fresh form, for "Add a chart": the last chart's details are not the next one's. */
        fun reset() {
            searchJob?.cancel()
            name = ""
            dateValue = null
            exactTimeValue = null
            knowsTimeValue = true
            estimateValue = TimeAccuracy.Unknown
            ayanamsha = Traditions.Ayanamsha.Lahiri
            houses = Traditions.HouseSystem.WholeSign
            sex = null
            placeQuery = ""
            suggestions = emptyList()
            place = null
            chosen = null
            placeStatus = PlaceStatus.Idle
        }

        fun snapshot(): JSONObject = JSONObject().apply {
            put("name", name)
            date?.let { put("date", it.toString()) }
            exactTime?.let { put("exactTime", it) }
            put("knowsTime", knowsTime)
            put("estimate", estimate.name)
            put("ayanamsha", ayanamsha.name)
            put("houses", houses.name)
            sex?.let { put("sex", it.apiValue) }
            put("placeQuery", placeQuery)
            place?.let { put("place", SavedChartsCodec.encodePlace(it)) }
            chosen?.let {
                put(
                    "chosen",
                    JSONObject().put("name", it.name).put("displayName", it.displayName).put("state", it.state).put("country", it.country),
                )
            }
        }

        fun restore(json: JSONObject) {
            name = json.optString("name").take(120)
            dateValue = (json.opt("date") as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            exactTimeValue = (json.opt("exactTime") as? String)?.takeIf { TIME.matches(it) }
            knowsTimeValue = json.optBoolean("knowsTime", true)
            estimateValue = TimeAccuracy.entries.firstOrNull { it.name == json.optString("estimate") } ?: TimeAccuracy.Unknown
            ayanamsha = Traditions.Ayanamsha.entries.firstOrNull { it.name == json.optString("ayanamsha") } ?: Traditions.Ayanamsha.Lahiri
            houses = Traditions.HouseSystem.entries.firstOrNull { it.name == json.optString("houses") } ?: Traditions.HouseSystem.WholeSign
            sex = BirthSex.from(json.opt("sex") as? String)
            placeQuery = json.optString("placeQuery").take(200)
            chosen = json.optJSONObject("chosen")?.let {
                LagnaApi.PlaceSuggestion(it.optString("name"), it.optString("displayName"), it.optString("state"), it.optString("country"))
            }
            place = json.optJSONObject("place")?.let(SavedChartsCodec::decodePlace)
            placeStatus = if (place != null) PlaceStatus.Located else PlaceStatus.Idle
        }

        fun onPlaceQuery(text: String) {
            placeQuery = text
            place = null
            chosen = null
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
            chosen = suggestion
            placeQuery = suggestion.displayName
            suggestions = emptyList()
            place = null
            placeStatus = PlaceStatus.Locating
            searchJob = viewModelScope.launch {
                place = try {
                    api.geocode(suggestion, date, castTime)
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
                sex = sex,
            )
        }
    }

    enum class Missing { Name, Date, Time, Place }

    private companion object {
        const val STATE_KEY = "app_state"
        const val KEY_DRAFT = "draft"
        const val KEY_CHART = "chart"
        val TIME = Regex("""([01]\d|2[0-3]):[0-5]\d""")
    }
}
