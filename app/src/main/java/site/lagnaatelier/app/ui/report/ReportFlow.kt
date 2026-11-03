package site.lagnaatelier.app.ui.report

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.LagnaApi

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
 * The "Your report" flow, drawn over the reading screen. SCAFFOLD: owned by
 * the Report feature, which replaces this body and adds files in ui/report and
 * strings in res/values/strings_report.xml. [details] is null for the sample.
 */
@Composable
fun ReportOverlay(controller: ReportController, chart: ChartSummary, details: BirthDetails?, api: LagnaApi) {
    if (!controller.requested) return
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Toast.makeText(context, context.getString(R.string.nav_soon, context.getString(R.string.reading_report)), Toast.LENGTH_SHORT).show()
        controller.finish()
    }
}
