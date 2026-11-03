package site.lagnaatelier.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import site.lagnaatelier.app.R

/*
 * Geist for everything you read, Geist Mono for degrees and dates.
 *
 * Both are variable fonts (weight 100-900) bundled in res/font, converted from
 * the WOFF2 files Next.js ships, so the app needs neither Play Services nor a
 * network to draw its type. Licence: licenses/Geist-OFL.txt.
 *
 * Each weight names its own variation setting: without it Android would load
 * the font's default instance for every weight and fake the bold.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: Int) = Font(
    resId,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Geist = FontFamily(
    variable(R.font.geist, 300),
    variable(R.font.geist, 400),
    variable(R.font.geist, 500),
    variable(R.font.geist, 600),
)

val GeistMono = FontFamily(
    variable(R.font.geist_mono, 400),
    variable(R.font.geist_mono, 500),
)

/*
 * The reference's signature is very large type at a light weight with the
 * letters pulled together, so the big styles carry negative tracking.
 */
val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-0.045).em,
    ),
    headlineLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-0.04).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 26.sp, letterSpacing = (-0.035).em,
    ),
    titleLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 27.sp, lineHeight = 29.sp, letterSpacing = (-0.04).em,
    ),
    titleMedium = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.01).em,
    ),
    titleSmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = (-0.03).em,
    ),
    bodyLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp, lineHeight = 19.5.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp, lineHeight = 15.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 14.sp,
    ),
)

/* Styles Material has no slot for. */
object LiquidType {
    /** The big figure on a card: "36", "56%". */
    val figure = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 40.sp, lineHeight = 38.sp, letterSpacing = (-0.04).em,
    )

    /** Sign names in the chart grid. */
    val gridLabel = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Normal,
        fontSize = 9.sp, lineHeight = 11.sp,
    )
}
