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
    /** What the AI couldn't tell about this dish, as choices for one ingredient each (pre-log only). */
    val choices: List<ChoiceGroup> = emptyList(),
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

    /** Caffeine across all ingredients (mg), or null while any of them is unknown. */
    val totalCaffeineMg: Float?
        get() = totalCaffeineOf(ingredients)

    /**
     * The Food / Drink switch: a drink is measured in ml and counts toward the day's water, so a
     * drink gets a water estimate for any part that had none. Turning it back into a food keeps the
     * water it had (food water still counts, as "from food").
     */
    fun asDrink(drink: Boolean): DetectedFood =
        if (drink == isDrink) this
        else copy(
            isDrink = drink,
            ingredients = if (!drink) ingredients else ingredients.map { ing ->
                if (ing.waterMlPer100g > 0f) ing
                else ing.copy(
                    waterMlPer100g = com.fitpal.app.domain.Drinks.estimateWaterPer100(
                        ing.carbsPer100g, ing.proteinPer100g, ing.fatPer100g
                    )
                )
            }
        )
}

/**
 * Something about a dish the AI couldn't tell from the photo or the words (docs/redesign/15):
 * which of [options] the ingredient labelled [label] really is — okroshka's base, a pierogi filling,
 * tomato or red pepper. [selected] is the option in the dish now (the AI's guess to start with).
 * Picking another replaces that ingredient at its current weight ([withChoice]); nothing is ever
 * added. Shown as "Not sure about" on the review card; never stored.
 */
data class ChoiceGroup(
    val label: String,
    val options: List<Ingredient>,
    val selected: Int = 0
) {
    val current: Ingredient? get() = options.getOrNull(selected)
}

/**
 * This dish with option [option] of choice [group] in place of the ingredient chosen now — at that
 * ingredient's current weight, so a portion the user changed stays. Null when nothing changes (same
 * option, or that ingredient was removed or renamed since).
 */
fun DetectedFood.withChoice(group: Int, option: Int): DetectedFood? {
    val g = choices.getOrNull(group) ?: return null
    if (option == g.selected) return null
    val pick = g.options.getOrNull(option) ?: return null
    val now = g.current ?: return null
    val idx = ingredients.indexOfFirst { it.name.equals(now.name, ignoreCase = true) }
    if (idx < 0) return null
    val swapped = ingredients.toMutableList().apply { this[idx] = pick.copy(grams = ingredients[idx].grams) }
    val picked = choices.toMutableList().apply { this[group] = g.copy(selected = option) }
    return copy(ingredients = swapped, choices = picked)
}
