package site.lagnaatelier.app.ui.timing

import site.lagnaatelier.app.ui.components.NavRoom
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.ReadingLanguage
import site.lagnaatelier.app.data.periods.PeriodsApi
import site.lagnaatelier.app.data.timing.TimingQueries
import site.lagnaatelier.app.data.timing.TimingRepository
import site.lagnaatelier.app.ui.components.GlowBehind
import java.time.LocalDate

/*
 * The Timing tab: the running periods, the week ahead, today's transits, a
 * good-time finder and this year's chart, top to bottom (mock "01 Timing").
 *
 * Each panel loads, fails and retries on its own, so one slow or failing
 * route never holds up the others. What has been fetched lives in a
 * [TimingHolder] kept per chart, day and language, so coming back to the tab shows it
 * at once. [details] is null for the built-in sample chart, which uses the
 * sample's own birth details.
 */
@Composable
fun TimingTab(chart: ChartSummary, details: BirthDetails?, api: LagnaApi, modifier: Modifier = Modifier) {
    val birth = details ?: TimingQueries.sampleDetails
    val today = remember { LocalDate.now() }
    /* The activity is recreated in a new language, so this is read afresh when it changes. */
    val language = ReadingLanguage.current
    val holder = remember(birth, today, language) { TimingHolders.get(birth, today, language) }
    val repo = remember(api) { TimingRepository(api) }
    val periodsApi = remember(api) { PeriodsApi(api) }
    val scope = rememberCoroutineScope()
    val readings = holder.readings

    LaunchedEffect(holder) {
        launch { holder.loadForecast(repo) }
        launch { holder.loadYear(repo) }
        /* The paragraph on the periods running now, once per chart and day, as the web's card asks on mount. */
        readings.startCurrentPeriod(periodsApi)
    }
    LaunchedEffect(holder, holder.weekIndex) { holder.loadWeek(repo, holder.weekIndex) }
    LaunchedEffect(holder, holder.activity) { holder.loadMuhurta(repo, holder.activity) }

    val retryForecast: () -> Unit = { scope.launch { holder.loadForecast(repo, force = true) } }

    /* The two screens the tab leads on to stand in for its panels; back returns to them. */
    BackHandler(enabled = readings.page != TimingPage.Panels) { readings.page = TimingPage.Panels }
    when (readings.page) {
        TimingPage.Chapters -> {
            LifeChaptersScreen(readings, periodsApi, onBack = { readings.page = TimingPage.Panels }, modifier = modifier)
            return
        }
        TimingPage.Compatibility -> {
            CompatibilityScreen(readings, api, periodsApi, onBack = { readings.page = TimingPage.Panels }, modifier = modifier)
            return
        }
        TimingPage.Panels -> Unit
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            /* Clears the floating nav pill. */
            .padding(start = 16.dp, end = 16.dp, bottom = NavRoom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            /* Kept inside the column's top edge, which would otherwise cut it with a hard line. */
            GlowBehind()
            PeriodsCard(
                chart,
                holder,
                onRetry = retryForecast,
                onRead = { chain -> readings.readPeriod(periodsApi, chain) },
            )
        }
        WeekPanel(holder) { scope.launch { holder.loadWeek(repo, holder.weekIndex, force = true) } }
        TransitsPanel(holder, onRetry = retryForecast)
        MuhurtaPanel(holder) { scope.launch { holder.loadMuhurta(repo, holder.activity, force = true) } }
        YearPanel(holder) { scope.launch { holder.loadYear(repo, force = true) } }
        FurtherPanel(onPage = { readings.page = it })
    }
}
