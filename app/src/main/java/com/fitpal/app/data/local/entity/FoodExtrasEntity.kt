package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The background nutrient check's answer for one food, cached so each food is only ever checked
 * once. The food database only carries calories + protein/fat/carbs, so a food logged from it has
 * no fibre, vitamins, minerals, water or caffeine; the quick AI fills those in (see
 * [com.fitpal.app.ml.NutrientFiller]) and the result lands here.
 *
 * [key] is `db:<fdcId>` for a food-database row, or `name:<name>|<kcal/100g>` for anything else (a
 * saved collection food, a typed custom food) — the same food logged again hits the cache and needs
 * no AI at all. [valuesJson] holds the per-100 g values the AI gave, keyed like the AI's own JSON
 * ("fiber", "caffeine", "vitC", …); only the fields that were asked are present.
 */
@Entity(tableName = "food_extras")
data class FoodExtrasEntity(
    @PrimaryKey
    val key: String,
    val valuesJson: String,
    /** Which model answered (for debugging a surprising number); null if unknown. */
    val model: String? = null,
    val checkedAt: Long = System.currentTimeMillis()
)
