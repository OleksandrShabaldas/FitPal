package com.fitpal.app.data.local.entity

import androidx.room.ColumnInfo
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
     * The one-tap situation tags the user set on this meal ("Home", "Family meal", …), joined as
     * "Home, Family meal" (see [com.fitpal.app.domain.model.MealContext]). Fed into the AI review so
     * it can reason about *where/why* a day looked unusual (e.g. restaurant days run higher).
     * Null = untagged, the norm. Older rows hold a single tag, which reads back the same way.
     */
    val context: String? = null,
    /**
     * A cached, meal-aware coaching tip as JSON (see [com.fitpal.app.domain.model.CoachingTip]) —
     * one short green note about *this* plate given the day so far. Null = no tip (the norm: only
     * generated when genuinely useful, for meals over ~200 kcal, online).
     */
    val coachingTipJson: String? = null,
    /**
     * When the meal was EATEN (epoch millis). Defaults to the moment it was logged; the photo's own
     * capture time or a time the user picked replaces it (see [timeSource]). Fasting adherence reads
     * its time of day.
     */
    val timestamp: Long = System.currentTimeMillis(),
    /**
     * Where [timestamp] came from: null = the moment it was logged, "photo" = the photo's own capture
     * time (trusted as proof for fasting), "manual" = a time the user picked. See
     * [com.fitpal.app.domain.model.TimeSource].
     */
    val timeSource: String? = null,
    /**
     * True when this meal was logged for its own day while the user was fasting and they chose "log
     * anyway" (no "ate earlier" pass, no photo proof). It then breaks that day's fast for good — even
     * if its time is edited into the eating window later — so a free time edit can't stand in for a
     * pass. See [com.fitpal.app.domain.model.adherenceByDay].
     */
    @ColumnInfo(defaultValue = "0")
    val loggedDuringFast: Boolean = false
)
