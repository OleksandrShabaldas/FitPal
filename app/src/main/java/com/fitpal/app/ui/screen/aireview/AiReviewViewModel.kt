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
import com.fitpal.app.ml.ReviewText
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
    val contextQuestion: ContextQuestion? = null,
    /** The coach's question at the end of the overview (QUESTION: line), and its tap-answers. */
    val followUpQuestion: String? = null,
    val followUpOptions: List<String> = emptyList(),
    /** What the user answered to [followUpQuestion] (null = not answered yet). */
    val followUpAnswer: String? = null,
    /**
     * Set when this overview may be out of date — food was logged (or changed) after it was written,
     * so its numbers, and any "target missed" it mentions, may no longer hold. The screen shows it
     * with a refresh. Null = up to date (or nothing to say).
     */
    val staleNote: String? = null
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
                showReview(saved.text, saved.source)
                _uiState.update { it.copy(staleNote = staleNoteFor(saved)) }
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
            _uiState.update {
                it.copy(isLoading = true, review = null, error = null, contextQuestion = null, staleNote = null)
            }
            generate()
        }
    }

    /**
     * The user answered the coach's end-of-overview question. Saved against this overview, so the
     * next overview reads it ("you asked…, they said…") and builds on it.
     */
    fun answerFollowUp(answer: String) {
        val question = _uiState.value.followUpQuestion ?: return
        val clean = answer.trim().take(MAX_ANSWER_CHARS).ifBlank { return }
        _uiState.update { it.copy(followUpAnswer = clean) }
        viewModelScope.launch {
            runCatching { contextNoteRepository.saveFollowUp(period, periodKey, question, clean) }
        }
    }

    /** Re-open the answer box to change what was said (the newest answer is the one that counts). */
    fun editFollowUp() {
        _uiState.update { it.copy(followUpAnswer = null) }
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
        showReview(text, saved?.source)
        _uiState.update { it.copy(progress = "") }
    }

    /**
     * Is a saved overview out of date? Written before more food was logged, it can't know about the
     * 10 PM snack — and may call a target "missed" that's since been met. Overviews saved with the
     * totals they were written from are compared exactly; older ones only know when they were
     * written, so they're flagged if that was before their day/week/month was over.
     */
    private suspend fun staleNoteFor(saved: com.fitpal.app.data.repository.SavedReview): String? {
        if (saved.dataSignature != null) {
            val current = runCatching { reviewGenerator.currentSignature(period, periodKey) }.getOrNull()
            return if (current != null && current != saved.dataSignature) "You've logged more since this was written" else null
        }
        val periodEnd = when (period) {
            "weekly" -> parseDateOr(periodKey, LocalDate.now()).plusDays(6)
            "monthly" -> parseMonthOr(periodKey, YearMonth.now()).atEndOfMonth()
            else -> parseDateOr(periodKey, LocalDate.now())
        }
        val endMillis = periodEnd.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val noun = when (period) { "weekly" -> "week"; "monthly" -> "month"; else -> "day" }
        return if (saved.generatedAt in 1 until endMillis) "Written before this $noun was over" else null
    }

    /**
     * Show a review: the prose, the coach's two action cards and its closing question — all lifted
     * off the tagged lines the model ends with (see [ReviewText]). Older reviews without those lines
     * just show prose. A question already answered shows the answer instead of the answer box.
     */
    private suspend fun showReview(text: String, source: AiSource?) {
        val p = ReviewText.parse(text)
        val answered = p.question?.let { q ->
            runCatching { contextNoteRepository.getFollowUp(period, periodKey) }.getOrNull()
                ?.takeIf { it.question == q }?.answer
        }
        _uiState.update {
            it.copy(
                isLoading = false, review = p.prose, focus = p.focus, watch = p.watch, aiSource = source,
                followUpQuestion = p.question, followUpOptions = p.answers, followUpAnswer = answered
            )
        }
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
        const val MAX_ANSWER_CHARS = 300
    }
}
