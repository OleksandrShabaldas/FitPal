package com.fitpal.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A daily step count entry — either manually logged or synced from Samsung Health.
 * Multiple entries per day are summed for the total.
 */
@Entity(tableName = "step_entries")
data class StepEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,               // "YYYY-MM-DD"
    val steps: Int,
    @ColumnInfo(defaultValue = "manual")
    val source: String = "manual",   // "manual" or "samsung_health"
    @ColumnInfo(defaultValue = "0")
    val caloriesBurned: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)
