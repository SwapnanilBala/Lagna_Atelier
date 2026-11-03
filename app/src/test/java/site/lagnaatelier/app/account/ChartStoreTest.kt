package site.lagnaatelier.app.account

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ChartStorage
import site.lagnaatelier.app.data.ChartStore
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.data.SavedChart
import site.lagnaatelier.app.data.SavedChartRules
import site.lagnaatelier.app.data.SavedChartsCodec
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.data.TimeAccuracy
import java.time.LocalDate

class ChartStoreTest {
    private val delhi = Place("New Delhi", "Delhi", "India", 28.6139, 77.209, "Asia/Kolkata", 330)
    private val brooklyn = Place("Brooklyn", "New York", "United States", 40.6782, -73.9442, "America/New_York", -240)

    private fun details(name: String, accuracy: TimeAccuracy = TimeAccuracy.Exact, place: Place = delhi) = BirthDetails(
        name = name,
        date = LocalDate.of(1990, 6, 15),
        exactTime = if (accuracy == TimeAccuracy.Exact) "14:30" else null,
        accuracy = accuracy,
        place = place,
    )

    private fun saved(name: String, at: Long, accuracy: TimeAccuracy = TimeAccuracy.Exact) = SavedChart(
        details = details(name, accuracy),
        ascendant = Sign.Virgo,
        savedAtMillis = at,
        ascendantDegree = 28.7507,
        moonSign = Sign.Aquarius,
        moonDegree = 19.9667,
        tradition = "Lahiri Classic",
        lastOpenedMillis = at,
    )

    private class MemoryStorage(var value: String? = null) : ChartStorage {
        var writes = 0
        override fun read() = value
        override fun write(value: String) {
            this.value = value
            writes++
        }
    }

    /* A store whose clock the test moves, loading on the calling thread. */
    private fun store(storage: MemoryStorage, clock: LongArray) =
        ChartStore(storage, clock = { clock[0]++ }, runLoad = { it() })

    // --- JSON round trip -------------------------------------------------

    @Test
    fun roundTripsEveryTimeAccuracy() {
        val charts = TimeAccuracy.entries.mapIndexed { index, accuracy -> saved("Person $index", 1000L + index, accuracy) }
        val decoded = SavedChartsCodec.decode(SavedChartsCodec.encode(charts))
        assertEquals(charts, decoded)
        assertEquals(TimeAccuracy.entries, decoded.map { it.details.accuracy })
    }

    @Test
    fun roundTripsUnicodeNamesAndPlaces() {
        val names = listOf("अनन्या मेहरा", "অনন্যা রায়", "Zoë O’Brien-Łukasz 李小龍", "Maya 🌙", "\"Quoted\" \\ back\nslash")
        val place = Place("São Paulo", "São Paulo", "Brasil", -23.5505, -46.6333, "America/Sao_Paulo", -180)
        val charts = names.mapIndexed { index, name ->
            saved(name, index.toLong()).copy(details = details(name, place = place))
        }.take(ChartStore.MAX_SAVED)
        assertEquals(charts, SavedChartsCodec.decode(SavedChartsCodec.encode(charts)))
    }

    @Test
    fun roundTripsEveryFieldOfTheDetails() {
        val original = SavedChart(
            details = BirthDetails("Jordan Lee", LocalDate.of(1989, 8, 2), "06:05", TimeAccuracy.Exact, brooklyn, "krishnamurti_placidus"),
            ascendant = Sign.Virgo,
            savedAtMillis = 1_793_000_000_000L,
            ascendantDegree = 19.99,
            moonSign = Sign.Pisces,
            moonDegree = 0.0,
            tradition = "Krishnamurti Placidus",
            lastOpenedMillis = 1_793_000_500_000L,
        )
        val decoded = SavedChartsCodec.decodeChart(JSONObject(SavedChartsCodec.encodeChart(original).toString()))
        assertEquals(original, decoded)
        assertEquals(original.details.chartQuery(), decoded!!.details.chartQuery())
    }

