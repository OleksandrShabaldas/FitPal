package com.fitpal.app.wear

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.shared.WearContract
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Receives commands from the Wear OS companion over the Data Layer. Google Play Services cold-starts
 * this service to deliver a message even when the FitPal app has been killed, which is what lets the
 * watch log water / kick off an AI analysis without the phone app being open.
 *
 * - Water is a fast DB write, done inline (the callback runs on a background thread; the service is
 *   kept alive until it returns).
 * - The AI meal / exercise analyses are handed to **expedited WorkManager** rather than started
 *   directly, because a wearable message is not an exemption to Android 12+'s
 *   background-foreground-service-start restriction (see [MealAnalysisWorker]).
 */
@AndroidEntryPoint
class WearDataLayerService : WearableListenerService() {

    @Inject lateinit var mealRepository: MealRepository
    @Inject lateinit var stepRepository: StepRepository
    @Inject lateinit var weightRepository: WeightRepository
    @Inject lateinit var statsPublisher: WearStatsPublisher
    @Inject lateinit var updateManager: com.fitpal.app.update.UpdateManager

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearContract.PATH_LOG_WATER -> {
                val ml = String(event.data).trim().toFloatOrNull() ?: return
                if (ml <= 0f) return
                runCatching {
                    runBlocking {
                        mealRepository.logWater(ml)
                        statsPublisher.publishNow()
                    }
                }
            }

            WearContract.PATH_DESCRIBE_MEAL -> {
                val text = String(event.data).trim()
                if (text.isNotEmpty()) enqueueMeal(text)
            }

            WearContract.PATH_DESCRIBE_EXERCISE -> {
                val text = String(event.data).trim()
                if (text.isNotEmpty()) enqueueExercise(text)
            }

            WearContract.PATH_REQUEST_STATS -> {
                runCatching { runBlocking { statsPublisher.publishNow() } }
            }

            WearContract.PATH_WATCH_VERSION -> {
                // The watch answering "what version are you on?" — drives the update card's
                // "your watch is on 1.0.0" line.
                val version = String(event.data).trim()
                if (version.isNotEmpty()) updateManager.onWatchVersionReported(version)
            }

            WearContract.PATH_LOG_STEPS -> {
                // "yyyy-MM-dd|steps" — the watch's own daily count, used as that day's step floor.
                val parts = String(event.data).trim().split('|')
                val dateIso = parts.getOrNull(0)?.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }
                val steps = parts.getOrNull(1)?.toIntOrNull() ?: 0
                if (dateIso != null && steps > 0) {
                    runCatching {
                        runBlocking {
                            val kg = weightRepository.getLatest().first()?.weightKg ?: 70f
                            stepRepository.recordWatchSteps(dateIso, steps, kg)
                            statsPublisher.publishNow()
                        }
                    }
                }
            }
        }
    }

    private fun enqueueMeal(text: String) {
        val request = OneTimeWorkRequestBuilder<MealAnalysisWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(Data.Builder().putString(MealAnalysisWorker.KEY_DESCRIPTION, text).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniqueWork(MealAnalysisWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun enqueueExercise(text: String) {
        val request = OneTimeWorkRequestBuilder<ExerciseAnalysisWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(Data.Builder().putString(ExerciseAnalysisWorker.KEY_DESCRIPTION, text).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniqueWork(ExerciseAnalysisWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
