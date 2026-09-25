package com.fitpal.app.ml

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.mealLogMeta
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.EatenAt
import com.fitpal.app.domain.model.LogDecision
import com.fitpal.app.domain.model.MealInsights
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the in-flight job came from a photo or a text description. */
enum class JobKind { IMAGE, TEXT }

/** The user's answer to the "about to use the on-device model" confirm prompt. */
enum class FallbackChoice { RETRY_ONLINE, USE_ON_DEVICE, CANCEL }

/** Thrown inside the worker when the user cancels the on-device confirm prompt. */
class AnalysisCancelledException : Exception()

/** Live state of the one background analysis job. */
sealed interface JobStatus {
    /** In progress. [source] = which engine is running right now (for the live badge). */
    data class Running(val message: String, val source: AiSource? = null) : JobStatus
    /** Online failed while the user was watching — awaiting their choice (retry / use on-device). */
    data class OnlineFailed(val reason: String) : JobStatus
    data class Done(
        val foods: List<DetectedFood>,
        val dishCandidates: List<DetectedFood>,
        val insights: MealInsights?,
        /** Which engine produced this — for the "online / on-device" badge. */
        val source: AiSource? = null,
        /** If it fell back to on-device, why (online error) — shown next to the badge. */
        val onlineError: String? = null
    ) : JobStatus
    data class Failed(val message: String) : JobStatus
}

data class AnalysisJob(
    val id: String,
    val kind: JobKind,
    val mealType: String,
    val targetDate: String?,
    val imageUri: String? = null,
    val note: String = "",
    val description: String = "",
    val status: JobStatus = JobStatus.Running("Starting…"),
    /** True once the meal has been logged (by the user or by auto-save) — don't save twice. */
    val saved: Boolean = false,
    /** When the meal was eaten, as set on screen (photo time / picked / now) — an auto-save keeps it. */
    val eatenAt: EatenAt = EatenAt(),
    /** Situation tags picked while waiting ("Home", "Family meal"…) — an auto-save keeps them. */
    val tags: Set<String> = emptySet()
)

/**
 * Holds the single in-flight (or just-finished) AI meal analysis so it survives the user
 * leaving the screen or the whole app. The generation itself runs in [AnalysisService] (a
 * foreground service); screens observe [state] and adopt the result. If the user abandons
 * the app before logging, the service calls [autoSaveAndStop] so the meal is still saved.
 */
