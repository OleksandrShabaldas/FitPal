package com.fitpal.app.ui.screen.coach

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.MealJson
import com.fitpal.app.data.local.entity.PlannedMealEntity
import com.fitpal.app.data.repository.PlannerRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.data.repository.WeightRepository
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.DailyTargets
import com.fitpal.app.domain.model.PlanRequest
import com.fitpal.app.domain.model.PlannedRecipe
import com.fitpal.app.ml.PlanJob
import com.fitpal.app.ml.PlannerJobManager
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

/** A planned meal with its recipe read back. */
data class PlannedItem(val entity: PlannedMealEntity, val recipe: PlannedRecipe)

/** One line of the shopping list: what, how much (summed), and whether it's ticked. */
data class ShoppingLine(val name: String, val grams: Float, val checked: Boolean)

/**
 * One plan's recipes (docs/redesign/10) — or the saved ideas — with schedule / log / save / regenerate
 * / dismiss, the day totals against the targets, and the shopping list (what the plan's meals need
 * that you said you don't have).
 */
@HiltViewModel
class PlanResultsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val planner: PlannerRepository,
    private val jobs: PlannerJobManager,
    private val settings: SettingsRepository,
    weights: WeightRepository
) : ViewModel() {

    val requestId: String = savedState.get<String>(Screen.PlanResults.ARG_REQUEST_ID) ?: ""
    val isSaved: Boolean = requestId == Screen.PlanResults.SAVED

    val items: StateFlow<List<PlannedItem>> =
        (if (isSaved) planner.saved() else planner.forRequest(requestId))
            .map { list -> list.mapNotNull { e -> MealJson.decodeRecipe(e.recipeJson)?.let { PlannedItem(e, it) } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The job, while it's this plan's. */
    val job: StateFlow<PlanJob?> = jobs.state
        .map { j -> j?.takeIf { it.requestId == requestId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val targets: StateFlow<DailyTargets?> = combine(weights.getLatest(), settings.userProfile) { w, profile ->
        w?.let { BmrCalculator.dailyTargets(profile, it.weightKg, settings.dailyCalorieGoal.value, settings.macroSelection.value) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val shopping: StateFlow<List<ShoppingLine>> = combine(items, settings.shoppingChecked) { list, checked ->
        list.filter { it.entity.status == PlannedMealEntity.IDEA || it.entity.status == PlannedMealEntity.SCHEDULED }
            .flatMap { it.recipe.ingredients }
            .filter { !it.have }
            .groupBy { it.ingredient.name.trim().lowercase() }
            .map { (_, same) ->
                val name = same.first().ingredient.name.trim()
                ShoppingLine(name, same.sumOf { it.ingredient.grams.toDouble() }.toFloat(), "$requestId|$name" in checked)
            }
            .sortedBy { it.checked }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleShopping(name: String) {
        val key = "$requestId|$name"
        val now = settings.shoppingChecked.value
        settings.setShoppingChecked(if (key in now) now - key else now + key)
    }

    fun schedule(id: Long, date: LocalDate, mealType: String) = viewModelScope.launch { planner.schedule(id, date, mealType) }

    /** Every idea onto its own day and meal, as planned — allergen-flagged ones stay put. */
    fun scheduleAll() = viewModelScope.launch {
        items.value.filter { it.entity.status == PlannedMealEntity.IDEA && it.recipe.allergens.isEmpty() }.forEach { p ->
            val date = p.entity.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
            planner.schedule(p.entity.id, date, p.entity.mealType ?: p.recipe.mealType)
        }
    }

    fun saveIdea(id: Long) = viewModelScope.launch { planner.saveIdea(id) }
    fun dismiss(id: Long) = viewModelScope.launch { planner.dismiss(id) }

    /** Make one meal again, keeping the rest — they're sent along so it isn't repeated. */
    fun regenerate(item: PlannedItem) {
        val request = PlanRequest.fromJson(settings.lastPlanRequest)
        val others = items.value.filter { it.entity.id != item.entity.id }.map { it.entity.name } + item.entity.name
        jobs.regenerate(item.entity.requestId, item.entity.id, request, others)
    }

    fun retryOnline() = jobs.chooseRetryOnline()
    fun useOnDevice() = jobs.chooseUseLocal()
    fun cancel() = jobs.cancel()
}
