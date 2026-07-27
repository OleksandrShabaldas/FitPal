package com.fitpal.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * FitPal theme — dark only.
 *
 * The premium look depends on a dark base (glowing backdrops + frosted glass),
 * so there is no light scheme. The `darkTheme` / `dynamicColor` parameters are
 * kept for call-site compatibility but ignored.
 */
private val FitPalDarkColors = darkColorScheme(
    primary = GoldLight,
    onPrimary = Color(0xFF3A2406),
    primaryContainer = Color(0xFF3A2616),
    onPrimaryContainer = Cream,
    secondary = Color(0xFFD9C5A8),
    onSecondary = Color(0xFF2A1D0C),
    secondaryContainer = Color(0xFF33291C),
    onSecondaryContainer = Cream,
    tertiary = AccentActivity,
    onTertiary = Color(0xFF052420),
    tertiaryContainer = Color(0xFF0E3A35),
    onTertiaryContainer = Color(0xFFBFEFE8),
    background = InkBlack,
    onBackground = Cream,
    surface = Ink,
    onSurface = Cream,
    surfaceVariant = Color(0xFF2A211B),
    onSurfaceVariant = CreamMuted,
    surfaceTint = GoldLight,
    inverseSurface = Cream,
    inverseOnSurface = Ink,
    outline = Color(0x40FFFFFF),
    outlineVariant = Color(0x1FFFFFFF),
    error = ScorePoor,
    onError = Color(0xFF3A0A06),
    errorContainer = Color(0xFF5C1813),
    onErrorContainer = Color(0xFFFFD8D2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A211B),
    surfaceDim = InkBlack,
    surfaceContainerLowest = Color(0xFF0A0807),
    surfaceContainerLow = Color(0xFF161109),
    surfaceContainer = Color(0xFF1B150D),
    surfaceContainerHigh = Color(0xFF231C12),
    surfaceContainerHighest = Color(0xFF2D2417)
)

@Composable
fun FitPalTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = FitPalDarkColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            // Dark backdrop → light (white) status/nav icons.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FitPalTypography,
        shapes = FitPalShapes,
        content = content
    )
}
