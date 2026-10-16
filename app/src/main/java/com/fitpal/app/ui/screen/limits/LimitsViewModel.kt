package com.fitpal.app.ui.screen.limits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.model.DietaryRule
import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** The Food limits page for one day (docs/redesign/09-fasting-weight-limits-pages.md). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LimitsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val dateIso: String = savedStateHandle.get<String>(Screen.Limits.ARG_DATE) ?: LocalDate.now().toString()
    val date: LocalDate = runCatching { LocalDate.parse(dateIso) }.getOrDefault(LocalDate.now())
    val isToday: Boolean get() = date == LocalDate.now()
    val weekStart: LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** The limits that are on. */
    val rules: StateFlow<List<DietaryRule>> = settingsRepository.dietaryRules
        .map { all -> all.filter { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settingsRepository.dietaryRules.value.filter { it.enabled })

    /** Calories in each limit's category on the day — the same tally Home and the gate use. */
    val consumed: StateFlow<Map<DietaryRuleKind, Int>> = combine(
        mealRepository.dietaryConsumedForDate(dateIso, DietaryRuleKind.DESSERT),
        mealRepository.dietaryConsumedForDate(dateIso, DietaryRuleKind.FRIED),
        mealRepository.dietaryConsumedForDate(dateIso, DietaryRuleKind.SUGARY_DRINK)
    ) { dessert, fried, drink ->
        mapOf(
            DietaryRuleKind.DESSERT to dessert.toInt(),
            DietaryRuleKind.FRIED to fried.toInt(),
            DietaryRuleKind.SUGARY_DRINK to drink.toInt()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** The day's items that count toward any limit. */
    val taggedItems: StateFlow<List<MealLogItemEntity>> = mealRepository.getItemsForDate(dateIso)
        .map { items -> items.filter { DietaryRuleKind.tagsFromDb(it.ruleTags).isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Which limit's week is shown — the one the page was opened for, else the first that's on. */
    private val _selected = MutableStateFlow(
        DietaryRuleKind.fromId(savedStateHandle.get<String>(Screen.Limits.ARG_FOCUS))
            ?: settingsRepository.dietaryRules.value.firstOrNull { it.enabled }?.kind
    )
    val selected: StateFlow<DietaryRuleKind?> = _selected.asStateFlow()
    fun select(kind: DietaryRuleKind) { _selected.value = kind }

    /** The selected limit's calories per day, Monday to Sunday (null for days still to come). */
    val week: StateFlow<List<Float?>> = _selected
        .flatMapLatest { kind ->
            if (kind == null) flowOf(List(7) { null })
            else mealRepository.dietaryConsumedByDay(weekStart.toString(), weekStart.plusDays(6).toString(), kind).map { rows ->
                val byDate = rows.associate { it.date to it.kcal }
                val today = LocalDate.now()
                (0L until 7L).map { i ->
                    val d = weekStart.plusDays(i)
                    if (d.isAfter(today)) null else (byDate[d.toString()] ?: 0f)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(7) { null })

    /** "This doesn't count as dessert": take one tag off an item. */
    fun removeTag(item: MealLogItemEntity, kind: DietaryRuleKind) {
        viewModelScope.launch {
            mealRepository.setItemRuleTags(item.id, DietaryRuleKind.tagsFromDb(item.ruleTags) - kind)
        }
    }
}
