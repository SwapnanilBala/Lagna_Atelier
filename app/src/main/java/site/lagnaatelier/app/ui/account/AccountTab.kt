package site.lagnaatelier.app.ui.account

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.ChartStore
import site.lagnaatelier.app.data.SavedChart
import site.lagnaatelier.app.ui.FeatureStub

/*
 * The Account tab. SCAFFOLD: owned by the Account feature, which replaces this
 * body, SavedChartsScreen.kt and data/ChartStore.kt's storage, and adds strings
 * in res/values/strings_account.xml.
 */
@Composable
fun AccountTab(store: ChartStore, onOpenSaved: (SavedChart) -> Unit, modifier: Modifier = Modifier) {
    FeatureStub(stringResource(R.string.nav_account), modifier)
}
