package com.fitpal.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/**
 * A pared-down take on FitPal's dark, warm palette for the watch: cream text, a warm amber accent,
 * black/near-black surfaces (easy on the OLED and consistent with the phone's design).
 */
private val Amber = Color(0xFFEBA35A)
private val Cream = Color(0xFFF2E7D5)
private val Surface = Color(0xFF1B140D)

private val FitPalWearColors = Colors(
    primary = Amber,
    primaryVariant = Color(0xFFC9823B),
    secondary = Color(0xFF8FB7C9),
    background = Color.Black,
    surface = Surface,
    error = Color(0xFFE07A6B),
    onPrimary = Color(0xFF1B140D),
    onSecondary = Color(0xFF10171B),
    onBackground = Cream,
    onSurface = Cream,
    onError = Color(0xFF1B140D)
)

@Composable
fun FitPalWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = FitPalWearColors, content = content)
}
