package site.lagnaatelier.app.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.BirthSex
import site.lagnaatelier.app.data.ChartStorage
import site.lagnaatelier.app.data.ChartStore
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.data.SavedChart
import site.lagnaatelier.app.data.Sign
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.account.AccountApi
import site.lagnaatelier.app.data.account.AccountSession
import site.lagnaatelier.app.data.account.ChartQueryString
import site.lagnaatelier.app.data.account.ChartSync
import site.lagnaatelier.app.data.account.IdTokenResult
import site.lagnaatelier.app.data.account.IdTokenSource
import site.lagnaatelier.app.data.account.SessionVault
import site.lagnaatelier.app.data.account.SyncMerge
import java.time.LocalDate

/*
 * The merge rules on their own, then whole passes against a fake site: what
 * goes up, what comes down, deletes both ways, and the lapsed session that
 * must not read as "everything was deleted".
 */
class ChartSyncTest {
    private val delhi = Place("New Delhi", "Delhi", "India", 28.6139, 77.209, "Asia/Kolkata", 330)

    private fun details(name: String, sex: BirthSex? = null) = BirthDetails(
        name = name,
        date = LocalDate.of(1990, 6, 15),
        exactTime = "14:30",
        accuracy = TimeAccuracy.Exact,
        place = delhi,
        sex = sex,
    )

    private fun saved(name: String, at: Long, remoteId: String? = null, sex: BirthSex? = null) = SavedChart(
        details = details(name, sex),
        ascendant = Sign.Virgo,
        savedAtMillis = at,
        moonSign = Sign.Aquarius,
        tradition = "Lahiri Classic",
        lastOpenedMillis = at,
        remoteId = remoteId,
    )

