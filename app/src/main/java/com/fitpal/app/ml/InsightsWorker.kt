package com.fitpal.app.ml

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitpal.app.data.local.MealJson
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.wear.WearWorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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

        val before = mealRepo.macrosBefore(meal.date, meal.timestamp)

        fun macroStr(kcal: Float, p: Float, f: Float, c: Float, fib: Float) =
            "${kcal.roundToInt()} kcal, P${p.roundToInt()}g F${f.roundToInt()}g C${c.roundToInt()}g Fiber ${fib.roundToInt()}g"

        val mealFoods = items.joinToString("; ") {
            "${it.name} (${it.calories.roundToInt()} kcal, P${it.protein.roundToInt()} F${it.fat.roundToInt()} C${it.carbs.roundToInt()} Fiber ${it.fiber.roundToInt()})"
        }
        val todayBefore = if (before.calories < 50f) "nothing substantial logged earlier today"
            else macroStr(before.calories, before.protein, before.fat, before.carbs, before.fiber)
        val mealP = items.sumOf { it.protein.toDouble() }.toFloat()
        val mealF = items.sumOf { it.fat.toDouble() }.toFloat()
        val mealC = items.sumOf { it.carbs.toDouble() }.toFloat()
        val mealFib = items.sumOf { it.fiber.toDouble() }.toFloat()
        val remaining = if (targets != null) {
            macroStr(
                targets.calories - before.calories - mealKcal,
                targets.proteinG - before.protein - mealP,
                targets.fatG - before.fat - mealF,
                targets.carbsG - before.carbs - mealC,
                targets.fiberG - before.fiber - mealFib
            ) + " remaining"
        } else "daily targets not set"
        val goal = "${profile.fitnessGoal.label} — ${profile.fitnessGoal.description}"

        val prompt = FoodPrompts.mealCoachingTip(mealFoods, todayBefore, remaining, goal)
        val response = aux.generate(prompt, jsonMode = true) ?: return
        val tip = FoodJsonParser.parseCoachingTip(response)
        // Save the result (a tip, or null when the model declined — leaving no card).
        mealRepo.saveCoachingTip(mealLogId, tip)
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
    }
}
