package site.lagnaatelier.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.data.LookPreferences

/*
 * Light or dark, following the phone unless Account -> Preferences says
 * otherwise. Each is its own palette (Color.kt), not one inverted.
 *
 * Dynamic colour (Material You) is deliberately not used. It would repaint the
 * app from the phone's wallpaper, and the golden-hour glow would be gone.
 */
private fun schemeFor(palette: LiquidPalette): ColorScheme {
    val base = if (palette.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = palette.ink,
        onPrimary = palette.card,
        secondary = GlowApricot,
        onSecondary = LightPalette.ink,
        tertiary = GlowRose,
        onTertiary = LightPalette.ink,
        background = palette.canvas,
        onBackground = palette.ink,
        surface = palette.card,
        onSurface = palette.ink,
        surfaceVariant = palette.canvas,
        onSurfaceVariant = palette.muted,
        outline = palette.hairline,
        outlineVariant = palette.hairline,
        /*
         * Material's own containers (the date and time pickers, menus,
         * dialogs) default to its baseline lavender. They take the app's
         * card, canvas and ink instead, so a picker looks like the app.
         */
        primaryContainer = palette.canvas,
        onPrimaryContainer = palette.ink,
        secondaryContainer = palette.canvas,
        onSecondaryContainer = palette.ink,
        tertiaryContainer = palette.canvas,
        onTertiaryContainer = palette.ink,
        surfaceTint = palette.card,
        surfaceBright = palette.card,
        surfaceDim = palette.canvas,
        surfaceContainerLowest = palette.card,
        surfaceContainerLow = palette.card,
        surfaceContainer = palette.card,
        surfaceContainerHigh = palette.card,
        surfaceContainerHighest = palette.canvas,
        inverseSurface = palette.ink,
        inverseOnSurface = palette.card,
        inversePrimary = palette.canvas,
    )
}

/*
 * Devanagari and Bengali take no tracking: the big styles' negative letter
 * spacing cuts the headline stroke and, on Android, breaks words between
 * letters ("कन् / या"). The web does the same under :lang(hi) and :lang(bn).
 */
private val UNTRACKED_SCRIPTS = setOf("hi", "bn")

private fun untracked(style: TextStyle) = style.copy(letterSpacing = 0.sp)

private val UntrackedTypography = Typography.run {
    copy(
        displaySmall = untracked(displaySmall), headlineLarge = untracked(headlineLarge),
        headlineSmall = untracked(headlineSmall), titleLarge = untracked(titleLarge),
        titleMedium = untracked(titleMedium), titleSmall = untracked(titleSmall),
        bodyLarge = untracked(bodyLarge), bodyMedium = untracked(bodyMedium), bodySmall = untracked(bodySmall),
        labelLarge = untracked(labelLarge), labelMedium = untracked(labelMedium), labelSmall = untracked(labelSmall),
    )
}

/** Whether the app is drawing dark right now, from the choice and the phone's setting. */
@Composable
fun isLiquidDark(): Boolean = LookPreferences.theme.isDark(isSystemInDarkTheme())

@Composable
fun LagnaAtelierTheme(dark: Boolean = isLiquidDark(), content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    /* Set during composition so this very frame reads the right colours. */
    LiquidTheme.palette = palette
    SideEffect { LiquidTheme.palette = palette }
    val scheme = remember(palette) { schemeFor(palette) }
    val language = LocalConfiguration.current.locales[0].language
    val typography = if (language in UNTRACKED_SCRIPTS) UntrackedTypography else Typography
    MaterialTheme(colorScheme = scheme, typography = typography) {
        /* Text with no colour of its own takes this, which Material leaves black outside a Surface. */
        CompositionLocalProvider(LocalContentColor provides palette.ink, content = content)
    }
}
