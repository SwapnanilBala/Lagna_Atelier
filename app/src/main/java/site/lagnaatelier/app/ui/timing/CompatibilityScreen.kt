package site.lagnaatelier.app.ui.timing

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.PlaceStatus
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.dayMonthYear
import site.lagnaatelier.app.data.periods.CompatibilityResult
import site.lagnaatelier.app.data.periods.FindingPolarity
import site.lagnaatelier.app.data.periods.PeriodsApi
import site.lagnaatelier.app.data.periods.SynastryAspect
import site.lagnaatelier.app.data.periods.SynastryFacet
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.Glow
import site.lagnaatelier.app.ui.components.LiquidChip
import site.lagnaatelier.app.ui.components.LiquidTextField
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.components.Segmented
import site.lagnaatelier.app.ui.components.TickBadge
import site.lagnaatelier.app.ui.intake.DateDialog
import site.lagnaatelier.app.ui.intake.TimeDialog
import site.lagnaatelier.app.ui.intake.ValueTile
import site.lagnaatelier.app.ui.intake.utcOffset
import site.lagnaatelier.app.ui.reading.planetName
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted
import java.util.Locale

/*
 * The website's /insights/compatibility: the reader's own chart is already
 * here, the partner's birth details are taken the way the intake form takes
 * them, and POST /api/compatibility reads the two together. The result is
 * the web page's: the score with what it is made of, then the themes, the
 * married-life read across the charts and every aspect between them, as three
 * tabs. No language model is involved, so comparing again costs nothing but
 * a moment.
 */
@Composable
internal fun CompatibilityScreen(
    readings: PeriodsHolder,
    api: LagnaApi,
    periodsApi: PeriodsApi,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val partner = readings.partner
    val scope = rememberCoroutineScope()
    var askDate by rememberSaveable { mutableStateOf(false) }
    var askTime by rememberSaveable { mutableStateOf(false) }
    val you = readings.details

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SubPageHeader(stringResource(R.string.compat_title), stringResource(R.string.compat_lead), onBack)

        Panel(Modifier.fillMaxWidth()) {
            CardLabel(stringResource(R.string.compat_your_chart))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(you.name, style = MaterialTheme.typography.titleSmall)
                Meta("${you.date.dayMonthYear()} · ${you.time} · ${you.place.label}")
            }
        }

        Box {
            Glow(Modifier.offset(x = (-40).dp, y = 10.dp).requiredSize(width = 400.dp, height = 260.dp))
            FloatCard(Modifier.fillMaxWidth(), spacing = 12.dp, padding = 16.dp) {
                CardLabel(stringResource(R.string.compat_their_chart))
                LiquidTextField(
                    value = partner.name,
                    onValueChange = { partner.name = it.take(120) },
                    label = stringResource(R.string.compat_their_name),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ValueTile(
                        label = stringResource(R.string.intake_date),
                        value = partner.date?.dayMonthYear() ?: stringResource(R.string.intake_choose),
                        filled = partner.date != null,
                        onClick = { askDate = true },
                    )
                    if (partner.knowsTime) {
                        ValueTile(
                            label = stringResource(R.string.intake_time),
                            value = partner.exactTime ?: stringResource(R.string.intake_choose),
                            filled = partner.exactTime != null,
                            onClick = { askTime = true },
                        )
                    }
                }
                PartnerTime(partner)
            }
        }

        PartnerPlace(partner, onQuery = { partner.onPlaceQuery(it, scope, api) }, onChoose = { partner.choose(it, scope, api) })

        val comparing = readings.compatibility is Load.Loading
        val missing = partner.missing
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when {
                    comparing -> stringResource(R.string.compat_comparing)
                    missing != null -> stringResource(missing.hint())
                    else -> stringResource(R.string.compat_ready)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                stringResource(R.string.compat_compare),
                onClick = { readings.compare(periodsApi) },
                enabled = missing == null && !comparing,
            )
        }

        when (val state = readings.compatibility) {
            is Load.Ready -> CompatibilityResultView(state.value, readings)
            is Load.Failed -> Text(
                stringResource(if (state.offline) R.string.timing_offline else state.message),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            else -> Unit
        }
    }

    if (askDate) DateDialog(partner.date, onDismiss = { askDate = false }) { partner.date = it; askDate = false }
    if (askTime) TimeDialog(partner.exactTime, onDismiss = { askTime = false }) { partner.exactTime = it; askTime = false }
}

private fun PartnerDraft.Missing.hint(): Int = when (this) {
    PartnerDraft.Missing.Name -> R.string.compat_missing_name
    PartnerDraft.Missing.Date -> R.string.intake_missing_date
    PartnerDraft.Missing.Time -> R.string.intake_missing_time
    PartnerDraft.Missing.Place -> R.string.intake_missing_place
}

