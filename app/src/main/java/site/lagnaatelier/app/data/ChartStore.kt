package site.lagnaatelier.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A chart the reader cast on this phone, enough to cast it again. */
data class SavedChart(
    val details: BirthDetails,
    val ascendant: Sign,
    val savedAtMillis: Long,
)

/*
 * Charts saved on this phone, the Android version of the web app's
 * signed-out profiles (up to five per device, kept in the browser there).
 *
 * SCAFFOLD: holds the list in memory only. The Account feature replaces the
 * body with real persistence (it owns this file); the public surface --
 * [saved], [save], [delete] -- is what the rest of the app already calls.
 */
class ChartStore(@Suppress("unused") private val context: Context) {
    var saved by mutableStateOf<List<SavedChart>>(emptyList())
        private set

    /** Records a chart the site just cast; called by AppViewModel on success. */
    fun save(details: BirthDetails, chart: ChartSummary) {
        val entry = SavedChart(details, chart.ascendant, System.currentTimeMillis())
        saved = (listOf(entry) + saved.filterNot { it.details == details }).take(MAX_SAVED)
    }

    fun delete(entry: SavedChart) {
        saved = saved - entry
    }

    companion object {
        const val MAX_SAVED = 5
    }
}
