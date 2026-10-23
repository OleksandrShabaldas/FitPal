package com.fitpal.app.ui.screen.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.PlannedMealEntity
import com.fitpal.app.data.repository.AiReviewRepository
import com.fitpal.app.data.repository.ContextNoteRepository
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.PastReview
import com.fitpal.app.data.repository.PlannerRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.ReviewPeriods
import com.fitpal.app.domain.model.FoodProfile
import com.fitpal.app.domain.model.PlanRequest
import com.fitpal.app.domain.model.PlanScope
import com.fitpal.app.ml.FoodAnalysisPipeline
import com.fitpal.app.ml.PlanJob
import com.fitpal.app.ml.PlannerJobManager
import com.fitpal.app.ml.ReviewText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/** The coach's question at the end of today's overview, and what the user said (if anything). */
data class CoachQuestion(val question: String, val answers: List<String>, val answered: String?)

/** One review row: which period, its key, its title, and whether there's one new since it was last opened. */
data class ReviewRow(val period: String, val key: String, val title: String, val subtitle: String, val ready: Boolean)

data class CoachUiState(
    val todayQuestion: CoachQuestion? = null,
    val todayReady: Boolean = false,
    val reviewRows: List<ReviewRow> = emptyList(),
    val earlier: List<PastReview> = emptyList()
)

/**
 * The Coach tab (docs/redesign/10): today's overview and the coach's question, the reviews, and the
 * meal planner — starting plans (they run in [PlannerJobManager]), what's coming up, saved ideas and
 * the food profile.
 */
@HiltViewModel
class CoachViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val reviews: AiReviewRepository,
    private val notes: ContextNoteRepository,
    private val planner: PlannerRepository,
    private val jobs: PlannerJobManager,
    private val meals: MealRepository,
    private val weights: WeightRepository,
    private val steps: StepRepository,
    private val exercise: ExerciseRepository,
    private val pipeline: FoodAnalysisPipeline
) : ViewModel() {

    private val _ui = MutableStateFlow(CoachUiState())
    val ui: StateFlow<CoachUiState> = _ui

    val plannerEnabled: StateFlow<Boolean> = settings.plannerEnabled
    val foodProfile: StateFlow<FoodProfile> = settings.foodProfile
    val job: StateFlow<PlanJob?> = jobs.state
    val upcoming: StateFlow<List<PlannedMealEntity>> = planner.upcoming()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val savedCount: StateFlow<Int> = planner.saved().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val lastOpenPlan: StateFlow<String?> = planner.latestOpenRequest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val todayKey: String get() = LocalDate.now().toString()

    /** Reload the reviews (on every visit — one may have been written since). */
    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val seen = settings.seenReviews.value
            fun readyFor(period: String, key: String, generatedAt: Long?) = generatedAt != null && "$period|$key|$generatedAt" !in seen
            val daily = reviews.get("daily", todayKey)
            val question = daily?.let { r ->
                val p = ReviewText.parse(r.text)
                p.question?.let { q ->
                    val answered = runCatching { notes.getFollowUp("daily", todayKey) }.getOrNull()?.takeIf { it.question == q }?.answer
                    CoachQuestion(q, p.answers, answered)
                }
            }
            val week = ReviewPeriods.week(today, today)
            val month = ReviewPeriods.month(today, today)
            val rows = listOf(
                ReviewRow("weekly", week.key, week.title, week.subtitle, readyFor("weekly", week.key, reviews.get("weekly", week.key)?.generatedAt)),
                ReviewRow("monthly", month.key, month.title, month.subtitle, readyFor("monthly", month.key, reviews.get("monthly", month.key)?.generatedAt))
            )
            _ui.update {
                it.copy(
                    todayQuestion = question,
                    todayReady = readyFor("daily", todayKey, daily?.generatedAt),
                    reviewRows = rows,
                    earlier = runCatching { reviews.recent(30) }.getOrDefault(emptyList())
                )
            }
        }
    }

    /** Opening a review clears its "ready" dot. */
    fun markOpened(period: String, key: String) {
        viewModelScope.launch {
            reviews.get(period, key)?.let { settings.markReviewSeen("$period|$key|${it.generatedAt}") }
        }
    }

    /** Tap-answer to the coach's question on today's overview — saved like on the review screen. */
    fun answer(answer: String) {
        val q = _ui.value.todayQuestion ?: return
        viewModelScope.launch {
            runCatching { notes.saveFollowUp("daily", todayKey, q.question, answer) }
            _ui.update { it.copy(todayQuestion = q.copy(answered = answer)) }
        }
    }

    // ---- The planner ----

    /** The last request, with [scope] picked from the button pressed. */
    fun requestFor(scope: PlanScope): PlanRequest {
        val last = PlanRequest.fromJson(settings.lastPlanRequest)
        val profile = settings.foodProfile.value
        return last.copy(
            scope = scope,
            date = if (scope == PlanScope.WEEK) LocalDate.now() else last.date,
            servings = if (settings.lastPlanRequest.isBlank()) profile.cookingFor else last.servings,
            budget = last.budget ?: profile.budget,
            budgetPer = if (last.budget == null) profile.budgetPer else last.budgetPer
        )
    }

    /** Start planning; returns the request id (the results screen follows it). */
    fun startPlan(request: PlanRequest): String {
        settings.lastPlanRequest = request.toJson()
        return jobs.start(request)
    }

    fun retryOnline() = jobs.chooseRetryOnline()
    fun useOnDevice() = jobs.chooseUseLocal()
    fun cancelPlan() = jobs.cancel()
    fun dismissJob() = jobs.clear()

    /** Whether online AI can run now — a week on the phone takes a while, and the form says so. */
    fun onlineAvailable(): Boolean = pipeline.canUseOnline()

    /** Recently logged ingredients, offered as "what you have" suggestions. */
    suspend fun recentIngredients(): List<String> = runCatching {
        meals.recentDistinctFoods(30).first().map { it.name.trim() }.filter { it.length in 2..30 }.distinct().take(12)
    }.getOrDefault(emptyList())

    /** What's left today (kcal, protein g), with the Home ring's maths — for "Fit what's left today". */
    suspend fun leftToday(): Pair<Int, Int>? {
        val kg = weights.getLatest().first()?.weightKg ?: return null
        val t = BmrCalculator.dailyTargets(settings.userProfile.value, kg, settings.dailyCalorieGoal.value, settings.macroSelection.value)
        val iso = LocalDate.now().toString()
        val activity = steps.getCaloriesBurned(iso).first() + exercise.getTotalBurnedForDate(iso).first()
        val eaten = meals.getDailyNutrition(iso).first()
        return ((t.calories + activity - eaten.calories).roundToInt().coerceAtLeast(0)) to
            ((t.proteinG - eaten.protein).roundToInt().coerceAtLeast(0))
    }
}
