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
    val foodCategory: String? = null
)
