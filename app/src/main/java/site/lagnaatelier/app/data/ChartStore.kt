package site.lagnaatelier.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.CountDownLatch

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
) {
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
 * It is read once, on a background thread, as the store is made; [save] and
 * [delete] wait for that read, so a chart cast in the first moments after
 * launch cannot be overwritten by the stored list arriving late. Writes use
 * `apply()`, which updates memory at once and the disk shortly after; Android
 * flushes pending writes before the process is let go.
 */
class ChartStore internal constructor(
    private val storage: ChartStorage,
    private val clock: () -> Long = System::currentTimeMillis,
    runLoad: (() -> Unit) -> Unit = { task -> Thread({ task() }, "chart-store-load").start() },
) {
    constructor(context: Context) : this(PreferencesStorage(context))

    var saved by mutableStateOf<List<SavedChart>>(emptyList())
        private set

    private val lock = Any()
    private val loaded = CountDownLatch(1)

    init {
        runLoad {
            val stored = try {
                SavedChartsCodec.decode(storage.read())
            } catch (e: Exception) {
                // Storage that cannot be read is treated as empty, never as a crash.
                emptyList()
            }
            synchronized(lock) { saved = stored }
            loaded.countDown()
        }
    }

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
    }

    fun delete(entry: SavedChart) {
        update { list -> list.filterNot { it.key == entry.key } }
    }

    private fun update(change: (List<SavedChart>) -> List<SavedChart>) {
        loaded.await()
        synchronized(lock) {
            val next = change(saved)
            saved = next
            try {
                storage.write(SavedChartsCodec.encode(next))
            } catch (e: Exception) {
                // A full disk loses the write, not the list on screen.
            }
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
        val merged = if (previous != null) entry.copy(savedAtMillis = previous.savedAtMillis) else entry
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
 *     "tradition":"Lahiri Classic","savedAt":1793000000000,"lastOpened":1793000000000}]}
 *
 * Signs are stored by enum name and accuracy by its API value. Decoding drops
 * any entry it cannot cast again (no name, date, place or a usable time) and
 * keeps the rest; a document it cannot read at all is an empty list.
 */
internal object SavedChartsCodec {
    const val VERSION = 1

    fun encode(charts: List<SavedChart>): String = JSONObject()
        .put("version", VERSION)
        .put("charts", JSONArray().apply { charts.forEach { put(encodeChart(it)) } })
        .toString()

    fun decode(raw: String?): List<SavedChart> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = try {
            val trimmed = raw.trim()
            // A bare array is accepted too, should an early build have written one.
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("charts")
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
    }

    fun decodeChart(json: JSONObject): SavedChart? {
        val details = json.optJSONObject("details")?.let(::decodeDetails) ?: return null
        val ascendant = json.string("ascendant")?.let(::sign) ?: return null
        val savedAt = json.long("savedAt") ?: 0L
        return SavedChart(
            details = details,
            ascendant = ascendant,
            savedAtMillis = savedAt,
            ascendantDegree = json.double("ascendantDegree")?.takeIf { it in 0.0..30.0 },
            moonSign = json.string("moonSign")?.let(::sign),
            moonDegree = json.double("moonDegree")?.takeIf { it in 0.0..30.0 },
            tradition = json.string("tradition").orEmpty(),
            lastOpenedMillis = json.long("lastOpened") ?: savedAt,
        )
    }

    fun encodeDetails(details: BirthDetails): JSONObject = JSONObject().apply {
        put("name", details.name)
        put("date", details.date.toString())
        details.exactTime?.let { put("exactTime", it) }
        put("accuracy", details.accuracy.apiValue)
        put("engineId", details.engineId)
        put("place", JSONObject().apply {
            val place = details.place
            put("city", place.city)
            put("state", place.state)
            put("country", place.country)
            put("latitude", place.latitude)
            put("longitude", place.longitude)
            put("timeZoneId", place.timeZoneId)
            put("timezoneOffsetMinutes", place.timezoneOffsetMinutes)
        })
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
        return BirthDetails(name, date, exactTime, accuracy, place, engineId)
    }

    private fun decodePlace(json: JSONObject): Place? {
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