    @Test
    fun keepsALeftoverExactTimeOnAnEstimate() {
        val original = saved("Maya", 1).copy(details = details("Maya", TimeAccuracy.Evening).copy(exactTime = "09:15"))
        assertEquals(listOf(original), SavedChartsCodec.decode(SavedChartsCodec.encode(listOf(original))))
    }

    // --- Corrupt and old data ---------------------------------------------

    @Test
    fun unreadableDocumentsAreEmpty() {
        listOf(null, "", "   ", "not json", "{", "[1,2", "42", "\"text\"", "{\"charts\":\"nope\"}", "{}", "null")
            .forEach { assertEquals("for <$it>", emptyList<SavedChart>(), SavedChartsCodec.decode(it)) }
    }

    @Test
    fun badEntriesAreDroppedAndGoodOnesKept() {
        val good = SavedChartsCodec.encodeChart(saved("Ananya Mehra", 5))
        fun broken(change: JSONObject.() -> Unit) = JSONObject(good.toString()).apply(change)
        fun brokenDetails(change: JSONObject.() -> Unit) = broken { getJSONObject("details").change() }
        fun brokenPlace(change: JSONObject.() -> Unit) = brokenDetails { getJSONObject("place").change() }

        val entries = JSONArray()
            .put(42)
            .put("a string")
            .put(JSONObject.NULL)
            .put(JSONObject())
            .put(broken { put("ascendant", "Ophiuchus") })
            .put(broken { remove("details") })
            .put(brokenDetails { put("name", "  ") })
            .put(brokenDetails { put("date", "1990-02-30") })
            .put(brokenDetails { put("accuracy", "dawn") })
            .put(brokenDetails { remove("exactTime") })
            .put(brokenDetails { put("exactTime", "25:00") })
            .put(brokenDetails { put("name", 7) })
            .put(brokenPlace { put("latitude", 123.0) })
            .put(brokenPlace { put("longitude", "77.2") })
            .put(brokenPlace { remove("timeZoneId") })
            .put(brokenPlace { put("timezoneOffsetMinutes", 330.5) })
            .put(good)
        val decoded = SavedChartsCodec.decode(JSONObject().put("version", 1).put("charts", entries).toString())
        assertEquals(listOf(saved("Ananya Mehra", 5)), decoded)
    }

    @Test
    fun olderEntriesWithoutTheNewerFieldsStillLoad() {
        val bare = SavedChartsCodec.encodeChart(saved("Maya", 9)).apply {
            remove("ascendantDegree")
            remove("moonSign")
            remove("moonDegree")
            remove("tradition")
            remove("lastOpened")
            getJSONObject("details").remove("engineId")
        }
        // A bare array, with an unknown future field, under no version.
        val decoded = SavedChartsCodec.decode(JSONArray().put(bare.put("colour", "blue")).toString()).single()
        assertEquals(Sign.Virgo, decoded.ascendant)
        assertNull(decoded.ascendantDegree)
        assertNull(decoded.moonSign)
        assertEquals("", decoded.tradition)
        assertEquals(9L, decoded.lastOpenedMillis)
        assertEquals("lahiri_classic", decoded.details.engineId)
    }

    @Test
    fun storedDuplicatesAndOverflowAreTidied() {
        val list = (1..8).map { saved("P$it", it.toLong()) } + saved("P1", 99)
        val decoded = SavedChartsCodec.decode(SavedChartsCodec.encode(list))
        assertEquals(listOf("P1", "P2", "P3", "P4", "P5"), decoded.map { it.details.name })
        assertEquals(1L, decoded.first().savedAtMillis)
    }

    @Test
    fun aStoreOverCorruptStorageStartsEmptyAndRecovers() {
        val storage = MemoryStorage("{\"charts\":[{\"details\":")
        val store = store(storage, longArrayOf(100))
        assertEquals(emptyList<SavedChart>(), store.saved)
        store.save(details("Ananya Mehra"), SampleChart.chart)
        assertEquals(1, SavedChartsCodec.decode(storage.value).size)
    }

