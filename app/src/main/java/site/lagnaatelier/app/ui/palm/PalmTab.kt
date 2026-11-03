package site.lagnaatelier.app.ui.palm

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.BirthDetails
import site.lagnaatelier.app.data.ChartSummary
import site.lagnaatelier.app.data.LagnaApi
import site.lagnaatelier.app.ui.FeatureStub

/*
 * The Palm tab. SCAFFOLD: owned by the Palm feature, which replaces this body
 * and adds files in this package (ui/palm) and strings in
 * res/values/strings_palm.xml. [details] is null for the sample chart.
 */
@Composable
fun PalmTab(chart: ChartSummary, details: BirthDetails?, api: LagnaApi, modifier: Modifier = Modifier) {
    FeatureStub(stringResource(R.string.nav_palm), modifier)
}
