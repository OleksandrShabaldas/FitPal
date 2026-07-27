package com.fitpal.app.wear

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ml.AnalysisCancelledException
import com.fitpal.app.ml.AnalysisJobManager
import com.fitpal.app.ml.AppForegroundState
import com.fitpal.app.ml.FallbackChoice
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.GeminiQuotaException
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Runs a watch-dictated meal description through the phone's AI, in the background, even if the
 * phone app was killed. Kicked off as **expedited** WorkManager work by [WearDataLayerService]
 * because a wearable message can't legally start a foreground service on Android 12+.
 *
 * It reuses the exact same pipeline (online Gemini → on-device Gemma fallback) and publishes into
 * the shared [AnalysisJobManager], so the result is identical to typing a description in the app —
 * and if the user opens the phone app, the Describe screen adopts this job as usual. On finish it
 * posts the same "tap to review" notification the in-app service does.
 */
class MealAnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val entry = EntryPointAccessors.fromApplication(appContext, WearWorkerEntryPoint::class.java)
    private val pipeline: FoodAnalysisPipeline = entry.pipeline()
    private val jobManager: AnalysisJobManager = entry.analysisJobManager()
    private val appForegroundState: AppForegroundState = entry.appForegroundState()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(AiJobNotifications.mealProgress(applicationContext, "Starting…"))

    override suspend fun doWork(): Result {
        AiJobNotifications.ensureChannels(applicationContext)
        val description = inputData.getString(KEY_DESCRIPTION)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        val mealType = inputData.getString(KEY_MEAL_TYPE)?.takeIf { it.isNotBlank() }
            ?: entry.mealRepository().defaultMealType()
        val date = inputData.getString(KEY_DATE) // null = today

        // Publish the job so an opened phone app adopts it; then take the foreground.
        jobManager.prepareTextJob(description, mealType, date)
        runCatching { setForeground(getForegroundInfo()) }

        return try {
            val outcome = analyzePrimary(description)
            pipeline.setPreferLocal(outcome.source == AiSource.OFFLINE)
            jobManager.setResult(
                outcome.foods, emptyList(), insights = null,
                source = outcome.source,
                onlineError = outcome.onlineError.takeIf { outcome.source == AiSource.OFFLINE }
            )
            AiJobNotifications.postMealDone(applicationContext)
            Result.success()
        } catch (e: AnalysisCancelledException) {
            jobManager.clear()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            jobManager.setFailed(e.message ?: "Analysis failed")
            AiJobNotifications.postMealFailed(applicationContext)
            Result.failure()
        } finally {
            pipeline.setPreferLocal(false)
        }
    }

    private class PrimaryOutcome(
        val foods: List<DetectedFood>,
        val source: AiSource,
        val onlineError: String?
    )

    /**
     * Prefer online; when we're about to use on-device, ask via the shared job manager (which, when
     * the phone app is backgrounded — the normal watch case — auto-picks on-device since nobody can
     * tap a button). Mirrors AnalysisService's text path.
     */
    private suspend fun analyzePrimary(description: String): PrimaryOutcome {
        var tryOnline = pipeline.canUseOnline()
        var onlineError: String? = if (tryOnline) null else pipeline.offlineReason()
        while (true) {
            if (!tryOnline) {
                when (confirmOnDevice(onlineError ?: pipeline.offlineReason())) {
                    FallbackChoice.RETRY_ONLINE -> {
                        tryOnline = pipeline.canUseOnline()
                        onlineError = if (tryOnline) null else pipeline.offlineReason()
                        continue
                    }
                    FallbackChoice.CANCEL -> throw AnalysisCancelledException()
                    FallbackChoice.USE_ON_DEVICE -> {
                        progress("Reading your description on-device…", AiSource.OFFLINE)
                        val foods = pipeline.describeMealLocal(description)
                        return PrimaryOutcome(foods, AiSource.OFFLINE, onlineError)
                    }
                }
            }
            progress("Preparing to ask the online AI…", AiSource.ONLINE)
            try {
                // describeMealOnline's onProgress is a plain (non-suspend) callback, but our
                // progress() is suspend (it calls setForeground). Bridge them: funnel callback
                // messages through a channel that one child coroutine drains in order.
                val foods = coroutineScope {
                    val progressChannel = Channel<String>(Channel.UNLIMITED)
                    val pump = launch {
                        for (msg in progressChannel) runCatching { progress(msg, AiSource.ONLINE) }
                    }
                    try {
                        pipeline.describeMealOnline(description) { msg -> progressChannel.trySend(msg) }
                    } finally {
                        progressChannel.close()
                        pump.join()
                    }
                }
                return PrimaryOutcome(foods, AiSource.ONLINE, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onlineError = friendlyReason(e)
                tryOnline = false
            }
        }
    }

    private suspend fun confirmOnDevice(reason: String): FallbackChoice {
        if (!pipeline.isOnlineAiEnabled()) return FallbackChoice.USE_ON_DEVICE
        if (!appForegroundState.isForeground) return FallbackChoice.USE_ON_DEVICE
        return jobManager.awaitFallbackChoice(reason)
    }

    private fun friendlyReason(e: Throwable): String = when (e) {
        is GeminiQuotaException -> "Daily free quota reached"
        else -> e.message ?: "Online request failed"
    }

    private suspend fun progress(message: String, source: AiSource) {
        jobManager.setProgress(message, source)
        runCatching { setForeground(foregroundInfo(AiJobNotifications.mealProgress(applicationContext, message))) }
    }

    private fun foregroundInfo(notification: android.app.Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(AiJobNotifications.NOTIF_MEAL_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(AiJobNotifications.NOTIF_MEAL_PROGRESS, notification)
        }

    companion object {
        const val KEY_DESCRIPTION = "description"
        const val KEY_MEAL_TYPE = "meal_type"
        const val KEY_DATE = "date"
        const val UNIQUE_NAME = "wear_meal_analysis"
    }
}