    @Test
    fun aStoreWhoseStorageThrowsStillWorksInMemory() {
        val failing = object : ChartStorage {
            override fun read(): String = throw IllegalStateException("disk")
            override fun write(value: String) = throw IllegalStateException("full")
        }
        val store = ChartStore(failing, clock = { 1L }, runLoad = { it() })
        store.save(details("Ananya Mehra"), SampleChart.chart)
        assertEquals(1, store.saved.size)
    }

    // --- The five-chart cap and order -------------------------------------

    @Test
    fun aSixthChartReplacesTheOneOpenedLongestAgo() {
        val storage = MemoryStorage()
        val store = store(storage, longArrayOf(1))
        listOf("A", "B", "C", "D", "E").forEach { store.save(details(it), SampleChart.chart) }
        // Opening A again lifts it, so B is now the one opened longest ago.
        store.save(details("A"), SampleChart.chart)
        store.save(details("F"), SampleChart.chart)
        assertEquals(listOf("F", "A", "E", "D", "C"), store.saved.map { it.details.name })
        assertEquals(ChartStore.MAX_SAVED, store.saved.size)
    }

    @Test
    fun reSavingMovesToTheFrontAndKeepsTheFirstSavedAt() {
        val storage = MemoryStorage()
        val clock = longArrayOf(10)
        val store = store(storage, clock)
        store.save(details("A"), SampleChart.chart)
        store.save(details("B"), SampleChart.chart)
        store.save(details("C"), SampleChart.chart)
        val firstSaved = store.saved.last().savedAtMillis

        store.save(details("A"), SampleChart.chart)
        val front = store.saved.first()
        assertEquals(listOf("A", "C", "B"), store.saved.map { it.details.name })
        assertEquals(firstSaved, front.savedAtMillis)
        assertTrue(front.lastOpenedMillis > front.savedAtMillis)
        assertEquals(3, store.saved.size)
    }

    @Test
    fun theSameNameWithOtherDetailsIsAnotherChart() {
        val rules = SavedChartRules
        val a = saved("Maya", 1)
        val moved = a.copy(details = a.details.copy(place = brooklyn), savedAtMillis = 2, lastOpenedMillis = 2)
        assertEquals(2, rules.upsert(listOf(a), moved).size)
        val otherTradition = a.copy(details = a.details.copy(engineId = "raman_classic"))
        assertEquals(2, rules.upsert(listOf(a), otherTradition).size)
    }

    @Test
    fun reSavingTakesTheNewFigures() {
        val old = saved("Maya", 1).copy(ascendantDegree = 3.0, moonSign = Sign.Leo)
        val fresh = saved("Maya", 50)
        val result = SavedChartRules.upsert(listOf(old), fresh).single()
        assertEquals(28.7507, result.ascendantDegree!!, 0.0)
        assertEquals(Sign.Aquarius, result.moonSign)
        assertEquals(1L, result.savedAtMillis)
        assertEquals(50L, result.lastOpenedMillis)
    }

    @Test
    fun theListSurvivesANewStoreOverTheSameStorage() {
        val storage = MemoryStorage()
        val first = store(storage, longArrayOf(1))
        first.save(details("Ananya Mehra"), SampleChart.chart)
        first.save(details("Jordan Lee", place = brooklyn), SampleChart.chart)
        first.delete(first.saved.last())

        val second = store(storage, longArrayOf(100))
        assertEquals(first.saved, second.saved)
        assertEquals(listOf("Jordan Lee"), second.saved.map { it.details.name })
        assertEquals(SampleChart.chart.ascendant, second.saved.single().ascendant)
        assertEquals(SampleChart.chart.moonSign, second.saved.single().moonSign)
    }

    @Test
    fun savingWaitsForTheStoredListToArrive() {
        val storage = MemoryStorage(SavedChartsCodec.encode(listOf(saved("A", 1), saved("B", 2))))
        var pending: (() -> Unit)? = null
        val store = ChartStore(storage, clock = { 3L }, runLoad = { task -> pending = task })
        val loader = Thread { Thread.sleep(50); pending!!() }
        loader.start()
        // save() blocks until the load finishes, then adds to the stored list rather than replacing it.
        store.save(details("C"), SampleChart.chart)
        loader.join()
        assertEquals(listOf("C", "A", "B"), store.saved.map { it.details.name })
    }
}
