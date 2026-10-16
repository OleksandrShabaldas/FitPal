package com.fitpal.app.ui.screen.steps

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** The Steps page for one day (docs/redesign/03-steps-page.md). */
@HiltViewModel
class StepsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stepRepository: StepRepository,
    private val settingsRepository: SettingsRepository,
    private val weightRepository: WeightRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val dateIso: String = savedStateHandle.get<String>(Screen.Steps.ARG_DATE) ?: LocalDate.now().toString()
    val date: LocalDate = runCatching { LocalDate.parse(dateIso) }.getOrDefault(LocalDate.now())
    val isToday: Boolean get() = date == LocalDate.now()

    val total: StateFlow<Int> = stepRepository.getTotalSteps(dateIso)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val manual: StateFlow<Int> = stepRepository.getManualSteps(dateIso)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val synced: StateFlow<Int> = stepRepository.getSyncedSteps(dateIso)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    /** The calories the day's steps add to the budget (after the user's trim) — what Home adds. */
    val calories: StateFlow<Float> = stepRepository.getCaloriesBurned(dateIso)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)
    val goal: StateFlow<Int> = stepRepository.stepGoal
    val lastSyncedAt: StateFlow<Long> = stepRepository.lastSyncedAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val healthConnectAvailable: Boolean = stepRepository.healthConnectAvailable()

    /** The calendar week around the day — the Week chart, like Analytics'. */
    val weekStart: LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week: StateFlow<List<Float?>> = stepRepository
        .getDailySteps(weekStart.toString(), weekStart.plusDays(6).toString())
        .map { rows ->
            val byDate = rows.associate { it.date to it.steps.toFloat() }
            val today = LocalDate.now()
            (0L until 7L).map { i ->
                val d = weekStart.plusDays(i)
                if (d.isAfter(today)) null else (byDate[d.toString()] ?: 0f).coerceAtLeast(0f)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(7) { null })

    /** Steps hour by hour from Health Connect; null when it has no intervals for the day. */
    private val _hourly = MutableStateFlow<List<Long>?>(null)
    val hourly: StateFlow<List<Long>?> = _hourly.asStateFlow()

    /** Every Health Connect writer's count for the day (package → steps), plus the watch's report. */
    private val _sources = MutableStateFlow<Map<String, Int>>(emptyMap())
    val sources: StateFlow<Map<String, Int>> = _sources.asStateFlow()

    /** Today | Week on the chart, remembered like the other tracker charts. */
    val chartView: StateFlow<Int> = settingsRepository.analyticsViews
        .map { it["tracker:steps"] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.analyticsViews.value["tracker:steps"] ?: 0)

    fun setChartView(index: Int) = settingsRepository.setAnalyticsView("tracker:steps", index)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    init {
        viewModelScope.launch { runCatching { stepRepository.refreshAutoStepGoal() } }
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _hourly.value = stepRepository.stepsByHour(dateIso)
            _sources.value = runCatching { stepRepository.stepSourcesForDate(dateIso) }.getOrDefault(emptyMap())
        }
    }

    /** Pull fresh steps from Health Connect, then reload where they came from. */
    fun sync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            val kg = weightRepository.getLatest().first()?.weightKg ?: 70f
            runCatching { stepRepository.syncFromHealthConnect(kg) }
            // FitPal hands fresh steps to Calistapp when it syncs (gated by the user's toggle), as Home does.
            if (settingsRepository.calistappSyncEnabled.value) {
                runCatching { com.fitpal.app.sync.CalistappNudge.nudge(appContext) }
            }
            loadDetails()
            _syncing.value = false
        }
    }

    /** SET the hand-entered steps for the day (replaces, doesn't add; negative trims the synced count). */
    fun setManual(steps: Int) {
        viewModelScope.launch {
            val kg = weightRepository.getLatest().first()?.weightKg ?: 70f
            stepRepository.setManualStepsForDate(dateIso, steps, kg)
        }
    }
}
