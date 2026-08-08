package com.fitpal.app.ui.screen.barcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitpal.app.data.repository.BarcodeRepository
import com.fitpal.app.data.repository.GalleryRepository
import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.MealLogContext
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.ServingPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BarcodeUiState(
    val scanning: Boolean = true,
    val lookingUp: Boolean = false,
    val product: Ingredient? = null,
    val notFound: Boolean = false,
    val scannedCode: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    /** True once the scanned product has been saved to the collection (filled-bookmark state). */
    val savedToGallery: Boolean = false
)

@HiltViewModel
class BarcodeViewModel @Inject constructor(
    private val barcodeRepository: BarcodeRepository,
    private val mealRepository: MealRepository,
    private val galleryRepository: GalleryRepository,
    settingsRepository: SettingsRepository,
    mealLogContext: MealLogContext
) : ViewModel() {

    private val _uiState = MutableStateFlow(BarcodeUiState())
    val uiState: StateFlow<BarcodeUiState> = _uiState

    val servingPresets: StateFlow<List<ServingPreset>> = settingsRepository.mealPresets
    val drinkPresets: StateFlow<List<ServingPreset>> = settingsRepository.drinkPresets

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

    fun onBarcodeScanned(code: String) {
        // Ignore repeat detections once we've locked onto a code.
        if (_uiState.value.scannedCode != null) return
        _uiState.update {
            it.copy(scanning = false, lookingUp = true, scannedCode = code)
        }
        viewModelScope.launch {
            val product = barcodeRepository.lookup(code)
            if (product == null) {
                _uiState.update { it.copy(lookingUp = false, notFound = true) }
            } else {
                val drink = com.fitpal.app.domain.Drinks.isDrink(product.description, product.foodCategory)
                _uiState.update {
                    it.copy(
                        lookingUp = false,
                        product = Ingredient(
                            name = product.description,
                            grams = product.commonServingGrams ?: 100f,
                            caloriesPer100g = product.caloriesPer100g,
                            proteinPer100g = product.proteinPer100g,
                            fatPer100g = product.fatPer100g,
                            carbsPer100g = product.carbsPer100g,
                            waterMlPer100g = if (drink) com.fitpal.app.domain.Drinks.estimateWaterPer100(product.carbsPer100g, product.proteinPer100g, product.fatPer100g) else 0f,
                            isDrink = drink
                        )
                    )
                }
            }
        }
    }

    fun updateGrams(newGrams: Float) {
        _uiState.update { state ->
            state.copy(product = state.product?.withGrams(newGrams))
        }
    }

    /** Rename the scanned product before logging — barcode names are the manufacturer's, not yours. */
    fun renameProduct(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        _uiState.update { state -> state.copy(product = state.product?.copy(name = clean)) }
    }

    fun saveToGallery() {
        val product = _uiState.value.product ?: return
        if (_uiState.value.savedToGallery) return
        viewModelScope.launch {
            galleryRepository.saveIngredient(product)
            _uiState.update { it.copy(savedToGallery = true) }
        }
    }

    fun scanAgain() {
        _uiState.value = BarcodeUiState()
    }

    fun logMeal() {
        val product = _uiState.value.product ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                mealRepository.logItems(listOf(product), _mealType.value, date = logDateIso())
                _uiState.update { it.copy(isSaving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
