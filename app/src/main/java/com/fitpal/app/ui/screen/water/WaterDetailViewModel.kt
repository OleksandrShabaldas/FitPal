package com.fitpal.app.ui.screen.water

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.dao.WaterTimelineRow
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** The Water page for one day (docs/redesign/02-water-page.md). */
@HiltViewModel
class WaterDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRepository,
    private val settingsRepository: SettingsRepository,
    weightRepository: WeightRepository
) : ViewModel() {

    private val dateIso: String = savedStateHandle.get<String>(Screen.WaterDetail.ARG_DATE)
        ?: LocalDate.now().toString()

    /** The day this page shows. */
    val date: LocalDate = runCatching { LocalDate.parse(dateIso) }.getOrDefault(LocalDate.now())
    val isToday: Boolean get() = date == LocalDate.now()

    /** The ~35 ml/kg goal the app would use without an override. */
    val autoGoal: StateFlow<Int> = weightRepository.getLatest()
        .map { SettingsRepository.autoWaterGoal(it?.weightKg ?: 70f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsRepository.autoWaterGoal(70f))

    /** The goal set by hand, 0 = automatic. */
    val goalOverride: StateFlow<Int> = settingsRepository.waterGoalOverrideMl

    /** Daily hydration target (ml): manual override if set, else ~35 ml/kg of body weight. */
    val waterGoal: StateFlow<Int> = combine(goalOverride, autoGoal) { override, auto -> if (override > 0) override else auto }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2000)

    /** Set a manual goal (ml); 0 restores the weight-based default. */
    fun setWaterGoal(ml: Int) = settingsRepository.setWaterGoalOverrideMl(ml)

    /** Every item that added water today, newest first, with when it was had. */
    val timeline: StateFlow<List<WaterTimelineRow>> = mealRepository.getWaterTimeline(dateIso)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalWater: StateFlow<Float> = mealRepository.getTotalWaterForDate(dateIso)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    /** The calendar week (Mon–Sun) around the viewed day — the Week chart, like Analytics'. */
    val weekStart: LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Water per day of that week (drinks + food), null for days still to come. */
    val week: StateFlow<List<Float?>> = mealRepository
        .getDailyWaterSplitRange(weekStart.toString(), weekStart.plusDays(6).toString())
        .map { rows ->
            val byDate = rows.associate { it.date to (it.drinkWater + it.foodWater) }
            val today = LocalDate.now()
            (0L until 7L).map { i ->
                val d = weekStart.plusDays(i)
                if (d.isAfter(today)) null else (byDate[d.toString()] ?: 0f)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(7) { null })

    val presets: StateFlow<List<Int>> = settingsRepository.waterPresets
    val wakeMinutes: StateFlow<Int> = settingsRepository.wakeMinutes
    val bedMinutes: StateFlow<Int> = settingsRepository.bedMinutes

    /** Today | Week on the chart, remembered across restarts like Analytics' card views. */
    val chartView: StateFlow<Int> = settingsRepository.analyticsViews
        .map { it[CHART_VIEW_KEY] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.analyticsViews.value[CHART_VIEW_KEY] ?: 0)

    fun setChartView(index: Int) = settingsRepository.setAnalyticsView(CHART_VIEW_KEY, index)

    /** Log [ml] to this page's day; [onLogged] gets the new meal log's id, for Undo. */
    fun addWater(ml: Float, onLogged: (Long) -> Unit = {}) {
        if (ml <= 0f) return
        viewModelScope.launch { onLogged(mealRepository.logWater(ml, dateIso)) }
    }

    /** Undo an add: the quick-add made its own meal log, so removing that removes exactly it. */
    fun undoAdd(mealLogId: Long) {
        viewModelScope.launch { mealRepository.deleteMeal(mealLogId) }
    }

    /** Remove a plain water quick-add. */
    fun remove(row: WaterTimelineRow) {
        viewModelScope.launch { mealRepository.deleteItem(row.itemId) }
    }

    /** Undo a removal: the same amount back at the time it was had. */
    fun restore(row: WaterTimelineRow) {
        viewModelScope.launch { mealRepository.logWater(row.waterMl, dateIso, row.timestamp) }
    }

    fun addPreset(ml: Int) {
        if (ml <= 0 || ml in presets.value) return
        settingsRepository.setWaterPresets(presets.value + ml)
    }

    fun removePreset(ml: Int) {
        settingsRepository.setWaterPresets(presets.value - ml)
    }

    private companion object {
        const val CHART_VIEW_KEY = "tracker:water"
    }
}
