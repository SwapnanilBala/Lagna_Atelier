package site.lagnaatelier.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/*
 * The Liquid palette: black, white and cool greys, and one coloured thing on
 * the screen -- the golden-hour glow behind the key cards. Taken from the
 * "Lagna Liquid" mock with the golden-hour glow the owner chose.
 *
 * Two of them. Dark is the same look at night rather than an inversion: a deep
 * warm ink canvas, cards raised by being lighter than it (a shadow barely
 * shows on dark), warm paper for the type, the black pills turned to paper,
 * and the glow kept but quieter.
 *
 * The names below (Ink, Canvas, CardWhite...) read the palette in use, so
 * every screen written against them follows the theme without change. They
 * are plain getters over snapshot state rather than a CompositionLocal on
 * purpose: they work in drawing lambdas and plain functions as well as in
 * composables, and Compose repaints whatever read them when the theme flips.
 * The names keep their light-theme meaning: "Ink" is the colour of type and
 * of the main pill, "CardWhite" the colour of a card and of type on a pill.
 */

@Immutable
data class LiquidPalette(
    val isDark: Boolean,
    val ink: Color,
    val canvas: Color,
    val card: Color,
    val muted: Color,
    val hairline: Color,
    val cardShadow: Color,
    /** The three glow patches, in draw order. */
    val glow: List<Color>,
    /** A warm mark on an Ink ground: the selected tab's icon. */
    val glowOnInk: Color,
    /** Between a hairline and type: bar fills and drawn outlines. */
    val midTone: Color,
    /** The warm ground behind a palm photo. */
    val photoGround: Color,
)

val GlowHoney = Color(0xFFFFD59A)
val GlowApricot = Color(0xFFFFB38A)
val GlowRose = Color(0xFFF4A6B8)

/*
 * Contrast (WCAG 2.x), checked when these were chosen:
 *   light  Ink on card 19.6, Muted on card 5.0, Muted on canvas 4.7
 *   dark   Ink on card 14.8, Muted on card 6.8, Muted on canvas 7.5,
 *          card-coloured type on the paper pill 14.8, selected tab icon 5.2
 * All AA for body text. Hairline is a divider, not type.
 */
val LightPalette = LiquidPalette(
    isDark = false,
    ink = Color(0xFF0B0B0F),
    canvas = Color(0xFFF7F8FB),
    card = Color(0xFFFFFFFF),
    muted = Color(0xFF6B6F7B),
    hairline = Color(0xFFE6E8EE),
    /* Card shadows are tinted, not grey, so they read as light rather than dirt. */
    cardShadow = Color(0x331E2850),
    glow = listOf(GlowHoney, GlowApricot, GlowRose),
    glowOnInk = GlowHoney,
    midTone = Color(0xFFC9CDD6),
    photoGround = Color(0xFFE6E1DA),
)

val DarkPalette = LiquidPalette(
    isDark = true,
    ink = Color(0xFFF3EEE7),
    canvas = Color(0xFF131116),
    card = Color(0xFF1E1B21),
    muted = Color(0xFFA8A2AC),
    hairline = Color(0xFF34303A),
    cardShadow = Color(0x80000000),
    /* The same three hues, dimmed: at full strength they read as a stain on a dark ground. */
    glow = listOf(GlowHoney.copy(alpha = 0.42f), GlowApricot.copy(alpha = 0.36f), GlowRose.copy(alpha = 0.32f)),
    glowOnInk = Color(0xFFA24A12),
    midTone = Color(0xFF57525C),
    photoGround = Color(0xFF2A2523),
)

/** The palette in use. Set by [LagnaAtelierTheme]; light until it runs. */
object LiquidTheme {
    var palette by mutableStateOf(LightPalette)
        internal set
}

val Ink: Color get() = LiquidTheme.palette.ink
val Canvas: Color get() = LiquidTheme.palette.canvas
val CardWhite: Color get() = LiquidTheme.palette.card
val Muted: Color get() = LiquidTheme.palette.muted
val Hairline: Color get() = LiquidTheme.palette.hairline
val CardShadow: Color get() = LiquidTheme.palette.cardShadow
val GlowOnInk: Color get() = LiquidTheme.palette.glowOnInk
val MidTone: Color get() = LiquidTheme.palette.midTone
val PhotoGround: Color get() = LiquidTheme.palette.photoGround

/* In draw order: the largest, warmest patch first. */
val GoldenGlow: List<Color> get() = LiquidTheme.palette.glow
