@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.fitpal.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fitpal.app.R

/*
 * Two bundled, offline families:
 *  - Fraunces (serif) for big headlines & section titles — with a true italic for
 *    emphasis words ("Looking *good* today").
 *  - Inter (sans) for everything else, with tabular figures so numbers don't jiggle.
 *
 * Both are variable fonts; we pin the weight axis per style. minSdk 28 supports
 * variation settings.
 */

private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

private fun fraunces(weight: Int) = Font(
    R.font.fraunces_variable,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

private fun frauncesItalic(weight: Int) = Font(
    R.font.fraunces_italic,
    weight = FontWeight(weight),
    style = FontStyle.Italic,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Roboto Flex (Material You's face) at a weight and an optical size — bigger sizes draw tighter. */
private fun robotoFlex(weight: Int, opticalSize: Float) = Font(
    R.font.roboto_flex,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize))
)

val InterFamily = FontFamily(
    inter(400), inter(500), inter(600), inter(700), inter(800)
)

val FrauncesFamily = FontFamily(
    fraunces(400), fraunces(500), fraunces(600), fraunces(700),
    frauncesItalic(400), frauncesItalic(500), frauncesItalic(600)
)

/** Material You's text face: Roboto Flex at text optical size. */
val RobotoFamily = FontFamily(
    robotoFlex(400, 14f), robotoFlex(500, 14f), robotoFlex(600, 14f), robotoFlex(700, 14f), robotoFlex(800, 14f)
)

/** Material You's display face: the same Roboto Flex at a display optical size. */
val RobotoDisplayFamily = FontFamily(
    robotoFlex(400, 36f), robotoFlex(500, 36f), robotoFlex(600, 36f), robotoFlex(700, 36f), robotoFlex(800, 36f)
)

/**
 * The face for the big hand-sized numbers (hero rings, tracker heroes): Inter, or Roboto Flex at
 * display size in Material You. A getter, so it follows the look.
 */
val NumberFamily: FontFamily get() = if (LookState.style == LookStyle.MATERIAL) RobotoDisplayFamily else InterFamily

private const val TNUM = "tnum"

/**
 * The type for a look:
 *  - Glow and Paper: Fraunces headlines over Inter ([FitPalTypography]).
 *  - Liquid glass: Inter only, at iPhone metrics — large bold titles (34 sp, tight tracking), 17 sp
 *    body, 13 sp captions. (Fraunces stays in Home's greeting only; SF Pro can't be bundled.)
 *  - Material You: Roboto Flex with Material 3's type scale (FitPal's own display sizes are kept
 *    for the hero numbers so nothing is pushed out of its card).
 */
fun typographyFor(style: LookStyle): Typography = when (style) {
    LookStyle.LIQUID -> LiquidTypography
    LookStyle.MATERIAL -> MaterialTypography
    else -> FitPalTypography
}

private fun sans(size: Float, line: Float, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = InterFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = tracking.sp, fontFeatureSettings = TNUM
)

private fun roboto(size: Float, line: Float, weight: FontWeight, tracking: Float = 0f, display: Boolean = false) = TextStyle(
    fontFamily = if (display) RobotoDisplayFamily else RobotoFamily, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp, fontFeatureSettings = TNUM
)

private val LiquidTypography = Typography(
    displayLarge = sans(40f, 46f, FontWeight.Bold, -0.6f),
    displayMedium = sans(32f, 38f, FontWeight.Bold, -0.5f),
    displaySmall = sans(26f, 32f, FontWeight.Bold, -0.4f),
    headlineLarge = sans(34f, 41f, FontWeight.Bold, -0.4f),
    headlineMedium = sans(28f, 34f, FontWeight.Bold, -0.3f),
    headlineSmall = sans(22f, 28f, FontWeight.SemiBold, -0.2f),
    titleLarge = sans(20f, 25f, FontWeight.SemiBold, -0.2f),
    titleMedium = sans(16f, 21f, FontWeight.SemiBold, -0.2f),
    titleSmall = sans(15f, 20f, FontWeight.SemiBold, -0.1f),
    bodyLarge = sans(17f, 22f, FontWeight.Normal, -0.2f),
    bodyMedium = sans(15f, 20f, FontWeight.Normal, -0.1f),
    bodySmall = sans(13f, 18f, FontWeight.Normal),
    labelLarge = sans(14f, 19f, FontWeight.Medium, -0.1f),
    labelMedium = sans(13f, 18f, FontWeight.Medium),
    labelSmall = sans(11f, 13f, FontWeight.Medium, 0.1f)
)

private val MaterialTypography = Typography(
    displayLarge = roboto(40f, 48f, FontWeight.Medium, -0.25f, display = true),
    displayMedium = roboto(32f, 40f, FontWeight.Medium, display = true),
    displaySmall = roboto(26f, 34f, FontWeight.Normal, display = true),
    headlineLarge = roboto(32f, 40f, FontWeight.Normal, display = true),
    headlineMedium = roboto(28f, 36f, FontWeight.Normal, display = true),
    headlineSmall = roboto(24f, 32f, FontWeight.Normal, display = true),
    titleLarge = roboto(22f, 28f, FontWeight.Normal),
    titleMedium = roboto(16f, 24f, FontWeight.Medium, 0.15f),
    titleSmall = roboto(14f, 20f, FontWeight.Medium, 0.1f),
    bodyLarge = roboto(16f, 24f, FontWeight.Normal, 0.5f),
    bodyMedium = roboto(14f, 20f, FontWeight.Normal, 0.25f),
    bodySmall = roboto(12f, 16f, FontWeight.Normal, 0.4f),
    labelLarge = roboto(14f, 20f, FontWeight.Medium, 0.1f),
    labelMedium = roboto(12f, 16f, FontWeight.Medium, 0.5f),
    labelSmall = roboto(11f, 16f, FontWeight.Medium, 0.5f)
)

val FitPalTypography = Typography(
    // ---- Display & headline → Fraunces (serif) ----
    displayLarge = TextStyle(
        fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp, lineHeight = 46.sp, fontFeatureSettings = TNUM
    ),
    displayMedium = TextStyle(
        fontFamily = FrauncesFamily, fontWeight = FontWeight.Medium,
        fontSize = 32.sp, lineHeight = 38.sp, fontFeatureSettings = TNUM
    ),
    displaySmall = TextStyle(
        fontFamily = FrauncesFamily, fontWeight = FontWeight.Medium,
        fontSize = 26.sp, lineHeight = 32.sp, fontFeatureSettings = TNUM
    ),
    headlineLarge = TextStyle(
        fontFamily = FrauncesFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = TNUM
    ),
    headlineMedium = TextStyle(
        fontFamily = FrauncesFamily, fontWeight = FontWeight.Medium,
        fontSize = 23.sp, lineHeight = 29.sp, fontFeatureSettings = TNUM
    ),
    headlineSmall = TextStyle(
        fontFamily = FrauncesFamily, fontWeight = FontWeight.Medium,
        fontSize = 19.sp, lineHeight = 25.sp, fontFeatureSettings = TNUM
    ),

    // ---- Titles / body / labels → Inter (sans) ----
    titleLarge = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, fontFeatureSettings = TNUM
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = TNUM
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, fontFeatureSettings = TNUM
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TNUM
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
        fontFeatureSettings = TNUM
    )
)
