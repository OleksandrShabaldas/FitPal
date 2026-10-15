package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * USDA FoodData Central nutritional entry.
 * This table is pre-populated from the USDA database (shipped as a bundled SQLite asset).
 * Used for looking up nutritional values when building ingredient lists.
 */
@Entity(tableName = "usda_foods")
data class UsdaFoodEntity(
    @PrimaryKey
    val fdcId: Int,
    val description: String,
    val caloriesPer100g: Float,
    val proteinPer100g: Float,
    val fatPer100g: Float,
    val carbsPer100g: Float,
    val commonServingGrams: Float? = null,
    val foodCategory: String? = null,
    /**
     * Dietary fibre per 100 g; 0 = not known. The built-in foods ship it, USDA imports read nutrient
     * 1079 and Open Food Facts its `fiber_100g`, so a database apple comes with its fibre straight
     * away, offline. Whatever is still 0 is filled later by the background check (NutrientFiller).
     */
    val fiberPer100g: Float = 0f,
    /**
     * Known to be a drink from its source — Open Food Facts' own beverage categories / an ml quantity,
     * or the built-in "Drinks" rows. False just means "not known": [com.fitpal.app.domain.Drinks]
     * still judges the name. Kept on the row so a cached scan of a bottle of water stays a drink.
     */
    val isDrink: Boolean = false
)
