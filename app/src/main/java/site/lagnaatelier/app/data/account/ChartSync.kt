package site.lagnaatelier.app.data.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ChartStore
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.SavedChart
import site.lagnaatelier.app.data.chartKey

/*
 * Keeping the phone's saved charts and the account's in step, once signed in
 * and once the reader has said yes (the consent the website asks for too:
 * lib/sync/consent.ts there). One pass:
 *
 *   1. the session is checked first, because GET /api/sync/charts answers a
 *      lapsed session with an empty list, which would read as "every chart
 *      was deleted elsewhere"
 *   2. deletes made here go to the account (DELETE ?chartId=)
 *   3. the account's list is read
 *   4. charts deleted elsewhere go from here; a chart both sides have is
 *      matched by its key, so it is never kept twice
 *   5. the account's charts this phone lacks come down, while there is room
 *      in the five (none is pushed out for one)
 *   6. this phone's charts the account lacks go up
 *
 * Pushing a sixth chart off the phone's list is not a delete: it stays on
 * the account. Deleting it here is.
 */
class ChartSync(
    private val store: ChartStore,
    private val api: AccountApi,
    private val session: AccountSession,
    /** Casts a chart that came down, for its figures (rising sign, Moon). */
    private val cast: suspend (BirthDetails) -> ChartSummary,
    /** The consent wording shown to the reader, sent as its evidence. */
    private val consentPrompt: () -> String,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Status { Idle, Syncing, Done, Failed }

    var status by mutableStateOf(Status.Idle)
        private set

    private var running: Job? = null
    private var again = false

    val active: Boolean get() = session.signedIn && session.syncCharts

    /** Starts a pass, or queues one more after the pass under way. */
    fun request() {
        if (!active) return
        if (running?.isActive == true) {
            again = true
            return
        }
        running = scope.launch {
            do {
                again = false
                status = Status.Syncing
                status = try {
                    if (syncOnce()) Status.Done else Status.Idle
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Status.Failed
                }
            } while (again && active)
        }
    }

    /** One pass. False when it stopped early because the session had ended. */
    suspend fun syncOnce(): Boolean {
        if (!active) return false
        when (session.verify()) {
            AccountSession.Verification.Valid -> Unit
            AccountSession.Verification.SignedOut -> return false
            AccountSession.Verification.Unreachable -> throw java.io.IOException("Account unreachable")
        }

        val (_, deletes) = store.snapshot()
        for (id in deletes) {
            api.delete(id)
            store.applySync { list, pending -> list to pending - id }
        }

        val remote = api.listCharts()
        val complete = remote.size < AccountApi.LIST_LIMIT
        val decoded = remote.mapNotNull { chart -> ChartQueryString.decode(chart.queryString)?.let { chart to it } }
        store.applySync { list, pending -> SyncMerge.reconcile(list, pending, decoded, complete) to pending }

        val (afterReconcile, pendingNow) = store.snapshot()
        val wanted = SyncMerge.downloads(afterReconcile, pendingNow, decoded)
        val downloaded = wanted.mapNotNull { (chart, details) ->
            try {
                SyncMerge.savedFrom(details, cast(details), chart, clock())
            } catch (e: CancellationException) {
                throw e
            } catch (e: LagnaApi.ApiException) {
                null // a chart the site will not cast stays on the account only
            }
        }
        if (downloaded.isNotEmpty()) {
            store.applySync { list, pending -> SyncMerge.addDownloaded(list, downloaded) to pending }
        }

        for (chart in SyncMerge.uploads(store.snapshot().first)) {
            if (!active) return false
            val id = api.upload(
                ChartQueryString.encode(chart.details),
                chart.ascendant.label,
                chart.moonSign?.label,
                consentPrompt(),
            )
            store.applySync { list, pending -> SyncMerge.markUploaded(list, chart, id) to pending }
        }
        return true
    }
}

