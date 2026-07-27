package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single body-weight measurement. One per day — logging again on the same
 * day replaces the previous entry (upsert on date).
 */
@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** "YYYY-MM-DD" — unique per day. */
    val date: String,
    val weightKg: Float,
    val timestamp: Long = System.currentTimeMillis()
)
