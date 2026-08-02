package com.fitpal.app.ui.screen.caloriedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.dao.DailyBurnRow
import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.dao.DailyStepRow
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.FocusedDate
import com.fitpal.app.ui.navigation.Screen
import com.fitpal.app.ui.screen.analytics.AnalyticsRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * One day's energy in and out.
 *
 * [restingBurn] is what the body spends just being alive (BMR + everyday movement) — by far
 * the biggest slice of the day, and the reason "burned = exercise only" always looked wrong.
 * [activeBurn] is the movement logged on top of it. [consumed] is 0 on days with nothing logged.
 */
data class DayEnergy(
    val date: LocalDate,
    val consumed: Float,
    val restingBurn: Float,
    val stepBurn: Float,
    val exerciseBurn: Float
) {
    /** Steps + workouts — the part the daily goal does *not* already account for. */
    val activeBurn: Float get() = stepBurn + exerciseBurn
    /** Everything the body spent today. */
    val burned: Float get() = restingBurn + activeBurn
    /** Days with no food logged don't count toward the balance (same rule as the Analytics card). */
    val logged: Boolean get() = consumed > 0f
}

/** Everything the detail screen shows for the chosen window. */
data class EnergySummary(
    val days: List<DayEnergy> = emptyList(),
    val goal: Int = 0,
    val periodLabel: String = ""
) {
    val loggedDays: List<DayEnergy> get() = days.filter { it.logged }

    // All totals count logged days only, so "in" and "out" are always measuring the same
    // days. Counting a resting burn for days with no food logged would make every gap in
    // the log look like a huge deficit.
    val consumed: Float get() = loggedDays.sumOf { it.consumed.toDouble() }.toFloat()
    val restingBurn: Float get() = loggedDays.sumOf { it.restingBurn.toDouble() }.toFloat()
    val stepBurn: Float get() = loggedDays.sumOf { it.stepBurn.toDouble() }.toFloat()
    val exerciseBurn: Float get() = loggedDays.sumOf { it.exerciseBurn.toDouble() }.toFloat()
    val activeBurn: Float get() = stepBurn + exerciseBurn
    val burned: Float get() = restingBurn + activeBurn

    /**
     * Net vs the goal, summed over logged days only — deliberately the same arithmetic as the
     * Analytics "Calorie balance" card so the two screens can never disagree.
     *
     * Resting burn is **not** subtracted here on purpose: the daily goal is already built from
     * it (see [com.fitpal.app.domain.BmrCalculator.restingBurn]), so taking it off again would
     * count the same 1,800-odd calories twice.
     */
    val net: Int get() = loggedDays.sumOf { (it.consumed - goal - it.activeBurn).toDouble() }.toInt()

    /**
     * The other honest number: what actually went in against what the body actually spent,
     * over the logged days. Answers "am I in a deficit?" rather than "did I hit my plan?".
     */
    val balance: Int get() = loggedDays.sumOf { (it.consumed - it.burned).toDouble() }.toInt()
}