/* "I know the exact time", and the part of the day when they do not: the intake form's own control. */
@Composable
private fun PartnerTime(partner: PartnerDraft) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.intake_knows_time), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = partner.knowsTime,
                onCheckedChange = { partner.knowsTime = it },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Ink,
                    checkedThumbColor = CardWhite,
                    uncheckedTrackColor = Hairline,
                    uncheckedThumbColor = CardWhite,
                    uncheckedBorderColor = Hairline,
                ),
            )
        }
        if (!partner.knowsTime) {
            CardLabel(stringResource(R.string.intake_estimate_label))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TimeAccuracy.Morning to R.string.intake_estimate_morning,
                    TimeAccuracy.Afternoon to R.string.intake_estimate_afternoon,
                    TimeAccuracy.Evening to R.string.intake_estimate_evening,
                    TimeAccuracy.Unknown to R.string.intake_estimate_unknown,
                ).forEach { (accuracy, label) ->
                    LiquidChip(stringResource(label), selected = partner.estimate == accuracy, onClick = { partner.estimate = accuracy })
                }
            }
            Text(
                stringResource(R.string.intake_estimate_note, partner.estimate.fallbackTime ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PartnerPlace(
    partner: PartnerDraft,
    onQuery: (String) -> Unit,
    onChoose: (LagnaApi.PlaceSuggestion) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val listInView = remember { BringIntoViewRequester() }
    LaunchedEffect(partner.suggestions) {
        if (partner.suggestions.isNotEmpty()) listInView.bringIntoView()
    }

    Panel(Modifier.fillMaxWidth()) {
        LiquidTextField(
            value = partner.placeQuery,
            onValueChange = onQuery,
            label = stringResource(R.string.intake_place),
            placeholder = stringResource(R.string.intake_place_hint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            trailing = when (partner.placeStatus) {
                PlaceStatus.Searching, PlaceStatus.Locating -> {
                    { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Ink) }
                }
                PlaceStatus.Located -> {
                    { TickBadge(20.dp) }
                }
                else -> null
            },
        )
        if (partner.suggestions.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(listInView)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(14.dp)),
            ) {
                partner.suggestions.forEachIndexed { index, suggestion ->
                    if (index > 0) HorizontalDivider(color = Hairline)
                    Text(
                        suggestion.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                keyboard?.hide()
                                focus.clearFocus()
                                onChoose(suggestion)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
        val place = partner.place
        val note = when (partner.placeStatus) {
            PlaceStatus.Located -> place?.let {
                stringResource(R.string.intake_place_located, it.timeZoneId.ifBlank { "UTC" }, utcOffset(it.timezoneOffsetMinutes))
            }
            PlaceStatus.NotFound -> stringResource(R.string.intake_place_not_found)
            PlaceStatus.Failed -> stringResource(R.string.intake_place_failed)
            else -> null
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

/* ── The result ─────────────────────────────────────────────────────────── */

@Composable
private fun CompatibilityResultView(result: CompatibilityResult, readings: PeriodsHolder) {
    FloatCard(Modifier.fillMaxWidth(), spacing = 14.dp, padding = 16.dp) {
        CardLabel(stringResource(R.string.compat_score_heading))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                Math.round(result.score).toString(),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 48.sp, lineHeight = 48.sp),
            )
            Column(Modifier.padding(bottom = 6.dp)) {
                Text(
                    stringResource(
                        when (result.band) {
                            CompatibilityResult.Band.Harmonious -> R.string.compat_band_harmonious
                            CompatibilityResult.Band.Compatible -> R.string.compat_band_compatible
                            CompatibilityResult.Band.Challenging -> R.string.compat_band_challenging
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Meta(stringResource(R.string.compat_score_of))
            }
        }
        ProgressLine(((result.score / 100.0).toFloat()).coerceIn(0f, 1f))
        Meta(stringResource(R.string.compat_names, result.primaryName, result.partnerName), color = Ink)
        Text(stringResource(R.string.compat_score_caption), style = MaterialTheme.typography.bodySmall, color = Muted)

        /* What the score is made of, counted from the same lists the tabs show in full. */
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlatTile(
                stringResource(R.string.compat_balance),
                stringResource(R.string.compat_supportive_count, result.supportive),
                stringResource(R.string.compat_friction_count, result.friction),
            )
            if (result.facets.any { it.findings.isNotEmpty() }) {
                FlatTile(
                    stringResource(R.string.compat_married_life),
                    stringResource(R.string.compat_clear_count, result.clearFindings),
                    stringResource(R.string.compat_caution_count, result.cautionFindings),
                )
            }
        }
        /* A whole row: "Saturn square Jupiter" does not fit half of one. */
        result.tightest?.let {
            Row { FlatTile(stringResource(R.string.compat_tightest), shortAspect(it), orb(it.orb)) }
        }
    }

    val tabs = buildList {
        add(R.string.compat_tab_themes)
        if (result.facets.isNotEmpty()) add(R.string.compat_tab_married)
        add(R.string.compat_tab_aspects)
    }
    val selected = readings.compatTab.coerceIn(0, tabs.size - 1)
    Segmented(tabs.map { stringResource(it) }, selected, onSelect = { readings.compatTab = it })

    Panel(Modifier.fillMaxWidth()) {
        when (tabs[selected]) {
            R.string.compat_tab_themes -> Themes(result)
            R.string.compat_tab_married -> MarriedLife(result)
            else -> Aspects(result)
        }
    }
}

@Composable
private fun Themes(result: CompatibilityResult) {
    PanelHead(stringResource(R.string.compat_themes_heading))
    if (result.summary.isNotBlank()) Text(result.summary, style = MaterialTheme.typography.bodyMedium, color = Muted)
    result.themes.forEach { Lead(it.title, it.insight) }
}

@Composable
private fun MarriedLife(result: CompatibilityResult) {
    var showMethod by rememberSaveable { mutableStateOf(false) }
    PanelHead(stringResource(R.string.compat_married_heading))
    Text(stringResource(R.string.compat_married_intro), style = MaterialTheme.typography.bodySmall, color = Muted)
    result.facets.forEachIndexed { index, facet ->
        if (index > 0) HorizontalDivider(color = Hairline)
        Facet(facet)
    }
    if (result.method.isNotBlank()) {
        TextLink(
            stringResource(if (showMethod) R.string.compat_derivation_hide else R.string.compat_derivation_show),
            onClick = { showMethod = !showMethod },
        )
        if (showMethod) Text(result.method, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun Facet(facet: SynastryFacet) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(facet.label, style = MaterialTheme.typography.titleMedium)
            GhostChip(facet.verdict)
        }
        facet.findings.forEach { finding ->
            Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Meta(
                    stringResource(
                        when (finding.polarity) {
                            FindingPolarity.Clear -> R.string.compat_polarity_clear
                            FindingPolarity.Caution -> R.string.compat_polarity_caution
                            FindingPolarity.Context -> R.string.compat_polarity_context
                        },
                    ),
                    color = if (finding.polarity == FindingPolarity.Context) Muted else Ink,
                    /* One width for all three, so the findings line up. */
                    modifier = Modifier.width(64.dp).padding(top = 2.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(finding.text, style = MaterialTheme.typography.bodyMedium)
                    if (finding.basis.isNotBlank()) Meta(finding.basis)
                }
            }
        }
        if (facet.sourcing.isNotBlank()) Meta(stringResource(R.string.compat_read_from, facet.sourcing))
    }
}

@Composable
private fun Aspects(result: CompatibilityResult) {
    PanelHead(stringResource(R.string.compat_aspects_heading))
    Text(stringResource(R.string.compat_aspects_intro), style = MaterialTheme.typography.bodySmall, color = Muted)
    if (result.aspects.isEmpty()) {
        Text(stringResource(R.string.compat_no_aspects), style = MaterialTheme.typography.bodyMedium, color = Muted)
        return
    }
    result.aspects.forEachIndexed { index, aspect ->
        if (index > 0) HorizontalDivider(color = Hairline)
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(
                        R.string.compat_aspect_pair,
                        result.primaryName,
                        planetName(aspect.primaryPlanet),
                        aspectWord(aspect.aspectType),
                        result.partnerName,
                        planetName(aspect.partnerPlanet),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Meta(orb(aspect.orb))
            }
            GhostChip(stringResource(if (aspect.harmonious) R.string.timing_tone_easy else R.string.timing_tone_testing))
        }
    }
}

/* The timing tab's own aspect words: "trine", "square", "conjunct". */
@Composable
private fun aspectWord(type: String): String = when (type.lowercase(Locale.ROOT)) {
    "conjunction" -> stringResource(R.string.timing_aspect_conjunction)
    "opposition" -> stringResource(R.string.timing_aspect_opposition)
    "trine" -> stringResource(R.string.timing_aspect_trine)
    "square" -> stringResource(R.string.timing_aspect_square)
    "sextile" -> stringResource(R.string.timing_aspect_sextile)
    else -> type.lowercase(Locale.ROOT)
}

@Composable
private fun shortAspect(aspect: SynastryAspect): String =
    stringResource(R.string.compat_aspect_short, planetName(aspect.primaryPlanet), aspectWord(aspect.aspectType), planetName(aspect.partnerPlanet))

@Composable
private fun orb(value: Double): String = stringResource(R.string.compat_orb, String.format(Locale.ENGLISH, "%.2f", value))
