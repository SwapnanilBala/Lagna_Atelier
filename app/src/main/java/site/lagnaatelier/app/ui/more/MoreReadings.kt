package site.lagnaatelier.app.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.ReadingLanguage
import site.lagnaatelier.app.data.timing.TimingQueries
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.LiquidIcons
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Muted

/*
 * More readings: the website's life areas, "Ask the classics" and varga atlas,
 * reached from one card at the foot of the Reading tab, plus Help, reached
 * from Account. Each is a screen drawn over the reading, as the report is, so
 * the reading underneath keeps its place and back returns to it.
 */

enum class MoreScreen { LifeAreas, AskClassics, Vargas, Help }

/** Which More screen is open, and where the first-run tour stands. */
@Stable
class MoreController internal constructor(
    private val memory: TourMemory,
    screen: MoreScreen?,
    tourStep: Int?,
) {
    var screen by mutableStateOf(screen)
        private set

    /** The varga open in the atlas, or null for the atlas itself. */
    var division by mutableStateOf<Int?>(null)

    /** Index into [TourStep.entries] while the tour is showing. */
    var tourStep by mutableStateOf(tourStep)
        private set

    fun open(next: MoreScreen) {
        screen = next
        division = null
    }

    /** Back: from a varga to the atlas, else out to the reading. */
    fun back() {
        if (screen == MoreScreen.Vargas && division != null) division = null else screen = null
    }

    /** Shows the tour on a first visit; called once the reading is on screen. */
    fun offerTour(seen: Boolean) {
        if (!seen && tourStep == null && screen == null) tourStep = 0
    }

    fun startTour() {
        screen = null
        tourStep = 0
    }

    fun nextTourStep() {
        val step = tourStep ?: return
        if (step >= TourStep.entries.lastIndex) finishTour() else tourStep = step + 1
    }

    /** Done or skipped alike: either way the tour is not offered again on this phone. */
    fun finishTour() {
        tourStep = null
        memory.markSeen()
    }

    internal fun saved(): List<Any> = listOf(screen?.name.orEmpty(), division ?: -1, tourStep ?: -1)

    internal companion object {
        fun restored(memory: TourMemory, saved: List<Any>): MoreController {
            val controller = MoreController(
                memory,
                MoreScreen.entries.firstOrNull { it.name == saved.getOrNull(0) },
                (saved.getOrNull(2) as? Int)?.takeIf { it in TourStep.entries.indices },
            )
            controller.division = (saved.getOrNull(1) as? Int)?.takeIf { it > 0 }
            return controller
        }
    }
}

/** Kept across rotation; the tour is offered once the device's memory of it has been read. */
@Composable
fun rememberMoreController(): MoreController {
    val context = LocalContext.current.applicationContext
    val memory = remember(context) { PreferencesTourMemory(context) }
    val controller = rememberSaveable(
        saver = Saver<MoreController, List<Any>>(save = { it.saved() }, restore = { MoreController.restored(memory, it) }),
    ) { MoreController(memory, screen = null, tourStep = null) }
    LaunchedEffect(controller) {
        /* Read off the main thread: the first read of a preferences file loads it from disk. */
        val seen = withContext(Dispatchers.IO) { memory.seen() }
        controller.offerTour(seen)
    }
    return controller
}

/** The one card on the Reading tab that opens the three readings. */
@Composable
fun MoreReadingsCard(onOpen: (MoreScreen) -> Unit, modifier: Modifier = Modifier) {
    Panel(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.more_card_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        val rows = listOf(
            Triple(MoreScreen.LifeAreas, R.string.more_card_areas, R.string.more_card_areas_note),
            Triple(MoreScreen.AskClassics, R.string.more_card_ask, R.string.more_card_ask_note),
            Triple(MoreScreen.Vargas, R.string.more_card_vargas, R.string.more_card_vargas_note),
        )
        rows.forEachIndexed { index, (screen, title, note) ->
            if (index > 0) HorizontalDivider(color = Hairline)
            LinkRow(stringResource(title), stringResource(note), onClick = { onOpen(screen) })
        }
    }
}

/** A title and a quiet line under it, with a chevron: opens something. */
@Composable
internal fun LinkRow(title: String, note: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Icon(LiquidIcons.Chevron, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
    }
}

/*
 * The open More screen, over the reading. [details] is null for the sample
 * chart, whose own birth details then stand in, as on the timing tab.
 */
@Composable
fun MoreOverlay(controller: MoreController, chart: ChartSummary, details: BirthDetails?, api: LagnaApi) {
    val screen = controller.screen ?: return
    val birth = details ?: TimingQueries.sampleDetails
    /* The activity is recreated in a new language, so this is read afresh when it changes. */
    val language = ReadingLanguage.current
    val holder = remember(birth, api, language) { MoreHolders.get(birth, api, language) }
    BackHandler(onBack = controller::back)

    Box(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            /* Holds every touch, so nothing reaches the reading underneath. */
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        when (screen) {
            MoreScreen.LifeAreas -> LifeAreasScreen(holder, onBack = controller::back)
            MoreScreen.AskClassics -> AskClassicsScreen(holder, onBack = controller::back)
            MoreScreen.Vargas -> VargasScreen(
                holder = holder,
                cast = chart.vargas,
                timeIsEstimated = chart.timeIsEstimated,
                tradition = chart.tradition,
                division = controller.division,
                onDivision = { controller.division = it },
                onBack = controller::back,
            )
            MoreScreen.Help -> HelpScreen(onTour = controller::startTour, onBack = controller::back)
        }
    }
}

/** A More screen's frame: a back button and title over a scrolling list that clears the gesture bar. */
@Composable
internal fun MoreScaffold(title: String, lead: String?, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val backLabel = stringResource(R.string.more_back)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardWhite)
                    .border(1.dp, Hairline, CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(LiquidIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (lead != null) item {
                Text(lead, style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(horizontal = 4.dp))
            }
            content()
            item { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

/** A quiet line while something loads, or the failure in the app's words with "Try again". */
@Composable
internal fun <T> FetchState(fetch: Fetch<T>, loading: String, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (fetch) {
        is Fetch.Ready -> content(fetch.value)
        is Fetch.Failed -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(fetch.message), style = MaterialTheme.typography.bodyMedium, color = Muted)
            GhostChip(stringResource(R.string.more_retry), onClick = onRetry)
        }
        Fetch.Idle, Fetch.Loading -> CardLabel(loading, Modifier.padding(vertical = 8.dp))
    }
}

/** A small heading inside a card. */
@Composable
internal fun SubHead(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
        modifier = modifier.semantics { heading() },
    )
}

/** A short list, each line led by a small dot. */
@Composable
internal fun Bullets(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = Muted)
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
