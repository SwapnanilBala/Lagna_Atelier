package site.lagnaatelier.app.ui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.dayMonthYear
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.Glow
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.theme.Canvas
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted

/* While the site casts the chart: the glow, the name, and what is being placed. */
@Composable
fun CastingScreen(details: BirthDetails) {
    CenteredOnGlow {
        FloatCard(Modifier.fillMaxWidth(), spacing = 10.dp, padding = 20.dp) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Ink)
            Text(stringResource(R.string.casting_title, details.name), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.casting_detail, details.date.dayMonthYear(), details.place.label),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
        }
    }
}

@Composable
fun CastingFailedScreen(message: String, onRetry: () -> Unit, onEdit: () -> Unit) {
    CenteredOnGlow {
        FloatCard(Modifier.fillMaxWidth(), spacing = 12.dp, padding = 20.dp) {
            Text(stringResource(R.string.casting_failed_title), style = MaterialTheme.typography.titleLarge)
            Text(
                message.ifBlank { stringResource(R.string.casting_failed_generic) },
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(stringResource(R.string.casting_retry), onClick = onRetry)
                GhostChip(stringResource(R.string.casting_edit), onClick = onEdit)
            }
        }
    }
}

@Composable
private fun CenteredOnGlow(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Canvas).safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Glow(Modifier.requiredSize(width = 420.dp, height = 380.dp))
        Column { content() }
    }
}