@Singleton
class AnalysisJobManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mealRepository: MealRepository,
    private val appForegroundState: AppForegroundState,
    private val settingsRepository: SettingsRepository
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<AnalysisJob?>(null)
    val state: StateFlow<AnalysisJob?> = _state.asStateFlow()
    val current: AnalysisJob? get() = _state.value

    /** Set while the service worker is paused waiting for the user to choose retry vs on-device. */
    @Volatile
    private var choiceSignal: CompletableDeferred<FallbackChoice>? = null

    init {
        // If the app goes to the background while we're waiting for a fallback choice, there's
        // no one to tap a button — pick on-device automatically so the analysis still finishes.
        appScope.launch {
            appForegroundState.isForegroundFlow.collect { foreground ->
                if (!foreground) choiceSignal?.complete(FallbackChoice.USE_ON_DEVICE)
            }
        }
    }

    fun startImageJob(
        imageUri: String,
        note: String,
        mealType: String,
        targetDate: String?,
        eatenAt: EatenAt = EatenAt(),
        tags: Set<String> = emptySet()
    ) {
        _state.value = AnalysisJob(
            id = UUID.randomUUID().toString(), kind = JobKind.IMAGE,
            mealType = mealType, targetDate = targetDate, imageUri = imageUri, note = note,
            eatenAt = eatenAt, tags = tags
        )
        launchService()
    }

    fun startTextJob(
        description: String,
        mealType: String,
        targetDate: String?,
        eatenAt: EatenAt = EatenAt(),
        tags: Set<String> = emptySet()
    ) {
        prepareTextJob(description, mealType, targetDate)
        _state.update { it?.copy(eatenAt = eatenAt, tags = tags) }
        launchService()
    }

    /**
     * Set up a text job's live state WITHOUT launching [AnalysisService]. Used by the watch path:
     * a wearable message can't legally start a foreground service on Android 12+, so the analysis
     * runs in an expedited Worker instead — but it still publishes into this manager so that if the
     * phone app is opened, the Describe screen adopts the in-flight/finished job exactly as usual.
     */
    fun prepareTextJob(description: String, mealType: String, targetDate: String?): AnalysisJob {
        val job = AnalysisJob(
            id = UUID.randomUUID().toString(), kind = JobKind.TEXT,
            mealType = mealType, targetDate = targetDate, description = description
        )
        _state.value = job
        return job
    }

    // ---- Called by the service as it works ----

    fun setProgress(message: String, source: AiSource? = null) =
        _state.update { it?.copy(status = JobStatus.Running(message, source)) }

    /**
     * Called by the service when online analysis failed (or wasn't available) while the app is in
     * the foreground: publish the "online failed" state and SUSPEND until the user chooses to retry
     * online, use on-device, or cancel (or the app backgrounds → on-device automatically). No new
     * service start is needed — the service's worker simply waits here.
     */
    suspend fun awaitFallbackChoice(reason: String): FallbackChoice {
        val deferred = CompletableDeferred<FallbackChoice>()
        choiceSignal = deferred
        _state.update { it?.copy(status = JobStatus.OnlineFailed(reason)) }
        // Guard a race: if we already slipped into the background, don't wait.
        if (!appForegroundState.isForeground) deferred.complete(FallbackChoice.USE_ON_DEVICE)
        return try {
            deferred.await()
        } finally {
            if (choiceSignal === deferred) choiceSignal = null
        }
    }

    /** User tapped "Try online again". */
    fun chooseRetryOnline() { choiceSignal?.complete(FallbackChoice.RETRY_ONLINE) }

    /** User tapped "Use on-device". */
    fun chooseUseLocal() { choiceSignal?.complete(FallbackChoice.USE_ON_DEVICE) }

    /** User tapped "Cancel" on the on-device confirm prompt — abandon the analysis. */
    fun chooseCancel() { choiceSignal?.complete(FallbackChoice.CANCEL) }

    fun setResult(
        foods: List<DetectedFood>,
        candidates: List<DetectedFood>,
        insights: MealInsights?,
        source: AiSource? = null,
        onlineError: String? = null
    ) = _state.update { it?.copy(status = JobStatus.Done(foods, candidates, insights, source, onlineError)) }

    /** Attach insights once they finish, without disturbing the (possibly edited) foods. */
    fun setInsights(insights: MealInsights) = _state.update { job ->
        val s = job?.status
        if (s is JobStatus.Done) job.copy(status = s.copy(insights = insights)) else job
    }

    /** Called when the user logs the meal from the screen — don't auto-save again. */
    fun completeByUser() {
        markSaved()
        clear()
        stopService()
    }

    fun setFailed(message: String) = _state.update { it?.copy(status = JobStatus.Failed(message)) }

    fun markSaved() = _state.update { it?.copy(saved = true) }

    /** Apply the meal type the user picked on-screen, so an auto-save uses it. */
    fun updateMealType(mealType: String) = _state.update { it?.copy(mealType = mealType) }

    /** Apply the log date the user picked on-screen (ISO, or null = today), so an auto-save uses it. */
    fun updateTargetDate(dateIso: String?) = _state.update { it?.copy(targetDate = dateIso) }

    /** Apply the eaten time the user set on-screen, so an auto-save uses it. */
    fun updateEatenAt(eatenAt: EatenAt) = _state.update { it?.copy(eatenAt = eatenAt) }

    /** Apply the situation tags the user picked on-screen, so an auto-save uses them. */
    fun updateTags(tags: Set<String>) = _state.update { it?.copy(tags = tags) }

    /** Clear the job entirely once it's been logged or dismissed. */
    fun clear() { _state.value = null }

    /**
     * Drop the current job WITHOUT saving it — the user reviewed the result and backed out, so it
     * should NOT be auto-saved on app close. Also completes any pending fallback choice so a
     * suspended worker unwinds cleanly.
     */
    fun discard() {
        choiceSignal?.complete(FallbackChoice.CANCEL)
        clear()
        stopService()
    }

    /**
     * Save the finished-but-unlogged meal (the user walked away) and stop the service.
     * Runs on an app-scoped coroutine so it completes even as the service is torn down.
     */
    fun autoSaveAndStop() {
        val job = current
        val status = job?.status
        if (job == null || status !is JobStatus.Done || job.saved) {
            stopService()
            return
        }
        markSaved()
        appScope.launch {
            try {
                val photo = if (job.kind == JobKind.IMAGE) persistImage(job.imageUri) else null
                val logDate = job.targetDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                    ?: java.time.LocalDate.now()
                mealRepository.logMeal(
                    foods = status.foods,
                    mealType = job.mealType,
                    photoPath = photo,
                    insights = status.insights,
                    date = job.targetDate,
                    source = status.source,
                    meta = mealLogMeta(logDate, job.eatenAt, autoSaveDecision(job, logDate), job.tags)
                )
            } catch (_: Exception) {
                // Best effort — nothing more we can do if the save fails as we're shutting down.
            } finally {
                clear()
                stopService()
            }
        }
    }

    /**
     * An auto-save never shows the fasting prompt (nobody's there to answer it), so it can't spend a
     * pass either: a meal auto-saved for today during a fast — without a photo proving it was eaten
     * inside the window — counts as logged during the fast, exactly like "log anyway".
     */
    private fun autoSaveDecision(job: AnalysisJob, logDate: java.time.LocalDate): LogDecision {
        val schedule = settingsRepository.fastingSchedule.value
        if (!schedule.enabled || logDate != java.time.LocalDate.now()) return LogDecision.NONE
        val now = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        if (schedule.isEatingAt(now)) return LogDecision.NONE
        val photoProof = job.eatenAt.isPhotoTime && schedule.isEatingAt(job.eatenAt.minuteOfDay())
        return if (photoProof) LogDecision.NONE else LogDecision(loggedDuringFast = true)
    }

    private fun launchService() {
        ContextCompat.startForegroundService(context, Intent(context, AnalysisService::class.java))
    }

    fun stopService() {
        runCatching { context.stopService(Intent(context, AnalysisService::class.java)) }
    }

    /** Copy a content URI into app storage so the saved meal's photo survives. */
    private fun persistImage(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            val dir = File(context.filesDir, "meal_photos").also { it.mkdirs() }
            val dest = File(dir, "meal_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return uriString
            dest.absolutePath
        } catch (e: Exception) {
            uriString
        }
    }
}
