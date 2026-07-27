package com.fitpal.app.data.repository

import com.fitpal.app.data.local.dao.DailyStepRow
import com.fitpal.app.data.local.dao.StepDao
import com.fitpal.app.data.local.entity.StepEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles step logging, Samsung Health syncing, and steps → calories conversion.
 *
 * Calories-from-steps formula (widely used in fitness research):
 *   caloriesBurned = steps × 0.04 × (weightKg / 70)
 *
 * This is based on the average of ~0.04 kcal per step for a 70 kg person,
 * scaled linearly by actual weight. It accounts for the fact that heavier
 * people burn more calories per step.
 *
 * Because phones/watches tend to OVER-count steps (arm swings, bumps), the
 * displayed calories are trimmed by a user-set percentage (Settings) applied
 * live on read — so the raw stored values stay intact and the trim can change
 * at any time, even for past days.
 *
 * For reference (before trim):
 * - 70 kg person, 10,000 steps ≈ 400 kcal
 * - 80 kg person, 10,000 steps ≈ 457 kcal
 * - 60 kg person, 10,000 steps ≈ 343 kcal
 */
@Singleton
class StepRepository @Inject constructor(
    private val stepDao: StepDao,
    private val settingsRepository: SettingsRepository,
    private val healthConnectManager: com.fitpal.app.health.HealthConnectManager
) {
    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

    /** Log steps manually for today. */
    suspend fun logSteps(steps: Int, weightKg: Float) {
        val today = LocalDate.now().format(dateFormat)
        val cal = stepsToCalories(steps, weightKg)
        stepDao.insert(
            StepEntryEntity(
                date = today,
                steps = steps,
                source = "manual",
                caloriesBurned = cal
            )
        )
    }

    /** Log steps for a specific date (manual edits or a sync source). */
    suspend fun logStepsForDate(date: String, steps: Int, weightKg: Float, source: String = "manual") {
        val cal = stepsToCalories(steps, weightKg)
        if (source != "manual") {
            // Replace previous sync data of this source for the day (no duplicates).
            stepDao.deleteBySourceForDate(date, source)
        }
        stepDao.insert(
            StepEntryEntity(
                date = date,
                steps = steps,
                source = source,
                caloriesBurned = cal
            )
        )
    }

    /**
     * SET (not add) the manual step count for a day — replaces any existing manual entry.
     * [steps] may be NEGATIVE, used to trim an over-counting sync (e.g. sync says 12k but you
     * really did 10k → set manual to -2000). The day's total = sync + manual.
     */
    suspend fun setManualStepsForDate(date: String, steps: Int, weightKg: Float) {
        setStepsForSource(date, steps, weightKg, SOURCE_MANUAL)
    }

    /** SET (not add) the synced step count for a day — lets the user correct a sync reading. */
    suspend fun setSyncedStepsForDate(date: String, steps: Int, weightKg: Float) {
        setStepsForSource(date, steps, weightKg, SOURCE_SYNC)
    }

    private suspend fun setStepsForSource(date: String, steps: Int, weightKg: Float, source: String) {
        stepDao.deleteBySourceForDate(date, source)
        // 0 means "no entry" — leave it deleted so it doesn't clutter the breakdown.
        if (steps != 0) {
            stepDao.insert(
                StepEntryEntity(date = date, steps = steps, source = source, caloriesBurned = stepsToCalories(steps, weightKg))
            )
        }
    }

    /** Steps logged by hand for the day (may be negative). */
    fun getManualSteps(date: String): Flow<Int> = stepDao.getStepsForSource(date, SOURCE_MANUAL)

    /** Steps synced from Samsung Health / Health Connect for the day. */
    fun getSyncedSteps(date: String): Flow<Int> = stepDao.getStepsForSource(date, SOURCE_SYNC)

    fun getTotalSteps(date: String): Flow<Int> = stepDao.getTotalStepsForDate(date)

    /**
     * Calories burned from steps, after applying the user's over-count trim.
     * Combining with the settings flow means changing the trim updates every
     * day's number instantly.
     */
    fun getCaloriesBurned(date: String): Flow<Float> = combine(
        stepDao.getCaloriesBurnedForDate(date),
        settingsRepository.stepCalorieReductionPercent
    ) { raw, pct -> (raw * (100 - pct) / 100f).coerceAtLeast(0f) }

    fun getEntriesForDate(date: String): Flow<List<StepEntryEntity>> =
        stepDao.getEntriesForDate(date)

    fun getDailySteps(from: String, to: String): Flow<List<DailyStepRow>> =
        stepDao.getDailySteps(from, to)

    // ---- Health Connect (Samsung Health) ----

    fun healthConnectAvailable(): Boolean = healthConnectManager.isAvailable()

    /**
     * Steps for a day grouped by source — every Health Connect writer, plus the watch's own
     * report (under [WATCH_SOURCE_KEY]) so the user can see it arrived.
     */
    suspend fun stepSourcesForDate(dateIso: String): Map<String, Int> {
        val sources = healthConnectManager.readStepsByOrigin(LocalDate.parse(dateIso))
            .mapValues { it.value.toInt() }.toMutableMap()
        settingsRepository.watchStepsFor(dateIso).takeIf { it > 0 }?.let { sources[WATCH_SOURCE_KEY] = it }
        return sources
    }

    /**
     * A step report from the watch itself (via the Wear Data Layer): remember it, then raise the
     * day's synced entry to it if it beats what we currently have. Health Connect can't be read
     * lower than this again — [syncFromHealthConnect] also takes the max with this report — so
     * watch-heavy days stop under-counting even when Samsung Health never writes watch steps
     * into Health Connect.
     */
    suspend fun recordWatchSteps(dateIso: String, steps: Int, weightKg: Float) {
        if (steps <= 0) return
        settingsRepository.setWatchSteps(dateIso, steps)
        val currentSynced = stepDao.getStepsForSourceOnce(dateIso, SOURCE_SYNC)
        if (steps > currentSynced) {
            logStepsForDate(dateIso, steps, weightKg, source = SOURCE_SYNC)
        }
    }

    suspend fun hasHealthConnectPermission(): Boolean = healthConnectManager.hasAllPermissions()
    val healthConnectPermissions: Set<String> get() = healthConnectManager.permissions

    /**
     * Pull the last [days] days of steps from Health Connect into our log, replacing any prior
     * Health-Connect data for those days. Each day is floored to the watch's own report (see
     * [recordWatchSteps]) so a Health Connect that's missing watch steps can't drag the count
     * down. No-op without permission. Returns how many days had steps.
     */
    suspend fun syncFromHealthConnect(weightKg: Float, days: Int = 7): Int {
        if (!healthConnectManager.hasAllPermissions()) return 0
        var synced = 0
        val today = LocalDate.now()
        for (i in 0 until days) {
            val date = today.minusDays(i.toLong())
            val dateIso = date.format(dateFormat)
            val steps = maxOf(
                healthConnectManager.readSteps(date).toInt(),
                settingsRepository.watchStepsFor(dateIso)
            )
            if (steps > 0) {
                logStepsForDate(dateIso, steps, weightKg, source = SOURCE_SYNC)
                synced++
            }
        }
        return synced
    }

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_SYNC = "health_connect"

        /** Pseudo-package key for the watch's own report in [stepSourcesForDate]. */
        const val WATCH_SOURCE_KEY = "fitpal_watch"

        /** ~0.04 kcal per step for a reference 70 kg person. */
        private const val KCAL_PER_STEP_REF = 0.04f
        private const val REFERENCE_WEIGHT_KG = 70f

        /**
         * Convert step count to estimated calories burned. Scales linearly with body weight.
         * Negative steps (a manual trim) give negative calories, so the day's totals net out.
         */
        fun stepsToCalories(steps: Int, weightKg: Float): Float {
            if (weightKg <= 0f) return 0f
            return steps * KCAL_PER_STEP_REF * (weightKg / REFERENCE_WEIGHT_KG)
        }
    }
}
