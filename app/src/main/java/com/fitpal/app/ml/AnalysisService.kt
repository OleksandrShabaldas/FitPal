package com.fitpal.app.ml

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.fitpal.app.MainActivity
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Foreground service that runs one meal analysis (photo or text) so it keeps going when the
 * user leaves the app. It writes progress + the result into [AnalysisJobManager] (which the
 * screens observe) and shows a notification: "Analysing…" while running, "tap to review" when
 * done. If the user walks away without logging, [AnalysisJobManager.autoSaveAndStop] saves it.
 */
@AndroidEntryPoint
class AnalysisService : Service() {

    @Inject lateinit var pipeline: FoodAnalysisPipeline
    @Inject lateinit var jobManager: AnalysisJobManager
    @Inject lateinit var appForegroundState: AppForegroundState

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null
    private var runningJobId: String? = null
    private val finishing = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val job = jobManager.current
        if (job == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        startForeground(progressNotification(job, "Starting…"))
        // Start (or restart, if the user kicked off a different analysis) the worker.
        if (runningJobId != job.id) {
            worker?.cancel()
            runningJobId = job.id
            worker = scope.launch { runJob(job) }
        }
        return START_NOT_STICKY
    }

    private suspend fun runJob(job: AnalysisJob) {
        try {
            when (job.kind) {
                JobKind.IMAGE -> runImage(job)
                JobKind.TEXT -> runText(job)
            }
        } catch (e: CancellationException) {
            throw e  // a newer job replaced this one — let it die quietly
        } catch (e: AnalysisCancelledException) {
            // The user cancelled the on-device confirm prompt — drop the job, no error notice.
            jobManager.clear()
            stopSelf()
        } catch (e: Exception) {
            jobManager.setFailed(e.message ?: "Analysis failed")
            notify(NOTIF_ID, failedNotification())
            stopSelf()
        } finally {
            // Release the per-job "secondary steps stay on-device" pin (see runImage/runText).
            pipeline.setPreferLocal(false)
        }
    }

    /** The result of the primary photo/description analysis, plus which engine made it. */
    private class PrimaryOutcome(
        val foods: List<DetectedFood>,
        val source: AiSource,
        val onlineError: String?,
        /** Candidate dishes from the online single pass (empty for on-device / text). */
        val dishCandidates: List<DetectedFood> = emptyList()
    )

    private suspend fun runImage(job: AnalysisJob) {
        updateProgress(job, "Reading and resizing the photo…")
        val bitmap = decodeBitmap(Uri.parse(job.imageUri))
        val outcome = analyzePrimaryImage(job, bitmap)
        // Keep the secondary steps (dish naming, insights) on the same engine the primary used,
        // so a forced on-device run doesn't waste time retrying a dead online connection.
        pipeline.setPreferLocal(outcome.source.isOffline)

        // Online already named the dish AND produced any candidates in its single pass. On-device
        // keeps the two-pass workaround: its weaker model names the dish better when shown the
        // photo again alongside the loose ingredients.
        val candidates: List<DetectedFood> = if (outcome.source.isOnline) {
            outcome.dishCandidates
        } else {
            val loose = outcome.foods.flatMap { it.ingredients }
            if (outcome.foods.isNotEmpty() && outcome.foods.none { it.isDrink }) {
                updateProgress(job, "Naming the dish…", outcome.source)
                runCatching { pipeline.identifyDish(loose, bitmap, job.note) }.getOrDefault(emptyList())
            } else emptyList()
        }
        // When there are candidate dishes, show the first as the current pick (the chips let the
        // user switch); otherwise the plate's foods are the result as-is.
        val finalFoods = if (candidates.isNotEmpty()) listOf(candidates.first()) else outcome.foods

        // Foods are ready — surface them and flip the notification to "tap to review".
        jobManager.setResult(
            finalFoods, candidates, insights = null,
            source = outcome.source,
            onlineError = outcome.onlineError.takeIf { outcome.source.isOffline }
        )
        notify(NOTIF_ID, completeNotification(job))

        // Health insights run after, updating the result in place (doesn't block review/save).
        val insights = computeInsights(finalFoods)
        jobManager.setInsights(insights)
    }

