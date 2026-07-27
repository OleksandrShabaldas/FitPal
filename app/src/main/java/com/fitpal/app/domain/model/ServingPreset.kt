package com.fitpal.app.domain.model

/**
 * A quick-tap portion size, e.g. "Can" = 330g. Shown as a chip on editable food
 * rows so the user can set an amount without typing. Customizable in Settings.
 */
data class ServingPreset(
    val label: String,
    val grams: Float
)
