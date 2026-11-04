package site.lagnaatelier.app.account

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.BirthSex
import site.lagnaatelier.app.data.ChartStorage
import site.lagnaatelier.app.data.ChartStore
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.data.SavedChart
import site.lagnaatelier.app.data.SavedChartsCodec
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.account.AccountApi
import site.lagnaatelier.app.data.chartKey
import site.lagnaatelier.app.data.palm.PalmReader
import site.lagnaatelier.app.data.report.ReportApi
import site.lagnaatelier.app.data.timing.TimingQueries
import site.lagnaatelier.app.ui.palm.readerFor
import java.time.LocalDate

/*
 * What the saved list keeps for the sync (account ids, deletes owed, sex at
 * birth), the account routes' answers, and where sex at birth is sent.
 */
class SyncBookkeepingTest {
    private val delhi = Place("New Delhi", "Delhi", "India", 28.6139, 77.209, "Asia/Kolkata", 330)
    private val remoteId = "0b6c2f4e-8d1a-4c3b-9e7f-1a2b3c4d5e6f"

    private fun details(sex: BirthSex? = null) = BirthDetails(
        name = "Ananya Mehra",
        date = LocalDate.of(1990, 6, 15),
        exactTime = "14:30",
        accuracy = TimeAccuracy.Exact,
        place = delhi,
        sex = sex,
    )

    private val chart = SavedChart(details(BirthSex.Female), Sign.Virgo, 10, remoteId = remoteId, needsUpload = true)

    private class Memory(var value: String? = null) : ChartStorage {
        override fun read() = value
        override fun write(value: String) {
            this.value = value
        }
    }

    private fun store(memory: Memory) = ChartStore(memory, clock = { 100L }, runLoad = { it() }, deliver = { it() })

    @Test
    fun theStoredDocumentKeepsSexAccountIdsAndDeletesOwed() {
        val raw = SavedChartsCodec.encode(listOf(chart), listOf(remoteId))
        val back = SavedChartsCodec.decodeDocument(raw)

        assertEquals(listOf(chart), back.charts)
        assertEquals(listOf(remoteId), back.pendingDeletes)
        assertEquals("female", JSONObject(raw).getJSONArray("charts").getJSONObject(0).getJSONObject("details").getString("sex"))
    }

    @Test
    fun anOlderDocumentReadsAsPhoneOnlyAndBadIdsAreDropped() {
        val raw = SavedChartsCodec.encode(listOf(chart.copy(remoteId = null, needsUpload = false)))
        assertFalse(raw.contains("pendingDeletes"))
        assertFalse(raw.contains("remoteId"))

        val tampered = JSONObject(SavedChartsCodec.encode(listOf(chart), listOf(remoteId))).apply {
            getJSONArray("charts").getJSONObject(0).put("remoteId", "../../etc")
            put("pendingDeletes", org.json.JSONArray(listOf("1 or 1=1", remoteId)))
        }.toString()
        val back = SavedChartsCodec.decodeDocument(tampered)
        assertNull(back.charts.single().remoteId)
        assertFalse(back.charts.single().needsUpload)
        assertEquals(listOf(remoteId), back.pendingDeletes)
    }

    @Test
    fun sexAtBirthIsNotPartOfTheChartsKey() {
        assertEquals(chartKey(details()), chartKey(details(BirthSex.Male)))
        assertFalse(details(BirthSex.Male).chartQuery().containsKey("birthSex"))
        assertFalse(details(BirthSex.Male).chartQuery().containsKey("birth_sex"))
    }

    @Test
    fun deletingASyncedChartOwesTheAccountADelete() {
        val memory = Memory()
        val store = store(memory)
        var changes = 0
        store.onChanged = { changes++ }
        store.applySync { _, d -> listOf(chart, chart.copy(details = details().copy(name = "Local only"), remoteId = null)) to d }

        store.delete(store.saved[1])
        assertTrue(store.pendingDeletes.isEmpty())
        store.delete(store.saved[0])
        assertEquals(listOf(remoteId), store.pendingDeletes)
        assertEquals(2, changes)

        /* Kept across a restart. */
        assertEquals(listOf(remoteId), store(memory).pendingDeletes)
    }

    @Test
    fun castingAgainKeepsTheAccountIdAndResendsAChangedChart() {
        val store = store(Memory())
        store.applySync { _, d -> listOf(chart.copy(details = details(), needsUpload = false)) to d }

        store.save(details(), SampleChart.chart)
        assertTrue(store.saved.single().synced)

        store.save(details(BirthSex.Male), SampleChart.chart)
        assertEquals(remoteId, store.saved.single().remoteId)
        assertTrue(store.saved.single().needsUpload)
    }

    @Test
    fun forgettingTheAccountKeepsEveryChartHere() {
        val store = store(Memory())
        store.applySync { _, _ -> listOf(chart) to listOf(remoteId) }

        store.forgetAccount()

        assertEquals(1, store.saved.size)
        assertNull(store.saved.single().remoteId)
        assertTrue(store.pendingDeletes.isEmpty())
    }

    @Test
    fun readsTheAccountRoutesAnswers() {
        val session = AccountApi.parseSession(
            """{"token":"${"t".repeat(43)}","expiresAt":"2026-12-03T00:00:00.000Z","user":{"email":"a@example.test","displayName":null}}""",
        )
        assertEquals("a@example.test", session.user.email)
        assertNull(session.user.displayName)
        assertEquals(java.time.Instant.parse("2026-12-03T00:00:00Z").toEpochMilli(), session.expiresAtMillis)

        assertNull(AccountApi.parseUser("""{"user":null}"""))
        assertEquals("n".repeat(43), AccountApi.parseNonce("""{"nonce":"${"n".repeat(43)}"}"""))
        assertTrue(runCatching { AccountApi.parseNonce("""{"nonce":"short"}""") }.isFailure)

        val charts = AccountApi.parseCharts(
            """{"charts":[{"chartId":"${remoteId.uppercase()}","queryString":"name=A","savedAt":"2026-11-01T10:00:00.000Z"},
               {"chartId":"not-an-id","queryString":"name=B"},{"chartId":"$remoteId","queryString":""}],"stored":3}""",
        )
        assertEquals(listOf(remoteId), charts.map { it.chartId })
    }

    @Test
    fun sendsSexAtBirthOnlyToTheRoutesThatReadItAndOnlyWhenGiven() {
        assertNull(ReportApi.storyReportQuery(details())["birthSex"])
        assertEquals("female", ReportApi.storyReportQuery(details(BirthSex.Female))["birthSex"])
        assertEquals("male", TimingQueries.weeklyEnergy(details(BirthSex.Male))["birthSex"])
        assertNull(TimingQueries.weeklyEnergy(details())["birthSex"])
    }

    @Test
    fun presetsWhoseHandFromSexAtBirth() {
        assertEquals(PalmReader.Woman, readerFor(BirthSex.Female))
        assertEquals(PalmReader.Man, readerFor(BirthSex.Male))
        assertEquals(PalmReader.Unspecified, readerFor(null))
    }
}
