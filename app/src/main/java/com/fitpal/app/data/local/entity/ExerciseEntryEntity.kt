package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A logged exercise / activity. Calories are computed deterministically from
 * MET × weight × time (the AI only identifies the activity, duration and MET).
 */
@Entity(tableName = "exercise_entries")
data class ExerciseEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,            // "YYYY-MM-DD"
    val name: String,
    val minutes: Int,
    val met: Float,
    val caloriesBurned: Float,
    val source: String = "manual",
    /** Which AI engine produced the analysis (ONLINE/OFFLINE), for the badge. */
    val aiSource: String? = null,
    /** The exact model behind it ("gemini-3-flash-preview", "Gemma 3n E4B"), for the badge. */
    val aiModel: String? = null,
    /** JSON array of short coaching tips generated with the estimate. */
    val suggestionsJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
