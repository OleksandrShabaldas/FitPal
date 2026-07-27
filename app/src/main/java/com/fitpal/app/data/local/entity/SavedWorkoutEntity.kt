package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A workout saved to the collection for one-tap re-logging — a template, not a log entry.
 * Stores the activity, its MET and a default duration; calories are recomputed from the user's
 * current weight when it's logged.
 */
@Entity(tableName = "saved_workouts")
data class SavedWorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val met: Float,
    val minutes: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis()
)
