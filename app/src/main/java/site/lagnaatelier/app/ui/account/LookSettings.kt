package site.lagnaatelier.app.ui.account

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R
import site.lagnaatelier.app.data.AppLanguage
import site.lagnaatelier.app.data.ChartStyle
import site.lagnaatelier.app.data.LookPreferences
import site.lagnaatelier.app.data.ThemeChoice
import site.lagnaatelier.app.ui.components.LiquidChip
import site.lagnaatelier.app.ui.theme.Hairline
import site.lagnaatelier.app.ui.theme.Muted

/*
 * The three choices about how the app looks, in Account -> Preferences:
 * appearance, chart style and language. Each is remembered on this phone.
 */
@Composable
internal fun LookSettings() {
    Choice(
        title = stringResource(R.string.look_theme),
        note = stringResource(R.string.look_theme_note),
        options = ThemeChoice.entries.map { it to stringResource(themeLabel(it)) },
        selected = LookPreferences.theme,
        onSelect = LookPreferences::choose,
    )
    HorizontalDivider(color = Hairline)
    val style = LookPreferences.chartStyle
    Choice(
        title = stringResource(R.string.account_chart_style),
        note = stringResource(
            if (style == ChartStyle.NorthIndian) R.string.look_chart_north_note else R.string.look_chart_south_note,
        ),
        options = ChartStyle.entries.map { it to stringResource(chartStyleLabel(it)) },
        selected = style,
        onSelect = LookPreferences::choose,
    )
    HorizontalDivider(color = Hairline)
    LanguageChoice()
}

@Composable
private fun LanguageChoice() {
    val activity = LocalActivity.current
    val context = LocalContext.current
    /* Choosing recreates the activity, so this is read again in the new language. */
    val chosen = remember(context) { AppLanguage.chosen(context) }
    val options = listOf<String?>(null) + AppLanguage.SUPPORTED
    Choice(
        title = stringResource(R.string.account_language),
        note = stringResource(R.string.look_language_note),
        options = options.map { it to languageLabel(it) },
        selected = chosen,
        onSelect = { tag -> if (tag != chosen && activity != null) AppLanguage.choose(activity, tag) },
    )
}

/* A setting's name, its choices as chips, and a quiet note under them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(title: String, note: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            Modifier.semantics { selectableGroup() },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { (value, label) ->
                LiquidChip(label, selected = value == selected, onClick = { onSelect(value) })
            }
        }
        Text(note, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

private fun themeLabel(choice: ThemeChoice): Int = when (choice) {
    ThemeChoice.System -> R.string.look_theme_system
    ThemeChoice.Light -> R.string.look_theme_light
    ThemeChoice.Dark -> R.string.look_theme_dark
}

internal fun chartStyleLabel(style: ChartStyle): Int = when (style) {
    ChartStyle.SouthIndian -> R.string.look_chart_south
    ChartStyle.NorthIndian -> R.string.look_chart_north
}

@Composable
internal fun languageLabel(tag: String?): String = stringResource(
    when (tag) {
        null -> R.string.look_language_system
        "hi" -> R.string.look_language_hi
        "bn" -> R.string.look_language_bn
        "es" -> R.string.look_language_es
        "fr" -> R.string.look_language_fr
        "it" -> R.string.look_language_it
        "de" -> R.string.look_language_de
        else -> R.string.look_language_en
    },
)
