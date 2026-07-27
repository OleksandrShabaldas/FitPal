package com.fitpal.app.ui.screen.custom

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.repository.GalleryRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.NutritionRepository
import com.fitpal.app.domain.MealLogContext
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomFoodUiState(
    val name: String = "",
    val amount: String = "",
    val calories: String = "",
    val protein: String = "",
    val fat: String = "",
    val carbs: String = "",
    val fiber: String = "",
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    /** True once this food has been saved to the collection (drives the button's saved state). */
    val savedToGallery: Boolean = false
) {
    val canSave: Boolean get() = name.isNotBlank() && (calories.toFloatOrNull() ?: 0f) > 0f
}

/**
 * Lets the user log a food by typing its own values (calories + macros) — for anything the
 * AI or the food databases don't know.
 */
@HiltViewModel
class CustomFoodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRepository,
    private val galleryRepository: GalleryRepository,
    private val nutritionRepository: NutritionRepository,
    mealLogContext: MealLogContext
) : ViewModel() {

    /** A scanned barcode this custom food should be linked to (so the next scan finds it). */
    val barcode: String? = savedStateHandle.get<String>(Screen.CustomFood.ARG_BARCODE)?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(CustomFoodUiState())
    val uiState: StateFlow<CustomFoodUiState> = _uiState

    private val _mealType =
        MutableStateFlow(mealLogContext.consume() ?: mealRepository.defaultMealType())
    val mealType: StateFlow<String> = _mealType
    fun setMealType(type: String) { _mealType.value = type }

    /** The day to log to — starts from Home's "+" (or today), user-editable. */
    private val _logDate = MutableStateFlow(
        mealLogContext.consumeDate()?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: java.time.LocalDate.now()
    )
    val logDate: StateFlow<java.time.LocalDate> = _logDate
    fun setLogDate(date: java.time.LocalDate) { _logDate.value = date }
    private fun logDateIso(): String = _logDate.value.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

    // Editing any field means the saved copy is now stale, so re-enable "Save to collection".
    fun onName(v: String) = _uiState.update { it.copy(name = v, savedToGallery = false) }
    fun onAmount(v: String) = _uiState.update { it.copy(amount = v.decimals(), savedToGallery = false) }
    fun onCalories(v: String) = _uiState.update { it.copy(calories = v.decimals(), savedToGallery = false) }
    fun onProtein(v: String) = _uiState.update { it.copy(protein = v.decimals(), savedToGallery = false) }
    fun onFat(v: String) = _uiState.update { it.copy(fat = v.decimals(), savedToGallery = false) }
    fun onCarbs(v: String) = _uiState.update { it.copy(carbs = v.decimals(), savedToGallery = false) }
    fun onFiber(v: String) = _uiState.update { it.copy(fiber = v.decimals(), savedToGallery = false) }

    private fun String.decimals() = filter { it.isDigit() || it == '.' }

    /**
     * The user enters values for ONE serving (the amount they ate). We store per-100 g so it
     * scales, but the logged numbers come out exactly as typed.
     */
    private fun buildIngredient(): Ingredient {
        val s = _uiState.value
        val grams = s.amount.toFloatOrNull()?.takeIf { it > 0f } ?: 100f
        fun per100(v: String) = (v.toFloatOrNull() ?: 0f) / grams * 100f
        return Ingredient(
            name = s.name.trim(),
            grams = grams,
            caloriesPer100g = per100(s.calories),
            proteinPer100g = per100(s.protein),
            fatPer100g = per100(s.fat),
            carbsPer100g = per100(s.carbs),
            fiberPer100g = per100(s.fiber)
        )
    }

    fun log() {
        if (!_uiState.value.canSave) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val ingredient = buildIngredient()
                mealRepository.logItems(listOf(ingredient), _mealType.value, date = logDateIso())
                // If this was reached from a not-found barcode scan, remember it for next time.
                barcode?.let { code ->
                    nutritionRepository.saveCustomBarcodeFood(
                        barcode = code,
                        name = ingredient.name,
                        caloriesPer100g = ingredient.caloriesPer100g,
                        proteinPer100g = ingredient.proteinPer100g,
                        fatPer100g = ingredient.fatPer100g,
                        carbsPer100g = ingredient.carbsPer100g,
                        servingGrams = ingredient.grams.takeIf { it > 0f }
                    )
                }
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun saveToGallery() {
        if (!_uiState.value.canSave || _uiState.value.savedToGallery) return
        viewModelScope.launch {
            galleryRepository.saveIngredient(buildIngredient())
            _uiState.update { it.copy(savedToGallery = true) }
        }
    }
}