    private suspend fun runText(job: AnalysisJob) {
        val outcome = analyzePrimaryText(job)
        pipeline.setPreferLocal(outcome.source.isOffline)
        jobManager.setResult(
            outcome.foods, emptyList(), insights = null,
            source = outcome.source,
            onlineError = outcome.onlineError.takeIf { outcome.source.isOffline }
        )
        notify(NOTIF_ID, completeNotification(job))
    }

    /**
     * Run the primary photo analysis, preferring online. Whenever we're about to use the on-device
     * model — because online failed OR isn't available — confirm with the user first (retry online
     * / continue on-device / cancel), unless they've turned online AI off or the app is backgrounded
     * (then on-device proceeds automatically). Returns once a result is produced.
     */
    private suspend fun analyzePrimaryImage(job: AnalysisJob, bitmap: android.graphics.Bitmap): PrimaryOutcome {
        var tryOnline = pipeline.canUseOnline()
        var onlineError: String? = if (tryOnline) null else pipeline.offlineReason()
        while (true) {
            if (!tryOnline) {
                when (confirmOnDevice(job, onlineError ?: pipeline.offlineReason())) {
                    FallbackChoice.RETRY_ONLINE -> {
                        tryOnline = pipeline.canUseOnline()
                        onlineError = if (tryOnline) null else pipeline.offlineReason()
                        continue
                    }
                    FallbackChoice.CANCEL -> throw AnalysisCancelledException()
                    FallbackChoice.USE_ON_DEVICE -> {
                        updateProgress(
                            job,
                            if (onlineError != null) "Switching to the on-device model…"
                            else "Looking at your food on-device…",
                            AiSource.offline()
                        )
                        val foods = pipeline.analyzeImageLocal(bitmap, job.note) { msg -> updateProgress(job, msg, AiSource.offline()) }
                        return PrimaryOutcome(foods, AiSource.offline(), onlineError)
                    }
                }
            }
            updateProgress(job, "Preparing to ask the online AI…", AiSource.online())
            try {
                // Which model answered is only known once the request lands (the client cascades
                // through the configured models), so it's captured here and named on the badge.
                var model: String? = null
                val result = pipeline.analyzeImageOnline(
                    bitmap, job.note, onModel = { model = it }
                ) { msg -> updateProgress(job, msg, AiSource.online(model)) }
                return PrimaryOutcome(result.foods, AiSource.online(model), null, result.dishCandidates)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onlineError = friendlyReason(e)
                tryOnline = false  // loop back to the confirm prompt before using on-device
            }
        }
    }

