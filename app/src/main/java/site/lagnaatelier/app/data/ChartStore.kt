package site.lagnaatelier.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred

/**
 * A chart the reader cast on this phone: enough to cast it again, plus the
 * few facts the saved list draws without the network.
 */
data class SavedChart(
    val details: BirthDetails,
    val ascendant: Sign,
    val savedAtMillis: Long,
    /** Degrees within [ascendant]. Null only for an entry stored without it. */
    val ascendantDegree: Double? = null,
    val moonSign: Sign? = null,
    val moonDegree: Double? = null,
    /** The site's name for the tradition, "Lahiri Classic". */
    val tradition: String = "",
    /** When it was last cast or opened; the list is kept in this order, newest first. */
    val lastOpenedMillis: Long = savedAtMillis,
    /**
     * The chart's id on the signed-in account (/api/sync/charts), once it is
     * there. Null for a chart kept on this phone only.
     */
    val remoteId: String? = null,
    /** On the account, but changed here since (sex at birth added, say): send it again. */
    val needsUpload: Boolean = false,
) {
    /** On the signed-in account as it stands here. */
    val synced: Boolean get() = remoteId != null && !needsUpload

    /** Which chart this is: the same birth details ask the site the same question. */
    val key: String get() = chartKey(details)
}

/** Two saves are the same chart when they would send the site the same query. */
fun chartKey(details: BirthDetails): String =
    details.chartQuery().entries.joinToString("&") { (name, value) -> "$name=$value" }

/*
 * Charts saved on this phone, the Android version of the web app's signed-out
 * chart history (lib/chart-history-store.ts, kept in the browser there). Five
 * at most; casting a sixth replaces the one opened longest ago.
 *
 * Stored as one JSON document in SharedPreferences (see [SavedChartsCodec]).
 * It is read once, on a background thread, as the store is made, and handed
 * to the main thread, where the list (Compose state) is only ever changed, so
 * the screens see it arrive. Nothing blocks on that read: a [save] or
 * [delete] made before it lands shows at once, and is applied again on top
 * of the stored list when it arrives, so a chart cast in the first moments
 * after launch is neither lost nor allowed to overwrite the list. The sync
 * waits for it with [awaitLoaded], suspending rather than blocking. Writes
 * use `apply()`, which updates memory at once and the disk shortly after;
 * Android flushes pending writes before the process is let go.
 *
 * Main thread only, as every caller is (the view model, the screens, and the
 * sync, which runs in the view model's scope).
 */
