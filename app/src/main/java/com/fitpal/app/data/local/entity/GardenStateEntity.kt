package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fitpal.app.domain.GardenRules

/**
 * The single-row state of the user's garden. Updated by the daily evaluation
 * (catch-up) in GardenRepository.
 */
@Entity(tableName = "garden_state")
data class GardenStateEntity(
    @PrimaryKey val id: Int = 1,
    /** Points toward the current plant's bloom (0..BLOOM_COST). */
    val growthPoints: Int = 0,
    /** 0 = healthy … grows with consecutive un-waterable days. */
    val wiltLevel: Int = 0,
    /** 💧 reserve — earned by logging, spent watering the plant. */
    val water: Int = GardenRules.STARTING_WATER,
    /** ⭐ points — earned by manual watering + challenges, spent in the shop. */
    val points: Int = 0,
    /** Last day the plant was watered (ISO), so we don't double-water. */
    val lastWateredDate: String? = null,
    /** (legacy, unused — kept for schema) */
    val buffers: Int = 0,
    /** (legacy, unused — kept for schema) */
    val bufferProgress: Int = 0,
    /** Logged / on-goal days for the *current* plant — drives bloom rarity. */
    val loggedThisPlant: Int = 0,
    val onGoalThisPlant: Int = 0,
    /** Last completed day we've already evaluated (ISO date), null = fresh. */
    val lastEvaluatedDate: String? = null,
    /** How many plants have bloomed and joined the collection. */
    val bloomCount: Int = 0,
    /** Monday (ISO) of the most recent calendar week we awarded the weekly bonus for. */
    val lastWeeklyEvalWeek: String? = null,
    /** How many weeks the user stayed on-track overall. */
    val onTrackWeeks: Int = 0,
    /** Bloom count the user has already celebrated — to fire the bloom party once. */
    val lastSeenBloomCount: Int = 0
)
