package site.lagnaatelier.app.ui.reading

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.reading.Claim
import site.lagnaatelier.app.data.reading.FindingFilter
import site.lagnaatelier.app.data.reading.FindingTheme
import site.lagnaatelier.app.data.reading.FullFinding
import site.lagnaatelier.app.ui.components.LiquidChip
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Muted

/* How many rows show before "See all": a screen's worth, not the whole reading. */
private const val FIRST_ROWS = 8

/** What the full reading panel remembers between scrolls: which filter, which finding is open, how many show. */
data class FullReadingUi(
    val filter: FindingFilter = FindingFilter.All,
    val openKey: String? = null,
    val showAll: Boolean = false,
) : java.io.Serializable

/*
 * Every finding the rule engine matched, not only the few the page leads
 * with: the web's /insights/full-reading and the phone page's "All readings".
 * The chosen few come first and carry their rank; a tapped row opens its
 * reading underneath, with what it rests on ("Why this reading") and how
 * common it is, which on the web sits behind the same disclosure.
 *
 * One row open at a time, as the web's reading room does it, so the list
 * stays a list.
 */
@Composable
internal fun FullReadingPanel(findings: List<FullFinding>, ui: FullReadingUi, onUi: (FullReadingUi) -> Unit) {
    val filters = FindingFilter.offered(findings)
    val filter = ui.filter.takeIf { it in filters } ?: FindingFilter.All
    val matching = findings.filter(filter::matches)
    val shown = if (ui.showAll) matching else matching.take(FIRST_ROWS)

    Panel {
        SectionHead(stringResource(R.string.full_title), subtitle = stringResource(R.string.full_subtitle, findings.size))
        if (filters.size > 2) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                filters.forEach { option ->
                    LiquidChip(
                        text = stringResource(option.label),
                        selected = option == filter,
                        onClick = { onUi(ui.copy(filter = option, showAll = false)) },
                    )
                }
            }
        }
        Column {
            shown.forEachIndexed { index, finding ->
                if (index > 0) HorizontalDivider(color = Hairline)
                FindingItem(
                    finding = finding,
                    open = ui.openKey == finding.key,
                    onToggle = { onUi(ui.copy(openKey = if (ui.openKey == finding.key) null else finding.key)) },
                )
            }
        }
        SeeAllLink(total = matching.size, shown = FIRST_ROWS, expanded = ui.showAll) {
            onUi(ui.copy(showAll = !ui.showAll))
        }
    }
}

@Composable
private fun FindingItem(finding: FullFinding, open: Boolean, onToggle: () -> Unit) {
    val theme = stringResource(finding.theme.label)
    val meta = if (finding.rare) "$theme · ${stringResource(R.string.full_rare)}" else theme
    Column {
        DisclosureRow(expanded = open, onToggle = onToggle) {
            RankMark(finding.rank)
            Column(Modifier.weight(1f)) {
                Text(finding.headline, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (finding.isTop) "$meta · ${stringResource(R.string.full_top_priority, finding.rank)}" else meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                )
            }
        }
        if (open) FindingDetail(finding)
    }
}

@Composable
private fun FindingDetail(finding: FullFinding) {
    Column(
        Modifier.fillMaxWidth().padding(start = 32.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (finding.body.isNotBlank()) Text(finding.body, style = MaterialTheme.typography.bodyMedium)
        finding.tension?.let {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BlockLabel(stringResource(R.string.full_other_side))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BlockLabel(stringResource(R.string.full_why))
            finding.claims.forEach { ClaimRow(it) }
            if (finding.technicalNote.isNotBlank()) {
                Text(finding.technicalNote, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        if (finding.rarityLabel.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BlockLabel(stringResource(R.string.full_how_common))
                Text(finding.rarityLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/* "Rising sign   Virgo", with the claim's detail, when it has one, under the value. */
@Composable
private fun ClaimRow(claim: Claim) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(claim.label, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.weight(1f))
        Column(Modifier.weight(1.2f)) {
            Text(claim.value, style = MaterialTheme.typography.bodySmall)
            claim.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
        }
    }
}

@get:StringRes
private val FindingFilter.label: Int
    get() = when (this) {
        FindingFilter.All -> R.string.full_filter_all
        FindingFilter.Top -> R.string.full_filter_top
        FindingFilter.Core -> R.string.full_theme_core
        FindingFilter.Career -> R.string.full_theme_career
        FindingFilter.Love -> R.string.full_theme_love
    }

@get:StringRes
private val FindingTheme.label: Int
    get() = when (this) {
        FindingTheme.Core -> R.string.full_theme_core
        FindingTheme.Career -> R.string.full_theme_career
        FindingTheme.Love -> R.string.full_theme_love
    }
