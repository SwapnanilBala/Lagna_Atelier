package site.lagnaatelier.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.ui.account.AccountTab
import site.lagnaatelier.app.ui.account.SavedChartsScreen
import site.lagnaatelier.app.ui.intake.IntakeScreen
import site.lagnaatelier.app.ui.landing.LandingScreen
import site.lagnaatelier.app.ui.palm.PalmTab
import site.lagnaatelier.app.ui.reading.AppTab
import site.lagnaatelier.app.ui.reading.CastingFailedScreen
import site.lagnaatelier.app.ui.reading.CastingScreen
import site.lagnaatelier.app.ui.reading.ReadingScreen
import site.lagnaatelier.app.ui.report.ReportOverlay
import site.lagnaatelier.app.ui.report.rememberReportController
import site.lagnaatelier.app.ui.theme.LagnaAtelierTheme
import site.lagnaatelier.app.ui.timing.TimingTab

private enum class Screen { Landing, Intake, Saved, Reading }

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /* Dark system-bar icons always: the canvas is light whatever the phone's theme. */
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            LagnaAtelierTheme {
                LagnaApp(model)
            }
        }
    }
}

@Composable
private fun LagnaApp(model: AppViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.Landing) }
    /* Where back from a reading returns to: home, the form, or the saved list. */
    var readingOrigin by rememberSaveable { mutableStateOf(Screen.Landing) }
    var tab by rememberSaveable { mutableStateOf(AppTab.Reading) }
    val report = rememberReportController()
    val context = LocalContext.current
    fun notYet(what: String) =
        Toast.makeText(context, context.getString(R.string.nav_soon, what), Toast.LENGTH_SHORT).show()

    fun openReading(from: Screen) {
        readingOrigin = from
        tab = AppTab.Reading
        screen = Screen.Reading
    }

    BackHandler(enabled = screen != Screen.Landing) {
        screen = if (screen == Screen.Reading) readingOrigin else Screen.Landing
    }

    when (screen) {
        Screen.Landing -> LandingScreen(
            today = model.today,
            todayUnavailable = model.todayUnavailable,
            onStart = { screen = Screen.Intake },
            onOpenSaved = { screen = Screen.Saved },
            onSignIn = { notYet(context.getString(R.string.landing_google)) },
        )

        Screen.Intake -> IntakeScreen(
            draft = model.intake,
            onBack = { screen = Screen.Landing },
            onSubmit = { details ->
                model.castChart(details)
                openReading(from = Screen.Intake)
            },
        )

        Screen.Saved -> SavedChartsScreen(
            store = model.store,
            onOpen = { saved ->
                model.castChart(saved.details)
                openReading(from = Screen.Saved)
            },
            onStartNew = { screen = Screen.Intake },
            onSample = {
                model.showSample()
                openReading(from = Screen.Saved)
            },
            onBack = { screen = Screen.Landing },
        )

        Screen.Reading -> when (val state = model.chartState) {
            is ChartState.Loading -> CastingScreen(state.details)
            is ChartState.Failed -> CastingFailedScreen(
                message = state.message,
                onRetry = model::retry,
                onEdit = { screen = Screen.Intake },
            )
            is ChartState.Loaded, ChartState.Sample -> {
                val chart = (state as? ChartState.Loaded)?.chart ?: SampleChart.chart
                /* Null for the sample: features that need real birth details say so. */
                val details = (state as? ChartState.Loaded)?.details
                Box {
                    ReadingScreen(
                        chart = chart,
                        isSample = state is ChartState.Sample,
                        onBack = { screen = readingOrigin },
                        onReport = report::start,
                        tab = tab,
                        onTab = { tab = it },
                        otherTab = { other ->
                            when (other) {
                                AppTab.Timing -> TimingTab(chart, details, model.api)
                                AppTab.Palm -> PalmTab(chart, details, model.api)
                                AppTab.Account -> AccountTab(
                                    store = model.store,
                                    onOpenSaved = { saved ->
                                        model.castChart(saved.details)
                                        openReading(from = readingOrigin)
                                    },
                                )
                                AppTab.Reading -> Unit
                            }
                        },
                    )
                    ReportOverlay(report, chart, details, model.api)
                }
            }
        }
    }
}
