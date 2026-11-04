package site.lagnaatelier.app.ui.reading

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.data.reading.Yoga
import site.lagnaatelier.app.data.reading.YogaFamily
import site.lagnaatelier.app.data.reading.YogaStrength
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted

/* How many yogas show before "See all": the strongest, which the classical note speaks to first. */
private const val FIRST_YOGAS = 6

/** What the yoga panel remembers between scrolls. */
data class YogasUi(val openId: String? = null, val showAll: Boolean = false) : java.io.Serializable

/*
 * The chart's yogas, strongest first, each with what it means; a tapped row
 * opens what forms it in this chart, and when it shows or what softens it
 * when the engine says. Under the list, what the Brihat Jataka says about
 * them (YogaClassicsCard), on request.
 *
 * The order is the web's yoga panel's (lib/knowledge/yoga-classics.ts
 * rankYogas), so the classical note speaks first about the yogas this list
 * puts first.
 */
@Composable
internal fun YogasPanel(yogas: List<Yoga>, ui: YogasUi, onUi: (YogasUi) -> Unit, api: LagnaApi?) {
    val shown = if (ui.showAll) yogas else yogas.take(FIRST_YOGAS)
    Panel {
        SectionHead(stringResource(R.string.yogas_title), subtitle = stringResource(R.string.yogas_subtitle, yogas.size))
        Legend(stringResource(R.string.yogas_intro))
        Column {
            shown.forEachIndexed { index, yoga ->
                if (index > 0) HorizontalDivider(color = Hairline)
                YogaItem(
                    yoga = yoga,
                    open = ui.openId == yoga.id,
                    onToggle = { onUi(ui.copy(openId = if (ui.openId == yoga.id) null else yoga.id)) },
                )
            }
        }
        SeeAllLink(total = yogas.size, shown = FIRST_YOGAS, expanded = ui.showAll) { onUi(ui.copy(showAll = !ui.showAll)) }
        if (api != null) {
            HorizontalDivider(color = Hairline)
            YogaClassicsCard(yogas, api)
        }
    }
}

@Composable
private fun YogaItem(yoga: Yoga, open: Boolean, onToggle: () -> Unit) {
    Column {
        DisclosureRow(expanded = open, onToggle = onToggle) {
            StrengthDots(yoga.strength)
            Column(Modifier.weight(1f)) {
                Text(yoga.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.yoga_meta, stringResource(yoga.strength.label), stringResource(yoga.family.label)),
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                )
            }
        }
        /* What it means shows on every row: it is the line the yoga is listed for. */
        if (yoga.effects.isNotBlank()) {
            Text(
                yoga.effects,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                modifier = Modifier.padding(start = 32.dp, bottom = if (open) 8.dp else 10.dp),
            )
        }
        if (open) YogaDetail(yoga)
    }
}

@Composable
private fun YogaDetail(yoga: Yoga) {
    Column(
        Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        yoga.sanskrit?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (yoga.planets.isNotEmpty()) {
            Text(yoga.planets.map { pointName(it) }.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall)
        }
        Block(R.string.yoga_forms, yoga.description)
        yoga.activationTiming?.let { Block(R.string.yoga_shows, it) }
        yoga.cancellation?.let { Block(R.string.yoga_softened, it) }
        yoga.source?.let { Text(stringResource(R.string.yoga_source, it), style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

@Composable
private fun Block(@StringRes label: Int, text: String) {
    if (text.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BlockLabel(stringResource(label))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/* Three dots, filled to the strength, as the web's yoga cards mark it. The row's text says it in words. */
@Composable
private fun StrengthDots(strength: YogaStrength) {
    val filled = when (strength) {
        YogaStrength.Strong -> 3
        YogaStrength.Moderate -> 2
        YogaStrength.Weak -> 1
    }
    Row(
        Modifier.size(width = 22.dp, height = 22.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val on = index < filled
            Box(
                Modifier
                    .size(5.dp)
                    .then(if (on) Modifier.background(Ink, CircleShape) else Modifier.border(1.dp, Muted, CircleShape)),
            )
        }
    }
}

@get:StringRes
private val YogaStrength.label: Int
    get() = when (this) {
        YogaStrength.Strong -> R.string.yoga_strength_strong
        YogaStrength.Moderate -> R.string.yoga_strength_moderate
        YogaStrength.Weak -> R.string.yoga_strength_weak
    }

@get:StringRes
private val YogaFamily.label: Int
    get() = when (this) {
        YogaFamily.Mahapurusha -> R.string.yoga_family_mahapurusha
        YogaFamily.Wealth -> R.string.yoga_family_wealth
        YogaFamily.Benefic -> R.string.yoga_family_benefic
        YogaFamily.Viparita -> R.string.yoga_family_viparita
        YogaFamily.Nabhasa -> R.string.yoga_family_nabhasa
        YogaFamily.Challenging -> R.string.yoga_family_challenging
    }
