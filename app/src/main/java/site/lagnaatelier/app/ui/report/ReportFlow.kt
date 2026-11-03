package site.lagnaatelier.app.ui.report

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.report.ReportStage
import site.lagnaatelier.app.ui.theme.Canvas

/** Asks for the report; [ReportOverlay] draws the flow while [requested]. */
class ReportController {
    var requested by mutableStateOf(false)
        private set

    fun start() {
        requested = true
    }

    fun finish() {
        requested = false
    }
}

@Composable
fun rememberReportController() = remember { ReportController() }

/*
 * The "Your report" flow, drawn over the reading screen: generating, then
 * ready (or failed). The work itself lives in [ReportSession], a ViewModel,
 * so a turn of the phone neither restarts nor loses it -- which is also why
 * the overlay stays up while the session holds a report even if the
 * controller's flag was reset with the activity.
 *
 * [details] is null for the sample chart; the session then uses the sample's
 * own birth details.
 */
@Composable
fun ReportOverlay(controller: ReportController, chart: ChartSummary, details: BirthDetails?, api: LagnaApi) {
    /* Scoped to the activity's ViewModel store, as AppViewModel is, so it outlives a rotation. */
    val owner = LocalActivity.current as ViewModelStoreOwner
    val session = remember(owner) { ViewModelProvider(owner)[ReportSession::class.java] }
    val state = session.state

    LaunchedEffect(controller.requested) {
        if (controller.requested && session.state is ReportState.Idle) session.start(chart, details, api)
    }

    if (!controller.requested && state is ReportState.Idle) return

    val close = {
        session.reset()
        controller.finish()
    }
    val retry = { session.retry(chart, details, api) }
    BackHandler(onBack = close)

    Box(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            /* Holds every touch, so nothing reaches the reading underneath. */
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        when (state) {
            /* The first frame, before the session has started. */
            ReportState.Idle -> GeneratingScreen(ReportState.Working(ReportStage.Calculating, chart.name), onStop = close)
            is ReportState.Working -> GeneratingScreen(state, onStop = close)
            is ReportState.Ready -> ReadyScreen(state, chart, onBack = close, onMakeAgain = retry)
            is ReportState.Failed -> FailedScreen(state, onRetry = retry, onClose = close)
        }
    }
}

