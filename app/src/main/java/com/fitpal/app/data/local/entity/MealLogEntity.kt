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
    val timestamp: Long = System.currentTimeMillis()
)
