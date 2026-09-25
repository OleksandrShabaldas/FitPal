package com.fitpal.app.ml

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fitpal.app.wear.WearWorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Fills the nutrient gaps of one just-logged meal in the background (see [NutrientFiller]): a food
 * from the database gets its fibre/vitamins/minerals/caffeine checked once, and anything with unknown
 * caffeine gets a caffeine check while the tracker is on. Only needs the network, so WorkManager
 * holds it until there's a connection and it survives the app being closed.
 *
 * Dependencies come through the same Hilt entry point as the other workers (the app avoids hilt-work).
 */
class NutrientCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val entry = EntryPointAccessors.fromApplication(appContext, WearWorkerEntryPoint::class.java)

    override suspend fun doWork(): Result {
        val mealLogId = inputData.getLong(KEY_MEAL_LOG_ID, -1L)
        if (mealLogId < 0L) return Result.failure()
        return try {
            val done = entry.mealRepository().fillNutrientGaps(mealLogId)
            when {
                done -> Result.success()
                // The quick AI couldn't run (no quota / flaky network) — try again later, a few times.
                runAttemptCount < MAX_ATTEMPTS -> Result.retry()
                else -> Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        const val KEY_MEAL_LOG_ID = "meal_log_id"
        private const val MAX_ATTEMPTS = 4

        /** Queue the check for [mealLogId] (idempotent per meal; waits for a network connection). */
        fun enqueue(context: Context, mealLogId: Long) {
            val request = OneTimeWorkRequestBuilder<NutrientCheckWorker>()
                .setInputData(workDataOf(KEY_MEAL_LOG_ID to mealLogId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("nutrients_$mealLogId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
