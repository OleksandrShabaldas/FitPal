package com.fitpal.app.domain.model

/**
 * Simple snapshot of nutritional totals — used for daily summaries
 * and anywhere we need a quick calories/protein/fat/carbs/fiber bundle.
 */
data class NutritionInfo(
    val calories: Float = 0f,
    val protein: Float = 0f,
    val fat: Float = 0f,
    val carbs: Float = 0f,
    val fiber: Float = 0f
) {
    operator fun plus(other: NutritionInfo) = NutritionInfo(
        calories = calories + other.calories,
        protein = protein + other.protein,
        fat = fat + other.fat,
        carbs = carbs + other.carbs,
        fiber = fiber + other.fiber
    )
}
