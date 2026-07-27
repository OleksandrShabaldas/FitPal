package com.fitpal.app.domain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny cross-screen handoff for "log into THIS meal category".
 *
 * Home's per-category "+" sets a pending meal type; the next logging screen consumes
 * it once (so it lands in the chosen category). Home clears it whenever it resumes,
 * so the global "+" always falls back to the time-of-day default.
 */
@Singleton
class MealLogContext @Inject constructor() {
    @Volatile
    var pendingMealType: String? = null

    /**
     * The day the user was viewing when they tapped "+". ISO date string, or null
     * to mean "today". Lets a meal added while browsing a past/future day land on
     * THAT day instead of today.
     */
    @Volatile
    var pendingDate: String? = null

    /** Read the pending category and clear it — so it's used exactly once. */
    fun consume(): String? = pendingMealType.also { pendingMealType = null }

    /** Read the pending date (ISO) and clear it — used exactly once. Null = today. */
    fun consumeDate(): String? = pendingDate.also { pendingDate = null }

    fun clear() {
        pendingMealType = null
        pendingDate = null
    }
}
