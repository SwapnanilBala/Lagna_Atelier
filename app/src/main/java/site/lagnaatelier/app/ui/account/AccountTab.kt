package site.lagnaatelier.app.ui.account

import site.lagnaatelier.app.ui.components.NavRoom
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.ChartStore
import site.lagnaatelier.app.data.SavedChart
import site.lagnaatelier.app.data.Traditions
import site.lagnaatelier.app.data.account.AccountSession
import site.lagnaatelier.app.data.account.ChartSync
import site.lagnaatelier.app.ui.components.CardLabel
import site.lagnaatelier.app.ui.components.FloatCard
import site.lagnaatelier.app.ui.components.GhostChip
import site.lagnaatelier.app.ui.components.GlowBehind
import site.lagnaatelier.app.ui.components.LiquidIcons
import site.lagnaatelier.app.ui.components.Panel
import site.lagnaatelier.app.ui.components.PillButton
import site.lagnaatelier.app.ui.theme.GeistMono
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Ink
import site.lagnaatelier.app.ui.theme.Muted

/* How many saved charts the tab lists before "Manage". */
private const val FIRST_SAVED = 3

/*
 * The Account tab (mock screen 05), drawn under the reading screen's top bar
 * and above the floating nav pill. Sign in with Google (SIGN_IN.md at the
 * repository root), then, only if the reader says yes, the saved charts kept
 * on the account too. A build without the web client id says sign-in is not
 * set up yet rather than offering a button that cannot work.
 */
@Composable
fun AccountTab(
    store: ChartStore,
    account: AccountSession,
    sync: ChartSync,
    onOpenSaved: (SavedChart) -> Unit,
    modifier: Modifier = Modifier,
    /* Opens Help (ui/more/HelpScreen.kt); no row when null. */
    onHelp: (() -> Unit)? = null,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = NavRoom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    stringResource(R.string.account_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                CardLabel(
                    stringResource(
                        if (account.status == AccountSession.Status.SignedIn) R.string.accounts_status_signed_in
                        else R.string.account_signed_out,
                    ),
                    Modifier.padding(bottom = 4.dp),
                )
            }
        }
        item {
            Box {
                GlowBehind(height = 260.dp)
                SignInCard(account)
            }
        }
        if (account.status == AccountSession.Status.SignedIn) {
            item { SyncPanel(account, sync) }
        }
        item { SavedPanel(store, syncing = sync.active, onOpenSaved = onOpenSaved) }
        item { PreferencesPanel() }
        item { AboutPanel(onHelp) }
    }
}

@Composable
private fun SignInCard(account: AccountSession) {
    val context = LocalContext.current
    val status = account.status
    val signedIn = status == AccountSession.Status.SignedIn || status == AccountSession.Status.SigningOut
    FloatCard(Modifier.fillMaxWidth(), spacing = 12.dp, padding = 16.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GoogleMark()
            when {
                status == AccountSession.Status.NotSetUp -> SoonTag(stringResource(R.string.accounts_not_set_up_tag))
                signedIn -> SoonTag(stringResource(R.string.accounts_signed_in_tag))
            }
        }
        if (signedIn) {
            val user = account.user
            Text(
                user?.displayName ?: stringResource(R.string.accounts_signed_in_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal),
            )
            user?.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted) }
            Text(stringResource(R.string.accounts_sign_out_note), style = MaterialTheme.typography.bodySmall, color = Muted)
            GhostChip(
                stringResource(
                    if (status == AccountSession.Status.SigningOut) R.string.accounts_signing_out else R.string.accounts_sign_out,
                ),
                onClick = if (status == AccountSession.Status.SignedIn) ({ account.signOut(context) }) else null,
            )
            return@FloatCard
        }
        Text(
            stringResource(R.string.accounts_signin_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal),
        )
        Text(
            stringResource(
                if (status == AccountSession.Status.NotSetUp) R.string.accounts_not_set_up else R.string.accounts_signin_copy,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
        SignInAction(account)
    }
}

