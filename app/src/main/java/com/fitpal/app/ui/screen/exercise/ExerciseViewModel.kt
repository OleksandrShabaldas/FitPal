package com.fitpal.app.ui.screen.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.SavedWorkoutEntity
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.ExerciseMet
import com.fitpal.app.domain.MealLogContext
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ml.ExerciseAnalysisJobManager
import com.fitpal.app.ml.ExerciseEstimateParser
import com.fitpal.app.ml.ExerciseJobStatus
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.FoodPrompts
import com.fitpal.app.ml.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseEstimate(
    val activity: String,
    val minutes: Int,
    val met: Float,
    val calories: Float,
    /** Short coaching tips from the AI. */
    val suggestions: List<String> = emptyList()
) {
    /** Intensity bucket from the MET value, for the badge. */
    val intensity: String get() = when {
        met < 3f -> "Light"
        met < 6f -> "Moderate"
        else -> "Vigorous"
    }
}

/** A one-tap common activity: a fixed MET + sensible default duration. No AI / network needed. */
data class QuickActivity(val label: String, val met: Float, val defaultMinutes: Int)

/** The common workouts offered as instant chips, using the same MET math as the AI path. */
val QUICK_ACTIVITIES: List<QuickActivity> = listOf(
    QuickActivity("Walk", 3.5f, 30),
    QuickActivity("Run", 9.8f, 30),
    QuickActivity("Cycle", 7.5f, 30),
    QuickActivity("Weights", 5f, 45),
    QuickActivity("HIIT", 8f, 20),
    QuickActivity("Swim", 7f, 30),
    QuickActivity("Yoga", 2.5f, 30),
    QuickActivity("Row", 7f, 20)
)

data class ExerciseUiState(
    val query: String = "",
    val isEstimating: Boolean = false,
    val estimate: ExerciseEstimate? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val modelReady: Boolean = true,
    /** Which engine produced the estimate (badge). */
    val aiSource: AiSource? = null
)

@HiltViewModel
class ExerciseViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val weightRepository: WeightRepository,
    private val pipeline: FoodAnalysisPipeline,
    private val modelManager: ModelManager,
    private val exerciseJobManager: ExerciseAnalysisJobManager,
    mealLogContext: MealLogContext
) : ViewModel() {

    private val targetDate: String? = mealLogContext.consumeDate()

    /** AI is usable if the online model is available OR the on-device model is downloaded. */
    private fun aiAvailable(): Boolean = pipeline.canUseOnline() || modelManager.isLlmReady

    private val _uiState = MutableStateFlow(ExerciseUiState(modelReady = aiAvailable()))
    val uiState: StateFlow<ExerciseUiState> = _uiState

    /** Set once a background (watch-triggered) estimate has been adopted, so it isn't re-adopted. */
    private var adoptedJobId: String? = null

    init {
        // If the watch kicked off an estimate that ran in the background, adopt it here for review.
        observeJob()
    }

    private fun observeJob() {
        viewModelScope.launch {
            exerciseJobManager.state.collect { job ->
                if (job == null) return@collect
                when (val s = job.status) {
                    is ExerciseJobStatus.Running -> _uiState.update {
                        it.copy(
                            query = it.query.ifBlank { job.query },
                            isEstimating = true, error = null
                        )
                    }
                    is ExerciseJobStatus.Done -> if (adoptedJobId != job.id) {
                        adoptedJobId = job.id
                        _uiState.update {
                            it.copy(
                                query = it.query.ifBlank { job.query },
                                isEstimating = false,
                                estimate = ExerciseEstimate(
                                    s.estimate.activity, s.estimate.minutes, s.estimate.met,
                                    s.estimate.calories, s.estimate.suggestions
                                ),
                                aiSource = s.source
                            )
                        }
                    }
                    is ExerciseJobStatus.Failed -> _uiState.update {
                        it.copy(isEstimating = false, error = "Couldn't estimate: ${s.message}")
                    }
                }
            }
        }
    }

    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q) }
    }

    fun estimate() {
        val desc = _uiState.value.query.trim()
        // Re-check availability (online may have come back). Don't require the on-device model.
        _uiState.update { it.copy(modelReady = aiAvailable()) }
        if (desc.isEmpty() || !aiAvailable()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isEstimating = true, error = null) }
            try {
                val weight = weightRepository.getLatest().first()?.weightKg ?: 70f
                val prompt = FoodPrompts.exerciseEstimate(desc, weight.toInt())
                val (response, source) = pipeline.generateRawTextWithSource(prompt)
                val parsed = ExerciseEstimateParser.parse(response, desc, weight)

                _uiState.update {
                    it.copy(
                        isEstimating = false,
                        estimate = ExerciseEstimate(
                            parsed.activity, parsed.minutes, parsed.met, parsed.calories, parsed.suggestions
                        ),
                        aiSource = source
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isEstimating = false, error = "Couldn't estimate: ${e.message}") }
            }
        }
    }

    /** Workouts the user saved to their collection — offered as one-tap chips in the add screen. */
    val savedWorkouts: StateFlow<List<SavedWorkoutEntity>> = exerciseRepository.getSavedWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Load a saved workout into the estimate card so the user can nudge it and log it. */
    fun pickSaved(workout: SavedWorkoutEntity) {
        viewModelScope.launch {
            val weight = weightRepository.getLatest().first()?.weightKg ?: 70f
            val cal = ExerciseMet.calories(workout.met, weight, workout.minutes)
            _uiState.update {
                it.copy(
                    estimate = ExerciseEstimate(workout.name, workout.minutes, workout.met, cal, emptyList()),
                    aiSource = null, error = null, isEstimating = false
                )
            }
        }
    }

    /**
     * One-tap log path: build an estimate straight from the MET table — no AI, no network, instant.
     * The user can still nudge the duration and log it like an AI estimate.
     */
    fun quickPick(activity: QuickActivity) {
        viewModelScope.launch {
            val weight = weightRepository.getLatest().first()?.weightKg ?: 70f
            val cal = ExerciseMet.calories(activity.met, weight, activity.defaultMinutes)
            _uiState.update {
                it.copy(
                    estimate = ExerciseEstimate(activity.label, activity.defaultMinutes, activity.met, cal, emptyList()),
                    aiSource = null, error = null, isEstimating = false
                )
            }
        }
    }

    /** Let the user nudge the duration; calories recompute from the same MET. */
    fun setMinutes(minutes: Int) {
        val est = _uiState.value.estimate ?: return
        viewModelScope.launch {
            val weight = weightRepository.getLatest().first()?.weightKg ?: 70f
            val m = minutes.coerceIn(1, 1000)
            _uiState.update {
                it.copy(estimate = est.copy(minutes = m, calories = ExerciseMet.calories(est.met, weight, m)))
            }
        }
    }

    fun logExercise() {
        val est = _uiState.value.estimate ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val weight = weightRepository.getLatest().first()?.weightKg ?: 70f
                exerciseRepository.logExercise(
                    name = est.activity,
                    minutes = est.minutes,
                    met = est.met,
                    weightKg = weight,
                    date = targetDate,
                    aiSource = _uiState.value.aiSource?.name,
                    suggestions = est.suggestions
                )
                exerciseJobManager.clear()
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "Couldn't save: ${e.message}") }
            }
        }
    }
}
