package site.lagnaatelier.app.ui.reading

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.Finding
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.antardashas
import site.lagnaatelier.app.data.SampleChart
import site.lagnaatelier.app.data.dayMonthYear
import site.lagnaatelier.app.data.formatDegree
import site.lagnaatelier.app.data.monthYear
import site.lagnaatelier.app.data.progress
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.Glow
import site.lagnaatelier.app.ui.components.LiquidIcons
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.components.Segmented
import site.lagnaatelier.app.ui.components.TickBadge
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.GlowOnInk
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.LagnaAtelierTheme
import site.lagnaatelier.app.ui.theme.LiquidType
import site.lagnaatelier.app.ui.theme.Muted
import java.time.LocalDate
import kotlin.math.roundToInt

/* How many findings show before "See all". */
private const val FIRST_FINDINGS = 3

enum class AppTab(val label: Int, val icon: ImageVector) {
    Reading(R.string.nav_reading, LiquidIcons.Reading),
    Timing(R.string.nav_timing, LiquidIcons.Timing),
    Palm(R.string.nav_palm, LiquidIcons.Palm),
    Account(R.string.nav_account, LiquidIcons.Account),
}

/*
 * Laid out like a portfolio screen: the period of life the reader is in is the
 * headline figure, then the four facts, then the timeline and the chart, then
 * the readings. The same order as the web app's phone page (app/m/insights).
 */
@Composable
fun ReadingScreen(
    chart: ChartSummary,
    isSample: Boolean,
    onBack: () -> Unit,
    onReport: () -> Unit,
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    today: LocalDate = LocalDate.now(),
    /* Where Share points: this chart on the website (ShareReading.link). */
    shareLink: String = LagnaApi.BASE_URL,
    /* The Timing, Palm and Account tabs, drawn under the same top bar. */
    otherTab: @Composable (AppTab) -> Unit = { ComingNext(it) },
    /* For the yogas' classical note; without it (previews) the note is left out. */
    api: LagnaApi? = null,
    /* The "More readings" card at the foot of the reading (ui/more). */
    moreReadings: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Canvas)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(chart, isSample, onBack, shareLink)
            if (tab == AppTab.Reading) {
                ReadingContent(chart, today, isSample, api, moreReadings)
            } else {
                Box(Modifier.weight(1f)) { otherTab(tab) }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tab == AppTab.Reading) {
                PillButton(stringResource(R.string.reading_report), onClick = onReport, icon = LiquidIcons.ArrowDown)
            }
            NavPill(tab, onTab)
        }
    }
}