/** The pass's decisions, apart from the network so they can be tested on their own. */
internal object SyncMerge {
    /**
     * Charts the account no longer has go from here (only when [complete]:
     * a full page of the list may not be the whole account), and a chart this
     * phone has that the account also has takes the account's id.
     */
    fun reconcile(
        local: List<SavedChart>,
        pendingDeletes: List<String>,
        remote: List<Pair<AccountApi.RemoteChart, BirthDetails>>,
        complete: Boolean,
    ): List<SavedChart> {
        val remoteIds = remote.map { it.first.chartId }.toSet()
        val remoteByKey = remote.associateBy { chartKey(it.second) }
        return local.mapNotNull { chart ->
            when {
                chart.remoteId != null && chart.remoteId in remoteIds -> chart
                chart.remoteId != null -> if (complete) null else chart
                else -> remoteByKey[chart.key]?.let { (match, details) ->
                    chart.copy(remoteId = match.chartId, needsUpload = details != chart.details)
                } ?: chart
            }
        }
    }

    /** The account's charts this phone lacks and has room for, newest first. */
    fun downloads(
        local: List<SavedChart>,
        pendingDeletes: List<String>,
        remote: List<Pair<AccountApi.RemoteChart, BirthDetails>>,
        max: Int = ChartStore.MAX_SAVED,
    ): List<Pair<AccountApi.RemoteChart, BirthDetails>> {
        val room = max - local.size
        if (room <= 0) return emptyList()
        val haveIds = local.mapNotNull { it.remoteId }.toSet()
        val haveKeys = local.map { it.key }.toSet()
        return remote
            .filter { (chart, details) ->
                chart.chartId !in haveIds && chart.chartId !in pendingDeletes && chartKey(details) !in haveKeys
            }
            .distinctBy { chartKey(it.second) }
            .sortedByDescending { it.first.savedAtMillis ?: 0L }
            .take(room)
    }

    fun savedFrom(details: BirthDetails, chart: ChartSummary, remote: AccountApi.RemoteChart, now: Long): SavedChart {
        val at = remote.savedAtMillis ?: now
        return SavedChart(
            details = details,
            ascendant = chart.ascendant,
            savedAtMillis = at,
            ascendantDegree = chart.ascendantDegree,
            moonSign = chart.moonSign,
            moonDegree = chart.moonDegree,
            tradition = chart.tradition,
            lastOpenedMillis = at,
            remoteId = remote.chartId,
        )
    }

    /** Adds what came down where there is still room, keeping the list newest-opened first. */
    fun addDownloaded(local: List<SavedChart>, downloaded: List<SavedChart>, max: Int = ChartStore.MAX_SAVED): List<SavedChart> {
        val haveKeys = local.map { it.key }.toMutableSet()
        val haveIds = local.mapNotNull { it.remoteId }.toMutableSet()
        val room = max - local.size
        val added = downloaded.filter { chart ->
            val fresh = chart.key !in haveKeys && chart.remoteId !in haveIds
            if (fresh) {
                haveKeys += chart.key
                chart.remoteId?.let { haveIds += it }
            }
            fresh
        }.take(room.coerceAtLeast(0))
        return (local + added).sortedByDescending { it.lastOpenedMillis }
    }

    /** This phone's charts the account lacks, or has an older copy of. */
    fun uploads(local: List<SavedChart>): List<SavedChart> = local.filter { it.remoteId == null || it.needsUpload }

    /** [sent] is on the account as [id], if it is still on the list and unchanged since it was sent. */
    fun markUploaded(local: List<SavedChart>, sent: SavedChart, id: String): List<SavedChart> = local.map { chart ->
        when {
            chart.key != sent.key -> chart
            chart.details == sent.details -> chart.copy(remoteId = id, needsUpload = false)
            /* Changed while it was being sent: keep the id and send it again. */
            else -> chart.copy(remoteId = id, needsUpload = true)
        }
    }
}
