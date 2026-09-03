package com.fitpal.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Daily background job (scheduled in `FitPalApplication`): refresh steps from Health Connect, then
 * nudge Calistapp to pull them. This is the "FitPal wakes Calistapp end-of-day" leg of the sync;
 * it's belt-and-suspenders with Calistapp's own self-scheduled job and its on-open reconciliation,
 * so a Samsung background kill on any one of them isn't fatal.
 *
 * Deps come from a Hilt @EntryPoint (same approach as the wear workers — no `hilt-work` needed).
 */
class StepNudgeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun stepRepository(): StepRepository
        fun weightRepository(): WeightRepository
        fun settingsRepository(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        runCatching {
            val ep = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)
            val kg = ep.weightRepository().getLatest().first()?.weightKg ?: 70f
            runCatching { ep.stepRepository().syncFromHealthConnect(kg) }
            if (ep.settingsRepository().calistappSyncEnabled.value) {
                CalistappNudge.nudge(applicationContext)
            }
        }
        // Never hard-fail: a missed nudge is recovered by Calistapp's on-open reconciliation.
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "fitpal_calistapp_step_nudge"
    }
}
