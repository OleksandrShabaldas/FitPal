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
 */
fun fitPalShapes(): Shapes = Shapes(
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
