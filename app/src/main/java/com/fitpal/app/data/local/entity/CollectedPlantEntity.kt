package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A plant that has bloomed and been pressed into the user's collection. */
@Entity(tableName = "collected_plants")
data class CollectedPlantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val speciesId: String,
    val rarity: String,
    val bloomedDate: String,
    /** Bloom order (1 = first ever) — newest shown first. */
    val orderIndex: Int
)
