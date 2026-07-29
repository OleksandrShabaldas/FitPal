package com.fitpal.app.ui.screen.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.dao.DailyBurnRow
import com.fitpal.app.data.local.dao.DailyMicros
import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.dao.DailyStepRow
import com.fitpal.app.data.local.dao.DailyWaterRow
import com.fitpal.app.data.local.dao.DailyWaterSplit
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.DailyTargets
import com.fitpal.app.domain.FocusedDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Which time range the user is looking at. */
enum class AnalyticsRange(val days: Int, val label: String) {
    WEEK(7, "Week"),
    MONTH(30, "30 days")
}

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val weightRepository: WeightRepository,
    private val stepRepository: StepRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val focusedDate: FocusedDate
) : ViewModel() {

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE
    private val today = LocalDate.now()

    // ---- Range picker ----
    private val _range = MutableStateFlow(AnalyticsRange.WEEK)
    val range: StateFlow<AnalyticsRange> = _range.asStateFlow()

    fun setRange(r: AnalyticsRange) { _range.value = r }

    /**
     * The day Analytics is centred on. It *follows* Home's shared [FocusedDate] (so browsing a past
     * day on Home opens that week/month here), but Analytics' own week/month paging stays local — it
     * doesn't drag Home's day around. Only an explicit "jump to this day" moves the shared cursor.
     */
    private val _anchor = MutableStateFlow(focusedDate.date.value)
    val anchor: StateFlow<LocalDate> = _anchor.asStateFlow()

    init {
        viewModelScope.launch { focusedDate.date.collect { _anchor.value = it } }
    }

    /**
     * The inclusive [start, end] the current view covers. WEEK snaps to the Mon–Sun calendar week
     * containing the anchor (so it "resets on Monday"); MONTH is a rolling 30-day window ending at
     * the anchor (so paging back gives the previous 30 days).
     */
    private fun rangeOf(r: AnalyticsRange, a: LocalDate): Pair<LocalDate, LocalDate> = when (r) {
        AnalyticsRange.WEEK -> {
            val start = a.minusDays((a.dayOfWeek.value - 1).toLong())
            start to start.plusDays(6)
        }
        AnalyticsRange.MONTH -> a.minusDays(29L) to a
    }

    private fun prevAnchor(r: AnalyticsRange, a: LocalDate): LocalDate =
        if (r == AnalyticsRange.WEEK) a.minusWeeks(1) else a.minusDays(30)

    private fun nextAnchor(r: AnalyticsRange, a: LocalDate): LocalDate =
        if (r == AnalyticsRange.WEEK) a.plusWeeks(1) else a.plusDays(30)

    /**
     * The "last N days" window most widgets use (calories, activity, water, …). In WEEK mode it ends
     * today (or the viewed week's Sunday if paged back) and spans 7 days; in MONTH mode it equals the
     * 30-day period. Only logged-days, calorie-balance and macro-balance use the calendar [rangeOf].
     */
    private fun rollingOf(r: AnalyticsRange, a: LocalDate): Pair<LocalDate, LocalDate> {
        val (_, pEnd) = rangeOf(r, a)
        val end = if (pEnd.isAfter(today)) today else pEnd
        return end.minusDays((r.days - 1).toLong()) to end
    }

    /** The union of the calendar period and the rolling window — what we actually fetch per-day data for. */
    private fun dataOf(r: AnalyticsRange, a: LocalDate): Pair<LocalDate, LocalDate> {
        val (pS, pE) = rangeOf(r, a)
        val (rS, rE) = rollingOf(r, a)
        return minOf(pS, rS) to maxOf(pE, rE)
    }

    private val periodBounds: StateFlow<Pair<LocalDate, LocalDate>> =
        combine(_range, _anchor) { r, a -> rangeOf(r, a) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, rangeOf(AnalyticsRange.WEEK, _anchor.value))

    private val rollingBounds: StateFlow<Pair<LocalDate, LocalDate>> =
        combine(_range, _anchor) { r, a -> rollingOf(r, a) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, rollingOf(AnalyticsRange.WEEK, _anchor.value))

    /** What per-day queries fetch — the union, so both date sets can be sliced from one result. */
    private val dataBounds: StateFlow<Pair<LocalDate, LocalDate>> =
        combine(_range, _anchor) { r, a -> dataOf(r, a) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, dataOf(AnalyticsRange.WEEK, _anchor.value))

    val periodStart: StateFlow<LocalDate> = periodBounds
        .map { it.first }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), periodBounds.value.first)
    val periodEnd: StateFlow<LocalDate> = periodBounds
        .map { it.second }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), periodBounds.value.second)
    val rollingStart: StateFlow<LocalDate> = rollingBounds
        .map { it.first }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), rollingBounds.value.first)
    val rollingEnd: StateFlow<LocalDate> = rollingBounds
        .map { it.second }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), rollingBounds.value.second)

    /** The earliest day with any logged meal — gates how far back paging can go. */
    val earliestLogged: StateFlow<LocalDate?> = mealRepository.getLoggedDatesDesc()
        .map { dates -> dates.lastOrNull()?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Can we page back? Only if the previous period still overlaps logged history. */
    val canGoPrev: StateFlow<Boolean> = combine(_range, _anchor, earliestLogged) { r, a, earliest ->
        earliest != null && !rangeOf(r, prevAnchor(r, a)).second.isBefore(earliest)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Can we page forward? Only while the next period doesn't start in the future. */
    val canGoNext: StateFlow<Boolean> = combine(_range, _anchor) { r, a ->
        !rangeOf(r, nextAnchor(r, a)).first.isAfter(today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun goPrev() {
        val r = _range.value
        val a = _anchor.value
        val earliest = earliestLogged.value ?: return
        val prev = prevAnchor(r, a)
        if (!rangeOf(r, prev).second.isBefore(earliest)) _anchor.value = prev
    }

    fun goNext() {
        val r = _range.value
        val a = _anchor.value
        val next = nextAnchor(r, a)
        if (!rangeOf(r, next).first.isAfter(today)) _anchor.value = next
    }

    /** Jump the shared cursor (Home + Analytics) to a specific day — used by tap-a-bar / tap-a-day. */
    fun focusDay(date: LocalDate) { focusedDate.set(date) }

    /** Build a StateFlow that re-queries whenever the viewed window changes (fetched over the union). */
    private fun <T> rangeDriven(query: (from: String, to: String) -> Flow<List<T>>): StateFlow<List<T>> {
        val out = MutableStateFlow<List<T>>(emptyList())
        viewModelScope.launch {
            dataBounds.collectLatest { (start, end) ->
                query(start.format(dateFormat), end.format(dateFormat)).collect { out.value = it }
            }
        }
        return out
    }

    // ---- Nutrition rows (fetched over the union; the screen slices per widget) ----
    val nutritionRows: StateFlow<List<DailyNutritionRow>> = run {
        val flow = MutableStateFlow<List<DailyNutritionRow>>(emptyList())
        viewModelScope.launch {
            dataBounds.collectLatest { (start, end) ->
                mealRepository.getDailyNutritionRange(start.format(dateFormat), end.format(dateFormat)).collect { flow.value = it }
            }
        }
        flow
    }

    // ---- Micronutrient totals (a "last N days" widget → rolling window) ----
    val microsTotals: StateFlow<DailyMicros?> = run {
        val flow = MutableStateFlow<DailyMicros?>(null)
        viewModelScope.launch {
            rollingBounds.collectLatest { (start, end) ->
                mealRepository.getMicrosForRange(start.format(dateFormat), end.format(dateFormat)).collect { flow.value = it }
            }
        }
        flow
    }

    // ---- "Lifetime" view: every logged day ever, for the weight / macro / micro cards ----
    // The window widgets normally use is the viewed week/month; these two flows ignore it entirely
    // so a card can show the whole history since the user's very first entry.

    val lifetimeRows: StateFlow<List<DailyNutritionRow>> =
        mealRepository.getDailyNutritionRange(LIFETIME_FROM, LIFETIME_TO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lifetimeMicros: StateFlow<DailyMicros?> =
        mealRepository.getMicrosForRange(LIFETIME_FROM, LIFETIME_TO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Which cards the user flipped to their lifetime view. Persisted, like the spotlight set. */
    val lifetime: StateFlow<Set<String>> = settingsRepository.analyticsLifetime

    fun toggleLifetime(key: String) {
        val next = lifetime.value.toMutableSet().apply { if (!add(key)) remove(key) }
        settingsRepository.setAnalyticsLifetime(next)
    }

    // ---- Long-press "spotlight" set: widgets the user pinned as highlighted ----
    // Persisted, so it survives leaving the tab AND a full app restart.
    val spotlight: StateFlow<Set<String>> = settingsRepository.analyticsSpotlight

    /** Toggle a widget's spotlight on/off. */
    fun toggleSpotlight(key: String) {
        val next = spotlight.value.toMutableSet().apply { if (!add(key)) remove(key) }
        settingsRepository.setAnalyticsSpotlight(next)
    }

    // ---- Switchable-card view choices (e.g. Macro balance "Average"/"Over time"), persisted ----
    val analyticsViews: StateFlow<Map<String, Int>> = settingsRepository.analyticsViews

    /** Remember which view a switchable card is showing. */
    fun setView(title: String, index: Int) = settingsRepository.setAnalyticsView(title, index)

    /** The user's chosen order / visibility for the Analytics cards. */
    val widgetLayout: StateFlow<List<com.fitpal.app.domain.model.WidgetSetting>> =
        settingsRepository.widgetLayouts
            .map { com.fitpal.app.domain.model.ScreenWidgets.resolve(it[com.fitpal.app.domain.model.ScreenWidgets.ANALYTICS].orEmpty(), com.fitpal.app.domain.model.ScreenWidgets.defaultsFor(com.fitpal.app.domain.model.ScreenWidgets.ANALYTICS)) }
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5000),
                com.fitpal.app.domain.model.ScreenWidgets.resolve(emptyList(), com.fitpal.app.domain.model.ScreenWidgets.defaultsFor(com.fitpal.app.domain.model.ScreenWidgets.ANALYTICS))
            )

    // ---- Water / Steps per day (range-driven) ----
    val waterRows: StateFlow<List<DailyWaterRow>> = rangeDriven(mealRepository::getDailyWaterRange)

    /** Per-day water split into clear drinks vs. water from food. */
    val waterSplitRows: StateFlow<List<DailyWaterSplit>> = rangeDriven(mealRepository::getDailyWaterSplitRange)
    val stepRows: StateFlow<List<DailyStepRow>> = rangeDriven(stepRepository::getDailySteps)

    /** Exercise calories burned per day (combines with step calories for total burned). */
    val exerciseBurnRows: StateFlow<List<DailyBurnRow>> = rangeDriven(exerciseRepository::getDailyBurnRange)

    /** How much to trim step calories (for the calories-burned chart). */
    val stepTrimPercent: StateFlow<Int> = settingsRepository.stepCalorieReductionPercent

    // ---- Weight entries ----
    val weightEntries: StateFlow<List<WeightEntryEntity>> = weightRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Daily targets ----
    val dailyTargets: StateFlow<DailyTargets?> = combine(
        settingsRepository.userProfile,
        settingsRepository.dailyCalorieGoal,
        weightRepository.getLatest(),
        settingsRepository.macroSelection
    ) { profile, manualGoal, weight, macros ->
        val kg = weight?.weightKg ?: return@combine null
        BmrCalculator.dailyTargets(profile, kg, manualGoal, macros)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Latest weight entry. */
    val latestWeight: StateFlow<WeightEntryEntity?> = weightRepository
        .getLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The user's fitness goal — used to frame the weight rate ("on track to lose fat"). */
    val fitnessGoal: StateFlow<com.fitpal.app.domain.model.FitnessGoal> =
        settingsRepository.userProfile
            .map { it.fitnessGoal }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.fitpal.app.domain.model.FitnessGoal.MAINTAIN)

    /** Smoothed weight rate of change (kg/week) over the viewed period, or null if too few points. */
    val weightRatePerWeek: StateFlow<Float?> = combine(weightEntries, periodBounds) { weights, (start, end) ->
        com.fitpal.app.domain.WeightTrend.ratePerWeek(weights, start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Implied maintenance kcal from avg logged intake vs. measured weight change (display only). */
    val impliedMaintenance: StateFlow<Int?> = combine(nutritionRows, weightRatePerWeek) { rows, rate ->
        val loggedDays = rows.size
        val avg = if (loggedDays > 0) rows.sumOf { it.calories.toDouble() }.toFloat() / loggedDays else 0f
        com.fitpal.app.domain.WeightTrend.impliedMaintenance(avg, rate, loggedDays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun logWeight(kg: Float) {
        viewModelScope.launch { weightRepository.logWeight(kg) }
    }

    private companion object {
        // Dates are stored as ISO text and compared as text, so these bracket every real date.
        const val LIFETIME_FROM = "0000-01-01"
        const val LIFETIME_TO = "9999-12-31"
    }
}
