package site.lagnaatelier.app.ui.more

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.reading.AppTab
import site.lagnaatelier.app.ui.theme.Veil

/*
 * A short first-run tour of the tabs, the app's version of the website's
 * first-visit guidance (DesktopOnboarding): one card per stop, over the
 * reading, and each stop opens the tab it is about. Shown once on this phone,
 * then again only when asked for from Account, Help.
 */

/** The stops, in order, each with the tab it opens. */
enum class TourStep(val tab: AppTab, @param:StringRes @get:StringRes val title: Int, @param:StringRes @get:StringRes val body: Int) {
    Reading(AppTab.Reading, R.string.guide_tour_reading_title, R.string.guide_tour_reading_body),
    More(AppTab.Reading, R.string.guide_tour_more_title, R.string.guide_tour_more_body),
    Timing(AppTab.Timing, R.string.guide_tour_timing_title, R.string.guide_tour_timing_body),
    Palm(AppTab.Palm, R.string.guide_tour_palm_title, R.string.guide_tour_palm_body),
    Account(AppTab.Account, R.string.guide_tour_account_title, R.string.guide_tour_account_body),
}

/** Whether the tour has been seen on this device. */
interface TourMemory {
    fun seen(): Boolean
    fun markSeen()
}

/*
 * One flag in its own preferences file. Like the saved charts it stays on
 * this phone: the app opts out of backup, so a new phone shows the tour again,
 * which is no harm.
 */
class PreferencesTourMemory(context: Context) : TourMemory {
    private val prefs = context.getSharedPreferences("lagna_guide", Context.MODE_PRIVATE)
    override fun seen(): Boolean = prefs.getBoolean(KEY, false)
    override fun markSeen() = prefs.edit().putBoolean(KEY, true).apply()

    private companion object {
        const val KEY = "tour_seen"
    }
}

/** The tour's card above the nav pill, with a light veil over the rest; [onTab] opens each stop's tab. */
@Composable
fun FirstRunTour(controller: MoreController, onTab: (AppTab) -> Unit) {
    val index = controller.tourStep ?: return
    if (controller.screen != null) return
    val step = TourStep.entries[index]
    LaunchedEffect(step) { onTab(step.tab) }
    val label = stringResource(step.title)
    /* Done or skipped, the reader is put back on their reading, where the tour began. */
    val leave = {
        onTab(AppTab.Reading)
        controller.finishTour()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Veil)
            /* Holds touches: the tour is left with Skip or Done. */
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        FloatCard(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 84.dp)
                .semantics {
                    paneTitle = label
                    liveRegion = LiveRegionMode.Polite
                },
            spacing = 8.dp,
            padding = 16.dp,
        ) {
            CardLabel(stringResource(R.string.guide_tour_progress, index + 1, TourStep.entries.size))
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(step.body), style = MaterialTheme.typography.bodyMedium)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val last = index == TourStep.entries.lastIndex
                if (!last) GhostChip(stringResource(R.string.guide_tour_skip), onClick = leave) else Box(Modifier)
                PillButton(
                    stringResource(if (last) R.string.guide_tour_done else R.string.guide_tour_next),
                    onClick = if (last) leave else controller::nextTourStep,
                )
            }
        }
    }
}
