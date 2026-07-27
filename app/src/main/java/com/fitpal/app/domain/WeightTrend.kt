package com.fitpal.app.domain

import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.domain.model.FitnessGoal
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns logged weights into the numbers a calorie tracker actually needs: a smoothed rate of change
 * (kg/week) and an "implied maintenance" estimate that cross-checks logged intake against real
 * weight change. Both are derived from the data — they never change the user's calorie budget.
 */
object WeightTrend {

    /** Energy in 1 kg of body-mass change (mostly fat). The standard ~7700 kcal/kg. */
    private const val KCAL_PER_KG = 7700f

    /**
     * Least-squares trend in kg/week over the weights dated within [from, to] inclusive.
     * Null when there are fewer than two points or they span under four days (too noisy to trust).
     */
    fun ratePerWeek(weights: List<WeightEntryEntity>, from: LocalDate, to: LocalDate): Float? {
        val pts = weights
            .mapNotNull { w -> runCatching { LocalDate.parse(w.date) }.getOrNull()?.let { it to w.weightKg } }
            .filter { !it.first.isBefore(from) && !it.first.isAfter(to) }
            .sortedBy { it.first }
        if (pts.size < 2) return null
        val x0 = pts.first().first.toEpochDay()
        val xs = pts.map { (it.first.toEpochDay() - x0).toFloat() }
        val ys = pts.map { it.second }
        if (xs.last() - xs.first() < 4f) return null
        val n = pts.size
        val sx = xs.sum()
        val sy = ys.sum()
        val sxx = xs.sumOf { (it * it).toDouble() }.toFloat()
        val sxy = xs.indices.sumOf { (xs[it] * ys[it]).toDouble() }.toFloat()
        val denom = n * sxx - sx * sx
        if (abs(denom) < 1e-3f) return null
        val slopePerDay = (n * sxy - sx * sy) / denom
        return slopePerDay * 7f
    }

    /** A short, goal-framed label for a weekly rate, e.g. "0.4 kg/week down · on track to lose fat". */
    fun rateLabel(ratePerWeek: Float, goal: FitnessGoal): String {
        val a = abs(ratePerWeek)
        val dir = when {
            a < 0.05f -> "holding steady"
            ratePerWeek < 0f -> "${"%.1f".format(a)} kg/week down"
            else -> "${"%.1f".format(a)} kg/week up"
        }
        val framing = when (goal) {
            FitnessGoal.LOSE_FAT -> when {
                ratePerWeek < -0.05f -> "on track to lose fat"
                a < 0.05f -> "not losing yet — check the deficit"
                else -> "trending up — tighten the deficit"
            }
            FitnessGoal.BUILD_MUSCLE -> when {
                ratePerWeek > 0.05f -> "gaining as planned"
                a < 0.05f -> "not gaining yet — add a little"
                else -> "trending down — eat more"
            }
            FitnessGoal.RECOMP -> "recomp — slow change is expected"
            FitnessGoal.MAINTAIN -> if (a < 0.15f) "steady — right where you want" else "drifting from maintenance"
        }
        return "$dir · $framing"
    }

    /**
     * Implied maintenance calories from average daily intake vs. measured weight change:
     * maintenance ≈ avgIntake − (weight change per day × 7700). Null until there's enough to trust
     * (at least ~10 logged days and a usable rate). A rough estimate, not a prescription.
     */
    fun impliedMaintenance(avgIntakeKcal: Float, ratePerWeek: Float?, loggedDays: Int): Int? {
        if (ratePerWeek == null || avgIntakeKcal <= 0f || loggedDays < 10) return null
        val ratePerDayKg = ratePerWeek / 7f
        val maintenance = avgIntakeKcal - ratePerDayKg * KCAL_PER_KG
        return maintenance.roundToInt().coerceIn(800, 6000)
    }
}