class ChartStore internal constructor(
    private val storage: ChartStorage,
    private val clock: () -> Long = System::currentTimeMillis,
    runLoad: (() -> Unit) -> Unit = { task -> Thread({ task() }, "chart-store-load").start() },
    /** Runs a task on the main thread; the tests run it in place. */
    deliver: (() -> Unit) -> Unit = { task -> Handler(Looper.getMainLooper()).post(task) },
) {
    constructor(context: Context) : this(PreferencesStorage(context))

    var saved by mutableStateOf<List<SavedChart>>(emptyList())
        private set

    /**
     * Account ids of charts deleted here that the account still has. Kept
     * with the list, so a delete made offline still reaches the account at
     * the next sync (data/account/ChartSync.kt).
     */
    var pendingDeletes by mutableStateOf<List<String>>(emptyList())
        private set

    /** Told after every change made on this phone; the sync listens. */
    @Volatile
    var onChanged: () -> Unit = {}

    /** False until the stored list has arrived; changes made before then wait in [early]. */
    var loaded by mutableStateOf(false)
        private set

    private val arrived = CompletableDeferred<Unit>()
    private val early = mutableListOf<(List<SavedChart>, List<String>) -> Pair<List<SavedChart>, List<String>>>()

    init {
        runLoad {
            val stored = try {
                SavedChartsCodec.decodeDocument(storage.read())
            } catch (e: Exception) {
                // Storage that cannot be read is treated as empty, never as a crash.
                SavedChartsCodec.Document(emptyList(), emptyList())
            }
            deliver { arrive(stored) }
        }
    }

    private fun arrive(stored: SavedChartsCodec.Document) {
        val changes = early.toList()
        early.clear()
        val (charts, deletes) = changes.fold(stored.charts to stored.pendingDeletes) { (list, gone), change -> change(list, gone) }
        saved = charts
        pendingDeletes = deletes
        loaded = true
        if (changes.isNotEmpty()) persist(charts, deletes)
        arrived.complete(Unit)
    }

    /** Suspends until the stored list is in, for the sync; never blocks a thread. */
    suspend fun awaitLoaded() = arrived.await()

    /** Records a chart the site just cast; called by AppViewModel on success. */
    fun save(details: BirthDetails, chart: ChartSummary) {
        val now = clock()
        val entry = SavedChart(
            details = details,
            ascendant = chart.ascendant,
            savedAtMillis = now,
            ascendantDegree = chart.ascendantDegree,
            moonSign = chart.moonSign,
            moonDegree = chart.moonDegree,
            tradition = chart.tradition,
            lastOpenedMillis = now,
        )
        update { SavedChartRules.upsert(it, entry) }
        onChanged()
    }

    /** Deletes [entry] here, and from the account at the next sync if it is there. */
    fun delete(entry: SavedChart) {
        updateAll { list, deletes ->
            val gone = list.filter { it.key == entry.key }.mapNotNull { it.remoteId }
            list.filterNot { it.key == entry.key } to (deletes + gone).distinct()
        }
        onChanged()
    }

    /** The list and the pending deletes as they stand; call [awaitLoaded] first. */
    fun snapshot(): Pair<List<SavedChart>, List<String>> = saved to pendingDeletes

    /**
     * A change the sync works out from what the account said. [change] runs
     * on the list as it is now, so a chart cast while the sync was on the
     * network is not lost. Does not call [onChanged]: the sync is not a
     * change made here.
     */
    fun applySync(change: (List<SavedChart>, List<String>) -> Pair<List<SavedChart>, List<String>>) {
        updateAll(change)
    }

    /**
     * Signed out, or another account: every chart becomes this phone's only,
     * and deletes owed to the old account are dropped. Nothing is deleted.
     */
    fun forgetAccount() {
        updateAll { list, _ -> list.map { it.copy(remoteId = null, needsUpload = false) } to emptyList() }
    }

    private fun update(change: (List<SavedChart>) -> List<SavedChart>) {
        updateAll { list, deletes -> change(list) to deletes }
    }

    private fun updateAll(change: (List<SavedChart>, List<String>) -> Pair<List<SavedChart>, List<String>>) {
        val (next, deletes) = change(saved, pendingDeletes)
        saved = next
        pendingDeletes = deletes
        /* Before the stored list is in, writing would overwrite it: the change waits and is replayed. */
        if (loaded) persist(next, deletes) else early += change
    }

    private fun persist(charts: List<SavedChart>, deletes: List<String>) {
        try {
            storage.write(SavedChartsCodec.encode(charts, deletes))
        } catch (e: Exception) {
            // A full disk loses the write, not the list on screen.
        }
    }

    companion object {
        const val MAX_SAVED = 5
    }
}

/** Where the store's one document lives; an interface so the tests can hold it in memory. */
internal interface ChartStorage {
    fun read(): String?
    fun write(value: String)
}

private class PreferencesStorage(context: Context) : ChartStorage {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(): String? = prefs.getString(KEY, null)

    override fun write(value: String) {
        prefs.edit().putString(KEY, value).apply()
    }

    companion object {
        const val FILE = "saved_charts"
        const val KEY = "charts"
    }
}

/** The list's rules, apart from storage so they can be tested on their own. */
internal object SavedChartRules {
    /**
     * Puts [entry] at the front. The same chart saved again keeps its first
     * savedAt and takes the new figures and lastOpened. When the list is full,
     * the one at the back -- opened longest ago -- makes room.
     */
    fun upsert(current: List<SavedChart>, entry: SavedChart, max: Int = ChartStore.MAX_SAVED): List<SavedChart> {
        val previous = current.firstOrNull { it.key == entry.key }
        /* The account copy is kept; if the details changed beyond the chart's
           key (sex at birth), it is sent again. */
        val merged = if (previous != null) {
            entry.copy(
                savedAtMillis = previous.savedAtMillis,
                remoteId = previous.remoteId,
                needsUpload = previous.remoteId != null && (previous.needsUpload || previous.details != entry.details),
            )
        } else {
            entry
        }
        val rest = current.filterNot { it.key == entry.key }
        return (listOf(merged) + rest).take(max)
    }

