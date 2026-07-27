package com.fitpal.app.ui.screen.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.ExerciseEntryEntity
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseDetailUiState(
    val entry: ExerciseEntryEntity? = null,
    val suggestions: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val deleted: Boolean = false,
    /** True once this workout has been saved to the collection (for the bookmark state). */
    val savedToCollection: Boolean = false,
    /** One-shot confirmation after "copy to another date" — shown as a toast, then cleared. */
    val copyConfirmation: String? = null
) {
    /** Intensity bucket from the MET value. */
    val intensity: String get() = when {
        (entry?.met ?: 0f) < 3f -> "Light"
        (entry?.met ?: 0f) < 6f -> "Moderate"
        else -> "Vigorous"
    }
}

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val id: Long = savedStateHandle.get<Long>(Screen.ExerciseDetail.ARG_ID) ?: 0L

    private val _uiState = MutableStateFlow(ExerciseDetailUiState())
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState

    init {
        viewModelScope.launch {
            val entry = exerciseRepository.getById(id)
            _uiState.update {
                it.copy(
                    entry = entry,
                    suggestions = entry?.let { e -> exerciseRepository.suggestionsFor(e) } ?: emptyList(),
                    isLoading = false
                )
            }
        }
    }

    /** Edit the logged duration; calories recompute and the screen refreshes. */
    fun setMinutes(newMinutes: Int) {
        val entry = _uiState.value.entry ?: return
        val m = newMinutes.coerceIn(1, 1000)
        if (m == entry.minutes) return
        viewModelScope.launch {
            val updated = exerciseRepository.updateMinutes(entry, m)
            _uiState.update { it.copy(entry = updated) }
        }
    }

    /** Copy this logged workout onto another day ([copies] times), keeping its calories/duration. */
    fun copyToDate(date: java.time.LocalDate, copies: Int = 1) {
        val entry = _uiState.value.entry ?: return
        val n = copies.coerceIn(1, 20)
        viewModelScope.launch {
            try {
                repeat(n) {
                    exerciseRepository.copyToDate(entry, date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE))
                }
                val label = com.fitpal.app.ui.component.logDateLabel(date).lowercase()
                _uiState.update {
                    it.copy(copyConfirmation = if (n == 1) "Copied to $label" else "$n copies to $label")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(copyConfirmation = "Couldn't copy: ${e.message}") }
            }
        }
    }

    fun clearCopyConfirmation() {
        _uiState.update { it.copy(copyConfirmation = null) }
    }

    /** Save this workout to the collection as a template for one-tap re-logging. */
    fun saveToCollection() {
        val entry = _uiState.value.entry ?: return
        viewModelScope.launch {
            exerciseRepository.saveWorkout(entry.name, entry.met, entry.minutes)
            _uiState.update { it.copy(savedToCollection = true) }
        }
    }

    fun delete() {
        viewModelScope.launch {
            exerciseRepository.delete(id)
            _uiState.update { it.copy(deleted = true) }
        }
    }
}
