package site.lagnaatelier.app.ui.more

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.ApiProblem
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ErrorText
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.chartKey
import site.lagnaatelier.app.data.lifeareas.AskAnswer
import site.lagnaatelier.app.data.lifeareas.AskClassicsParser
import site.lagnaatelier.app.data.lifeareas.AskQuestion
import site.lagnaatelier.app.data.lifeareas.ClassicalReading
import site.lagnaatelier.app.data.lifeareas.LifeArea
import site.lagnaatelier.app.data.lifeareas.LifeAreaKey
import site.lagnaatelier.app.data.lifeareas.MoreReadingsApi
import site.lagnaatelier.app.data.vargas.Varga

/** One call's answer, from asking to answered. */
sealed interface Fetch<out T> {
    data object Idle : Fetch<Nothing>
    data object Loading : Fetch<Nothing>
    data class Ready<T>(val value: T) : Fetch<T>
    data class Failed(@param:StringRes @get:StringRes val message: Int) : Fetch<Nothing>
}

/** What the screens fetch, as an interface so the holder can be tested without the network. */
interface MoreSource {
    suspend fun lifeAreas(details: BirthDetails): List<LifeArea>
    suspend fun briefs(details: BirthDetails, domain: LifeAreaKey): Map<LifeAreaKey, String>
    suspend fun areaClassics(details: BirthDetails): Map<LifeAreaKey, ClassicalReading>
    suspend fun askAvailable(details: BirthDetails): List<AskQuestion>
    suspend fun ask(details: BirthDetails, question: AskQuestion): AskAnswer
    suspend fun askTyped(details: BirthDetails, text: String): AskAnswer
    suspend fun vargas(details: BirthDetails): List<Varga>
    suspend fun vargaNotes(vargas: List<Varga>): Map<Int, String>
}

/** The real thing: the routes in MoreReadingsApi, written in [language], and /api/chart for a chart's vargas. */
class NetworkMoreSource(private val api: LagnaApi, language: String) : MoreSource {
    private val routes = MoreReadingsApi(api, language)
    override suspend fun lifeAreas(details: BirthDetails) = routes.lifeAreas(details)
    override suspend fun briefs(details: BirthDetails, domain: LifeAreaKey) = routes.briefs(details, domain)
    override suspend fun areaClassics(details: BirthDetails) = routes.areaClassics(details)
    override suspend fun askAvailable(details: BirthDetails) = routes.askAvailable(details)
    override suspend fun ask(details: BirthDetails, question: AskQuestion) = routes.ask(details, question)
    override suspend fun askTyped(details: BirthDetails, text: String) = routes.askTyped(details, text)
    override suspend fun vargas(details: BirthDetails) = api.chart(details).vargas
    override suspend fun vargaNotes(vargas: List<Varga>) = routes.vargaNotes(vargas)
}

/*
 * Everything More readings has fetched for one chart, held for the life of
 * the process (see [MoreHolders]) so leaving a screen and coming back shows it
 * at once and never asks twice.
 *
 * The calls run in the holder's own scope, not the screen's: a written answer
 * is charged to the reader's allowance as soon as the site starts writing it,
 * so leaving the screen must not throw it away. It is there when they return.
 * Read and written on the main thread only.
 */
