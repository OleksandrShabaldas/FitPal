package com.fitpal.app.domain.model

/**
 * A single ingredient with its weight and nutritional values.
 * The per-100g values are fixed (from USDA / AI); the actual values
 * recalculate automatically when grams changes.
 */
data class Ingredient(
    val name: String,
    val grams: Float,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float,
    val fiberPer100g: Float = 0f,
    /** ml of pure water per 100 g/ml — used for hydration tracking (drinks). */
    val waterMlPer100g: Float = 0f,
    /** True for drinks (cola, juice, milk…) — measured in ml and counts toward hydration. */
    val isDrink: Boolean = false,
    /** Vitamins & minerals per 100 g. */
    val microsPer100g: Micronutrients = Micronutrients()
) {
    /** Actual calories for this amount of this ingredient. */
    val calories: Float get() = caloriesPer100g * grams / 100f
    val protein: Float get() = proteinPer100g * grams / 100f
    val fat: Float get() = fatPer100g * grams / 100f
    val carbs: Float get() = carbsPer100g * grams / 100f
    val fiber: Float get() = fiberPer100g * grams / 100f

    /** Net pure water in this amount (ml). */
    val waterMl: Float get() = waterMlPer100g * grams / 100f

    /** Actual micronutrient amounts for this portion. */
    val micros: Micronutrients get() = microsPer100g.scaledTo(grams)

    /** Create a copy with a different weight — calories etc. recalculate automatically. */
    fun withGrams(newGrams: Float): Ingredient = copy(grams = newGrams)
}
