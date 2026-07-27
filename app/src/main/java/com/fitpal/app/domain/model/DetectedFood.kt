package com.fitpal.app.domain.model

import android.graphics.RectF

/**
 * A food item detected in a photo by the ML pipeline.
 * Contains everything needed to display and edit on the analysis screen.
 */
data class DetectedFood(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val estimatedGrams: Float,
    val ingredients: List<Ingredient>,
    val variations: List<FoodVariation> = emptyList(),
    /** True if this is a drink (measured in ml, contributes to water intake). */
    val isDrink: Boolean = false,
    /**
     * How many of this item were eaten (UI-only multiplier on the analysis screen — the
     * ingredient grams are already scaled to match, so logging needs no special handling).
     */
    val servings: Int = 1
) {
    /** Total calories from all ingredients at their current weights. */
    val totalCalories: Float
        get() = ingredients.sumOf { it.calories.toDouble() }.toFloat()

    val totalProtein: Float
        get() = ingredients.sumOf { it.protein.toDouble() }.toFloat()

    val totalFat: Float
        get() = ingredients.sumOf { it.fat.toDouble() }.toFloat()

    val totalCarbs: Float
        get() = ingredients.sumOf { it.carbs.toDouble() }.toFloat()

    val totalFiber: Float
        get() = ingredients.sumOf { it.fiber.toDouble() }.toFloat()

    /** Summed micronutrients across all ingredients at their current weights. */
    val totalMicros: Micronutrients
        get() = ingredients.fold(Micronutrients()) { acc, ing -> acc + ing.micros }

    /** Current total weight from all ingredients (reflects any portion edits). */
    val totalGrams: Float
        get() = ingredients.sumOf { it.grams.toDouble() }.toFloat()

    /** Net pure water across all ingredients (ml) — for hydration tracking. */
    val totalWaterMl: Float
        get() = ingredients.sumOf { it.waterMl.toDouble() }.toFloat()
}

/**
 * A common variation of a food that the LLM suggests.
 * Shown as a popup with choices — e.g. "with skin?" or "marinated?"
 */
data class FoodVariation(
    val description: String,
    val ingredientChanges: List<Ingredient>,
    val isSelected: Boolean = false
)
