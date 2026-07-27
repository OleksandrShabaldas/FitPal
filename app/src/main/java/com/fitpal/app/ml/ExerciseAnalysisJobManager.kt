package com.fitpal.app.ml

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Live state of the one background exercise estimate (mirrors [AnalysisJobManager] for meals). */
sealed interface ExerciseJobStatus {
    data class Running(val message: String) : ExerciseJobStatus
    data class Done(val estimate: ParsedExerciseEstimate, val source: AiSource? = null) : ExerciseJobStatus
    data class Failed(val message: String) : ExerciseJobStatus
}

data class ExerciseJob(
    val id: String,
    val query: String,
    val status: ExerciseJobStatus = ExerciseJobStatus.Running("Starting…")
)

/**
 * Holds the single in-flight (or just-finished) AI exercise estimate so it survives the user
 * leaving the screen or the whole app. The generation runs in
 * [com.fitpal.app.wear.ExerciseAnalysisWorker] (watch-triggered) and the Log-exercise screen
 * observes [state] to adopt the result for review / log.
 */
@Singleton
class ExerciseAnalysisJobManager @Inject constructor() {

    private val _state = MutableStateFlow<ExerciseJob?>(null)
    val state: StateFlow<ExerciseJob?> = _state.asStateFlow()
    val current: ExerciseJob? get() = _state.value

    /** Set up a job's state (no service launch — the worker does the work). */
    fun prepare(query: String): ExerciseJob {
        val job = ExerciseJob(id = UUID.randomUUID().toString(), query = query)
        _state.value = job
        return job
    }

    fun setProgress(message: String) =
        _state.update { it?.copy(status = ExerciseJobStatus.Running(message)) }

    fun setResult(estimate: ParsedExerciseEstimate, source: AiSource?) =
        _state.update { it?.copy(status = ExerciseJobStatus.Done(estimate, source)) }

    fun setFailed(message: String) =
        _state.update { it?.copy(status = ExerciseJobStatus.Failed(message)) }

    /** Called when the user logs (or dismisses) the estimate so it isn't re-adopted. */
    fun clear() { _state.value = null }
}