    /** One entry per chart, newest first as stored, at most [max]. */
    fun tidy(list: List<SavedChart>, max: Int = ChartStore.MAX_SAVED): List<SavedChart> =
        list.distinctBy { it.key }.take(max)
}

/*
 * The stored document:
 *
 *   {"version":1,"charts":[{
 *     "details":{"name":"Ananya Mehra","date":"1990-06-15","exactTime":"14:30",
 *                "accuracy":"exact","engineId":"lahiri_classic",
 *                "place":{"city":"New Delhi","state":"Delhi","country":"India",
 *                         "latitude":28.6139,"longitude":77.209,
 *                         "timeZoneId":"Asia/Kolkata","timezoneOffsetMinutes":330}},
 *     "ascendant":"Virgo","ascendantDegree":28.7507,"moonSign":"Aquarius","moonDegree":19.97,
 *     "tradition":"Lahiri Classic","savedAt":1793000000000,"lastOpened":1793000000000,
 *     "remoteId":"0b6c2f4e-...","needsUpload":true}],
 *    "pendingDeletes":["5d1e..."]}
 *
 * details.sex ("female"/"male") is left out when not given. remoteId,
 * needsUpload and pendingDeletes are the account sync's bookkeeping
 * (data/account/ChartSync.kt); a document without them is a phone-only list.
 *
 * Signs are stored by enum name and accuracy by its API value. Decoding drops
 * any entry it cannot cast again (no name, date, place or a usable time) and
 * keeps the rest; a document it cannot read at all is an empty list.
 */
internal object SavedChartsCodec {
    const val VERSION = 1

    /** The whole stored document: the charts, and deletes still owed to the account. */
    data class Document(val charts: List<SavedChart>, val pendingDeletes: List<String>)

    fun encode(charts: List<SavedChart>, pendingDeletes: List<String> = emptyList()): String = JSONObject()
        .put("version", VERSION)
        .put("charts", JSONArray().apply { charts.forEach { put(encodeChart(it)) } })
        .apply { if (pendingDeletes.isNotEmpty()) put("pendingDeletes", JSONArray(pendingDeletes)) }
        .toString()

    fun decode(raw: String?): List<SavedChart> = decodeDocument(raw).charts

    fun decodeDocument(raw: String?): Document {
        if (raw.isNullOrBlank()) return Document(emptyList(), emptyList())
        val trimmed = raw.trim()
        val root = if (trimmed.startsWith("{")) runCatching { JSONObject(trimmed) }.getOrNull() else null
        val deletes = root?.optJSONArray("pendingDeletes")?.let { array ->
            (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.takeIf(REMOTE_ID::matches) }.distinct()
        }.orEmpty()
        return Document(decodeCharts(trimmed, root), deletes)
    }

