package site.lagnaatelier.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * The Liquid palette: black, white and cool greys, and one coloured thing on
 * the screen -- the golden-hour glow behind the key cards. Taken from the
 * "Lagna Liquid" mock with the golden-hour glow the owner chose.
 */

val Ink = Color(0xFF0B0B0F)
val Canvas = Color(0xFFF7F8FB)
val CardWhite = Color(0xFFFFFFFF)
val Muted = Color(0xFF6B6F7B)
val Hairline = Color(0xFFE6E8EE)

/* Card shadows are tinted, not grey, so they read as light rather than dirt. */
val CardShadow = Color(0x331E2850)

val GlowHoney = Color(0xFFFFD59A)
val GlowApricot = Color(0xFFFFB38A)
val GlowRose = Color(0xFFF4A6B8)

/* In draw order: the largest, warmest patch first. */
val GoldenGlow = listOf(GlowHoney, GlowApricot, GlowRose)
