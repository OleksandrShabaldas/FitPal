package com.fitpal.app.data.local.entity

import androidx.room.Entity

/**
 * A saved AI nutrition overview for a specific period.
 *
 * Keyed by (period, periodKey) so each day/week/month has exactly one saved
 * review. Opening that period again loads the saved text instead of asking the
 * AI to regenerate it.
 *
 *  - period   = "daily" | "weekly" | "monthly"
 *  - periodKey = "2026-06-10" (daily), the week's Monday date (weekly),
 *                or "2026-06" (monthly)
 */
@Entity(tableName = "ai_reviews", primaryKeys = ["period", "periodKey"])
data class AiReviewEntity(
    val period: String,
    val periodKey: String,
    val review: String,
    val generatedAt: Long = System.currentTimeMillis(),
    /** Which AI produced it — "ONLINE" / "OFFLINE" (see [com.fitpal.app.ml.AiSource]); null if unknown. */
    val source: String? = null,
    /** The exact model that produced it ("gemini-3-flash-preview", "Gemma 3n E4B"); null if unknown. */
    val model: String? = null
)
