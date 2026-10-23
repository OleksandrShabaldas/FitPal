package com.fitpal.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Generously rounded corners, scaled per look (Paper is squarer, Liquid glass rounder) and by the
 * user's roundness knob (see [LookShapes]).
 *
 *  - Cards use `medium` (22dp in Glow)
 *  - Hero/glass cards use `large` (26dp in Glow)
 *  - Dialogs/sheets use `extraLarge` (34dp in Glow)
 *
 * Material You uses Material's own shape scale instead (8 / 12 / 16 / 28), so its buttons, chips,
 * dialogs and sheets have the exact corners a Pixel app's do.
 */
fun fitPalShapes(style: LookStyle = LookState.style): Shapes =
    if (style == LookStyle.MATERIAL) {
        val k = LookState.appearance.roundness
        Shapes(
            extraSmall = RoundedCornerShape((4 * k).dp),
            small = RoundedCornerShape((8 * k).dp),
            medium = RoundedCornerShape((12 * k).dp),
            large = RoundedCornerShape((16 * k).dp),
            extraLarge = RoundedCornerShape((28 * k).dp)
        )
    } else Shapes(
        extraSmall = RoundedCornerShape(LookShapes.radius(12.dp)),
        small = RoundedCornerShape(LookShapes.radius(16.dp)),
        medium = RoundedCornerShape(LookShapes.radius(22.dp)),
        large = RoundedCornerShape(LookShapes.radius(26.dp)),
        extraLarge = RoundedCornerShape(LookShapes.radius(34.dp))
    )

/** The original Glow shapes (kept for anything that wants them regardless of look). */
val FitPalShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)