class MoreHolder(
    val details: BirthDetails,
    private val source: MoreSource,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    var areas by mutableStateOf<Fetch<List<LifeArea>>>(Fetch.Idle)
        private set
    var briefs by mutableStateOf<Fetch<Map<LifeAreaKey, String>>>(Fetch.Idle)
        private set
    var classics by mutableStateOf<Fetch<Map<LifeAreaKey, ClassicalReading>>>(Fetch.Idle)
        private set
    var questions by mutableStateOf<Fetch<List<AskQuestion>>>(Fetch.Idle)
        private set
    var vargas by mutableStateOf<Fetch<List<Varga>>>(Fetch.Idle)
        private set
    var vargaNotes by mutableStateOf<Fetch<Map<Int, String>>>(Fetch.Idle)
        private set

    /** Each ready-made question's answer, once asked. */
    val answers = mutableStateMapOf<AskQuestion, AskAnswer>()

    /** The question the reader last typed, as sent, and where its answer stands. */
    var typed by mutableStateOf<Pair<String, AskAnswer>?>(null)
        private set

    /** Which answer the Ask screen shows: a ready-made question, or the typed one when null. */
    var showing by mutableStateOf<AskQuestion?>(null)

    /** The life areas, then every area's brief; the classical notes alongside. */
    fun openLifeAreas(force: Boolean = false) {
        load({ areas }, { areas = it }, force, after = { list -> list.firstOrNull()?.let { loadBriefs(it.key) } }) {
            source.lifeAreas(details)
        }
        load({ classics }, { classics = it }, force) { source.areaClassics(details) }
    }

    fun retryClassics() = load({ classics }, { classics = it }, force = true) { source.areaClassics(details) }

    /* Any area's key buys all seven briefs; the first is the one the route ranks first. */
    private fun loadBriefs(domain: LifeAreaKey, force: Boolean = false) =
        load({ briefs }, { briefs = it }, force) { source.briefs(details, domain) }

    fun openAsk(force: Boolean = false) = load({ questions }, { questions = it }, force) { source.askAvailable(details) }

    /** Asks a ready-made question unless it is out or already answered; a failed one is asked again. */
    fun ask(question: AskQuestion) {
        showing = question
        when (answers[question]) {
            AskAnswer.Pending, is AskAnswer.Ready, AskAnswer.Empty -> return
            else -> Unit
        }
        answers[question] = AskAnswer.Pending
        scope.launch { answers[question] = answer { source.ask(details, question) } }
    }

    /**
     * Sends a typed question. One at a time: a second tap while one is out
     * would spend another of the day's questions. Returns whether it was sent.
     */
    fun askTyped(raw: String): Boolean {
        val text = AskClassicsParser.clean(raw)
        if (text.length < AskClassicsParser.MIN_LENGTH || typed?.second == AskAnswer.Pending) return false
        showing = null
        typed = text to AskAnswer.Pending
        scope.launch { typed = text to answer { source.askTyped(details, text) } }
        return true
    }

    /** The chart's vargas (already cast with it, or fetched for the sample), then the key ones' notes. */
    fun openVargas(cast: List<Varga>, force: Boolean = false) {
        if (cast.isNotEmpty() && vargas !is Fetch.Ready) vargas = Fetch.Ready(cast)
        load({ vargas }, { vargas = it }, force, after = { loadNotes(it) }) { source.vargas(details) }
        (vargas as? Fetch.Ready)?.let { loadNotes(it.value) }
    }

    fun retryNotes() = (vargas as? Fetch.Ready)?.let { loadNotes(it.value, force = true) }

    private fun loadNotes(list: List<Varga>, force: Boolean = false) =
        load({ vargaNotes }, { vargaNotes = it }, force) { source.vargaNotes(list) }

    private suspend fun answer(fetch: suspend () -> AskAnswer): AskAnswer = try {
        fetch()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AskAnswer.Failed(askFailure(e))
    }

    /*
     * Answered and in-flight calls are left alone; a failed one is asked
     * again on "Try again" ([force]) or when its screen opens again.
     */
    private fun <T> load(
        get: () -> Fetch<T>,
        set: (Fetch<T>) -> Unit,
        force: Boolean = false,
        after: (T) -> Unit = {},
        fetch: suspend () -> T,
    ) {
        val current = get()
        if (current is Fetch.Loading) return
        if (current is Fetch.Ready && !force) return
        set(Fetch.Loading)
        scope.launch {
            val result = try {
                Fetch.Ready(fetch())
            } catch (e: CancellationException) {
                set(Fetch.Idle)
                throw e
            } catch (e: Exception) {
                // Including a response the parser did not expect: said plainly, never crashed on.
                Fetch.Failed(ErrorText.of(e))
            }
            set(result)
            if (result is Fetch.Ready) after(result.value)
        }
    }

    companion object {
        /** A question refused for today's allowance reads as the panel's own line, not the app's general one. */
        @StringRes
        fun askFailure(error: Throwable): Int {
            val problem = (error as? LagnaApi.ApiException)?.problem
            return if (problem == ApiProblem.DailyLimit) R.string.more_ask_limited else ErrorText.of(error)
        }
    }
}

/*
 * A few charts' worth, for the life of the process, as the timing tab keeps
 * its own. Each chart in each language is a holder of its own: the written
 * answers are in the language they were asked in, so a reader who switches
 * language gets them asked again in the new one rather than shown in the old.
 * Main thread only.
 */
object MoreHolders {
    private const val KEEP = 4
    private val holders = object : LinkedHashMap<String, MoreHolder>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MoreHolder>?) = size > KEEP
    }

    /** The key a chart's holder is kept under: the reading language and the chart. */
    fun key(details: BirthDetails, language: String): String = "$language|${chartKey(details)}"

    fun get(details: BirthDetails, api: LagnaApi, language: String): MoreHolder =
        holders.getOrPut(key(details, language)) { MoreHolder(details, NetworkMoreSource(api, language)) }
}
