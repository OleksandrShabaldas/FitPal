package com.fitpal.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A cached AI overview for a food, keyed by a content **signature** (name + per-100 g macros). The
 * same food — logged from the database, from your collection, or typed by hand — reuses its overview
 * instead of the AI regenerating it every time. Portion-independent, since the signature is per-100 g.
 */
@Entity(tableName = "food_insights_cache")
data class FoodInsightsCacheEntity(
    @PrimaryKey val signature: String,
    val insightsJson: String,
    val generatedAt: Long
)
