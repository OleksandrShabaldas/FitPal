package com.fitpal.app.ui.screen.caffeine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.CaffeineSettings
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** The Caffeine page for one day (docs/redesign/04-caffeine-page.md). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CaffeineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    mealRepository: MealRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val dateIso: String = savedStateHandle.get<String>(Screen.Caffeine.ARG_DATE) ?: LocalDate.now().toString()
    val date: LocalDate = runCatching { LocalDate.parse(dateIso) }.getOrDefault(LocalDate.now())
    val isToday: Boolean get() = date == LocalDate.now()

    val settings: StateFlow<CaffeineSettings> = settingsRepository.caffeineSettings

    /** The day's tracker, or null while the tracker is off. */
    val day: StateFlow<CaffeineDay?> = settingsRepository.caffeineSettings
        .flatMapLatest { s -> if (!s.enabled) flowOf(null) else caffeineDayFlow(mealRepository, s, date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val weekStart: LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week: StateFlow<List<Float?>> = caffeineWeekFlow(mealRepository, weekStart)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(7) { null })

    /** The Profile bedtime (11:00 PM until set) — what the verdict is judged against. */
    val bedMinutes: StateFlow<Int> = settingsRepository.bedMinutes

    /** Today | Week, remembered like the other tracker charts. */
    val chartView: StateFlow<Int> = settingsRepository.analyticsViews
        .map { it[CHART_VIEW_KEY] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.analyticsViews.value[CHART_VIEW_KEY] ?: 0)

    fun setChartView(index: Int) = settingsRepository.setAnalyticsView(CHART_VIEW_KEY, index)

    /** Your drinks (Settings → Caffeine), least caffeine first. */
    val drinks: StateFlow<List<com.fitpal.app.domain.CaffeinePreset>> = settingsRepository.caffeineDrinks

    /** The starred drinks (labels) — the planner card shows them, topped up to three. */
    val quickDrinks: StateFlow<List<String>> = settingsRepository.caffeineQuickDrinks

    /**
     * Star / unstar a drink for the planner card. False when it can't be starred because three
     * already are (the sheet says so); unstarring always works.
     */
    fun toggleQuickDrink(label: String): Boolean {
        val current = quickDrinks.value.filter { q -> drinks.value.any { it.label.equals(q, ignoreCase = true) } }
        val on = current.any { it.equals(label, ignoreCase = true) }
        if (!on && current.size >= com.fitpal.app.domain.Caffeine.QUICK_DRINK_COUNT) return false
        settingsRepository.setCaffeineQuickDrinks(if (on) current.filterNot { it.equals(label, ignoreCase = true) } else current + label)
        return true
    }

    /** "Planning another one?" open or folded — remembered, so a fold stays until it's opened again. */
    val plannerOpen: StateFlow<Boolean> = settingsRepository.caffeinePlannerOpen

    fun setPlannerOpen(open: Boolean) = settingsRepository.setCaffeinePlannerOpen(open)

    private companion object {
        const val CHART_VIEW_KEY = "tracker:caffeine"
    }
}
