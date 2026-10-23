package com.fitpal.app.ml

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fitpal.app.domain.model.PlanRequest
import com.fitpal.app.domain.model.PlanScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Where the one meal-planning job is. */
sealed interface PlanStatus {
    data class Running(val message: String, val source: AiSource? = null) : PlanStatus
    /** Online failed while the user was watching — waiting for Retry / Use on-device / Cancel. */
    data class OnlineFailed(val reason: String) : PlanStatus
    /** [note] = a neutral word when it ran on the phone ("recipes may be simpler"). */
    data class Done(val count: Int, val source: AiSource?, val note: String? = null) : PlanStatus
    data class Failed(val message: String) : PlanStatus
}

data class PlanJob(
    val requestId: String,
    val scope: PlanScope,
    val status: PlanStatus,
    /** Set when one meal is being regenerated in place. */
    val regenerateId: Long? = null
)

/** Thrown inside the worker when the user cancels the on-device prompt. */
class PlanCancelledException : Exception()

/**
 * The meal planner's one background job (docs/redesign/10), like [AnalysisJobManager]: a day or a
 * week can take minutes, so generation runs in [PlannerWorker] (WorkManager, survives leaving the
 * app) and the screens only start it and watch this state.
 */
@Singleton
class PlannerJobManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appForeground: AppForegroundState
) {
    private val _state = MutableStateFlow<PlanJob?>(null)
    val state: StateFlow<PlanJob?> = _state

    private var choiceSignal: CompletableDeferred<FallbackChoice>? = null

    /** Start planning; returns the request id the results will carry. */
    fun start(request: PlanRequest): String {
        val id = UUID.randomUUID().toString()
        _state.value = PlanJob(id, request.scope, PlanStatus.Running("Starting…"))
        enqueue(id, request, regenerateId = null, avoid = emptyList())
        return id
    }

    /** Make one planned meal again, in place; [avoid] = the other meals' names, so it doesn't repeat. */
    fun regenerate(requestId: String, mealId: Long, request: PlanRequest, avoid: List<String>) {
        _state.value = PlanJob(requestId, request.scope, PlanStatus.Running("Starting…"), regenerateId = mealId)
        enqueue(requestId, request, mealId, avoid)
    }

    private fun enqueue(requestId: String, request: PlanRequest, regenerateId: Long?, avoid: List<String>) {
        val work = OneTimeWorkRequestBuilder<PlannerWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                workDataOf(
                    PlannerWorker.KEY_REQUEST to request.toJson(),
                    PlannerWorker.KEY_REQUEST_ID to requestId,
                    PlannerWorker.KEY_REGENERATE_ID to (regenerateId ?: -1L),
                    PlannerWorker.KEY_AVOID to avoid.joinToString("\n")
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(PlannerWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, work)
    }

    fun setProgress(message: String, source: AiSource?) =
        _state.update { it?.copy(status = PlanStatus.Running(message, source)) }

    fun setDone(count: Int, source: AiSource?, note: String?) =
        _state.update { it?.copy(status = PlanStatus.Done(count, source, note)) }

    fun setFailed(message: String) = _state.update { it?.copy(status = PlanStatus.Failed(message)) }

    fun clear() { _state.value = null }

    /** Online failed: ask the user (foreground) or go on-device (background), like the photo analysis. */
    suspend fun awaitFallbackChoice(reason: String): FallbackChoice {
        if (!appForeground.isForeground) return FallbackChoice.USE_ON_DEVICE
        val deferred = CompletableDeferred<FallbackChoice>()
        choiceSignal = deferred
        _state.update { it?.copy(status = PlanStatus.OnlineFailed(reason)) }
        if (!appForeground.isForeground) deferred.complete(FallbackChoice.USE_ON_DEVICE)
        return try {
            deferred.await()
        } finally {
            if (choiceSignal === deferred) choiceSignal = null
        }
    }

    fun chooseRetryOnline() { choiceSignal?.complete(FallbackChoice.RETRY_ONLINE) }
    fun chooseUseLocal() { choiceSignal?.complete(FallbackChoice.USE_ON_DEVICE) }
    fun cancel() {
        choiceSignal?.complete(FallbackChoice.CANCEL)
        WorkManager.getInstance(context).cancelUniqueWork(PlannerWorker.UNIQUE_NAME)
        clear()
    }
}
