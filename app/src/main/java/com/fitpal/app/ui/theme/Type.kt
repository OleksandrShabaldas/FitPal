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

val InterFamily = FontFamily(
    inter(400), inter(500), inter(600), inter(700), inter(800)
)

val FrauncesFamily = FontFamily(
    fraunces(400), fraunces(500), fraunces(600), fraunces(700),
    frauncesItalic(400), frauncesItalic(500), frauncesItalic(600)
)

private const val TNUM = "tnum"

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
