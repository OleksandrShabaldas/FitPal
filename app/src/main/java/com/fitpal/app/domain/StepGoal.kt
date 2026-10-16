package com.fitpal.app.domain

import kotlin.math.ceil

/**
 * The automatic step goal: a little more than you already do. Your average over the recent days you
 * walked, plus 10%, rounded up to the next 500 and kept between 4,000 and 15,000. With fewer than five
 * days of steps there's nothing to go on yet, so it's the familiar 8,000.
 */
object StepGoal {
    const val FALLBACK = 8_000
    private const val MIN_DAYS = 5

    fun automatic(recentDailySteps: List<Int>): Int {
        val walked = recentDailySteps.filter { it > 0 }
        if (walked.size < MIN_DAYS) return FALLBACK
        val target = walked.average() * 1.10
        return (ceil(target / 500.0).toInt() * 500).coerceIn(4_000, 15_000)
    }
}
