package com.fitpal.app.domain

import com.fitpal.app.domain.model.FitnessGoal

/**
 * Decides whether a day's intake counts as "on goal" — direction-aware, so it
 * matches what the user is actually trying to do, with sane bounds so we never
 * reward starving or gorging.
 *
 *  - LOSE_FAT  → at/under target, but NOT below resting BMR (no starvation reward)
 *  - BUILD_MUSCLE → at/over target, but not past a sane surplus ceiling
 *  - MAINTAIN / RECOMP → within a band around target
 */
object GoalAdherence {

    /** How far above target still counts as a healthy "building" surplus. */
    private const val SURPLUS_CEILING = 600f
    /** Half-width of the maintain/recomp band, as a fraction of target. */
    private const val BAND = 0.10f

    fun isOnGoal(
        goal: FitnessGoal,
        calories: Float,
        targetCalories: Int,
        bmrFloor: Float
    ): Boolean {
        if (calories <= 0f || targetCalories <= 0) return false
        val t = targetCalories.toFloat()
        return when (goal) {
            FitnessGoal.LOSE_FAT -> calories in bmrFloor..t
            FitnessGoal.BUILD_MUSCLE -> calories in t..(t + SURPLUS_CEILING)
            FitnessGoal.MAINTAIN, FitnessGoal.RECOMP -> calories in (t * (1 - BAND))..(t * (1 + BAND))
        }
    }

    /**
     * Whether a whole week's totals trend the right way for the goal. Used for the
     * weekly bonus — a slip day is forgiven if the week nets out. (Caller guards that
     * enough days were logged so skipping a blowout can't help.)
     */
    fun isWeekOnTrack(goal: FitnessGoal, totalCalories: Float, totalTarget: Float): Boolean {
        if (totalTarget <= 0f) return false
        return when (goal) {
            FitnessGoal.LOSE_FAT -> totalCalories <= totalTarget
            FitnessGoal.BUILD_MUSCLE -> totalCalories >= totalTarget
            FitnessGoal.MAINTAIN, FitnessGoal.RECOMP ->
                totalCalories in (totalTarget * (1 - BAND))..(totalTarget * (1 + BAND))
        }
    }
}
