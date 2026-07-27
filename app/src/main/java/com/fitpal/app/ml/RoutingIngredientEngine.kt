package com.fitpal.app.ml

import android.graphics.Bitmap
import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.DailyTargets
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.UserProfile
import kotlinx.coroutines.CancellationException

/**
 * Chooses between the online ([remote], Gemini) and on-device ([local], Gemma) engines, and the
 * only class that knows online exists — everything above it talks to [IngredientEngine].
 *
 * Two ways it's used:
 *  - **Auto** (the [IngredientEngine] methods): try online when viable, silently fall back to
 *    on-device on any failure. Used for the *secondary* steps (dish naming, insights, reviews,
 *    raw text) where a silent fallback is fine.
 *  - **Explicit** ([onlineEngine] / [offlineEngine] + [canUseOnline]): the caller drives the
 *    choice itself. [AnalysisService] uses this for the *primary* photo/description analysis so
 *    it can offer the user a manual retry instead of silently falling back.
 *
 * [setForceLocal] pins the auto path to on-device for the rest of a job (so once the primary
 * went on-device, the secondary steps don't waste time retrying a dead online connection).
 */
class RoutingIngredientEngine(
    private val remote: RemoteIngredientEngine,
    private val local: LlmIngredientEngine,
    private val settings: SettingsRepository,
    private val network: NetworkMonitor
) : IngredientEngine {

    @Volatile
    private var forceLocal = false

    /** Direct access to each engine, for callers that drive the online/offline choice themselves. */
    val onlineEngine: RemoteIngredientEngine get() = remote
    val offlineEngine: IngredientEngine get() = local

    /** Whether the user has the online AI master switch on (they *want* online when possible). */
    fun onlineEnabled(): Boolean = settings.onlineAiEnabled.value

    /** Whether online is worth attempting right now (enabled, has key, has network, quota left). */
    fun canUseOnline(): Boolean =
        settings.onlineAiEnabled.value &&
            !settings.geminiApiKey.value.isNullOrBlank() &&
            settings.anyModelHasQuotaToday() &&
            network.isOnline()

    /** Pin the auto (secondary-step) path to on-device for the current job, or release it. */
    fun setForceLocal(value: Boolean) { forceLocal = value }

    /** A short, human reason online isn't being used right now — for the UI badge. */
    fun offlineReason(): String = when {
        !settings.onlineAiEnabled.value -> "Online AI is turned off"
        settings.geminiApiKey.value.isNullOrBlank() -> "No API key set"
        !settings.anyModelHasQuotaToday() -> "All models out of free quota for today"
        !network.isOnline() -> "No internet connection"
        else -> "Online AI unavailable"
    }

    private fun shouldTryRemote(): Boolean = !forceLocal && canUseOnline()

    /** Auto path: try online, silently fall back to on-device on any failure. */
    private suspend fun <T> withFallback(
        remoteCall: suspend () -> T,
        localCall: suspend () -> T
    ): T {
        if (!shouldTryRemote()) return localCall()
        return try {
            remoteCall()
        } catch (e: GeminiQuotaException) {
            // GeminiClient already marked each model's quota; just fall back.
            localCall()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            localCall()
        }
    }

    override suspend fun describeMeal(text: String): List<DetectedFood> =
        withFallback({ remote.describeMeal(text) }, { local.describeMeal(text) })

    override suspend fun analyzeImage(
        bitmap: Bitmap,
        note: String,
        onProgress: (String) -> Unit
    ): List<DetectedFood> =
        withFallback(
            { remote.analyzeImage(bitmap, note, onProgress) },
            { local.analyzeImage(bitmap, note, onProgress) }
        )

    override suspend fun identifyDish(
        ingredients: List<Ingredient>,
        image: Bitmap?,
        note: String
    ): List<DetectedFood> =
        withFallback(
            { remote.identifyDish(ingredients, image, note) },
            { local.identifyDish(ingredients, image, note) }
        )

    override suspend fun analyzeMealInsights(foods: List<DetectedFood>): MealInsights =
        withFallback({ remote.analyzeMealInsights(foods) }, { local.analyzeMealInsights(foods) })

    override suspend fun generateNutritionReview(
        period: String,
        rows: List<DailyNutritionRow>,
        totalDays: Int,
        profile: UserProfile,
        targets: DailyTargets?,
        weights: List<WeightEntryEntity>,
        foodLog: String,
        extraContext: String
    ): String =
        withFallback(
            { remote.generateNutritionReview(period, rows, totalDays, profile, targets, weights, foodLog, extraContext) },
            { local.generateNutritionReview(period, rows, totalDays, profile, targets, weights, foodLog, extraContext) }
        )

    override suspend fun generateRawText(prompt: String): String =
        withFallback({ remote.generateRawText(prompt) }, { local.generateRawText(prompt) })

    override fun close() {
        remote.close()
        local.close()
    }
}
