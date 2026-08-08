package com.fitpal.app.ui.screen.mealgroup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.ui.component.logDateLabel
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** One dish of the meal: the logged item plus its saved ingredient breakdown (if any). */
data class MealGroupDish(
    val item: MealLogItemEntity,
    val ingredients: List<Ingredient>
)

data class MealGroupUiState(
    val dishes: List<MealGroupDish> = emptyList(),
    val mealType: String = "",
    /** The name the user gave this whole meal, or null while it's unnamed. */
    val mealName: String? = null,
    /** The day this meal is logged on — so "copy to date" can center on it. */
    val date: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    /** True once the whole meal was deleted — the screen should pop back. */
    val deleted: Boolean = false,
    /** One-shot confirmation after "copy to another date" — shown as a toast, then cleared. */
    val copyConfirmation: String? = null
) {
    val totalCalories: Float get() = dishes.sumOf { it.item.calories.toDouble() }.toFloat()
    /** The shared photo for the generation, if any. */
    val photoPath: String? get() = dishes.firstOrNull { !it.item.photoPath.isNullOrBlank() }?.item?.photoPath
}

/**
 * Shows every dish of a single logged meal (one photo/describe generation) on one screen —
 * the multi-dish overview opened by tapping a meal group on Home. Tapping a dish opens its full
 * [EntryDetailScreen] for editing; the top bar can copy or delete the whole meal.
 */
@HiltViewModel
class MealGroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRepository
) : ViewModel() {

    private val mealLogId: Long = savedStateHandle.get<Long>(Screen.MealGroup.ARG_MEAL_LOG_ID) ?: 0L

    private val _uiState = MutableStateFlow(MealGroupUiState())
    val uiState: StateFlow<MealGroupUiState> = _uiState

    init {
        viewModelScope.launch {
            val type = mealRepository.getMealTypeForMeal(mealLogId) ?: ""
            val date = mealRepository.getMealDate(mealLogId)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
            val name = mealRepository.getMealName(mealLogId)
            _uiState.update { it.copy(mealType = type, date = date, mealName = name) }
        }
        viewModelScope.launch {
            mealRepository.getItemsForMealFlow(mealLogId).collect { items ->
                val dishes = items.map { item ->
                    MealGroupDish(item, mealRepository.ingredientsForItem(item))
                }
                _uiState.update { it.copy(dishes = dishes, isLoading = false) }
            }
        }
    }

    // ---- Inline editing (per dish), persisted to the DB; the Flow reconciles totals ----

    /** Set one ingredient's weight on a dish. */
    fun updateIngredientGrams(itemId: Long, index: Int, newGrams: Float) {
        if (newGrams <= 0f) return
        editDish(itemId) { ingredients ->
            ingredients.toMutableList().also {
                if (index in it.indices) it[index] = it[index].withGrams(newGrams)
            }
        }
    }

    /** Scale a whole dish to a new total weight, keeping ingredient proportions. */
    fun scaleDishToGrams(itemId: Long, newTotalGrams: Float) {
        if (newTotalGrams <= 0f) return
        editDish(itemId) { ingredients ->
            val current = ingredients.sumOf { it.grams.toDouble() }.toFloat()
            if (current <= 0f) ingredients else {
                val factor = newTotalGrams / current
                ingredients.map { it.withGrams(it.grams * factor) }
            }
        }
    }

    /** Remove one ingredient from a dish; removing the last one deletes the whole dish. */
    fun removeIngredient(itemId: Long, index: Int) {
        editDish(itemId) { ingredients -> ingredients.filterIndexed { i, _ -> i != index } }
    }

    /** Delete a whole dish from the meal. If it was the last, the screen pops back. */
    fun removeDish(itemId: Long) {
        viewModelScope.launch { mealRepository.deleteItem(itemId) }
    }

    /**
     * Apply [transform] to a dish's ingredients: update local state optimistically (so rapid
     * edits build on the latest values and the field stays responsive), then persist. An empty
     * result deletes the dish.
     */
    private fun editDish(itemId: Long, transform: (List<Ingredient>) -> List<Ingredient>) {
        val dishes = _uiState.value.dishes
        val idx = dishes.indexOfFirst { it.item.id == itemId }
        if (idx < 0) return
        val newIngredients = transform(dishes[idx].ingredients)
        if (newIngredients.isEmpty()) { removeDish(itemId); return }
        val updated = dishes.toMutableList()
        updated[idx] = updated[idx].copy(ingredients = newIngredients)
        _uiState.update { it.copy(dishes = updated) }
        viewModelScope.launch { mealRepository.updateItemIngredients(itemId, newIngredients) }
    }

    /**
     * Name the whole meal ("Sunday roast") — an empty name clears it and the meal goes back to
     * being described by its dishes. Renaming a *dish* is [renameDish].
     */
    fun renameMeal(name: String) {
        val clean = name.trim().takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            mealRepository.renameMeal(mealLogId, clean)
            _uiState.update { it.copy(mealName = clean) }
        }
    }

    /** Rename one dish of the meal — including one logged straight from the food database. */
    fun renameDish(itemId: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        // The items Flow refreshes the card; no optimistic update needed.
        viewModelScope.launch { mealRepository.renameItem(itemId, clean) }
    }

    /** Change this whole meal's category (Breakfast/Lunch/Dinner/Snack) — all dishes move together. */
    fun setMealType(type: String) {
        if (type == _uiState.value.mealType) return
        viewModelScope.launch {
            mealRepository.setMealMealType(mealLogId, type)
            _uiState.update { it.copy(mealType = type) }
        }
    }

    /** Copy the whole meal (all dishes) onto another day, into the chosen meal category. */
    fun copyToDate(date: LocalDate, mealType: String? = null, copies: Int = 1) {
        val n = copies.coerceIn(1, 20)
        viewModelScope.launch {
            try {
                repeat(n) {
                    mealRepository.copyMealToDate(mealLogId, date.format(DateTimeFormatter.ISO_LOCAL_DATE), mealType)
                }
                val label = logDateLabel(date).lowercase()
                _uiState.update {
                    it.copy(copyConfirmation = if (n == 1) "Copied to $label" else "$n copies to $label")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(copyConfirmation = "Couldn't copy: ${e.message}") }
            }
        }
    }

    fun clearCopyConfirmation() {
        _uiState.update { it.copy(copyConfirmation = null) }
    }

    /** Delete the whole meal (every dish). */
    fun deleteMeal() {
        viewModelScope.launch {
            mealRepository.deleteMeal(mealLogId)
            _uiState.update { it.copy(deleted = true) }
        }
    }
}