    private fun decodeCharts(trimmed: String, root: JSONObject?): List<SavedChart> {
        val array = try {
            // A bare array is accepted too, should an early build have written one.
            if (trimmed.startsWith("[")) JSONArray(trimmed) else root?.optJSONArray("charts")
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val charts = (0 until array.length()).mapNotNull { index ->
            try {
                (array.opt(index) as? JSONObject)?.let(::decodeChart)
            } catch (e: Exception) {
                null
            }
        }
        return SavedChartRules.tidy(charts)
    }

    fun encodeChart(chart: SavedChart): JSONObject = JSONObject().apply {
        put("details", encodeDetails(chart.details))
        put("ascendant", chart.ascendant.name)
        chart.ascendantDegree?.takeIf { it.isFinite() }?.let { put("ascendantDegree", it) }
        chart.moonSign?.let { put("moonSign", it.name) }
        chart.moonDegree?.takeIf { it.isFinite() }?.let { put("moonDegree", it) }
        put("tradition", chart.tradition)
        put("savedAt", chart.savedAtMillis)
        put("lastOpened", chart.lastOpenedMillis)
        chart.remoteId?.let { put("remoteId", it) }
        if (chart.needsUpload) put("needsUpload", true)
    }

    fun decodeChart(json: JSONObject): SavedChart? {
        val details = json.optJSONObject("details")?.let(::decodeDetails) ?: return null
        val ascendant = json.string("ascendant")?.let(::sign) ?: return null
        val savedAt = json.long("savedAt") ?: 0L
        val remoteId = json.string("remoteId")?.takeIf(REMOTE_ID::matches)
        return SavedChart(
            details = details,
            ascendant = ascendant,
            savedAtMillis = savedAt,
            ascendantDegree = json.double("ascendantDegree")?.takeIf { it in 0.0..30.0 },
            moonSign = json.string("moonSign")?.let(::sign),
            moonDegree = json.double("moonDegree")?.takeIf { it in 0.0..30.0 },
            tradition = json.string("tradition").orEmpty(),
            lastOpenedMillis = json.long("lastOpened") ?: savedAt,
            remoteId = remoteId,
            needsUpload = json.opt("needsUpload") == true && remoteId != null,
        )
    }

    fun encodeDetails(details: BirthDetails): JSONObject = JSONObject().apply {
        put("name", details.name)
        put("date", details.date.toString())
        details.exactTime?.let { put("exactTime", it) }
        put("accuracy", details.accuracy.apiValue)
        put("engineId", details.engineId)
        details.sex?.let { put("sex", it.apiValue) }
        put("place", encodePlace(details.place))
    }

    fun encodePlace(place: Place): JSONObject = JSONObject().apply {
        put("city", place.city)
        put("state", place.state)
        put("country", place.country)
        put("latitude", place.latitude)
        put("longitude", place.longitude)
        put("timeZoneId", place.timeZoneId)
        put("timezoneOffsetMinutes", place.timezoneOffsetMinutes)
    }

    fun decodeDetails(json: JSONObject): BirthDetails? {
        val name = json.string("name")?.takeIf { it.isNotBlank() } ?: return null
        val date = json.string("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val accuracy = json.string("accuracy")?.let { value -> TimeAccuracy.entries.firstOrNull { it.apiValue == value } }
            ?: return null
        val exactTime = json.string("exactTime")?.takeIf { TIME.matches(it) }
        if (accuracy == TimeAccuracy.Exact && exactTime == null) return null
        val place = json.optJSONObject("place")?.let(::decodePlace) ?: return null
        val engineId = json.string("engineId")?.takeIf { it.isNotBlank() } ?: Traditions.DEFAULT_ENGINE_ID
        val sex = BirthSex.from(json.string("sex"))
        return BirthDetails(name, date, exactTime, accuracy, place, engineId, sex)
    }

    fun decodePlace(json: JSONObject): Place? {
        val latitude = json.double("latitude")?.takeIf { it in -90.0..90.0 } ?: return null
        val longitude = json.double("longitude")?.takeIf { it in -180.0..180.0 } ?: return null
        val timeZoneId = json.string("timeZoneId")?.takeIf { it.isNotBlank() } ?: return null
        val offset = json.long("timezoneOffsetMinutes")?.takeIf { it in -16L * 60..16L * 60 } ?: return null
        return Place(
            city = json.string("city").orEmpty(),
            state = json.string("state").orEmpty(),
            country = json.string("country").orEmpty(),
            latitude = latitude,
            longitude = longitude,
            timeZoneId = timeZoneId,
            timezoneOffsetMinutes = offset.toInt(),
        )
    }

    private val TIME = Regex("""([01]\d|2[0-3]):[0-5]\d""")

    /** A chart id on the account: a uuid, as /api/sync/charts hands it out. */
    val REMOTE_ID = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")

    private fun sign(name: String): Sign? = Sign.entries.firstOrNull { it.name == name }

    /* Typed reads: a value of the wrong kind is treated as missing, not coerced. */
    private fun JSONObject.string(name: String): String? = opt(name) as? String

    private fun JSONObject.double(name: String): Double? =
        (opt(name) as? Number)?.toDouble()?.takeIf { it.isFinite() }

    private fun JSONObject.long(name: String): Long? = (opt(name) as? Number)?.let {
        val value = it.toDouble()
        if (value.isFinite() && value == Math.floor(value)) it.toLong() else null
    }
}