/**
 * "Sign in with Google" and what went wrong last time, for any card that
 * asks for an account (this tab's, and the Palm tab's). Nothing at all when
 * the build has no client id: the card says "not set up" in its own words.
 */
@Composable
internal fun SignInAction(account: AccountSession) {
    val context = LocalContext.current
    val status = account.status
    if (status != AccountSession.Status.NotSetUp) {
        val busy = status == AccountSession.Status.SigningIn || status == AccountSession.Status.Checking
        PillButton(
            stringResource(
                when (status) {
                    AccountSession.Status.SigningIn -> R.string.accounts_signing_in
                    AccountSession.Status.Checking -> R.string.accounts_checking
                    else -> R.string.accounts_signin_button
                },
            ),
            onClick = { account.signIn(context) },
            enabled = !busy,
        )
    }
    account.message?.let { text ->
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

/*
 * Whether this phone's charts are kept on the account. Off until the reader
 * says yes to the words in accounts_sync_prompt, which go to the site as the
 * record of what they agreed to, as the website's own consent does.
 */
@Composable
private fun SyncPanel(account: AccountSession, sync: ChartSync) {
    var asking by rememberSaveable { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.accounts_sync_title)) {
            if (account.syncCharts) {
                val label = when (sync.status) {
                    ChartSync.Status.Syncing -> R.string.accounts_sync_syncing
                    ChartSync.Status.Failed -> null
                    else -> R.string.accounts_sync_done
                }
                if (label != null) Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
        if (account.syncCharts) {
            Text(stringResource(R.string.accounts_sync_on_copy), style = MaterialTheme.typography.bodySmall, color = Muted)
            if (sync.status == ChartSync.Status.Failed) {
                Text(stringResource(R.string.accounts_sync_failed), style = MaterialTheme.typography.bodySmall, color = Ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostChip(stringResource(R.string.accounts_sync_now), onClick = { sync.request() })
                GhostChip(stringResource(R.string.accounts_sync_stop), onClick = { account.chooseSyncCharts(false) })
            }
            Text(stringResource(R.string.accounts_sync_stop_note), style = MaterialTheme.typography.bodySmall, color = Muted)
        } else if (asking) {
            Text(stringResource(R.string.accounts_sync_prompt), style = MaterialTheme.typography.bodyMedium, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostChip(stringResource(R.string.accounts_sync_not_now), onClick = { asking = false })
                PillButton(
                    stringResource(R.string.accounts_sync_yes),
                    onClick = {
                        asking = false
                        account.chooseSyncCharts(true)
                    },
                )
            }
        } else {
            Text(stringResource(R.string.accounts_sync_off_copy), style = MaterialTheme.typography.bodySmall, color = Muted)
            GhostChip(stringResource(R.string.accounts_sync_ask), onClick = { asking = true })
        }
    }
}

@Composable
private fun SavedPanel(store: ChartStore, syncing: Boolean, onOpenSaved: (SavedChart) -> Unit) {
    val charts = store.saved
    var managing by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = if (managing) charts else charts.take(FIRST_SAVED)

    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.account_saved_title)) {
            Text(
                stringResource(R.string.account_saved_count, charts.size, ChartStore.MAX_SAVED),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
        if (charts.isEmpty()) {
            Text(stringResource(R.string.saved_empty), style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        shown.forEachIndexed { index, chart ->
            SavedChartRow(
                chart = chart,
                onOpen = { onOpenSaved(chart) },
                onDelete = if (managing) ({ confirming = chart.key }) else null,
                darkDisc = index == 0,
                showDegree = false,
                syncing = syncing,
            )
            if (managing && confirming == chart.key) {
                DeleteConfirm(
                    name = chart.details.name,
                    onAccount = chart.remoteId != null,
                    onCancel = { confirming = null },
                    onDelete = {
                        confirming = null
                        store.delete(chart)
                    },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardLabel(stringResource(if (syncing) R.string.accounts_saved_synced else R.string.account_saved_local))
            if (charts.isNotEmpty()) {
                GhostChip(
                    stringResource(if (managing) R.string.account_manage_done else R.string.account_manage),
                    onClick = {
                        managing = !managing
                        confirming = null
                    },
                )
            }
        }
    }
}

@Composable
private fun PreferencesPanel() {
    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.account_prefs_title)) {}
        LookSettings()
        HorizontalDivider(color = Hairline)
        Setting(
            stringResource(R.string.account_new_charts),
            traditionName(Traditions.DEFAULT_ENGINE_ID, fallback = ""),
            stringResource(R.string.account_new_charts_note),
        )
    }
}

@Composable
private fun AboutPanel(onHelp: (() -> Unit)?) {
    val context = LocalContext.current
    val version = remember(context) { versionText(context) }
    var licencesOpen by rememberSaveable { mutableStateOf(false) }

    Panel(Modifier.fillMaxWidth()) {
        PanelHead(stringResource(R.string.account_about_title)) {
            if (version != null) {
                Text(version, style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
        if (onHelp != null) {
            Setting(
                stringResource(R.string.guide_account_help),
                "",
                stringResource(R.string.guide_account_help_note),
                icon = LiquidIcons.Chevron,
                onClick = onHelp,
            )
            HorizontalDivider(color = Hairline)
        }
        val url = stringResource(R.string.account_website_url)
        val failed = stringResource(R.string.account_website_failed)
        Setting(
            stringResource(R.string.account_website),
            stringResource(R.string.account_website_value),
            stringResource(R.string.account_website_note),
            icon = AccountIcons.External,
            onClick = { openWebsite(context, url, failed) },
        )
        HorizontalDivider(color = Hairline)
        Setting(
            stringResource(R.string.account_licences),
            "",
            stringResource(R.string.account_licences_summary),
            icon = if (licencesOpen) AccountIcons.ChevronUp else AccountIcons.ChevronDown,
            onClick = { licencesOpen = !licencesOpen },
        )
        if (licencesOpen) Licences()
    }
}

@Composable
private fun Licences() {
    var fullText by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LicenceEntry(stringResource(R.string.account_licences_fonts), stringResource(R.string.account_licences_fonts_note))
        LicenceEntry(stringResource(R.string.account_licences_engine), stringResource(R.string.account_licences_engine_note))
        GhostChip(
            stringResource(if (fullText) R.string.account_licences_hide else R.string.account_licences_show),
            onClick = { fullText = !fullText },
        )
        if (fullText) {
            val context = LocalContext.current
            val text by produceState("", context) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        context.resources.openRawResource(R.raw.geist_ofl).bufferedReader().use { it.readText() }
                    }.getOrDefault("")
                }
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = GeistMono, fontSize = 10.5.sp, lineHeight = 15.sp),
                color = Ink,
            )
        }
    }
}

@Composable
private fun LicenceEntry(title: String, note: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp))
        Text(note, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

/* One preference or fact: name and value on a line, a quiet note under it. */
@Composable
private fun Setting(
    title: String,
    value: String,
    note: String?,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val base = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    Column(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base).padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), modifier = Modifier.weight(1f))
            if (value.isNotEmpty()) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.padding(start = 4.dp).size(15.dp))
            }
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun PanelHead(title: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        trailing()
    }
}

/** "1.0 (1)" from the installed package, or null if the system will not say. */
private fun versionText(context: Context): String? = try {
    val manager = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        manager.getPackageInfo(context.packageName, 0)
    }
    context.getString(R.string.account_version, info.versionName.orEmpty(), info.longVersionCode.toInt())
} catch (e: PackageManager.NameNotFoundException) {
    null
}

private fun openWebsite(context: Context, url: String, failed: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
    }
}
