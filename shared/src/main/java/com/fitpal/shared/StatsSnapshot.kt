package com.fitpal.shared

import com.google.android.gms.wearable.DataMap

/**
 * A compact snapshot of the day's numbers the phone pushes to the watch (see
 * [WearContract.PATH_STATS]). Everything the watch's stats screen, tile and complications need,
 * in one immutable bundle. All energy values are kcal, macros grams, water millilitres.
 *
 * "Calories left" uses an eat-back model — the day's target plus what you've burned, minus what
 * you've eaten — because on the watch the useful question is "how much can I still eat today".
 */
data class StatsSnapshot(
    val dateIso: String = "",
    val caloriesConsumed: Int = 0,
    val caloriesTarget: Int = 0,
    /** Calories burned from logged workouts. */
    val exerciseBurned: Int = 0,
    /** Calories burned from steps (already trimmed for over-count on the phone). */
    val stepCalories: Int = 0,
    val steps: Int = 0,
    val proteinG: Int = 0,
    val proteinTargetG: Int = 0,
    val fatG: Int = 0,
    val fatTargetG: Int = 0,
    val carbsG: Int = 0,
    val carbsTargetG: Int = 0,
    val fiberG: Int = 0,
    val fiberTargetG: Int = 0,
    val waterMl: Int = 0,
    val waterGoalMl: Int = 0,
    /** The user's quick-add water amounts (ml), mirrored from the phone. */
    val waterPresets: List<Int> = listOf(200, 330, 500),
    /** When the phone built this snapshot (epoch millis) — lets the watch show staleness. */
    val updatedAt: Long = 0L
) {
    /** Total calories burned today (exercise + steps). */
    val totalBurned: Int get() = exerciseBurned + stepCalories

    /** Eat-back "still available to eat" figure; negative means over budget. */
    val caloriesLeft: Int get() = caloriesTarget + totalBurned - caloriesConsumed

    fun toDataMap(): DataMap = DataMap().apply {
        putString(K_DATE, dateIso)
        putInt(K_CAL_CONSUMED, caloriesConsumed)
        putInt(K_CAL_TARGET, caloriesTarget)
        putInt(K_EX_BURNED, exerciseBurned)
        putInt(K_STEP_CAL, stepCalories)
        putInt(K_STEPS, steps)
        putInt(K_PROTEIN, proteinG)
        putInt(K_PROTEIN_T, proteinTargetG)
        putInt(K_FAT, fatG)
        putInt(K_FAT_T, fatTargetG)
        putInt(K_CARBS, carbsG)
        putInt(K_CARBS_T, carbsTargetG)
        putInt(K_FIBER, fiberG)
        putInt(K_FIBER_T, fiberTargetG)
        putInt(K_WATER, waterMl)
        putInt(K_WATER_GOAL, waterGoalMl)
        putIntegerArrayList(K_WATER_PRESETS, ArrayList(waterPresets))
        putLong(K_UPDATED_AT, updatedAt)
    }

    companion object {
        private const val K_DATE = "date"
        private const val K_CAL_CONSUMED = "cal_consumed"
        private const val K_CAL_TARGET = "cal_target"
        private const val K_EX_BURNED = "ex_burned"
        private const val K_STEP_CAL = "step_cal"
        private const val K_STEPS = "steps"
        private const val K_PROTEIN = "protein"
        private const val K_PROTEIN_T = "protein_t"
        private const val K_FAT = "fat"
        private const val K_FAT_T = "fat_t"
        private const val K_CARBS = "carbs"
        private const val K_CARBS_T = "carbs_t"
        private const val K_FIBER = "fiber"
        private const val K_FIBER_T = "fiber_t"
        private const val K_WATER = "water"
        private const val K_WATER_GOAL = "water_goal"
        private const val K_WATER_PRESETS = "water_presets"
        private const val K_UPDATED_AT = "updated_at"

        fun fromDataMap(map: DataMap): StatsSnapshot = StatsSnapshot(
            dateIso = map.getString(K_DATE, ""),
            caloriesConsumed = map.getInt(K_CAL_CONSUMED, 0),
            caloriesTarget = map.getInt(K_CAL_TARGET, 0),
            exerciseBurned = map.getInt(K_EX_BURNED, 0),
            stepCalories = map.getInt(K_STEP_CAL, 0),
            steps = map.getInt(K_STEPS, 0),
            proteinG = map.getInt(K_PROTEIN, 0),
            proteinTargetG = map.getInt(K_PROTEIN_T, 0),
            fatG = map.getInt(K_FAT, 0),
            fatTargetG = map.getInt(K_FAT_T, 0),
            carbsG = map.getInt(K_CARBS, 0),
            carbsTargetG = map.getInt(K_CARBS_T, 0),
            fiberG = map.getInt(K_FIBER, 0),
            fiberTargetG = map.getInt(K_FIBER_T, 0),
            waterMl = map.getInt(K_WATER, 0),
            waterGoalMl = map.getInt(K_WATER_GOAL, 0),
            waterPresets = map.getIntegerArrayList(K_WATER_PRESETS)?.toList()
                ?: listOf(200, 330, 500),
            updatedAt = map.getLong(K_UPDATED_AT, 0L)
        )
    }
}
