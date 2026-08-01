package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fitpal.app.domain.TrailRules

/**
 * The single-row state of "The Trail" game. Advanced by the catch-up evaluation in
 * TrailRepository — one tick per logged day, never per elapsed hour.
 */
@Entity(tableName = "trail_state")
data class TrailStateEntity(
    @PrimaryKey val id: Int = 1,
    /** Which site along the trail you're restoring. */
    val siteIndex: Int = 0,
    /** 🌿 collected and spendable. */
    val growth: Long = 0,
    /** 🌿 produced but not yet collected — the "waiting for you" pile. */
    val bankedGrowth: Long = 0,
    /** 💧 reserve — earned by logging, one spent per tick. */
    val water: Int = TrailRules.STARTING_WATER,
    /** ⭐ — challenges (phase C) and hand-collecting. */
    val points: Int = 0,
    /** Production multiplier; climbs with logging, falls when you skip. */
    val vitality: Float = TrailRules.VITALITY_START,
    /** Last *completed* day folded into the state (ISO). */
    val lastEvaluatedDate: String? = null,
    /** Last day a tick actually ran (ISO) — stops a day ticking twice. */
    val lastTickDate: String? = null,
    /** Permanent multiplier carried across regions. */
    val legacyMultiplier: Float = 1.0f,
    /** Equipped scene theme id (see ThemeCatalog). */
    val activeTheme: String = "meadow",
    /** Bitmask of tutorial coach-marks already shown (see TutorialStep). */
    val tutorialSeen: Int = 0
)
