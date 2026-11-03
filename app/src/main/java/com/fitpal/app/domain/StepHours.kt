package com.fitpal.app.domain

/**
 * The Steps page's hour-by-hour bars, scaled to the count the page shows (docs/redesign/12, #12).
 *
 * The hours come from the most detailed Health Connect source, which can be a little under the day's
 * count (the count uses the highest source, or the watch). Scaling keeps the shape and makes the
 * bars add up to the number on the ring. Largest-remainder rounding, so the sum is exact; an hour
 * with no steps stays empty (so nothing appears after "now").
 */
object StepHours {

    fun scaleTo(hours: List<Long>, total: Long): List<Long> {
        val sum = hours.sum()
        if (sum <= 0L || total <= 0L || sum == total) return hours
        val exact = hours.map { it.toDouble() * total / sum }
        val floors = exact.map { kotlin.math.floor(it).toLong() }.toMutableList()
        var left = total - floors.sum()
        exact.indices
            .filter { hours[it] > 0L }
            .sortedByDescending { exact[it] - floors[it] }
            .forEach { i -> if (left > 0) { floors[i] += 1; left-- } }
        return floors
    }

    /** The busiest hour (0–23), or null when there are no steps. */
    fun busiest(hours: List<Long>): Int? = hours.indices.filter { hours[it] > 0L }.maxByOrNull { hours[it] }
}
