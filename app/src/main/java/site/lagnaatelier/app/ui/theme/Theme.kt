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
    MaterialTheme(colorScheme = scheme, typography = Typography) {
        /* Text with no colour of its own takes this, which Material leaves black outside a Surface. */
        CompositionLocalProvider(LocalContentColor provides palette.ink, content = content)
    }
}
