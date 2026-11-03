package site.lagnaatelier.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.ChartStore
import site.lagnaatelier.app.data.SavedChart
import site.lagnaatelier.app.ui.FeatureStub
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.theme.Canvas

/*
 * Opened from the landing page's "Saved charts". SCAFFOLD: owned by the
 * Account feature. [onSample] opens the built-in sample chart.
 */
@Composable
fun SavedChartsScreen(
    store: ChartStore,
    onOpen: (SavedChart) -> Unit,
    onStartNew: () -> Unit,
    onSample: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Canvas).safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GhostChip(stringResource(R.string.reading_back), onClick = onBack)
        GhostChip(stringResource(R.string.reading_sample), onClick = onSample)
        FeatureStub(stringResource(R.string.landing_saved), Modifier.weight(1f))
    }
}
