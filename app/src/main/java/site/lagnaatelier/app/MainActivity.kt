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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.ui.intake.IntakeScreen
import site.lagnaatelier.app.ui.landing.LandingScreen
import site.lagnaatelier.app.ui.reading.AppTab
import site.lagnaatelier.app.ui.reading.CastingFailedScreen
import site.lagnaatelier.app.ui.reading.CastingScreen
import site.lagnaatelier.app.ui.reading.ReadingScreen
import site.lagnaatelier.app.ui.theme.LagnaAtelierTheme

private enum class Screen { Landing, Intake, Reading }

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
    var tab by rememberSaveable { mutableStateOf(AppTab.Reading) }
    val context = LocalContext.current
    fun notYet(what: String) =
        Toast.makeText(context, context.getString(R.string.nav_soon, what), Toast.LENGTH_SHORT).show()

    /* Back from a reading the reader cast goes to their details; from the sample, home. */
    val backFromReading = { screen = if (model.chartState is ChartState.Sample) Screen.Landing else Screen.Intake }
    BackHandler(enabled = screen != Screen.Landing) {
        if (screen == Screen.Reading) {
            backFromReading()
        } else {
            screen = Screen.Landing
        }
    }

    when (screen) {
        Screen.Landing -> LandingScreen(
            today = model.today,
            todayUnavailable = model.todayUnavailable,
            onStart = { screen = Screen.Intake },
            onOpenSaved = {
                // Saving charts comes with sign-in; until then this opens the sample.
                model.showSample()
                tab = AppTab.Reading
                screen = Screen.Reading
            },
            onSignIn = { notYet(context.getString(R.string.landing_google)) },
        )

        Screen.Intake -> IntakeScreen(
            draft = model.intake,
            onBack = { screen = Screen.Landing },
            onSubmit = { details ->
                model.castChart(details)
                tab = AppTab.Reading
                screen = Screen.Reading
            },
        )

        Screen.Reading -> when (val state = model.chartState) {
            is ChartState.Loading -> CastingScreen(state.details)
            is ChartState.Failed -> CastingFailedScreen(
                message = state.message,
                onRetry = model::retry,
                onEdit = { screen = Screen.Intake },
            )
            is ChartState.Loaded, ChartState.Sample -> ReadingScreen(
                chart = (state as? ChartState.Loaded)?.chart ?: SampleChart.chart,
                isSample = state is ChartState.Sample,
                onBack = backFromReading,
                onReport = { notYet(context.getString(R.string.reading_report)) },
                tab = tab,
                onTab = { tab = it },
            )
        }
    }
}
