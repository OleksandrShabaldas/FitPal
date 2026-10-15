package com.fitpal.app.ui.screen.addfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.domain.MealLogContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AddFoodViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val mealLogContext: MealLogContext,
    settingsRepository: com.fitpal.app.data.repository.SettingsRepository
) : ViewModel() {

    /** The user's Add food layout: which ways to log show, in what order, small (square) or wide. */
    val layout: StateFlow<List<com.fitpal.app.domain.model.ResolvedCard>> =
        settingsRepository.widgetLayouts
            .map { com.fitpal.app.domain.model.ScreenWidgets.resolveCards(ADD_FOOD, it[ADD_FOOD].orEmpty()) }
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5000),
                com.fitpal.app.domain.model.ScreenWidgets.resolveCards(ADD_FOOD, settingsRepository.widgetLayouts.value[ADD_FOOD].orEmpty())
            )

    private companion object {
        const val ADD_FOOD = com.fitpal.app.domain.model.ScreenWidgets.ADD_FOOD
    }

    /** Recently logged foods (newest first) for the one-tap "Your usuals" row. */
    val recentFoods: StateFlow<List<MealLogItemEntity>> =
        mealRepository.recentDistinctFoods(limit = 12)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Flips true after a one-tap re-log so the screen can pop home. */
    private val _logged = MutableStateFlow(false)
    val logged: StateFlow<Boolean> = _logged

    /**
     * Whether this visit logs for today rather than back-filling a day Home was showing — read without
     * consuming the handoff, which the logging screen still needs. Only today can break today's fast,
     * so the intense-fasting stop only covers today.
     */
    val loggingForToday: Boolean =
        mealLogContext.pendingDate.let { it == null || it == LocalDate.now().toString() }

    /**
     * One-tap re-log of a recent food. Honours the meal category / date the user was on when they
     * opened "+" (consuming the handoff so a sub-flow doesn't reuse it), else the time-of-day meal
     * and today.
     */
    fun quickLog(
        item: MealLogItemEntity,
        decision: com.fitpal.app.domain.model.LogDecision = com.fitpal.app.domain.model.LogDecision.NONE
    ) {
        viewModelScope.launch {
            val mealType = mealLogContext.consume() ?: mealRepository.defaultMealType()
            val date = mealLogContext.consumeDate()
            val logDate = date?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                ?: java.time.LocalDate.now()
            mealRepository.quickLogRecent(
                item, mealType, date,
                meta = com.fitpal.app.data.repository.mealLogMeta(logDate, com.fitpal.app.domain.model.EatenAt(), decision)
            )
            _logged.value = true
        }
    }
}
