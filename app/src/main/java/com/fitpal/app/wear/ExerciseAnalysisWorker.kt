package com.fitpal.app.wear

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.fitpal.app.ml.ExerciseAnalysisJobManager
import com.fitpal.app.ml.ExerciseEstimateParser
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.FoodPrompts
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Runs a watch-dictated exercise description through the phone's AI in the background (expedited
 * WorkManager work, same reasoning as [MealAnalysisWorker]). Reuses the shared exercise prompt +
 * parser and publishes into [ExerciseAnalysisJobManager] so the Log-exercise screen can adopt the
 * estimate for review / log. Posts a "tap to review" notification on finish.
 */
class ExerciseAnalysisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val entry = EntryPointAccessors.fromApplication(appContext, WearWorkerEntryPoint::class.java)
    private val pipeline: FoodAnalysisPipeline = entry.pipeline()
    private val jobManager: ExerciseAnalysisJobManager = entry.exerciseAnalysisJobManager()

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(AiJobNotifications.exerciseProgress(applicationContext, "Starting…"))

    override suspend fun doWork(): Result {
        AiJobNotifications.ensureChannels(applicationContext)
        val description = inputData.getString(KEY_DESCRIPTION)?.takeIf { it.isNotBlank() }
            ?: return Result.failure()

        jobManager.prepare(description)
        runCatching { setForeground(getForegroundInfo()) }

        return try {
            val weight = entry.weightRepository().getLatest().first()?.weightKg ?: 70f
            jobManager.setProgress("Estimating on your phone…")
            runCatching {
                setForeground(foregroundInfo(AiJobNotifications.exerciseProgress(applicationContext, "Estimating…")))
            }
            val prompt = FoodPrompts.exerciseEstimate(description, weight.toInt())
            val (response, source) = pipeline.generateRawTextWithSource(prompt)
            val estimate = ExerciseEstimateParser.parse(response, description, weight)
            jobManager.setResult(estimate, source)
            AiJobNotifications.postExerciseDone(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            jobManager.setFailed(e.message ?: "Estimate failed")
            AiJobNotifications.postExerciseFailed(applicationContext)
            Result.failure()
        }
    }

    private fun foregroundInfo(notification: android.app.Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(AiJobNotifications.NOTIF_EXERCISE_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(AiJobNotifications.NOTIF_EXERCISE_PROGRESS, notification)
        }

    companion object {
        const val KEY_DESCRIPTION = "description"
        const val UNIQUE_NAME = "wear_exercise_analysis"
    }
}
