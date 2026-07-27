package com.fitpal.app.wear

import android.content.Context
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.shared.StatsSnapshot
import com.fitpal.shared.WearContract
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the current-day [StatsSnapshot] from the same repositories the Home screen reads, and
 * pushes it to the watch as a Data Layer item ([WearContract.PATH_STATS]). Called when the watch
 * asks for a refresh and after any watch-driven change (e.g. logging water).
 */
@Singleton
class WearStatsPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mealRepository: MealRepository,
    private val exerciseRepository: ExerciseRepository,
    private val stepRepository: StepRepository,
    private val weightRepository: WeightRepository,
    private val settingsRepository: SettingsRepository
) {
    /** Compute today's numbers and push them to the watch. Best-effort; never throws. */
    suspend fun publishNow() {
        runCatching {
            val snapshot = buildSnapshot()
            val request = PutDataMapRequest.create(WearContract.PATH_STATS).apply {
                dataMap.putAll(snapshot.toDataMap())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        }
    }

    private suspend fun buildSnapshot(): StatsSnapshot {
        val today = LocalDate.now().toString()

        val nutrition = mealRepository.getDailyNutrition(today).first()
        val water = mealRepository.getTotalWaterForDate(today).first()
        val exerciseBurned = exerciseRepository.getTotalBurnedForDate(today).first()
        val stepCalories = stepRepository.getCaloriesBurned(today).first()
        val steps = stepRepository.getTotalSteps(today).first()

        val weight = weightRepository.getLatest().first()?.weightKg
        val targets = weight?.let {
            BmrCalculator.dailyTargets(
                settingsRepository.userProfile.value, it,
                settingsRepository.dailyCalorieGoal.value,
                settingsRepository.macroSelection.value
            )
        }

        val waterOverride = settingsRepository.waterGoalOverrideMl.value
        val waterGoal = if (waterOverride > 0) waterOverride
            else SettingsRepository.autoWaterGoal(weight ?: 70f)

        return StatsSnapshot(
            dateIso = today,
            caloriesConsumed = nutrition.calories.toInt(),
            caloriesTarget = targets?.calories ?: 0,
            exerciseBurned = exerciseBurned.toInt(),
            stepCalories = stepCalories.toInt(),
            steps = steps,
            proteinG = nutrition.protein.toInt(),
            proteinTargetG = targets?.proteinG ?: 0,
            fatG = nutrition.fat.toInt(),
            fatTargetG = targets?.fatG ?: 0,
            carbsG = nutrition.carbs.toInt(),
            carbsTargetG = targets?.carbsG ?: 0,
            fiberG = nutrition.fiber.toInt(),
            fiberTargetG = targets?.fiberG ?: 0,
            waterMl = water.toInt(),
            waterGoalMl = waterGoal,
            waterPresets = settingsRepository.waterPresets.value,
            updatedAt = System.currentTimeMillis()
        )
    }
}
