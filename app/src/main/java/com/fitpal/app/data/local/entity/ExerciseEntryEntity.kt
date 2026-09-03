package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A logged exercise / activity. Calories are computed deterministically from
 * MET × weight × time (the AI only identifies the activity, duration and MET) — EXCEPT for rows
 * imported from Calistapp ([externalId] set), whose [caloriesBurned] is Calistapp's own HR-based
 * figure, stored verbatim and never recomputed.
 */
@Entity(
    tableName = "exercise_entries",
    // Unique on the external id so a re-transfer from Calistapp updates the row instead of
    // duplicating it. SQLite treats multiple NULLs as distinct, so the many manual rows
    // (externalId = null) never collide.
    indices = [Index(value = ["externalId"], unique = true)]
)
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
    /**
     * Stable id from an external source (currently the Calistapp session id). Null for entries
     * created inside FitPal. Its presence is what makes a Calistapp re-transfer idempotent.
     */
    val externalId: String? = null,
    /**
     * Opaque JSON of extra detail from the source (Calistapp ships the per-exercise breakdown +
     * intensity here). FitPal stores and can display it but never parses it for its own totals.
     */
    val detailsJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
