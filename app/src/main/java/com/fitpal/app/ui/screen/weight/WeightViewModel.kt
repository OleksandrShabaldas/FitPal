package com.fitpal.app.ui.screen.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.model.FitnessGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The Weight page (docs/redesign/09-fasting-weight-limits-pages.md). */
@HiltViewModel
class WeightViewModel @Inject constructor(
    private val weightRepository: WeightRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** Every weigh-in, oldest first. */
    val weights: StateFlow<List<WeightEntryEntity>> = weightRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goal: StateFlow<FitnessGoal> = settingsRepository.userProfile
        .map { it.fitnessGoal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FitnessGoal.MAINTAIN)

    /** 30 days | All on the trend, remembered like the other tracker charts. */
    val trendView: StateFlow<Int> = settingsRepository.analyticsViews
        .map { it[TREND_VIEW_KEY] ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.analyticsViews.value[TREND_VIEW_KEY] ?: 0)

    fun setTrendView(index: Int) = settingsRepository.setAnalyticsView(TREND_VIEW_KEY, index)

    /** Today's weigh-in (replaces one already logged today). */
    fun save(kg: Float) {
        viewModelScope.launch { weightRepository.logWeight(kg) }
    }

    fun delete(entry: WeightEntryEntity) {
        viewModelScope.launch { weightRepository.delete(entry.id) }
    }

    /** Undo a delete: the same weight back on its own day. */
    fun restore(entry: WeightEntryEntity) {
        viewModelScope.launch {
            runCatching { LocalDate.parse(entry.date) }.getOrNull()?.let { weightRepository.logWeight(entry.weightKg, it) }
        }
    }

    private companion object {
        const val TREND_VIEW_KEY = "tracker:weight"
    }
}
