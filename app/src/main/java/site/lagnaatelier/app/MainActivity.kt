package site.lagnaatelier.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import site.lagnaatelier.app.data.AppDates
import site.lagnaatelier.app.data.AppLanguage
import site.lagnaatelier.app.data.LookPreferences
import site.lagnaatelier.app.data.ShareReading
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.ui.account.AccountTab
import site.lagnaatelier.app.ui.account.SavedChartsScreen
import site.lagnaatelier.app.ui.intake.IntakeScreen
import site.lagnaatelier.app.ui.landing.LandingScreen
import site.lagnaatelier.app.ui.more.FirstRunTour
import site.lagnaatelier.app.ui.more.MoreOverlay
import site.lagnaatelier.app.ui.more.MoreReadingsCard
import site.lagnaatelier.app.ui.more.MoreScreen
import site.lagnaatelier.app.ui.more.rememberMoreController
import site.lagnaatelier.app.ui.palm.PalmTab
import site.lagnaatelier.app.ui.reading.AppTab
import site.lagnaatelier.app.ui.reading.CastingFailedScreen
import site.lagnaatelier.app.ui.reading.CastingScreen
import site.lagnaatelier.app.ui.reading.ReadingScreen
import site.lagnaatelier.app.ui.report.ReportOverlay
import site.lagnaatelier.app.ui.report.rememberReportController
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.LagnaAtelierTheme
import site.lagnaatelier.app.ui.theme.isLiquidDark
import site.lagnaatelier.app.ui.timing.TimingTab

private enum class Screen { Landing, Intake, Saved, Reading }

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()

    /* Before Android 13 the app's own language choice is applied here; see AppLanguage. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LookPreferences.load(this)
        AppDates.locale = AppLanguage.resolve(resources.configuration)
        setContent {
            LagnaAtelierTheme {
                /* System-bar icons dark on the light canvas, light on the dark one. */
                val dark = isLiquidDark()
                DisposableEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose {}
                }
                Box(Modifier.fillMaxSize().background(Canvas)) {
                    LagnaApp(model)
                }
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
    val more = rememberMoreController()
    val context = LocalContext.current
    val account = model.account

    /* The landing page has no room for the sign-in card's messages; say them briefly. */
    LaunchedEffect(account.message) {
        val text = account.message ?: return@LaunchedEffect
        if (screen == Screen.Landing) {
            Toast.makeText(context, context.getString(text), Toast.LENGTH_LONG).show()
            account.dismissMessage()
        }
    }

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
            signedInAs = account.user?.let { it.displayName ?: it.email },
            onSignIn = {
                /* Signed in already, the button names the account and opens its charts. */
                if (account.signedIn) screen = Screen.Saved else account.signIn(context)
            },
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
            syncing = model.sync.active,
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
                message = stringResource(state.message),
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
                        shareLink = ShareReading.link(details),
                        api = model.api,
                        moreReadings = { MoreReadingsCard(onOpen = more::open) },
                        otherTab = { other ->
                            when (other) {
                                AppTab.Timing -> TimingTab(chart, details, model.api)
                                AppTab.Palm -> PalmTab(chart, details, model.api, model.account)
                                AppTab.Account -> AccountTab(
                                    store = model.store,
                                    account = model.account,
                                    sync = model.sync,
                                    onOpenSaved = { saved ->
                                        model.castChart(saved.details)
                                        openReading(from = readingOrigin)
                                    },
                                    onHelp = { more.open(MoreScreen.Help) },
                                )
                                AppTab.Reading -> Unit
                            }
                        },
                    )
                    ReportOverlay(report, chart, details, model.api)
                    MoreOverlay(more, chart, details, model.api)
                    FirstRunTour(more, onTab = { tab = it })
                }
            }
        }
    }
}
