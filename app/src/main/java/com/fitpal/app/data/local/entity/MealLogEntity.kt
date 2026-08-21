package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A meal event — one entry per meal (breakfast, lunch, dinner, snack).
 * Contains one or more food items logged via MealLogItemEntity.
 */
@Entity(tableName = "meal_logs")
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,           // "YYYY-MM-DD" format for easy grouping by day
    val mealType: String,       // "breakfast", "lunch", "dinner", "snack"
    /**
     * A name the user gave this whole meal ("Sunday roast"), shown instead of "Meal · 3 dishes".
     * Null = unnamed, which is the norm — the dishes speak for themselves.
     */
    val name: String? = null,
    /**
     * An optional one-tap situation tag the user set on this meal ("Home", "Restaurant",
     * "Family meal", …). Fed into the AI review so it can reason about *where/why* a day looked
     * unusual (e.g. restaurant days run higher). Null = untagged, the norm.
     */
    val context: String? = null,
    /**
     * A cached, meal-aware coaching tip as JSON (see [com.fitpal.app.domain.model.CoachingTip]) —
     * one short green note about *this* plate given the day so far. Null = no tip (the norm: only
     * generated when genuinely useful, for meals over ~200 kcal, online).
     */
    val coachingTipJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
