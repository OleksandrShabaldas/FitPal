package com.fitpal.app.ui.screen.aireview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.repository.AiReviewRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.ModelManager
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class AiReviewUiState(
    val title: String = "AI Overview",
    val subtitle: String = "",
    val isLoading: Boolean = true,
    val review: String? = null,
    val modelReady: Boolean = true,
    val error: String? = null,
    /** Which engine produced this overview — for the online / on-device badge. */
    val aiSource: AiSource? = null,
    /** Live stage shown while generating (gathering data → sending → waiting → …). */
    val progress: String = ""
)

@HiltViewModel
class AiReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRepository,
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    private val aiReviewRepository: AiReviewRepository,
    private val pipeline: FoodAnalysisPipeline,
    private val modelManager: ModelManager,
    private val reviewGenerator: com.fitpal.app.ml.ReviewGenerator
) : ViewModel() {

    private val period: String = savedStateHandle.get<String>(Screen.AiReview.ARG_PERIOD) ?: "daily"
    private val periodKey: String = savedStateHandle.get<String>(Screen.AiReview.ARG_PERIOD_KEY) ?: ""

    private val isoFormat = DateTimeFormatter.ISO_LOCAL_DATE

    private val _uiState = MutableStateFlow(AiReviewUiState())
    val uiState: StateFlow<AiReviewUiState> = _uiState

    init {
        _uiState.update {
            it.copy(title = titleFor(period), subtitle = subtitleFor(period, periodKey))
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            // Already generated for this period? Show it instantly.
            val saved = aiReviewRepository.get(period, periodKey)
            if (saved != null) {
                _uiState.update { it.copy(isLoading = false, review = saved.text, aiSource = saved.source) }
                return@launch
            }
            if (!reviewGenerator.canGenerate()) {
                _uiState.update {
                    it.copy(isLoading = false, modelReady = false,
                        error = "Set up the AI model (or add an online AI key) to generate this overview.")
                }
                return@launch
            }
            generate()
        }
    }

    /** Allow the user to discard and regenerate. */
    fun regenerate() {
        if (!reviewGenerator.canGenerate()) return
        viewModelScope.launch {
            aiReviewRepository.delete(period, periodKey)
            _uiState.update { it.copy(isLoading = true, review = null, error = null) }
            generate()
        }
    }

    private suspend fun generate() {
        val text = reviewGenerator.generateAndCache(period, periodKey) { msg ->
            _uiState.update { it.copy(progress = msg) }
        }
        if (text == null) {
            _uiState.update { it.copy(isLoading = false, error = "Couldn't generate overview. Please try again.") }
            return
        }
        // Read back so the online/on-device badge reflects which engine produced it.
        val saved = aiReviewRepository.get(period, periodKey)
        _uiState.update { it.copy(isLoading = false, review = text, aiSource = saved?.source, progress = "") }
    }

    // ---------- Period helpers ----------

    private fun titleFor(period: String): String = when (period) {
        "weekly" -> "Weekly Overview"
        "monthly" -> "Monthly Overview"
        else -> "Daily Overview"
    }

    private fun subtitleFor(period: String, key: String): String = when (period) {
        "weekly" -> {
            val start = parseDateOr(key, LocalDate.now().minusDays(6))
            "${start.format(DateTimeFormatter.ofPattern("d MMM"))} – " +
                start.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
        }
        "monthly" -> {
            val ym = parseMonthOr(key, YearMonth.now())
            "${ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${ym.year}"
        }
        else -> {
            val d = parseDateOr(key, LocalDate.now())
            d.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
        }
    }

    private fun parseDateOr(s: String, fallback: LocalDate): LocalDate =
        try { LocalDate.parse(s) } catch (e: Exception) { fallback }

    private fun parseMonthOr(s: String, fallback: YearMonth): YearMonth =
        try { YearMonth.parse(s) } catch (e: Exception) { fallback }
}
