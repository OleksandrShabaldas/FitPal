package com.fitpal.app.ui.screen.fasting

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.dao.DayMealRow
import com.fitpal.app.data.local.dao.MealTimeRow
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.model.FastingDayResult
import com.fitpal.app.domain.model.FastingSchedule
import com.fitpal.app.domain.model.adherenceByDay
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** Everything the Fasting page shows that comes from the history. */
data class FastingHistory(
    val adherence: Map<String, FastingDayResult> = emptyMap(),
    val streak: Int = 0,
    val best: Int = 0,
    val passesLeft: Int = SettingsRepository.FASTING_BACKDATE_LIMIT
)

/** The Fasting page for one day (docs/redesign/09-fasting-weight-limits-pages.md). */
@HiltViewModel
class FastingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    mealRepository: MealRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val dateIso: String = savedStateHandle.get<String>(Screen.Fasting.ARG_DATE) ?: LocalDate.now().toString()
    val date: LocalDate = runCatching { LocalDate.parse(dateIso) }.getOrDefault(LocalDate.now())
    val isToday: Boolean get() = date == LocalDate.now()
    val weekStart: LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val schedule: StateFlow<FastingSchedule> = settingsRepository.fastingSchedule

    /** Today | Week on the chart, remembered like the other tracker charts. */
    val chartView: StateFlow<Int> = settingsRepository.analyticsViews
        .map { it["tracker:fasting"] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.analyticsViews.value["tracker:fasting"] ?: 0)

    fun setChartView(index: Int) = settingsRepository.setAnalyticsView("tracker:fasting", index)

    /** Days a pass was used (dateIso → the attested eating minute). */
    val grace: StateFlow<Map<String, Int>> = settingsRepository.fastingGrace

    /** The day's meals with their eaten times. */
    val meals: StateFlow<List<DayMealRow>> = mealRepository.getMealsForDay(dateIso)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Kept/broken per day over the last four months (enough for a best streak), the current streak
     * and the passes left this month — the same rules as Analytics: drinks the drink rule allows never
     * break a day, a pass keeps it, "log anyway" breaks it.
     */
    val history: StateFlow<FastingHistory> = combine(
        mealRepository.getMealTimesInRange(LocalDate.now().minusDays(LOOKBACK_DAYS).toString(), LocalDate.now().toString()),
        settingsRepository.fastingSchedule,
        settingsRepository.fastingGrace
    ) { rows, s, grace ->
        val breaking = rows.filterNot { s.allowsDuringFast(it.fastingIntake) }
        val adherence = s.adherenceByDay(
            breaking.map { it.date to minuteOfDay(it.timestamp) },
            grace.keys,
            breaking.filter(MealTimeRow::loggedDuringFast).map { it.date }.toSet()
        )
        val month = LocalDate.now().toString().take(7)
        FastingHistory(
            adherence = adherence,
            streak = currentStreak(adherence),
            best = bestStreak(adherence),
            passesLeft = (SettingsRepository.FASTING_BACKDATE_LIMIT - grace.keys.count { it.take(7) == month }).coerceAtLeast(0)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FastingHistory())

    /** Consecutive kept days up to today; today not judged yet doesn't break it. */
    private fun currentStreak(adherence: Map<String, FastingDayResult>): Int {
        var day = LocalDate.now()
        if (adherence[day.toString()] == null) day = day.minusDays(1)
        var n = 0
        while (adherence[day.toString()] == FastingDayResult.HELD) {
            n++
            day = day.minusDays(1)
        }
        return n
    }

    /** The longest run of consecutive kept days in the lookback. */
    private fun bestStreak(adherence: Map<String, FastingDayResult>): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        adherence.filterValues { it == FastingDayResult.HELD }.keys
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .sorted()
            .forEach { d ->
                run = if (prev != null && d == prev!!.plusDays(1)) run + 1 else 1
                best = maxOf(best, run)
                prev = d
            }
        return best
    }

    private fun minuteOfDay(epochMillis: Long): Int =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }

    private companion object {
        const val LOOKBACK_DAYS = 120L
    }
}
