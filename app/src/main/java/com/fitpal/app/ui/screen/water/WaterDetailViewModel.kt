package com.fitpal.app.ui.screen.water

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.MealLogItemEntity
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
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class WaterDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRepository,
    private val settingsRepository: SettingsRepository,
    weightRepository: WeightRepository
) : ViewModel() {

    private val date: String = savedStateHandle.get<String>(Screen.WaterDetail.ARG_DATE)
        ?: LocalDate.now().toString()

    /** Daily hydration target (ml): manual override if set, else ~35 ml/kg of body weight. */
    val waterGoal: StateFlow<Int> = combine(
        settingsRepository.waterGoalOverrideMl,
        weightRepository.getLatest()
    ) { override, weight ->
        if (override > 0) override else SettingsRepository.autoWaterGoal(weight?.weightKg ?: 70f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2000)

    /** Set a manual goal (ml); 0 restores the weight-based default. */
    fun setWaterGoal(ml: Int) = settingsRepository.setWaterGoalOverrideMl(ml)

    /** Today's items that contribute water (drinks + watery foods). */
    val items: StateFlow<List<MealLogItemEntity>> = mealRepository.getItemsForDate(date)
        .map { list -> list.filter { it.waterMl > 0f } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalWater: StateFlow<Float> = mealRepository.getTotalWaterForDate(date)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val presets: StateFlow<List<Int>> = settingsRepository.waterPresets

    fun addWater(ml: Float) {
        if (ml <= 0f) return
        viewModelScope.launch { mealRepository.logWater(ml, date) }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { mealRepository.deleteItem(id) }
    }

    fun addPreset(ml: Int) {
        if (ml <= 0) return
        settingsRepository.setWaterPresets(presets.value + ml)
    }

    fun removePreset(ml: Int) {
        settingsRepository.setWaterPresets(presets.value - ml)
    }
}
