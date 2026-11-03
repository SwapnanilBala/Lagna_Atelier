package site.lagnaatelier.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.ui.landing.LandingScreen
import site.lagnaatelier.app.ui.reading.AppTab
import site.lagnaatelier.app.ui.reading.ReadingScreen
import site.lagnaatelier.app.ui.theme.LagnaAtelierTheme

private enum class Screen { Landing, Reading }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /* Dark system-bar icons always: the canvas is light whatever the phone's theme. */
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            LagnaAtelierTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.Landing) }
                var tab by rememberSaveable { mutableStateOf(AppTab.Reading) }
                val context = LocalContext.current
                fun notYet(what: String) = Toast.makeText(context, what, Toast.LENGTH_SHORT).show()

                BackHandler(enabled = screen != Screen.Landing) { screen = Screen.Landing }

                when (screen) {
                    /* Until the intake form exists, both ways in open the sample chart. */
                    Screen.Landing -> LandingScreen(
                        today = SampleChart.today,
                        onStart = { screen = Screen.Reading },
                        onOpenSaved = { screen = Screen.Reading },
                        onSignIn = { notYet(getString(R.string.nav_soon, getString(R.string.landing_google))) },
                    )
                    Screen.Reading -> ReadingScreen(
                        chart = SampleChart.chart,
                        isSample = true,
                        onBack = { screen = Screen.Landing },
                        onReport = { notYet(getString(R.string.nav_soon, getString(R.string.reading_report))) },
                        tab = tab,
                        onTab = { tab = it },
                    )
                }
            }
        }
    }
}