    private fun id(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

    private fun remote(name: String, n: Int, at: Long = n * 1000L, sex: BirthSex? = null): Pair<AccountApi.RemoteChart, BirthDetails> {
        val d = details(name, sex)
        return AccountApi.RemoteChart(id(n), ChartQueryString.encode(d), "Virgo", at) to d
    }

    // --- The rules ---------------------------------------------------------

    @Test
    fun aChartDeletedElsewhereGoesFromHereOnlyWhenTheListIsWhole() {
        val local = listOf(saved("Gone", 1, id(1)), saved("Kept", 2, id(2)), saved("Local", 3))
        val account = listOf(remote("Kept", 2))

        assertEquals(listOf("Kept", "Local"), SyncMerge.reconcile(local, emptyList(), account, complete = true).map { it.details.name })
        /* A full page of 50 may not be the whole account: nothing is assumed gone. */
        assertEquals(3, SyncMerge.reconcile(local, emptyList(), account, complete = false).size)
    }

    @Test
    fun aChartBothSidesHaveIsMatchedByItsKeyNotKeptTwice() {
        val local = listOf(saved("Both", 1))
        val matched = SyncMerge.reconcile(local, emptyList(), listOf(remote("Both", 7)), complete = true).single()

        assertEquals(id(7), matched.remoteId)
        assertFalse(matched.needsUpload)
        assertTrue(SyncMerge.downloads(listOf(matched), emptyList(), listOf(remote("Both", 7))).isEmpty())
    }

    @Test
    fun aMatchWithDifferentSexIsSentAgain() {
        val local = listOf(saved("Both", 1, sex = BirthSex.Female))
        val matched = SyncMerge.reconcile(local, emptyList(), listOf(remote("Both", 7)), complete = true).single()
        assertTrue(matched.needsUpload)
        assertEquals(listOf(matched), SyncMerge.uploads(listOf(matched)))
    }

    @Test
    fun bringsDownOnlyWhatFitsNewestFirstAndNeverWhatWasJustDeleted() {
        val local = listOf(saved("A", 1, id(1)), saved("B", 2, id(2)), saved("C", 3))
        val account = listOf(remote("A", 1), remote("B", 2), remote("Old", 3), remote("New", 4), remote("Newest", 5), remote("Deleted", 6))

        val wanted = SyncMerge.downloads(local, pendingDeletes = listOf(id(6)), remote = account)

        assertEquals(listOf("Newest", "New"), wanted.map { it.second.name })
        assertTrue(SyncMerge.downloads(List(5) { saved("F$it", it.toLong()) }, emptyList(), account).isEmpty())
    }

    @Test
    fun whatComesDownJoinsTheListByWhenItWasOpened() {
        val local = listOf(saved("Here", 5000))
        val down = SyncMerge.savedFrom(details("There"), SampleChart.chart, remote("There", 9, at = 9000).first, now = 1)
        val merged = SyncMerge.addDownloaded(local, listOf(down, down))

        assertEquals(listOf("There", "Here"), merged.map { it.details.name })
        assertEquals(id(9), merged.first().remoteId)
        assertEquals(5, SyncMerge.addDownloaded(List(5) { saved("F$it", it.toLong()) }, listOf(down)).size)
    }

    @Test
    fun anUploadIsMarkedOnlyIfTheChartWasNotChangedMeanwhile() {
        val sent = saved("A", 1)
        assertTrue(SyncMerge.markUploaded(listOf(sent), sent, id(4)).single().synced)

        val changed = sent.copy(details = sent.details.copy(sex = BirthSex.Male))
        val marked = SyncMerge.markUploaded(listOf(changed), sent, id(4)).single()
        assertEquals(id(4), marked.remoteId)
        assertTrue(marked.needsUpload)
    }

    // --- Whole passes against a fake site -----------------------------------

    private val site = FakeSite()
    private val lagna = LagnaApi(site.baseUrl)
    private val accountApi = AccountApi(lagna)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val storage = object : ChartStorage {
        var value: String? = null
        override fun read() = value
        override fun write(value: String) {
            this.value = value
        }
    }
    private val store = ChartStore(storage, clock = { 1L }, runLoad = { it() })
    private val vault = SessionVault(MemoryVault(), XorCipher())
    private val session = AccountSession(accountApi, vault, NoTokens, "web.apps.googleusercontent.com", scope,
        onSignedOut = { store.forgetAccount() })
    private val cast = mutableListOf<String>()
    private val sync = ChartSync(store, accountApi, session, cast = { cast += it.name; SampleChart.chart },
        consentPrompt = { "Keep the charts?" }, scope = scope)

    @After
    fun close() = site.close()

    private fun settle() = runBlocking { scope.coroutineContext.job.children.forEach { it.join() } }

    private fun signedInWithSync() {
        site.on("POST /api/auth/google/android/nonce", body = """{"nonce":"${"n".repeat(43)}"}""")
        site.on("POST /api/auth/google/android", body = """{"token":"${"t".repeat(43)}","expiresAt":"2099-01-01T00:00:00Z","user":{"email":"a@example.test"}}""")
        session.signInWith { _, _ -> IdTokenResult.Token("h.p.s") }
        settle()
        session.chooseSyncCharts(true)
        settle()
        site.on("GET /api/auth/session", body = """{"user":{"email":"a@example.test"}}""")
    }

    private fun accountHas(vararg charts: Pair<AccountApi.RemoteChart, BirthDetails>) {
        val array = JSONArray()
        charts.forEach { (chart) ->
            array.put(JSONObject().put("chartId", chart.chartId).put("queryString", chart.queryString)
                .put("ascendantSign", "Virgo").put("savedAt", java.time.Instant.ofEpochMilli(chart.savedAtMillis!!).toString()))
        }
        site.on("GET /api/sync/charts", body = JSONObject().put("charts", array).put("stored", charts.size).toString())
    }

    @Test
    fun sendsUpWhatTheAccountLacksWithTheConsentWording() = runBlocking {
        signedInWithSync()
        store.applySync { _, d -> listOf(saved("Mine", 10)) to d }
        accountHas()
        site.on("POST /api/sync/charts", body = """{"chartId":"${id(42)}","created":true}""")

        assertTrue(sync.syncOnce())

        val body = JSONObject(site.callsTo("POST /api/sync/charts").single().body)
        assertEquals("Mine", ChartQueryString.decode(body.getString("queryString"))!!.name)
        assertEquals(true, body.getJSONObject("consent").get("granted"))
        assertEquals("Keep the charts?", body.getJSONObject("consent").getString("prompt"))
        assertEquals("settings", body.getJSONObject("consent").getString("captureSource"))
        assertEquals(id(42), store.saved.single().remoteId)
        assertTrue(store.saved.single().synced)
    }

    @Test
    fun bringsDownTheAccountsChartsAndCastsThem() = runBlocking {
        signedInWithSync()
        accountHas(remote("From the website", 3))

        sync.syncOnce()

        assertEquals(listOf("From the website"), cast)
        assertEquals(id(3), store.saved.single().remoteId)
        assertTrue(site.callsTo("POST /api/sync/charts").isEmpty())
    }

    @Test
    fun aDeleteHereReachesTheAccount() = runBlocking {
        signedInWithSync()
        store.applySync { _, d -> listOf(saved("Gone", 1, id(5))) to d }
        store.delete(store.saved.single())
        assertEquals(listOf(id(5)), store.pendingDeletes)
        site.on("DELETE /api/sync/charts", body = """{"revoked":1,"remaining":0}""")
        accountHas()

        sync.syncOnce()

        assertEquals("chartId=${id(5)}", site.callsTo("DELETE /api/sync/charts").single().query)
        assertTrue(store.pendingDeletes.isEmpty())
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun aDeleteOnTheAccountReachesThisPhone() = runBlocking {
        signedInWithSync()
        store.applySync { _, d -> listOf(saved("Deleted on the web", 1, id(8)), saved("Never sent", 2)) to d }
        accountHas()
        site.on("POST /api/sync/charts", body = """{"chartId":"${id(9)}","created":true}""")

        sync.syncOnce()

        assertEquals(listOf("Never sent"), store.saved.map { it.details.name })
    }

    @Test
    fun aLapsedSessionDeletesNothingAndSignsOut() = runBlocking {
        signedInWithSync()
        store.applySync { _, d -> listOf(saved("Synced", 1, id(8))) to d }
        /* The list route answers a lapsed session with an empty list; the session check comes first. */
        site.on("GET /api/auth/session", body = """{"user":null}""")
        accountHas()

        assertFalse(sync.syncOnce())
        settle()

        assertEquals(listOf("Synced"), store.saved.map { it.details.name })
        assertEquals(null, store.saved.single().remoteId) // now this phone's only
        assertEquals(AccountSession.Status.SignedOut, session.status)
        assertTrue(site.callsTo("GET /api/sync/charts").isEmpty())
    }

    @Test
    fun nothingMovesUntilTheReaderSaysYes() = runBlocking {
        signedInWithSync()
        session.chooseSyncCharts(false)
        settle()
        store.applySync { _, d -> listOf(saved("Mine", 10)) to d }

        assertFalse(sync.syncOnce())
        assertTrue(site.callsTo("GET /api/sync/charts").isEmpty())
        assertTrue(site.callsTo("POST /api/sync/charts").isEmpty())
    }

    private object NoTokens : IdTokenSource {
        override suspend fun idToken(activity: android.content.Context, serverClientId: String, nonce: String) =
            IdTokenResult.Failed

        override suspend fun forget(context: android.content.Context) = Unit
    }
}
