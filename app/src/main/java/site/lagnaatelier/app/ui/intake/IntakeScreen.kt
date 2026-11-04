package site.lagnaatelier.app.ui.intake

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.content.res.Configuration
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.AppViewModel
import site.lagnaatelier.app.PlaceStatus
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.BirthSex
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.Place
import site.lagnaatelier.app.data.TimeAccuracy
import site.lagnaatelier.app.data.Traditions
import site.lagnaatelier.app.data.dayMonthYear
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.Glow
import site.lagnaatelier.app.ui.components.LiquidChip
import site.lagnaatelier.app.ui.components.LiquidIcons
import site.lagnaatelier.app.ui.components.LiquidTextField
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.components.TickBadge
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntakeScreen(
    draft: AppViewModel.IntakeDraft,
    onBack: () -> Unit,
    onSubmit: (BirthDetails) -> Unit,
) {
    var askDate by rememberSaveable { mutableStateOf(false) }
    var askTime by rememberSaveable { mutableStateOf(false) }
    /*
     * A phone on its side with the keyboard up has about a third of its
     * height left: the header and the footer would take all of it and hide
     * the field being typed in. They fold away until the keyboard goes.
     */
    val folded = WindowInsets.isImeVisible &&
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(Modifier.fillMaxSize().background(Canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            if (!folded) Header(onBack)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box {
                    Glow(
                        Modifier
                            .offset(x = (-40).dp, y = 10.dp)
                            .requiredSize(width = 400.dp, height = 260.dp),
                    )
                    FloatCard(Modifier.fillMaxWidth(), spacing = 12.dp, padding = 16.dp) {
                        LiquidTextField(
                            value = draft.name,
                            onValueChange = { draft.name = it.take(120) },
                            label = stringResource(R.string.intake_name),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ValueTile(
                                label = stringResource(R.string.intake_date),
                                value = draft.date?.dayMonthYear() ?: stringResource(R.string.intake_choose),
                                filled = draft.date != null,
                                onClick = { askDate = true },
                            )
                            if (draft.knowsTime) {
                                ValueTile(
                                    label = stringResource(R.string.intake_time),
                                    value = draft.exactTime ?: stringResource(R.string.intake_choose),
                                    filled = draft.exactTime != null,
                                    onClick = { askTime = true },
                                )
                            }
                        }
                        TimeKnowledge(draft)
                    }
                }

                PlacePanel(draft)
                BirthSexPanel(draft)
                TraditionPanel(draft)
            }

            if (!folded) Footer(draft, onSubmit)
        }
    }

    if (askDate) DateDialog(draft.date, onDismiss = { askDate = false }) { draft.date = it; askDate = false }
    if (askTime) TimeDialog(draft.exactTime, onDismiss = { askTime = false }) { draft.exactTime = it; askTime = false }
}

@Composable
private fun Header(onBack: () -> Unit) {
    val backLabel = stringResource(R.string.reading_back)
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
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
        Column {
            Text(stringResource(R.string.intake_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.intake_lead), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
internal fun RowScope.ValueTile(label: String, value: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .weight(1f)
            .clip(shape)
            .background(CardWhite)
            .border(1.dp, if (filled) Ink else Hairline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        CardLabel(label)
        Text(value, style = MaterialTheme.typography.titleSmall, color = if (filled) Ink else Muted)
    }
}

/* "I know the exact time", and the part of the day when the reader does not. */
@Composable
private fun TimeKnowledge(draft: AppViewModel.IntakeDraft) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.intake_knows_time), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = draft.knowsTime,
                onCheckedChange = { draft.knowsTime = it },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Ink,
                    checkedThumbColor = CardWhite,
                    uncheckedTrackColor = Hairline,
                    uncheckedThumbColor = CardWhite,
                    uncheckedBorderColor = Hairline,
                ),
            )
        }
        if (!draft.knowsTime) {
            CardLabel(stringResource(R.string.intake_estimate_label))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    TimeAccuracy.Morning to R.string.intake_estimate_morning,
                    TimeAccuracy.Afternoon to R.string.intake_estimate_afternoon,
                    TimeAccuracy.Evening to R.string.intake_estimate_evening,
                    TimeAccuracy.Unknown to R.string.intake_estimate_unknown,
                ).forEach { (accuracy, label) ->
                    LiquidChip(stringResource(label), selected = draft.estimate == accuracy, onClick = { draft.estimate = accuracy })
                }
            }
            Text(
                stringResource(R.string.intake_estimate_note, draft.estimate.fallbackTime ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
    }
}

@Composable
private fun PlacePanel(draft: AppViewModel.IntakeDraft) {
    PlaceSearchPanel(
        query = draft.placeQuery,
        onQuery = draft::onPlaceQuery,
        status = draft.placeStatus,
        suggestions = draft.suggestions,
        place = draft.place,
        onChoose = draft::choose,
    )
}

