package com.fitpal.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.dao.DailyMicros
import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.dao.MealLogItemWithType
import com.fitpal.app.data.local.entity.ExerciseEntryEntity
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.data.repository.ExerciseRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.DailyTargets
import com.fitpal.app.domain.FocusedDate
import com.fitpal.app.domain.HomeCoachTip
import com.fitpal.app.domain.MealLogContext
import com.fitpal.app.domain.Streaks
import com.fitpal.app.domain.model.FitnessGoal
import com.fitpal.app.domain.model.MealTypes
import com.fitpal.app.domain.model.NutritionInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Today's items for one meal category. */
data class MealSection(
    val type: String,
    val label: String,
    val items: List<MealLogItemEntity>,
    val calories: Float
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val weightRepository: WeightRepository,
    private val stepRepository: StepRepository,
    private val mealLogContext: MealLogContext,
    private val settingsRepository: SettingsRepository,
    private val exerciseRepository: ExerciseRepository,
    private val focusedDate: FocusedDate
) : ViewModel() {

    private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * The date currently being viewed — backed by the app-wide [FocusedDate] so Home and Analytics
     * stay on the same week/month. Defaults to today.
     */
    private val _selectedDate = focusedDate.date
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val selectedDateString = _selectedDate.map { it.format(dateFormat) }

    fun selectDate(date: LocalDate) { _selectedDate.value = date }
    fun goToday() { _selectedDate.value = LocalDate.now() }
    fun goPrevDay() { _selectedDate.value = _selectedDate.value.minusDays(1) }
    fun goNextDay() {
        val next = _selectedDate.value.plusDays(1)
        if (!next.isAfter(LocalDate.now())) _selectedDate.value = next
    }

    /** Nutrition for the selected date. */
    val dailyNutrition: StateFlow<NutritionInfo> = selectedDateString
        .flatMapLatest { mealRepository.getDailyNutrition(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NutritionInfo())

    /** Meal sections for the selected date. */
    val mealSections: StateFlow<List<MealSection>> = selectedDateString
        .flatMapLatest { mealRepository.getItemsWithTypeForDate(it) }
        .map { rows -> buildSections(rows) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Water for the selected date. */
    val dailyWater: StateFlow<Float> = selectedDateString
        .flatMapLatest { mealRepository.getTotalWaterForDate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    /** Micros for the selected date. */
    val dailyMicros: StateFlow<DailyMicros> = selectedDateString
        .flatMapLatest { mealRepository.getDailyMicros(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DailyMicros(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

    /** Steps for the selected date (manual + synced combined). */
    val dailySteps: StateFlow<Int> = selectedDateString
        .flatMapLatest { stepRepository.getTotalSteps(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Steps entered by hand for the selected date (may be negative — a trim). */
    val manualSteps: StateFlow<Int> = selectedDateString
        .flatMapLatest { stepRepository.getManualSteps(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Steps synced from Samsung Health for the selected date. */
    val syncedSteps: StateFlow<Int> = selectedDateString
        .flatMapLatest { stepRepository.getSyncedSteps(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Calories burned from steps for the selected date. */
    val stepCalories: StateFlow<Float> = selectedDateString
        .flatMapLatest { stepRepository.getCaloriesBurned(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    /** Exercise calories burned for the selected date. */
    val exerciseCalories: StateFlow<Float> = selectedDateString
        .flatMapLatest { exerciseRepository.getTotalBurnedForDate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    /** Logged exercise entries for the selected date. */
    val exerciseEntries: StateFlow<List<ExerciseEntryEntity>> = selectedDateString
        .flatMapLatest { exerciseRepository.getForDate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteExercise(id: Long) {
        viewModelScope.launch { exerciseRepository.delete(id) }
    }

    /** Latest weight entry. */
    val latestWeight: StateFlow<WeightEntryEntity?> = weightRepository
        .getLatest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Current logging streak — consecutive days with food logged. */
    val dailyStreak: StateFlow<Int> = mealRepository.getLoggedDatesDesc()
        .map { dates ->
            val set = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
            Streaks.current(set)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Editable quick-add water amounts (ml) for the Water widget. */
    val waterPresets: StateFlow<List<Int>> = settingsRepository.waterPresets

    /** Daily hydration target (ml): the manual override if set, else ~35 ml/kg of body weight. */
    val waterGoal: StateFlow<Int> = combine(
        settingsRepository.waterGoalOverrideMl,
        weightRepository.getLatest()
    ) { override, weight ->
        if (override > 0) override else SettingsRepository.autoWaterGoal(weight?.weightKg ?: 70f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2000)

    /** When true, empty meal panels collapse to a slim add-row on Home (Settings). */
    val compactEmptyMeals: StateFlow<Boolean> = settingsRepository.compactEmptyMeals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** The user's chosen order / visibility for the Home cards. */
    val widgetLayout: StateFlow<List<com.fitpal.app.domain.model.WidgetSetting>> =
        settingsRepository.widgetLayouts
            .map { com.fitpal.app.domain.model.ScreenWidgets.resolve(it[com.fitpal.app.domain.model.ScreenWidgets.HOME].orEmpty(), com.fitpal.app.domain.model.ScreenWidgets.defaultsFor(com.fitpal.app.domain.model.ScreenWidgets.HOME)) }
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5000),
                com.fitpal.app.domain.model.ScreenWidgets.resolve(emptyList(), com.fitpal.app.domain.model.ScreenWidgets.defaultsFor(com.fitpal.app.domain.model.ScreenWidgets.HOME))
            )

    /**
     * Daily targets from BMR + goal. Uses the manual calorie goal if set (>0),
     * otherwise computes from the user profile + latest weight.
     */
    val dailyTargets: StateFlow<DailyTargets?> = combine(
        settingsRepository.userProfile,
        settingsRepository.dailyCalorieGoal,
        weightRepository.getLatest(),
        settingsRepository.macroSelection
    ) { profile, manualGoal, weight, macros ->
        val kg = weight?.weightKg ?: return@combine null
        BmrCalculator.dailyTargets(profile, kg, manualGoal, macros)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The user's fitness goal — drives the goal-aware coaching line on Home. */
    val fitnessGoal: StateFlow<FitnessGoal> = settingsRepository.userProfile
        .map { it.fitnessGoal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FitnessGoal.RECOMP)

    /**
     * Running calorie balance over the last week, **today excluded**: net kcal vs the daily eating
     * target across recent fully-logged days. Positive = ran a surplus to catch up on, negative =
     * banked room. Null until there's enough trustworthy history. Anchored to the real today (not
     * the viewed day) since the coaching line only shows for today. See [HomeCoachTip.weeklyBalance].
     */
    val weeklyBalanceKcal: StateFlow<Int?> = dailyTargets.flatMapLatest { targets ->
        val target = targets?.calories ?: return@flatMapLatest flowOf(null)
        val today = LocalDate.now()
        val from = today.minusDays(7).format(dateFormat)
        val to = today.minusDays(1).format(dateFormat)
        mealRepository.getDailyNutritionRange(from, to).map { rows ->
            HomeCoachTip.weeklyBalance(rows.map { it.calories }, target)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** ISO date of the day currently being viewed — so logs land on that day, not today. */
    private fun selectedDateIso(): String = _selectedDate.value.format(dateFormat)

    /** Quick-add plain water to the viewed day. */
    fun addWater(ml: Float) {
        if (ml <= 0f) return
        viewModelScope.launch { mealRepository.logWater(ml, date = selectedDateIso()) }
    }

    /** Log a weight measurement. */
    fun logWeight(kg: Float) {
        viewModelScope.launch { weightRepository.logWeight(kg) }
    }

    /** SET the hand-entered steps for the viewed day (replaces, doesn't add; may be negative). */
    fun setManualSteps(steps: Int) {
        viewModelScope.launch {
            val kg = latestWeight.value?.weightKg ?: 70f
            stepRepository.setManualStepsForDate(selectedDateIso(), steps, kg)
        }
    }

    /** SET the synced (Samsung Health) steps for the viewed day — a manual correction. */
    fun setSyncedSteps(steps: Int) {
        viewModelScope.launch {
            val kg = latestWeight.value?.weightKg ?: 70f
            stepRepository.setSyncedStepsForDate(selectedDateIso(), steps, kg)
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { mealRepository.deleteItem(id) }
    }

    fun updateItemGrams(item: MealLogItemEntity, newGrams: Float) {
        viewModelScope.launch { mealRepository.updateItemGrams(item, newGrams) }
    }

    /** Change a logged entry's meal category (Breakfast/Lunch/Dinner/Snack). */
    fun setItemMealType(item: MealLogItemEntity, type: String) {
        viewModelScope.launch { mealRepository.setItemMealType(item, type) }
    }

    /** Duplicate a logged entry onto another day ([copies] times), into the chosen meal category. */
    fun copyItemToDate(item: MealLogItemEntity, date: java.time.LocalDate, mealType: String? = null, copies: Int = 1) {
        viewModelScope.launch {
            repeat(copies.coerceIn(1, 20)) {
                mealRepository.copyItemToDate(item, date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE), mealType)
            }
        }
    }

    fun startAddTo(type: String) {
        mealLogContext.pendingMealType = type
        // Remember which day we're viewing so the new meal lands on it, not today.
        mealLogContext.pendingDate = selectedDateIso()
    }

    /** Remember the viewed day so a directly-opened logger (e.g. exercise) lands on it. */
    fun prepareLogToViewedDate() {
        mealLogContext.pendingDate = selectedDateIso()
    }
    fun clearPendingMealType() { mealLogContext.clear() }

    /** Whether Samsung Health / Health Connect is available on this device. */
    val healthConnectAvailable: Boolean get() = stepRepository.healthConnectAvailable()

    /** Per-app step counts in Health Connect for the viewed day (so the user can see sources). */
    private val _stepSources = MutableStateFlow<Map<String, Int>>(emptyMap())
    val stepSources: StateFlow<Map<String, Int>> = _stepSources.asStateFlow()

    fun loadStepSources() {
        viewModelScope.launch {
            runCatching { _stepSources.value = stepRepository.stepSourcesForDate(selectedDateIso()) }
        }
    }

    /** Pull fresh steps from Health Connect (no-op if not connected). */
    fun syncHealthConnect() {
        viewModelScope.launch {
            val kg = latestWeight.value?.weightKg ?: 70f
            runCatching { stepRepository.syncFromHealthConnect(kg) }
            loadStepSources()
        }
    }

    // ---- Calendar calorie data for a given month ----

    /** Map of "YYYY-MM-DD" -> calories for the calendar overlay. */
    private val _calendarCalories = MutableStateFlow<Map<String, Float>>(emptyMap())
    val calendarCalories: StateFlow<Map<String, Float>> = _calendarCalories.asStateFlow()

    fun loadCalendarMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            val from = yearMonth.atDay(1).format(dateFormat)
            val to = yearMonth.atEndOfMonth().format(dateFormat)
            val rows = mealRepository.getDailyNutritionRange(from, to).first()
            _calendarCalories.value = rows.associate { it.date to it.calories }
        }
    }

    private fun buildSections(rows: List<MealLogItemWithType>): List<MealSection> {
        val byType = rows.groupBy { it.mealType }
        return MealTypes.ALL.map { type ->
            val items = byType[type]?.map { it.item } ?: emptyList()
            MealSection(
                type = type,
                label = MealTypes.label(type),
                items = items,
                calories = items.sumOf { it.calories.toDouble() }.toFloat()
            )
        }
    }
}
