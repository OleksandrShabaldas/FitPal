package com.fitpal.app.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * FitPal theme — whatever look is current ([LookState]): Material components (switches, dialogs,
 * text fields) get a colour scheme built from the look's tokens, the type and shapes follow the
 * look, and the system bars get dark or light icons to match. The `darkTheme` / `dynamicColor`
 * parameters are kept for call-site compatibility but ignored — Settings → Appearance decides.
 */
@Composable
fun FitPalTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val look = LookState.look
    // Material You hands Material its real scheme (every component then looks like a Pixel app's);
    // the other looks build one from their own tokens.
    val colorScheme = remember(look) { look.scheme ?: colorSchemeFor(look.colors) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            // Behind everything (and seen for a frame before Compose draws): the look's own base.
            window.decorView.setBackgroundColor(look.colors.inkBlack.toArgb())
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = look.isLight
                isAppearanceLightNavigationBars = look.isLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = remember(look.style) { typographyFor(look.style) },
        shapes = remember(look) { fitPalShapes(look.style) },
        content = content
    )
}

/** The Material colour scheme for a look, built from FitPal's own tokens. */
private fun colorSchemeFor(c: FitPalColors): ColorScheme {
    val surfaceVariant = lerp(c.ink, c.cream, if (c.isLight) 0.06f else 0.10f)
    fun container(k: Float) = lerp(c.inkBlack, c.cream, k)
    return if (c.isLight) lightColorScheme(
        primary = c.goldLight,
        onPrimary = c.onGold,
        primaryContainer = lerp(c.ink, c.goldLight, 0.18f),
        onPrimaryContainer = c.cream,
        secondary = lerp(c.cream, c.goldLight, 0.4f),
        onSecondary = c.ink,
        secondaryContainer = lerp(c.ink, c.goldLight, 0.10f),
        onSecondaryContainer = c.cream,
        tertiary = c.accentActivity,
        onTertiary = Color.White,
        background = c.inkBlack,
        onBackground = c.cream,
        surface = c.ink,
        onSurface = c.cream,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = c.creamMuted,
        surfaceTint = c.goldLight,
        inverseSurface = c.cream,
        inverseOnSurface = c.ink,
        outline = c.cream.copy(alpha = 0.30f),
        outlineVariant = c.cream.copy(alpha = 0.14f),
        error = c.scorePoor,
        onError = Color.White,
        scrim = Color.Black,
        surfaceBright = c.ink,
        surfaceDim = container(0.06f),
        surfaceContainerLowest = c.ink,
        surfaceContainerLow = container(0.02f),
        surfaceContainer = container(0.04f),
        surfaceContainerHigh = container(0.07f),
        surfaceContainerHighest = container(0.10f)
    ) else darkColorScheme(
        primary = c.goldLight,
        onPrimary = c.onGold,
        primaryContainer = lerp(c.ink, c.gold, 0.25f),
        onPrimaryContainer = c.cream,
        secondary = lerp(c.cream, c.goldLight, 0.3f),
        onSecondary = c.ink,
        secondaryContainer = lerp(c.ink, c.gold, 0.15f),
        onSecondaryContainer = c.cream,
        tertiary = c.accentActivity,
        onTertiary = Color(0xFF052420),
        tertiaryContainer = lerp(c.ink, c.accentActivity, 0.25f),
        onTertiaryContainer = c.cream,
        background = c.inkBlack,
        onBackground = c.cream,
        surface = c.ink,
        onSurface = c.cream,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = c.creamMuted,
        surfaceTint = c.goldLight,
        inverseSurface = c.cream,
        inverseOnSurface = c.ink,
        outline = Color(0x40FFFFFF),
        outlineVariant = Color(0x1FFFFFFF),
        error = c.scorePoor,
        onError = Color(0xFF3A0A06),
        errorContainer = Color(0xFF5C1813),
        onErrorContainer = Color(0xFFFFD8D2),
        scrim = Color.Black,
        surfaceBright = surfaceVariant,
        surfaceDim = c.inkBlack,
        surfaceContainerLowest = lerp(c.inkBlack, Color.Black, 0.3f),
        surfaceContainerLow = container(0.03f),
        surfaceContainer = container(0.05f),
        surfaceContainerHigh = container(0.08f),
        surfaceContainerHighest = container(0.12f)
    )
}
