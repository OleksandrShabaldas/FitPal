package com.fitpal.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Generously rounded corners for the dark/glass look.
 *
 *  - Cards use `medium` (22dp)
 *  - Hero/glass cards use `large` (26dp)
 *  - Dialogs/sheets use `extraLarge` (34dp)
 */
val FitPalShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)
