package com.fitpal.app.ui.screen.addfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.MealLogContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddFoodViewModel @Inject constructor(
    private val weightRepository: WeightRepository,
    private val mealRepository: MealRepository,
    private val mealLogContext: MealLogContext
) : ViewModel() {

    /** Recently logged foods (newest first) for the one-tap "Your usuals" row. */
    val recentFoods: StateFlow<List<MealLogItemEntity>> =
        mealRepository.recentDistinctFoods(limit = 12)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Flips true after a one-tap re-log so the screen can pop home. */
    private val _logged = MutableStateFlow(false)
    val logged: StateFlow<Boolean> = _logged

    /**
     * One-tap re-log of a recent food. Honours the meal category / date the user was on when they
     * opened "+" (consuming the handoff so a sub-flow doesn't reuse it), else the time-of-day meal
     * and today.
     */
    fun quickLog(item: MealLogItemEntity) {
        viewModelScope.launch {
            val mealType = mealLogContext.consume() ?: mealRepository.defaultMealType()
            val date = mealLogContext.consumeDate()
            mealRepository.quickLogRecent(item, mealType, date)
            _logged.value = true
        }
    }

    fun logWeight(kg: Float) {
        viewModelScope.launch { weightRepository.logWeight(kg) }
    }
}
