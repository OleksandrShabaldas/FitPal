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
import com.fitpal.app.domain.model.adherenceByDay
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
     * The days of the viewed period that have happened: the period clipped to today. Every card uses the
     * same period (one meaning of "week"); charts draw the whole period with the days still to come as
     * gaps, and averages and changes use only these days.
     */
    private fun elapsedOf(r: AnalyticsRange, a: LocalDate): Pair<LocalDate, LocalDate> {
        val (pStart, pEnd) = rangeOf(r, a)
        return pStart to (if (pEnd.isAfter(today)) today else pEnd)
    }

    /** The period just before the viewed one — what "vs last week" compares against. */
    private fun previousOf(r: AnalyticsRange, a: LocalDate): Pair<LocalDate, LocalDate> = rangeOf(r, prevAnchor(r, a))

    /** The previous period through the viewed one — what we actually fetch per-day data for. */
    private fun dataOf(r: AnalyticsRange, a: LocalDate): Pair<LocalDate, LocalDate> =
        previousOf(r, a).first to rangeOf(r, a).second

    private val periodBounds: StateFlow<Pair<LocalDate, LocalDate>> =
        combine(_range, _anchor) { r, a -> rangeOf(r, a) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, rangeOf(AnalyticsRange.WEEK, _anchor.value))

    private val elapsedBounds: StateFlow<Pair<LocalDate, LocalDate>> =
        combine(_range, _anchor) { r, a -> elapsedOf(r, a) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, elapsedOf(AnalyticsRange.WEEK, _anchor.value))

    private val previousBounds: StateFlow<Pair<LocalDate, LocalDate>> =
        combine(_range, _anchor) { r, a -> previousOf(r, a) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, previousOf(AnalyticsRange.WEEK, _anchor.value))

    /** What per-day queries fetch — previous + viewed period, so both can be sliced from one result. */
    private val dataBounds: StateFlow<Pair<LocalDate, LocalDate>> =
        combine(_range, _anchor) { r, a -> dataOf(r, a) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, dataOf(AnalyticsRange.WEEK, _anchor.value))

    val periodStart: StateFlow<LocalDate> = periodBounds
        .map { it.first }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), periodBounds.value.first)
    val periodEnd: StateFlow<LocalDate> = periodBounds
        .map { it.second }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), periodBounds.value.second)
    val elapsedStart: StateFlow<LocalDate> = elapsedBounds
        .map { it.first }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), elapsedBounds.value.first)
    val elapsedEnd: StateFlow<LocalDate> = elapsedBounds
        .map { it.second }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), elapsedBounds.value.second)
    val previousStart: StateFlow<LocalDate> = previousBounds
        .map { it.first }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), previousBounds.value.first)
    val previousEnd: StateFlow<LocalDate> = previousBounds
        .map { it.second }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), previousBounds.value.second)

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

    // ---- Micronutrient totals over the days of the period so far ----
    val microsTotals: StateFlow<DailyMicros?> = run {
        val flow = MutableStateFlow<DailyMicros?>(null)
        viewModelScope.launch {
            elapsedBounds.collectLatest { (start, end) ->
                mealRepository.getMicrosForRange(start.format(dateFormat), end.format(dateFormat)).collect { flow.value = it }
            }
        }
        flow
    }

    // ---- Fasting adherence (derived from when meals were logged; no extra storage) ----
    val fastingSchedule: StateFlow<com.fitpal.app.domain.model.FastingSchedule> = settingsRepository.fastingSchedule
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.fitpal.app.domain.model.FastingSchedule.DISABLED)

    /**
     * Meal (day, log-time) rows over the viewed window — colours the fasting heatmap. Drink-only logs
     * the user's drink rule allows are left out, so they never break a day (applied at read time, so
     * changing the rule re-judges past days too).
     */
    private val fastingMealTimes: StateFlow<List<com.fitpal.app.data.local.dao.MealTimeRow>> =
        rangeDriven { from, to -> mealRepository.getMealTimesInRange(from, to) }
            .let { rows -> combine(rows, settingsRepository.fastingSchedule) { r, s -> r.breakingOnly(s) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun List<com.fitpal.app.data.local.dao.MealTimeRow>.breakingOnly(
        schedule: com.fitpal.app.domain.model.FastingSchedule
    ) = filterNot { schedule.allowsDuringFast(it.fastingIntake) }

    /** Days the user marked "I ate earlier" (dateIso -> attested eating time) — count as kept. */
    val fastingGrace: StateFlow<Map<String, Int>> = settingsRepository.fastingGrace
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Per-day HELD/BROKE over the viewed window (empty when fasting is off). */
    val fastingAdherence: StateFlow<Map<String, com.fitpal.app.domain.model.FastingDayResult>> =
        combine(fastingMealTimes, settingsRepository.fastingSchedule, settingsRepository.fastingGrace) { times, schedule, grace ->
            schedule.adherenceByDay(
                times.map { it.date to minuteOfDay(it.timestamp) }, grace.keys, loggedDuringFastDays(times)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Per-day sorted minute-of-day of each non-water meal — lets the day dialog explain a broken fast. */
    val fastingMealMinutesByDate: StateFlow<Map<String, List<Int>>> =
        fastingMealTimes.map { rows ->
            rows.groupBy({ it.date }, { minuteOfDay(it.timestamp) }).mapValues { it.value.sorted() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Days in the viewed window where a meal was logged with "log anyway" during the fast. */
    val fastingLoggedDuringFastDays: StateFlow<Set<String>> =
        fastingMealTimes.map { loggedDuringFastDays(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private fun loggedDuringFastDays(rows: List<com.fitpal.app.data.local.dao.MealTimeRow>): Set<String> =
        rows.filter { it.loggedDuringFast }.map { it.date }.toSet()

    /** Consecutive days up to today the fast was kept — over a 90-day lookback, independent of the view. */
    val fastingStreak: StateFlow<Int> = run {
        val recent = MutableStateFlow<List<com.fitpal.app.data.local.dao.MealTimeRow>>(emptyList())
        viewModelScope.launch {
            mealRepository.getMealTimesInRange(today.minusDays(90).format(dateFormat), today.format(dateFormat))
                .collect { recent.value = it }
        }
        combine(recent, settingsRepository.fastingSchedule, settingsRepository.fastingGrace) { all, schedule, grace ->
            val times = all.breakingOnly(schedule)
            fastingStreakOf(
                schedule.adherenceByDay(
                    times.map { it.date to minuteOfDay(it.timestamp) }, grace.keys, loggedDuringFastDays(times)
                )
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    }

    private fun minuteOfDay(epochMillis: Long): Int =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault())
            .let { it.hour * 60 + it.minute }

    private fun fastingStreakOf(adherence: Map<String, com.fitpal.app.domain.model.FastingDayResult>): Int {
        var streak = 0
        var day = today
        // Today may not have any logged food yet — start from yesterday so it doesn't zero the streak.
        if (adherence[day.format(dateFormat)] == null) day = day.minusDays(1)
        while (adherence[day.format(dateFormat)] == com.fitpal.app.domain.model.FastingDayResult.HELD) {
            streak++
            day = day.minusDays(1)
        }
        return streak
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
    val widgetLayout: StateFlow<List<com.fitpal.app.domain.model.ResolvedCard>> =
        settingsRepository.widgetLayouts
            .map { com.fitpal.app.domain.model.ScreenWidgets.resolveCards(com.fitpal.app.domain.model.ScreenWidgets.ANALYTICS, it[com.fitpal.app.domain.model.ScreenWidgets.ANALYTICS].orEmpty()) }
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5000),
                com.fitpal.app.domain.model.ScreenWidgets.resolveCards(com.fitpal.app.domain.model.ScreenWidgets.ANALYTICS, emptyList())
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

    /** Weekly weight trend over the user's whole history — the basis for the maintenance estimate. */
    private val lifetimeWeightRate: StateFlow<Float?> = weightEntries
        .map { com.fitpal.app.domain.WeightTrend.lifetimeRatePerWeek(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * The days that count as "eaten" over the whole history: every logged day with real food
     * (calories > 0). Water-only days log as a 0-kcal row (see `MealRepository.logWater`), so they'd
     * otherwise pad the day count and drag the average intake DOWN — the same `consumed > 0` rule the
     * Calorie-balance detail uses, so the two screens agree. Also feeds the "Average intake" popup.
     */
    val intakeDays: StateFlow<List<DailyNutritionRow>> = lifetimeRows
        .map { rows -> rows.filter { it.calories > 0f } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Implied maintenance kcal from **lifetime** average intake vs. **lifetime** weight change
     * (display only — never changes the budget). Uses the whole history on purpose: over months the
     * day-to-day water/glycogen swings average out, so the 7700 kcal/kg conversion tracks real fat
     * change instead of amplifying noise — which is what made the old 30-day version read far too
     * high. Independent of the viewed range, so the card shows in Week and 30-day alike (once there
     * are enough logged days and weigh-ins).
     */
    val maintenanceBreakdown: StateFlow<com.fitpal.app.domain.WeightTrend.MaintenanceEstimate?> =
        combine(intakeDays, lifetimeWeightRate) { rows, rate ->
            val loggedDays = rows.size
            val avg = if (loggedDays > 0) rows.sumOf { it.calories.toDouble() }.toFloat() / loggedDays else 0f
            com.fitpal.app.domain.WeightTrend.impliedMaintenanceBreakdown(avg, rate, loggedDays)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The body's estimated resting burn (BMR × 1.2) — the base the daily goal and the theoretical
     *  total burn are both built on. Mirrors `CalorieDetailViewModel`'s resting burn exactly. */
    private val restingBurnPerDay: StateFlow<Float> = combine(
        settingsRepository.userProfile,
        weightRepository.getLatest()
    ) { profile, weight ->
        weight?.let { BmrCalculator.restingBurn(profile, it.weightKg) } ?: 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // Lifetime per-day step + exercise burn — the "active" slice on top of resting, so the popup can
    // rebuild the SAME total burn the Calorie-balance screen shows (resting + steps + workouts).
    private val lifetimeStepBurn: StateFlow<List<DailyStepRow>> =
        stepRepository.getDailySteps(LIFETIME_FROM, LIFETIME_TO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val lifetimeExerciseBurn: StateFlow<List<DailyBurnRow>> =
        exerciseRepository.getDailyBurnRange(LIFETIME_FROM, LIFETIME_TO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The theoretical total burn per eaten day — resting + step + workout calories, averaged over the
     * SAME eaten days as the intake average (step calories trimmed by the user's setting, exactly like
     * the Calorie-balance screen). This is the number that screen's "deficit" is measured against, so
     * the maintenance popup can show it beside the weight-based figure and explain the gap.
     */
    val theoreticalBurnPerDay: StateFlow<Float> = combine(
        intakeDays, lifetimeStepBurn, lifetimeExerciseBurn, restingBurnPerDay, stepTrimPercent
    ) { days, steps, ex, resting, trim ->
        if (days.isEmpty() || resting <= 0f) return@combine 0f
        val dates = days.mapTo(HashSet()) { it.date }
        val stepCal = steps.filter { it.date in dates }.sumOf { (it.caloriesBurned * (100 - trim) / 100f).toDouble() }
        val exCal = ex.filter { it.date in dates }.sumOf { it.burned.toDouble() }
        resting + ((stepCal + exCal) / days.size).toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    /** Just the final number (the card's headline + the "show maintenance" gate). */
    val lifetimeMaintenance: StateFlow<Int?> = maintenanceBreakdown
        .map { it?.maintenanceKcal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ---- Sections (docs/redesign/07): all open unless the user folded one away ----
    val collapsed: StateFlow<Set<String>> = settingsRepository.analyticsCollapsed

    fun toggleSection(id: String) {
        val now = collapsed.value
        settingsRepository.setAnalyticsCollapsed(if (id in now) now - id else now + id)
    }

    fun expandSection(id: String) {
        if (id in collapsed.value) settingsRepository.setAnalyticsCollapsed(collapsed.value - id)
    }

    // ---- Habits: caffeine and food limits per day (fetched over the union, like the rest) ----
    val caffeineSettings: StateFlow<com.fitpal.app.domain.CaffeineSettings> = settingsRepository.caffeineSettings

    /** Caffeine (mg) per logged day — the same per-drink amounts the Caffeine page uses. */
    val caffeineByDay: StateFlow<Map<String, Float>> = rangeDriven { from, to -> mealRepository.caffeineRowsInRange(from, to) }
        .let { rows ->
            rows.map { list ->
                list.mapNotNull { r -> com.fitpal.app.domain.Caffeine.dose(r.timestamp, r.name, r.grams, r.caffeineMg)?.let { r.date to it.mg } }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, mgs) -> mgs.sum() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** The food limits that are on. */
    val enabledLimits: StateFlow<List<com.fitpal.app.domain.model.DietaryRule>> = settingsRepository.dietaryRules
        .map { rules -> rules.filter { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.dietaryRules.value.filter { it.enabled })

    /** Kcal counted toward each food limit, per day (`DietaryTally`, like Home and the Limits page). */
    val limitKcalByDay: StateFlow<Map<com.fitpal.app.domain.model.DietaryRuleKind, Map<String, Float>>> = run {
        val kinds = com.fitpal.app.domain.model.DietaryRuleKind.entries
        val perKind = kinds.map { kind ->
            rangeDriven { from, to -> mealRepository.dietaryConsumedByDay(from, to, kind) }
        }
        combine(perKind) { lists -> kinds.zip(lists.toList()).associate { (k, rows) -> k to rows.associate { it.date to it.kcal } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    }

    /** Daily water goal (ml): the hand-set one, else ~35 ml/kg — the same rule as Home. */
    val waterGoal: StateFlow<Int> = combine(settingsRepository.waterGoalOverrideMl, weightRepository.getLatest()) { override, w ->
        if (override > 0) override else SettingsRepository.autoWaterGoal(w?.weightKg ?: 70f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2000)

    val stepGoal: StateFlow<Int> = settingsRepository.stepGoal

    private companion object {
        // Dates are stored as ISO text and compared as text, so these bracket every real date.
        const val LIFETIME_FROM = "0000-01-01"
        const val LIFETIME_TO = "9999-12-31"
    }
}
