package site.lagnaatelier.app.ui.reading

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.reading.HouseGroup
import site.lagnaatelier.app.data.reading.HouseRow
import site.lagnaatelier.app.data.reading.HouseSupport
import site.lagnaatelier.app.data.reading.SupportBand
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.LiquidType
import site.lagnaatelier.app.ui.theme.Muted

/* Observed sign totals run roughly 18 to 40; 45 leaves headroom without flattening the differences. */
private const val BAR_MAX_BINDUS = 45f
private val BAR_HEIGHT = 72.dp

/*
 * Which house is responsible for what, and how much support each holds: the
 * web's /insights/house-support page and the phone page's section, in one
 * panel. The figures come first (the support for the self, the twelve bars,
 * the best and least supported houses); the twelve-house reference opens
 * under them, since it is a lookup table rather than a reading.
 */
@Composable
internal fun HouseSupportPanel(support: HouseSupport, rolesOpen: Boolean, onToggleRoles: () -> Unit) {
    val self = support.houses.first()
    Panel {
        SectionHead(stringResource(R.string.houses_title), subtitle = stringResource(R.string.houses_subtitle))

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.houses_self_value, self.percent), style = LiquidType.figure.copy(fontSize = LiquidType.figure.fontSize * 0.8f))
            Column(Modifier.padding(bottom = 3.dp)) {
                CardLabel(stringResource(R.string.houses_self_label))
                Text(
                    stringResource(R.string.houses_self_meta, self.bindus, self.sign.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }

        HouseBars(support.houses)
        BandLegend()

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Fact(stringResource(R.string.houses_best), readout(support.strongest))
            Fact(stringResource(R.string.houses_least), readout(support.weakest))
            Text(stringResource(R.string.houses_above_average, support.aboveAverage), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Legend(stringResource(R.string.houses_bindus_legend))
        if (!support.totalIsExact) Legend(stringResource(R.string.houses_caveat, support.totalBindus))

        HorizontalDivider(color = Hairline)
        DisclosureRow(expanded = rolesOpen, onToggle = onToggleRoles) {
            Text(stringResource(R.string.houses_show_roles), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        if (rolesOpen) {
            val bhavas = stringArrayResource(R.array.bhava_names)
            val themes = stringArrayResource(R.array.house_themes)
            support.houses.forEachIndexed { index, house ->
                if (index > 0) HorizontalDivider(color = Hairline)
                HouseRoleRow(
                    house = house,
                    bhava = bhavas[house.house - 1],
                    theme = themes[house.house - 1],
                    peak = when (house.house) {
                        support.strongest.house -> R.string.houses_peak_best
                        support.weakest.house -> R.string.houses_peak_least
                        else -> null
                    },
                )
            }
            Legend(stringResource(R.string.houses_group_legend), Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun readout(house: HouseRow) = stringResource(R.string.houses_readout_house, house.house, house.sign.label, house.bindus)

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

/* Ink for strong, grey for neutral, a pale grey for weak: the band reads without colour. */
private fun SupportBand.fill(): Color = when (this) {
    SupportBand.Strong -> Ink
    SupportBand.Neutral -> Muted
    SupportBand.Weak -> Muted.copy(alpha = 0.35f)
}

/*
 * Twelve bars, house 1 to 12, against the 28.1 average. Drawn as boxes, not
 * on a canvas, so the numbers are text the system scales with the reader's
 * font size; the whole strip is read out as one description.
 */
@Composable
private fun HouseBars(houses: List<HouseRow>) {
    val description = stringResource(R.string.houses_bars_description) + " " +
        houses.joinToString(", ") { "${it.house}: ${it.bindus}" }
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Box(Modifier.fillMaxWidth().height(BAR_HEIGHT + 16.dp)) {
            /* The average, as a hairline across all twelve, drawn first so the bars sit over it. */
            val averageFromBottom = BAR_HEIGHT * (HouseSupport.AVERAGE.toFloat() / BAR_MAX_BINDUS)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = -averageFromBottom)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Ink.copy(alpha = 0.5f)),
            )
            Row(Modifier.fillMaxWidth().height(BAR_HEIGHT + 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                houses.forEach { house ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text("${house.bindus}", style = LiquidType.gridLabel, color = Muted)
                        Spacer(Modifier.height(2.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .height(BAR_HEIGHT * (house.bindus.coerceAtMost(BAR_MAX_BINDUS.toInt()) / BAR_MAX_BINDUS))
                                .background(house.band.fill(), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = Hairline)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            houses.forEach { house ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${house.house}", style = LiquidType.gridLabel, textAlign = TextAlign.Center)
                    Text(house.sign.label.take(3), style = LiquidType.gridLabel, color = Muted, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandLegend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            SupportBand.Strong to R.string.houses_band_strong,
            SupportBand.Neutral to R.string.houses_band_neutral,
            SupportBand.Weak to R.string.houses_band_weak,
        ).forEach { (band, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(8.dp).background(band.fill(), CircleShape))
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = Muted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HouseRoleRow(house: HouseRow, bhava: String, theme: String, @StringRes peak: Int?) {
    val bhavaName = stringResource(R.string.houses_bhava, bhava)
    val groups = house.groups.map { stringResource(it.label) }
    val peakText = peak?.let { stringResource(it) }
    val description = stringResource(R.string.houses_row_description, house.house, bhava, house.sign.label, house.bindus, theme) +
        " " + (groups + listOfNotNull(peakText)).joinToString(", ")

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        /* The band as a short rail beside the number, as the web's role rows carry it. */
        Box(Modifier.width(3.dp).height(36.dp).background(house.band.fill(), RoundedCornerShape(2.dp)))
        Text("${house.house}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(bhavaName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.houses_readout, house.sign.label, house.bindus), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Text(theme.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                groups.forEach { Tag(it) }
                if (peakText != null) Tag(peakText, strong = true)
            }
        }
    }
}

@get:StringRes
private val HouseGroup.label: Int
    get() = when (this) {
        HouseGroup.Kendra -> R.string.house_group_kendra
        HouseGroup.Panaphara -> R.string.house_group_panaphara
        HouseGroup.Apoklima -> R.string.house_group_apoklima
        HouseGroup.Trikona -> R.string.house_group_trikona
        HouseGroup.Upachaya -> R.string.house_group_upachaya
        HouseGroup.Dusthana -> R.string.house_group_dusthana
    }
