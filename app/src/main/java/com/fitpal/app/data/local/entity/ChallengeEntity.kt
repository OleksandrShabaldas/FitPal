package com.fitpal.app.data.local.entity

import androidx.room.Entity

/**
 * One assigned challenge. Keyed by (periodKey, slot) so assignment is idempotent —
 * re-running it on every open can never duplicate or reroll a live challenge.
 */
@Entity(tableName = "challenges", primaryKeys = ["periodKey", "slot"])
data class ChallengeEntity(
    val periodKey: String,
    val slot: String,
    /** DAILY / WEEKLY / MONTHLY. */
    val period: String,
    /** CONCRETE (code-verified) or CREATIVE (AI-judged). */
    val kind: String,
    /** [com.fitpal.app.domain.ConcreteType] name — null for creative challenges. */
    val typeName: String? = null,
    val threshold: Float = 0f,
    val text: String,
    val rewardPoints: Int,
    /** Epoch millis, 0 = not yet met. */
    val completedAt: Long = 0L,
    /** Epoch millis, 0 = points not banked yet. */
    val claimedAt: Long = 0L,
    /** The AI judge's reasoning when it last declined — shown so a "no" isn't mysterious. */
    val verdictNote: String? = null
)
