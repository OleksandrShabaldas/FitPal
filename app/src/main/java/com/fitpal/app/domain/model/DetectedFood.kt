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

    /**
     * The ingredients with their food/drink flags made consistent with the dish (docs/redesign/16): a
     * drink whose parts were never flagged one by one (a single database or typed-in drink) counts
     * every part as a drink.
     */
    val drinkAwareIngredients: List<Ingredient>
        get() = if (isDrink && ingredients.none { it.isDrink }) ingredients.map { it.copy(isDrink = true) } else ingredients

    /** Water from the drink ingredients (ml) — "drinks"; the rest of [totalWaterMl] is "from food". */
    val drinkWaterMl: Float
        get() = drinkAwareIngredients.filter { it.isDrink }.sumOf { it.waterMl.toDouble() }.toFloat()

    /**
     * The cup on one ingredient: it becomes a drink (in ml, its water counting as drinks — with an
     * estimate when it had none) or food again. The dish is a drink only when every part is.
     */
    fun withIngredientDrink(index: Int, drink: Boolean): DetectedFood {
        val list = drinkAwareIngredients.toMutableList()
        val ing = list.getOrNull(index) ?: return this
        if (ing.isDrink == drink) return this
        list[index] = ing.asDrinkPart(drink)
        return copy(ingredients = list, isDrink = list.all { it.isDrink })
    }

    /** Caffeine across all ingredients (mg), or null while any of them is unknown. */
    val totalCaffeineMg: Float?
        get() = totalCaffeineOf(ingredients)

    /**
     * The Food / Drink switch: a drink is measured in ml and counts toward the day's water, so a
     * drink gets a water estimate for any part that had none. Turning it back into a food keeps the
     * water it had (food water still counts, as "from food").
     */
    fun asDrink(drink: Boolean): DetectedFood =
        if (drink == isDrink && ingredients.all { it.isDrink == drink }) this
        else copy(isDrink = drink, ingredients = ingredients.map { it.asDrinkPart(drink) })
}

/** This ingredient as a drink part (with a water estimate when it had none) or as food. */
fun Ingredient.asDrinkPart(drink: Boolean): Ingredient = when {
    !drink -> copy(isDrink = false)
    waterMlPer100g > 0f -> copy(isDrink = true)
    else -> copy(
        isDrink = true,
        waterMlPer100g = com.fitpal.app.domain.Drinks.estimateWaterPer100(carbsPer100g, proteinPer100g, fatPer100g)
    )
}

/** "370 g", "250 ml", or "170 g + 200 ml" for a dish with both. */
fun weightLabel(ingredients: List<Ingredient>, fallbackGrams: Float = 0f, fallbackDrink: Boolean = false): String {
    if (ingredients.isEmpty()) return "${fallbackGrams.toInt()} ${if (fallbackDrink) "ml" else "g"}"
    val g = ingredients.filterNot { it.isDrink }.sumOf { it.grams.toDouble() }.toInt()
    val ml = ingredients.filter { it.isDrink }.sumOf { it.grams.toDouble() }.toInt()
    return when {
        ml == 0 -> "$g g"
        g == 0 -> "$ml ml"
        else -> "$g g + $ml ml"
    }
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

/**
 * This dish with [totalMl] of water in it, spread over its ingredients — the 💧 field on every dish card
 * (photo, describe and logged meals). Null when it can't change (negative, or a dish with no weight).
 */
fun DetectedFood.withWaterTotal(totalMl: Float): DetectedFood? =
    ingredients.withWaterTotal(totalMl)?.let { copy(ingredients = it) }

/**
 * These ingredients holding [totalMl] of water in all: scaled in proportion when they already hold some,
 * otherwise spread evenly per 100 g. Null when it can't change (negative, or no weight at all).
 */
fun List<Ingredient>.withWaterTotal(totalMl: Float): List<Ingredient>? {
    if (totalMl < 0f) return null
    val totalGrams = sumOf { it.grams.toDouble() }.toFloat()
    if (totalGrams <= 0f) return null
    val currentWater = sumOf { it.waterMl.toDouble() }.toFloat()
    return if (currentWater > 0f) {
        val factor = totalMl / currentWater
        map { it.copy(waterMlPer100g = it.waterMlPer100g * factor) }
    } else {
        val per100 = totalMl / totalGrams * 100f
        map { it.copy(waterMlPer100g = per100) }
    }
}

/**
 * This dish as [servings] of it (the Amount stepper): every ingredient's weight scaled by the change, so the
 * grams stay the single source of truth. Null when nothing changes.
 */
fun DetectedFood.withServings(servings: Int): DetectedFood? {
    val n = servings.coerceIn(1, 50)
    val current = this.servings.coerceAtLeast(1)
    if (n == current) return null
    val factor = n.toFloat() / current
    return copy(servings = n, ingredients = ingredients.map { it.withGrams(it.grams * factor) })
}