@HiltViewModel
class CalorieDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRepository,
    private val stepRepository: StepRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    private val focusedDate: FocusedDate
) : ViewModel() {

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM")

    /** The day the period is built around — passed in so we open on exactly what Analytics showed. */
    private val anchor: LocalDate = savedStateHandle.get<String>(Screen.CalorieDetail.ARG_ANCHOR)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: LocalDate.now()

    private val _range = MutableStateFlow(
        savedStateHandle.get<String>(Screen.CalorieDetail.ARG_RANGE)
            ?.let { r -> runCatching { AnalyticsRange.valueOf(r) }.getOrNull() }
            ?: AnalyticsRange.WEEK
    )
    val range: StateFlow<AnalyticsRange> = _range.asStateFlow()

    fun setRange(r: AnalyticsRange) { _range.value = r }

    /** Same windows as Analytics: WEEK = the Mon–Sun week around the anchor, MONTH = 30 days to it. */
    private fun rangeOf(r: AnalyticsRange, a: LocalDate): Pair<LocalDate, LocalDate> = when (r) {
        AnalyticsRange.WEEK -> {
            val start = a.minusDays((a.dayOfWeek.value - 1).toLong())
            start to start.plusDays(6)
        }
        AnalyticsRange.MONTH -> a.minusDays(29L) to a
    }

    private val bounds: StateFlow<Pair<LocalDate, LocalDate>> = _range
        .map { rangeOf(it, anchor) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, rangeOf(_range.value, anchor))

    /** Re-query whenever the window changes. */
    private fun <T> boundsDriven(query: (from: String, to: String) -> Flow<List<T>>): StateFlow<List<T>> {
        val out = MutableStateFlow<List<T>>(emptyList())
        viewModelScope.launch {
            bounds.collectLatest { (start, end) ->
                query(start.format(dateFormat), end.format(dateFormat)).collect { out.value = it }
            }
        }
        return out
    }

    private val nutritionRows: StateFlow<List<DailyNutritionRow>> =
        boundsDriven(mealRepository::getDailyNutritionRange)
    private val stepRows: StateFlow<List<DailyStepRow>> = boundsDriven(stepRepository::getDailySteps)
    private val burnRows: StateFlow<List<DailyBurnRow>> = boundsDriven(exerciseRepository::getDailyBurnRange)

    /** Merged so the summary stays within combine()'s 5-flow limit. */
    private val burnSources: StateFlow<Pair<List<DailyStepRow>, List<DailyBurnRow>>> =
        combine(stepRows, burnRows) { steps, ex -> steps to ex }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<DailyStepRow>() to emptyList())

    /** The daily target the balance is measured against, plus the resting burn behind it. */
    private data class Baseline(val goal: Int, val resting: Float)

    /**
     * Both come from the same profile + weight, so they're derived together — the goal is
     * literally built on top of the resting burn, and they must never be computed from
     * different inputs. The latest weight is used for every day in the window, the same
     * approximation the goal itself makes.
     */
    private val baseline: StateFlow<Baseline> = combine(
        settingsRepository.userProfile,
        settingsRepository.dailyCalorieGoal,
        weightRepository.getLatest(),
        settingsRepository.macroSelection
    ) { profile, manualGoal, weight, macros ->
        val kg = weight?.weightKg ?: return@combine Baseline(0, 0f)
        Baseline(
            goal = BmrCalculator.dailyTargets(profile, kg, manualGoal, macros).calories,
            resting = BmrCalculator.restingBurn(profile, kg)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Baseline(0, 0f))

    val summary: StateFlow<EnergySummary> = combine(
        bounds,
        nutritionRows,
        burnSources,
        settingsRepository.stepCalorieReductionPercent,
        baseline
    ) { window, rows, burns, trim, base ->
        val (start, end) = window
        val (steps, exercise) = burns
        val calByDate = rows.associate { it.date to it.calories }
        // Step calories get the user's trim applied — same figure the Analytics charts use.
        val stepByDate = steps.associate { it.date to it.caloriesBurned * (100 - trim) / 100f }
        val exByDate = exercise.associate { it.date to it.burned }
        val days = generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { d ->
                val key = d.format(dateFormat)
                DayEnergy(
                    date = d,
                    consumed = calByDate[key] ?: 0f,
                    restingBurn = base.resting,
                    stepBurn = stepByDate[key] ?: 0f,
                    exerciseBurn = exByDate[key] ?: 0f
                )
            }
            .toList()
        EnergySummary(
            days = days,
            goal = base.goal,
            periodLabel = "${start.format(dayMonth)} – ${end.format(dayMonth)}"
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EnergySummary())

    /** Move the shared Home/Analytics cursor to a day, so tapping a row can open it. */
    fun focusDay(date: LocalDate) { focusedDate.set(date) }
}