/*
 * The place of birth: the search field, the suggestions under it, and what
 * the chosen place resolved to. The intake form and the compatibility form
 * both use it.
 *
 * Whenever new suggestions arrive, the field and the list are scrolled into
 * the part of the screen nothing covers: above the keyboard when it is up,
 * and above [barClearance] of floating chrome (the timing pages' nav pill)
 * when it is down. A list too tall for that space keeps the field and the
 * top suggestions in view. The scroll area around it must stop at the
 * keyboard (imePadding) for "in view" to mean visible.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlaceSearchPanel(
    query: String,
    onQuery: (String) -> Unit,
    status: PlaceStatus,
    suggestions: List<LagnaApi.PlaceSuggestion>,
    place: Place?,
    onChoose: (LagnaApi.PlaceSuggestion) -> Unit,
    modifier: Modifier = Modifier,
    barClearance: Dp = 0.dp,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val panelInView = remember { BringIntoViewRequester() }
    val fieldInView = remember { BringIntoViewRequester() }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val keyboardUp = WindowInsets.isImeVisible
    val below by rememberUpdatedState(with(density) { (PLACE_LIST_GAP + if (keyboardUp) 0.dp else barClearance).toPx() })
    LaunchedEffect(suggestions) {
        if (suggestions.isEmpty()) return@LaunchedEffect
        withFrameNanos { } // one frame, so the panel's measured size includes the list
        panelInView.bringIntoView(Rect(0f, 0f, panelSize.width.toFloat(), panelSize.height + below))
        // When field and list do not both fit, the field and the first suggestions win.
        fieldInView.bringIntoView()
    }

    Panel(
        modifier
            .bringIntoViewRequester(panelInView)
            .onSizeChanged { panelSize = it },
    ) {
        LiquidTextField(
            value = query,
            onValueChange = onQuery,
            label = stringResource(R.string.intake_place),
            placeholder = stringResource(R.string.intake_place_hint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            modifier = Modifier.bringIntoViewRequester(fieldInView),
            trailing = when (status) {
                PlaceStatus.Searching, PlaceStatus.Locating -> {
                    { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Ink) }
                }
                PlaceStatus.Located -> {
                    { TickBadge(20.dp) }
                }
                else -> null
            },
        )

        if (suggestions.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(14.dp)),
            ) {
                suggestions.forEachIndexed { index, suggestion ->
                    if (index > 0) HorizontalDivider(color = Hairline)
                    Text(
                        suggestion.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) {
                                // The place is chosen; the keyboard only hides the rest of the form now.
                                keyboard?.hide()
                                focus.clearFocus()
                                onChoose(suggestion)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }

        val note = when (status) {
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

/* Room left between the last suggestion and whatever is below it. */
private val PLACE_LIST_GAP = 12.dp

/* +330 -> "UTC+5:30", -300 -> "UTC−5:00" */
internal fun utcOffset(minutes: Int): String {
    val sign = if (minutes < 0) "−" else "+"
    val abs = kotlin.math.abs(minutes)
    return "UTC$sign${abs / 60}:${(abs % 60).toString().padStart(2, '0')}"
}

/* The website's question, in its order: Woman, Man, Prefer not to say (where it starts). */
@Composable
private fun BirthSexPanel(draft: AppViewModel.IntakeDraft) {
    Panel {
        Text(stringResource(R.string.birth_sex_label), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                BirthSex.Female to R.string.birth_sex_female,
                BirthSex.Male to R.string.birth_sex_male,
                null to R.string.birth_sex_unspecified,
            ).forEach { (sex, label) ->
                LiquidChip(stringResource(label), selected = draft.sex == sex, onClick = { draft.sex = sex })
            }
        }
        Text(stringResource(R.string.birth_sex_hint), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun TraditionPanel(draft: AppViewModel.IntakeDraft) {
    Panel {
        Text(stringResource(R.string.intake_tradition), style = MaterialTheme.typography.titleMedium)
        CardLabel(stringResource(R.string.intake_ayanamsha))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Traditions.Ayanamsha.entries.forEach { option ->
                LiquidChip(option.label, selected = draft.ayanamsha == option, onClick = { draft.ayanamsha = option })
            }
        }
        CardLabel(stringResource(R.string.intake_houses))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Traditions.HouseSystem.entries.forEach { option ->
                LiquidChip(option.label, selected = draft.houses == option, onClick = { draft.houses = option })
            }
        }
        Text(stringResource(R.string.intake_tradition_note), style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun Footer(draft: AppViewModel.IntakeDraft, onSubmit: (BirthDetails) -> Unit) {
    val missing = draft.missing
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            missing?.let { stringResource(it.hint()) } ?: stringResource(R.string.intake_ready),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.weight(1f),
        )
        PillButton(
            stringResource(R.string.intake_submit),
            onClick = { draft.details()?.let(onSubmit) },
            enabled = missing == null,
        )
    }
}

private fun AppViewModel.Missing.hint(): Int = when (this) {
    AppViewModel.Missing.Name -> R.string.intake_missing_name
    AppViewModel.Missing.Date -> R.string.intake_missing_date
    AppViewModel.Missing.Time -> R.string.intake_missing_time
    AppViewModel.Missing.Place -> R.string.intake_missing_place
}

/* Material's date picker works in UTC milliseconds; the birth date is a calendar day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(current: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val todayMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (current ?: LocalDate.of(1990, 1, 1)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = 1900..LocalDate.now().year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.intake_ok), color = Ink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.intake_cancel), color = Muted) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(current: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val (h, m) = current?.split(":")?.let { it[0].toInt() to it[1].toInt() } ?: (12 to 0)
    val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick("${state.hour.toString().padStart(2, '0')}:${state.minute.toString().padStart(2, '0')}")
            }) { Text(stringResource(R.string.intake_ok), color = Ink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.intake_cancel), color = Muted) } },
        title = { Text(stringResource(R.string.intake_time)) },
        text = { TimePicker(state = state) },
        containerColor = CardWhite,
    )
}
