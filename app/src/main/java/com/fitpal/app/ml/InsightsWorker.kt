package com.fitpal.app.ml

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitpal.app.data.local.MealJson
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.MealBudgetCheck
import com.fitpal.app.domain.WaterPace
import com.fitpal.app.wear.WearWorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Generates the AI overview for every item of a just-logged meal, in the background — so it happens
 * even if the user logs without waiting and then leaves the app (WorkManager keeps it alive). Each
 * item's overview is cached on the item ([MealLogItemEntity.insightsJson]); the result is also kept
 * in a content-signature cache and, for a saved food, on the food itself, so the same food never
 * regenerates. Enqueued by [com.fitpal.app.data.repository.MealRepository] after each log.
 *
 * Dependencies are pulled from Hilt via an [EntryPointAccessors] (the app deliberately avoids
 * hilt-work — see [WearWorkerEntryPoint]).
 */
class InsightsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val entry = EntryPointAccessors.fromApplication(appContext, WearWorkerEntryPoint::class.java)

    override suspend fun doWork(): Result {
        val mealLogId = inputData.getLong(KEY_MEAL_LOG_ID, -1L)
        if (mealLogId < 0L) return Result.failure()

        val mealRepo = entry.mealRepository()
        val galleryRepo = entry.galleryRepository()
        val generator = entry.insightsGenerator()

        return try {
            val items = mealRepo.itemsForMeal(mealLogId)
            // Fill from cache first; collect everything still missing so ALL of it is generated in
            // ONE batched call (a 4-dish meal = 1 request, not 4 — kinder to the free-tier limit).
            val toGenerate = mutableListOf<MealLogItemEntity>()
            items.forEach { item ->
                // Already has an overview (e.g. carried from the photo/describe analysis) — leave it.
                if (!item.insightsJson.isNullOrBlank()) return@forEach
                val cached = mealRepo.cachedInsights(signatureFor(item))
                if (cached != null) {
                    MealJson.decodeInsights(cached.insightsJson)?.let { mealRepo.saveInsights(item.id, it) }
                } else {
                    toGenerate.add(item)
                }
            }
            if (toGenerate.isNotEmpty()) {
                val specs = toGenerate.map { item ->
                    InsightsGenerator.ItemSpec(
                        name = item.name, grams = item.grams, calories = item.calories,
                        protein = item.protein, fat = item.fat, carbs = item.carbs, fiber = item.fiber,
                        isDrink = item.isDrink, ingredients = mealRepo.ingredientsForItem(item).map { it.name }
                    )
                }
                val (insightsList, source) = generator.generateBatch(specs)
                toGenerate.forEachIndexed { i, item ->
                    val insights = insightsList.getOrNull(i) ?: return@forEachIndexed
                    mealRepo.saveInsights(item.id, insights)
                    mealRepo.cacheInsights(signatureFor(item), MealJson.encodeInsights(insights))
                    // A saved food keeps its own copy so the collection detail reuses it too.
                    item.galleryFoodId?.let { galleryRepo.saveInsights(it, insights, source) }
                }
            }
            // One meal-level coaching tip, judged against the day so far — only when useful.
            runCatching { generateCoachingTip(mealLogId, items) }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Transient (network / model warming up) → retry a few times, then give up quietly; the
            // detail screen can still generate on demand.
            if (runAttemptCount >= 3) Result.success() else Result.retry()
        }
    }

    /**
     * Generate ONE meal-level coaching tip for [mealLogId] and cache it on the meal. Deliberately
     * conservative: only for a real meal (>200 kcal) and only online (the tip needs the capable
     * model to be specific and safe); the prompt itself returns nothing for a meal that's fine.
     * The day's numbers and the calorie verdict come from [MealBudgetCheck], against Home's budget
     * (base + that day's steps and workouts), so the model never does the sums.
     */
    private suspend fun generateCoachingTip(mealLogId: Long, items: List<MealLogItemEntity>) {
        val mealRepo = entry.mealRepository()
        val meal = mealRepo.getMealLog(mealLogId) ?: return
        // Don't overwrite one already present (e.g. re-run), and never tip a water quick-add.
        if (!meal.coachingTipJson.isNullOrBlank() || meal.mealType == "water") return

        val mealKcal = items.sumOf { it.calories.toDouble() }.toFloat()
        if (mealKcal <= 200f) return

        // Runs on the cheap lite trio (off the analysis models' quota), not the main models.
        val aux = entry.auxAiGenerator()
        if (!aux.canUseOnline()) return

        val settings = entry.settingsRepository()
        val profile = settings.userProfile.value
        val weightKg = entry.weightRepository().getLatest().first()?.weightKg
        val targets = weightKg?.let {
            BmrCalculator.dailyTargets(profile, it, settings.dailyCalorieGoal.value, settings.macroSelection.value)
        }

        // What came before this meal that day, and Home's budget for that day: base + its steps
        // (after the over-count trim) and workouts.
        val before = mealRepo.macrosBefore(meal.date, meal.timestamp)
        val activity = (entry.stepRepository().getCaloriesBurned(meal.date).first() +
            entry.exerciseRepository().getTotalBurnedForDate(meal.date).first()).roundToInt()

        val mealFoods = items.joinToString("; ") {
            "${it.name} (${it.calories.roundToInt()} kcal, P${it.protein.roundToInt()} F${it.fat.roundToInt()} C${it.carbs.roundToInt()} Fiber ${it.fiber.roundToInt()})"
        }
        val facts = MealBudgetCheck.factsBlock(
            baseKcal = targets?.calories,
            activityKcal = activity,
            targets = targets?.let { MealBudgetCheck.MacroTargets(it.proteinG, it.fatG, it.carbsG, it.fiberG) },
            before = MealBudgetCheck.Totals(before.calories, before.protein, before.fat, before.carbs, before.fiber),
            meal = MealBudgetCheck.Totals(
                mealKcal,
                items.sumOf { it.protein.toDouble() }.toFloat(),
                items.sumOf { it.fat.toDouble() }.toFloat(),
                items.sumOf { it.carbs.toDouble() }.toFloat(),
                items.sumOf { it.fiber.toDouble() }.toFloat()
            )
        )
        val goal = "${profile.fitnessGoal.label} — ${profile.fitnessGoal.description}"

        val prompt = FoodPrompts.mealCoachingTip(mealFoods, facts + "\n" + whenEaten(meal.timestamp, settings), goal)
        val response = aux.generate(prompt, jsonMode = true) ?: return
        val tip = FoodJsonParser.parseCoachingTip(response)
        // Save the result (a tip, or null when the model declined — leaving no card).
        mealRepo.saveCoachingTip(mealLogId, tip)
    }

    /**
     * When the meal was eaten, the eating window and bedtime, and a plain statement when it's clearly
     * the day's last meal (the window closes within the hour, or it's close to or after bedtime), so
     * the coach never suggests eating more that night.
     */
    private fun whenEaten(timestamp: Long, settings: SettingsRepository): String {
        val t = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
        val m = t.hour * 60 + t.minute
        val schedule = settings.fastingSchedule.value
        val bed = settings.bedMinutes.value
        val window = schedule.enabled && schedule.eatEndMin > schedule.eatStartMin
        val parts = mutableListOf("EATEN AT: ${WaterPace.clockLabel(m)}")
        if (window) parts += "eating window ${WaterPace.clockLabel(schedule.eatStartMin)} to ${WaterPace.clockLabel(schedule.eatEndMin)}"
        parts += "bedtime about ${WaterPace.clockLabel(bed)}"
        val lastMeal = (window && schedule.eatEndMin - m <= LAST_MEAL_WINDOW_MIN) ||
            (bed - m).mod(DAY_MIN) <= LAST_MEAL_BED_MIN || (m - bed).mod(DAY_MIN) <= AFTER_BED_MIN
        return parts.joinToString(" · ") +
            if (lastMeal) "\nTHIS IS MOST LIKELY THE DAY'S LAST MEAL: the day's eating is done after it." else ""
    }

    /** Portion-independent key: name + per-100 g macros, so the same food matches at any amount. */
    private fun signatureFor(item: MealLogItemEntity): String {
        val g = item.grams.takeIf { it > 0f } ?: 100f
        fun p100(v: Float): Int = (v / g * 100f).toInt()
        return "${item.name.trim().lowercase()}|${p100(item.calories)}|${p100(item.protein)}|" +
            "${p100(item.fat)}|${p100(item.carbs)}|${p100(item.fiber)}"
    }

    companion object {
        const val KEY_MEAL_LOG_ID = "meal_log_id"

        /** A meal this close to the eating window's end (or after it) is the day's last. */
        private const val LAST_MEAL_WINDOW_MIN = 60
        /** …and so is one within three hours of bedtime, or up to four hours after it. */
        private const val LAST_MEAL_BED_MIN = 180
        private const val AFTER_BED_MIN = 240
        private const val DAY_MIN = 24 * 60
    }
}
