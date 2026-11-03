package site.lagnaatelier.app.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Muted

/** The main terms, in the order a reader meets them (the website's onboarding.terms, shortened). */
internal val HelpTerms = listOf(
    R.string.guide_term_chart to R.string.guide_term_chart_body,
    R.string.guide_term_lagna to R.string.guide_term_lagna_body,
    R.string.guide_term_house to R.string.guide_term_house_body,
    R.string.guide_term_nakshatra to R.string.guide_term_nakshatra_body,
    R.string.guide_term_dasha to R.string.guide_term_dasha_body,
    R.string.guide_term_yoga to R.string.guide_term_yoga_body,
    R.string.guide_term_varga to R.string.guide_term_varga_body,
    R.string.guide_term_traditions to R.string.guide_term_traditions_body,
)

/*
 * Help, from Account: where things are, the words the readings use, and how
 * far to trust them. The website's Help guide, cut to what a phone needs.
 */
@Composable
internal fun HelpScreen(onTour: () -> Unit, onBack: () -> Unit) {
    MoreScaffold(stringResource(R.string.guide_help_title), stringResource(R.string.guide_help_lead), onBack) {
        item {
            Panel(Modifier.fillMaxWidth()) {
                SubHead(stringResource(R.string.guide_help_ways_title))
                listOf(
                    R.string.nav_reading to R.string.guide_help_reading,
                    R.string.more_card_title to R.string.guide_help_more,
                    R.string.nav_timing to R.string.guide_help_timing,
                    R.string.nav_palm to R.string.guide_help_palm,
                    R.string.nav_account to R.string.guide_help_account,
                ).forEachIndexed { index, (name, body) ->
                    if (index > 0) HorizontalDivider(color = Hairline)
                    Entry(stringResource(name), stringResource(body))
                }
                PillButton(stringResource(R.string.guide_help_tour), onClick = onTour)
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SubHead(stringResource(R.string.guide_help_terms_title))
                HelpTerms.forEachIndexed { index, (term, body) ->
                    if (index > 0) HorizontalDivider(color = Hairline)
                    Entry(stringResource(term), stringResource(body))
                }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                SubHead(stringResource(R.string.guide_help_honest_title))
                Text(stringResource(R.string.guide_help_honest), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.guide_help_time), style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
    }
}

@Composable
private fun Entry(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.95f))
        Text(body, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
