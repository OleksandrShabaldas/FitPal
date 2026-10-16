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

    private companion object {
        const val CHART_VIEW_KEY = "tracker:caffeine"
    }
}