@Composable
private fun TopBar(chart: ChartSummary, isSample: Boolean, onBack: () -> Unit, shareLink: String) {
    val backLabel = stringResource(R.string.reading_back)
    val context = LocalContext.current
    val shareText = stringResource(
        R.string.reading_share_text,
        chart.name,
        signName(chart.ascendant),
        signName(chart.moonSign),
        nakshatraName(chart.nakshatra),
        "${planetName(chart.mahadasha.lord)} · ${planetName(chart.antardasha.lord)}",
        chart.antardasha.end.dayMonthYear(),
        shareLink,
    )
    val shareSubject = stringResource(R.string.reading_share_subject, chart.name)
    val shareChooser = stringResource(R.string.reading_share_chooser)
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(chart.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${chart.place} · ${chart.birthDate.dayMonthYear()} · ${chart.birthTime}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (isSample || chart.timeIsEstimated) {
                Text(
                    stringResource(if (isSample) R.string.reading_sample else R.string.reading_time_estimated),
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        GhostChip(stringResource(R.string.reading_share), onClick = { shareReading(context, shareText, shareSubject, shareChooser) })
    }
}

/** The system share sheet, with the reading as plain text: any app that takes text can send it. */
private fun shareReading(context: Context, text: String, subject: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
        .putExtra(Intent.EXTRA_SUBJECT, subject)
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

@Composable
private fun ReadingContent(chart: ChartSummary, today: LocalDate, isSample: Boolean, api: LagnaApi?, moreReadings: @Composable () -> Unit) {
    var chartView by rememberSaveable { mutableIntStateOf(0) }
    var dashaView by rememberSaveable { mutableIntStateOf(0) }
    var showAllFindings by rememberSaveable { mutableStateOf(false) }
    /* The longer sections' own state, held here so it outlives their items scrolling away. */
    var houseRolesOpen by rememberSaveable { mutableStateOf(false) }
    var fullReadingUi by rememberSaveable { mutableStateOf(FullReadingUi()) }
    var yogasUi by rememberSaveable { mutableStateOf(YogasUi()) }
    val sections = if (isSample) rememberSampleSections() else chart.sections

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box {
                /* Kept inside the list's top edge, which would otherwise cut it with a hard line. */
                Glow(
                    Modifier
                        .offset(x = (-40).dp, y = 24.dp)
                        .requiredSize(width = 400.dp, height = 300.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NowCard(chart, today)
                    FactTiles(chart)
                }
            }
        }
        item {
            Panel {
                PanelHead(stringResource(R.string.reading_dasha_title)) {
                    Segmented(
                        listOf(stringResource(R.string.reading_dasha_maha), stringResource(R.string.reading_dasha_antar)),
                        selected = dashaView,
                        onSelect = { dashaView = it },
                    )
                }
                val antardashas = chart.antardashas
                if (dashaView == 1 && antardashas.isNotEmpty()) {
                    /* The antardashas of the mahadasha running now, on its own span. */
                    val maha = antardashas.first().start to antardashas.last().end
                    DashaTimeline(
                        antardashas,
                        current = chart.antardasha,
                        today = today,
                        from = maha.first,
                        to = maha.second,
                        labelEvery = if (maha.second.year - maha.first.year > 12) 4 else 2,
                    )
                } else {
                    DashaTimeline(chart.mahadashas, current = chart.mahadasha, today = today)
                }
            }
        }
        item {
            Panel {
                PanelHead(stringResource(R.string.reading_chart_title)) {
                    Segmented(
                        listOf(stringResource(R.string.reading_chart_rasi), stringResource(R.string.reading_chart_navamsa)),
                        selected = chartView,
                        onSelect = { chartView = it },
                    )
                }
                val navamsaAscendant = chart.navamsaAscendant
                when {
                    chartView == 0 -> RasiChart(
                        chart.placements,
                        ascendant = chart.ascendant,
                        title = stringResource(R.string.reading_chart_rasi),
                        subtitle = chart.tradition,
                    )
                    chart.navamsa.isNotEmpty() && navamsaAscendant != null -> RasiChart(
                        chart.navamsa,
                        ascendant = navamsaAscendant,
                        title = stringResource(R.string.reading_chart_navamsa),
                        subtitle = "D9 · ${chart.tradition}",
                    )
                    else -> Text(
                        stringResource(R.string.reading_navamsa_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
        sections.positions?.let { table -> item { PlanetPositionsPanel(table) } }
        sections.houseSupport?.let { support ->
            item { HouseSupportPanel(support, rolesOpen = houseRolesOpen, onToggleRoles = { houseRolesOpen = !houseRolesOpen }) }
        }
        if (chart.findings.isNotEmpty()) item {
            val shown = if (showAllFindings) chart.findings else chart.findings.take(FIRST_FINDINGS)
            Panel {
                PanelHead(stringResource(R.string.reading_attention_title)) {
                    if (chart.findings.size > FIRST_FINDINGS) {
                        Text(
                            if (showAllFindings) stringResource(R.string.reading_see_fewer)
                            else stringResource(R.string.reading_see_all, chart.findings.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = Muted,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { showAllFindings = !showAllFindings }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
                shown.forEachIndexed { index, finding ->
                    if (index > 0) HorizontalDivider(color = Hairline)
                    FindingRow(finding)
                }
            }
        }
        if (sections.findings.isNotEmpty()) item {
            FullReadingPanel(sections.findings, fullReadingUi, onUi = { fullReadingUi = it })
        }
        if (sections.yogas.isNotEmpty()) item {
            YogasPanel(sections.yogas, yogasUi, onUi = { yogasUi = it }, api = api)
        }
        item { moreReadings() }
    }
}

@Composable
private fun NowCard(chart: ChartSummary, today: LocalDate) {
    val period = chart.antardasha
    val fraction = period.progress(today)
    val percent = (fraction * 100).roundToInt()

    FloatCard(Modifier.fillMaxWidth(), spacing = 12.dp, padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                CardLabel(stringResource(R.string.reading_now_label))
                Text(
                    buildAnnotatedString {
                        append(planetName(chart.mahadasha.lord))
                        withStyle(SpanStyle(color = Muted)) { append(" · ") }
                        append(planetName(period.lord))
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    buildAnnotatedString {
                        append("$percent")
                        withStyle(SpanStyle(fontSize = LiquidType.figure.fontSize * 0.42f)) { append("%") }
                    },
                    style = LiquidType.figure.copy(fontSize = LiquidType.figure.fontSize * 1.05f),
                )
                CardLabel(stringResource(R.string.reading_now_through))
            }
        }
        ProgressLine(fraction)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(period.start.dayMonthYear(), style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(stringResource(R.string.reading_now_to, period.end.dayMonthYear()), style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

/* A hairline track with a black fill and a ringed dot at today. */
@Composable
private fun ProgressLine(fraction: Float) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
        val x = maxWidth * fraction
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(2.dp).background(Hairline, RoundedCornerShape(2.dp)))
        Box(Modifier.align(Alignment.CenterStart).size(width = x, height = 2.dp).background(Ink, RoundedCornerShape(2.dp)))
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = x - 7.dp)
                .size(14.dp)
                .background(Ink, CircleShape)
                .border(3.dp, CardWhite, CircleShape)
                .border(1.dp, Ink, CircleShape),
        )
    }
}

@Composable
private fun FactTiles(chart: ChartSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FactTile(stringResource(R.string.reading_fact_lagna), signName(chart.ascendant), formatDegree(chart.ascendantDegree))
            FactTile(stringResource(R.string.reading_fact_moon), signName(chart.moonSign), formatDegree(chart.moonDegree))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FactTile(
                stringResource(R.string.reading_fact_nakshatra),
                nakshatraName(chart.nakshatra),
                stringResource(R.string.reading_pada, chart.pada, planetName(chart.nakshatraLord)),
            )
            FactTile(
                stringResource(R.string.reading_fact_mahadasha),
                planetName(chart.mahadasha.lord),
                stringResource(R.string.reading_until, chart.mahadasha.end.monthYear()),
            )
        }
    }
}

@Composable
private fun RowScope.FactTile(label: String, value: String, meta: String) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .weight(1f)
            .background(CardWhite, shape)
            .border(1.dp, Hairline, shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CardLabel(label)
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(meta, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun PanelHead(title: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        trailing()
    }
}

@Composable
private fun FindingRow(finding: Finding) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 1.dp)) { TickBadge() }
        Column(Modifier.weight(1f)) {
            Text(finding.headline, style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.97f))
            finding.body?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.96f), color = Muted)
            }
        }
    }
}

@Composable
private fun ComingNext(tab: AppTab) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.nav_soon, stringResource(tab.label)),
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
        )
    }
}

/* The navigation bar as one floating black pill, as in the mock. */
@Composable
private fun NavPill(selected: AppTab, onSelect: (AppTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(CircleShape)
            .background(Ink)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTab.entries.forEach { tab ->
            val on = tab == selected
            val label = stringResource(tab.label)
            Column(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .semantics { this.selected = on }
                    .alpha(if (on) 1f else 0.6f)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(tab.icon, contentDescription = null, tint = if (on) GlowOnInk else CardWhite, modifier = Modifier.size(22.dp))
                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize * 0.92f), color = CardWhite)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun ReadingPreview() {
    LagnaAtelierTheme {
        val tab = remember { AppTab.Reading }
        ReadingScreen(
            chart = SampleChart.chart,
            isSample = true,
            onBack = {},
            onReport = {},
            tab = tab,
            onTab = {},
            today = LocalDate.of(2026, 11, 2),
        )
    }
}