    private suspend fun analyzePrimaryText(job: AnalysisJob): PrimaryOutcome {
        var tryOnline = pipeline.canUseOnline()
        var onlineError: String? = if (tryOnline) null else pipeline.offlineReason()
        while (true) {
            if (!tryOnline) {
                when (confirmOnDevice(job, onlineError ?: pipeline.offlineReason())) {
                    FallbackChoice.RETRY_ONLINE -> {
                        tryOnline = pipeline.canUseOnline()
                        onlineError = if (tryOnline) null else pipeline.offlineReason()
                        continue
                    }
                    FallbackChoice.CANCEL -> throw AnalysisCancelledException()
                    FallbackChoice.USE_ON_DEVICE -> {
                        updateProgress(
                            job,
                            if (onlineError != null) "Switching to the on-device model…"
                            else "Reading your description on-device…",
                            AiSource.offline()
                        )
                        val foods = pipeline.describeMealLocal(job.description)
                        return PrimaryOutcome(foods, AiSource.offline(), onlineError)
                    }
                }
            }
            updateProgress(job, "Preparing to ask the online AI…", AiSource.online())
            try {
                var model: String? = null
                val foods = pipeline.describeMealOnline(
                    job.description, onModel = { model = it }
                ) { msg -> updateProgress(job, msg, AiSource.online(model)) }
                return PrimaryOutcome(foods, AiSource.online(model), null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onlineError = friendlyReason(e)
                tryOnline = false  // loop back to the confirm prompt before using on-device
            }
        }
    }

    /**
     * About to use the on-device model. Ask the user to confirm — retry online / continue
     * on-device / cancel. Skipped (auto on-device) when they've turned online AI off entirely
     * (on-device IS their choice) or the app is in the background (no one to tap a button).
     */
    private suspend fun confirmOnDevice(job: AnalysisJob, reason: String): FallbackChoice {
        if (!pipeline.isOnlineAiEnabled()) return FallbackChoice.USE_ON_DEVICE
        if (!appForegroundState.isForeground) return FallbackChoice.USE_ON_DEVICE
        notify(NOTIF_ID, onlineFailedNotification(job))
        return jobManager.awaitFallbackChoice(reason)
    }

    private fun friendlyReason(e: Throwable): String = when (e) {
        is GeminiQuotaException -> "Daily free quota reached"
        else -> e.message ?: "Online request failed"
    }

    private suspend fun computeInsights(foods: List<DetectedFood>): MealInsights {
        val ai = runCatching { pipeline.analyzeMealInsights(foods) }.getOrNull()
        val scored = HealthScorer.score(
            calories = foods.sumOf { it.totalCalories.toDouble() }.toFloat(),
            protein = foods.sumOf { it.totalProtein.toDouble() }.toFloat(),
            fat = foods.sumOf { it.totalFat.toDouble() }.toFloat(),
            carbs = foods.sumOf { it.totalCarbs.toDouble() }.toFloat(),
            fiber = foods.sumOf { it.totalFiber.toDouble() }.toFloat(),
            grams = foods.sumOf { it.totalGrams.toDouble() }.toFloat(),
            isDrink = foods.isNotEmpty() && foods.all { it.isDrink },
            micros = foods.fold(Micronutrients()) { acc, f -> acc + f.totalMicros }
        )
        return (ai ?: MealInsights(scored.score, emptyList(), emptyList(), "", "", emptyList()))
            .copy(healthScore = scored.score, scoreFactors = scored.factors)
    }

    private fun updateProgress(job: AnalysisJob, message: String, source: AiSource? = null) {
        jobManager.setProgress(message, source)
        notify(NOTIF_ID, progressNotification(job, message))
    }

    // ---- Abandonment → auto-save ----

    override fun onTaskRemoved(rootIntent: Intent?) {
        triggerFinish()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        triggerFinish()
        scope.cancel()
        super.onDestroy()
    }

    /** Save the result if the user left without logging, then let the service die. Idempotent. */
    private fun triggerFinish() {
        if (!finishing.compareAndSet(false, true)) return
        jobManager.autoSaveAndStop()
    }

    // ---- Notifications ----

    private fun startForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Meal analysis", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Progress and results of AI meal analysis"
                        setShowBadge(false)
                    }
                )
            }
        }
    }

    private fun notify(id: Int, notification: android.app.Notification) {
        runCatching { getSystemService(NotificationManager::class.java).notify(id, notification) }
    }

    private fun progressNotification(job: AnalysisJob, message: String): android.app.Notification =
        baseNotification(job)
            .setContentTitle("Analysing your meal…")
            .setContentText(message)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()

    private fun completeNotification(job: AnalysisJob): android.app.Notification =
        baseNotification(job)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Meal analysed")
            .setContentText("Tap to review and log it")
            .setOngoing(true)
            .build()

    private fun failedNotification(): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Couldn't analyse the meal")
            .setContentText("Open FitPal to try again")
            .setAutoCancel(true)
            .build()

    private fun onlineFailedNotification(job: AnalysisJob): android.app.Notification =
        baseNotification(job)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Online AI unavailable")
            .setContentText("Tap to retry or use on-device")
            .setOngoing(true)
            .build()

    private fun baseNotification(job: AnalysisJob): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(openIntent(routeFor(job)))
            .setOnlyAlertOnce(true)

    private fun routeFor(job: AnalysisJob): String = when (job.kind) {
        JobKind.IMAGE -> Screen.Analysis.buildRoute(job.imageUri)
        JobKind.TEXT -> Screen.DescribeFood.route
    }

    private fun openIntent(route: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(MainActivity.EXTRA_NAV_ROUTE, route)
        }
        return PendingIntent.getActivity(
            this, route.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Decode a content URI into a downscaled software bitmap (matches AnalysisViewModel). */
    private fun decodeBitmap(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.isMutableRequired = true
            val maxDim = maxOf(info.size.width, info.size.height)
            if (maxDim > MAX_IMAGE_DIMENSION) {
                decoder.setTargetSampleSize((maxDim / MAX_IMAGE_DIMENSION).coerceAtLeast(1))
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "meal_analysis"
        private const val NOTIF_ID = 4201
        private const val MAX_IMAGE_DIMENSION = 1024
    }
}
