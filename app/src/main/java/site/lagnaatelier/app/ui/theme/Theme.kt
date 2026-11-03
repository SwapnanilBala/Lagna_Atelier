package site.lagnaatelier.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/*
 * Light only, like the reference. A dark version is a later decision: the
 * look depends on white cards floating over a pale canvas, and it needs its
 * own design rather than an inverted palette.
 *
 * Dynamic colour (Material You) is deliberately not used. It would repaint the
 * app from the phone's wallpaper, and the golden-hour glow would be gone.
 */
private val LiquidColors = lightColorScheme(
    primary = Ink,
    onPrimary = CardWhite,
    secondary = GlowApricot,
    onSecondary = Ink,
    tertiary = GlowRose,
    onTertiary = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = Canvas,
    onSurfaceVariant = Muted,
    outline = Hairline,
    outlineVariant = Hairline,
)

@Composable
fun LagnaAtelierTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LiquidColors,
        typography = Typography,
        content = content,
    )
}
