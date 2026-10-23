package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A meal the planner made (docs/redesign/10). Kept apart from `meal_logs` on purpose: nothing here
 * ever counts toward a day (ring, macros, water, limits, fasting, Analytics, widgets, watch) until
 * it's logged, which writes a real meal log and links it back ([loggedMealLogId]).
 */
@Entity(tableName = "planned_meals", indices = [Index("requestId"), Index("date")])
data class PlannedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** One generation (a meal, a day or a week). */
    val requestId: String,
    /** IDEA (on the results screen), SAVED (kept as an idea), SCHEDULED, LOGGED, DISMISSED. */
    val status: String,
    /** The day it's for (ISO). Scheduled meals show on Home that day. */
    val date: String?,
    val mealType: String?,
    val name: String,
    /** The recipe: ingredients (+ have/buy), steps, tips, cuisine, time, difficulty — see `MealJson.encodeRecipe`. */
    val recipeJson: String,
    val kcalPerServing: Float,
    val proteinPerServing: Float,
    val servings: Int,
    /** Which AI made it — engine and exact model in separate columns, like the other AI rows. */
    val aiSource: String? = null,
    val aiModel: String? = null,
    val loggedMealLogId: Long? = null
) {
    companion object {
        const val IDEA = "IDEA"
        const val SAVED = "SAVED"
        const val SCHEDULED = "SCHEDULED"
        const val LOGGED = "LOGGED"
        const val DISMISSED = "DISMISSED"
    }
}
