package site.lagnaatelier.app.ui.timing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.ui.components.LiquidIcons
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.theme.CardWhite
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Muted

/*
 * The foot of the Timing tab: the two pages it leads on to, the website's
 * /insights/life-shifts and /insights/compatibility. Each opens in place of
 * the tab's panels, and back returns to them.
 */
@Composable
internal fun FurtherPanel(onPage: (TimingPage) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.periods_more_title))
        Column {
            EntryRow(
                stringResource(R.string.chapters_entry_title),
                stringResource(R.string.chapters_entry_body),
                onClick = { onPage(TimingPage.Chapters) },
            )
            HorizontalDivider(color = Hairline)
            EntryRow(
                stringResource(R.string.compat_entry_title),
                stringResource(R.string.compat_entry_body),
                onClick = { onPage(TimingPage.Compatibility) },
            )
        }
    }
}

@Composable
private fun EntryRow(title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Icon(LiquidIcons.Chevron, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

/*
 * The head of a page the tab leads on to: a quiet way back to the panels
 * (the reading's own round back button sits just above, and leaves the
 * chart), then the title and a line under it.
 */
@Composable
internal fun SubPageHeader(title: String, lead: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(CardWhite)
                .border(1.dp, Hairline, CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(LiquidIcons.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.periods_back_to_timing), style = MaterialTheme.typography.bodySmall)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(lead, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
