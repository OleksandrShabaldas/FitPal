package com.fitpal.app.ml

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitpal.app.data.local.MealJson
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.wear.WearWorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException

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
            mealRepo.itemsForMeal(mealLogId).forEach { item ->
                // Already has an overview (e.g. carried from the photo/describe analysis) — leave it.
                if (!item.insightsJson.isNullOrBlank()) return@forEach

                val signature = signatureFor(item)
                val cached = mealRepo.cachedInsights(signature)
                if (cached != null) {
                    MealJson.decodeInsights(cached.insightsJson)?.let { mealRepo.saveInsights(item.id, it) }
                    return@forEach
                }

                val (insights, source) = generator.generate(
                    name = item.name,
                    grams = item.grams,
                    calories = item.calories,
                    protein = item.protein,
                    fat = item.fat,
                    carbs = item.carbs,
                    fiber = item.fiber,
                    isDrink = item.isDrink,
                    ingredients = mealRepo.ingredientsForItem(item).map { it.name }
                )
                mealRepo.saveInsights(item.id, insights)
                mealRepo.cacheInsights(signature, MealJson.encodeInsights(insights))
                // A saved food keeps its own copy so the collection detail reuses it too.
                item.galleryFoodId?.let { galleryRepo.saveInsights(it, insights, source) }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Transient (network / model warming up) → retry a few times, then give up quietly; the
            // detail screen can still generate on demand.
            if (runAttemptCount >= 3) Result.success() else Result.retry()
        }
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
