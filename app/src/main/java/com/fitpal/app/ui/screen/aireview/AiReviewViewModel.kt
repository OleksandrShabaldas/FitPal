package com.fitpal.app.ui.screen.aireview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.repository.AiReviewRepository
import com.fitpal.app.data.repository.ContextNoteRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.model.ContextQuestion
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ml.ContextQuestionGenerator
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
    /** The green "do this next" coaching card, parsed from the review's FOCUS: line (null if absent). */
    val focus: String? = null,
    /** The red "keep an eye on this" card, parsed from the review's WATCH: line (null if absent). */
    val watch: String? = null,
    val modelReady: Boolean = true,
    val error: String? = null,
    /** Which engine produced this overview — for the online / on-device badge. */
    val aiSource: AiSource? = null,
    /** Live stage shown while generating (gathering data → sending → waiting → …). */
    val progress: String = "",
    /** A check-in question to ask before generating; when non-null, shown instead of the review. */
    val contextQuestion: ContextQuestion? = null
)

@HiltViewModel
class AiReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRepository,
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    private val aiReviewRepository: AiReviewRepository,
    private val contextNoteRepository: ContextNoteRepository,
    private val contextQuestionGenerator: ContextQuestionGenerator,
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
                val p = parseReview(saved.text)
                _uiState.update {
                    it.copy(isLoading = false, review = p.prose, focus = p.focus, watch = p.watch, aiSource = saved.source)
                }
                return@launch
            }
            if (!reviewGenerator.canGenerate()) {
                _uiState.update {
                    it.copy(isLoading = false, modelReady = false,
                        error = "Set up the AI model (or add an online AI key) to generate this overview.")
                }
                return@launch
            }
            // When the day/week looks unusual, ask one AI-generated check-in question first; its
            // answer feeds the review. Any failure just falls through to generating directly.
            val question = runCatching { contextQuestionGenerator.maybeGenerate(period, periodKey) }.getOrNull()
            if (question != null) {
                _uiState.update { it.copy(isLoading = false, contextQuestion = question) }
                return@launch
            }
            generate()
        }
    }

    /** User picked an answer chip (or typed one) — remember it, then generate with it in context. */
    fun answerQuestion(answer: String) {
        val q = _uiState.value.contextQuestion ?: return
        val clean = answer.trim().ifBlank { return }
        viewModelScope.launch {
            runCatching { contextNoteRepository.save(periodKey, period, q.question, clean) }
            _uiState.update { it.copy(contextQuestion = null, isLoading = true, progress = "") }
            generate()
        }
    }

    /** User dismissed the question — record the skip (so we don't re-ask) and generate anyway. */
    fun skipQuestion() {
        val q = _uiState.value.contextQuestion ?: return
        viewModelScope.launch {
            runCatching { contextNoteRepository.save(periodKey, period, q.question, "skipped") }
            _uiState.update { it.copy(contextQuestion = null, isLoading = true, progress = "") }
            generate()
        }
    }

    /** Allow the user to discard and regenerate. Skips the check-in question (already handled once). */
    fun regenerate() {
        if (!reviewGenerator.canGenerate()) return
        viewModelScope.launch {
            aiReviewRepository.delete(period, periodKey)
            _uiState.update { it.copy(isLoading = true, review = null, error = null, contextQuestion = null) }
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
        val p = parseReview(text)
        _uiState.update {
            it.copy(isLoading = false, review = p.prose, focus = p.focus, watch = p.watch, aiSource = saved?.source, progress = "")
        }
    }

    private data class ParsedReview(val prose: String, val focus: String?, val watch: String?)

    /**
     * Split the coach's two action cards off the prose. The model ends the review with `FOCUS:` and
     * `WATCH:` lines (see [com.fitpal.app.ml.FoodPrompts]); we lift those into the green/red cards and
     * keep them out of the body. Tolerant of markdown bold/bullets around the tags, and of older
     * reviews that have no such lines (both come back null → no cards).
     */
    private fun parseReview(text: String): ParsedReview {
        var focus: String? = null
        var watch: String? = null
        val prose = StringBuilder()
        text.lines().forEach { line ->
            val m = CARD_LINE.find(line)
            if (m == null) {
                prose.appendLine(line)
                return@forEach
            }
            val body = m.groupValues[2].trim().trim('*', '_', ' ').ifBlank { null }
            when (m.groupValues[1]) {
                "FOCUS" -> if (focus == null) focus = body
                "WATCH" -> if (watch == null) watch = body
            }
        }
        return ParsedReview(prose.toString().trim(), focus, watch)
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

    private companion object {
        /**
         * A `FOCUS:`/`WATCH:` card line, tolerating leading markdown (**, -, >, #). Uppercase-only on
         * purpose (the prompt always emits it that way) so a prose sentence like "Watch your portions"
         * can't be misread as a card.
         */
        private val CARD_LINE = Regex("^[\\s*_#>\\-]*(FOCUS|WATCH)\\s*:\\s*(.+)$")
    }
}
