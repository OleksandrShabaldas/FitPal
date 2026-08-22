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
    val savedToGallery: Boolean = false,
    /** True while the AI is reading a snapped nutrition label into the fields. */
    val isReadingLabel: Boolean = false,
    val labelError: String? = null
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
    private val pipeline: com.fitpal.app.ml.FoodAnalysisPipeline,
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

    /**
     * Changing the amount re-scales the calories/macros when they came from a snapped label (stored
     * per 100 g) — so "enter the weight and the fields fill in". Hand-typed values have no per-100g
     * basis, so the amount just changes on its own.
     */
    fun onAmount(v: String) {
        val amount = v.decimals()
        val basis = per100Basis
        val g = amount.toFloatOrNull()
        _uiState.update {
            if (basis != null && g != null && g > 0f) it.copy(
                amount = amount, savedToGallery = false,
                calories = clean(basis.calories * g / 100f),
                protein = clean(basis.protein * g / 100f),
                fat = clean(basis.fat * g / 100f),
                carbs = clean(basis.carbs * g / 100f),
                fiber = clean(basis.fiber * g / 100f)
            ) else it.copy(amount = amount, savedToGallery = false)
        }
    }

    /**
     * Editing a macro after a snap **re-bases** that macro (from the value + current amount) instead
     * of throwing the label basis away — so correcting one number doesn't stop the amount field from
     * rescaling everything. A value typed from scratch (no basis) stays exact, as before.
     */
    fun onCalories(v: String) = editMacro(v, { s, x -> s.copy(calories = x) }) { b, p -> b.copy(calories = p) }
    fun onProtein(v: String) = editMacro(v, { s, x -> s.copy(protein = x) }) { b, p -> b.copy(protein = p) }
    fun onFat(v: String) = editMacro(v, { s, x -> s.copy(fat = x) }) { b, p -> b.copy(fat = p) }
    fun onCarbs(v: String) = editMacro(v, { s, x -> s.copy(carbs = x) }) { b, p -> b.copy(carbs = p) }
    fun onFiber(v: String) = editMacro(v, { s, x -> s.copy(fiber = x) }) { b, p -> b.copy(fiber = p) }

    private fun editMacro(
        raw: String,
        setField: (CustomFoodUiState, String) -> CustomFoodUiState,
        reBase: (Per100, Float) -> Per100
    ) {
        val v = raw.decimals()
        val basis = per100Basis
        val amount = _uiState.value.amount.toFloatOrNull()
        // With a label basis, re-derive this macro's per-100 g from the edited value so the amount
        // field keeps rescaling. Without one (hand-typed food), leave the basis null → exact values.
        if (basis != null && amount != null && amount > 0f) {
            per100Basis = reBase(basis, (v.toFloatOrNull() ?: 0f) / amount * 100f)
        }
        _uiState.update { setField(it, v).copy(savedToGallery = false) }
    }

    private fun String.decimals() = filter { it.isDigit() || it == '.' }

    /** Per-100 g values from a snapped label, so [onAmount] can scale them to what you ate. */
    private data class Per100(val calories: Float, val protein: Float, val fat: Float, val carbs: Float, val fiber: Float)
    private var per100Basis: Per100? = null

    private fun clean(v: Float): String {
        if (v <= 0f) return ""
        val r = kotlin.math.round(v * 10f) / 10f
        return if (r == r.toLong().toFloat()) r.toLong().toString() else r.toString()
    }

    fun clearLabelError() = _uiState.update { it.copy(labelError = null) }

    /**
     * Read a snapped nutrition-facts label with the AI and fill the form. The label is stored per
     * 100 g (so the amount field can rescale it); the fields show the per-100 g values to start. Uses
     * the online model when available, the on-device one otherwise — same as everywhere else.
     */
    fun readLabel(photoPath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isReadingLabel = true, labelError = null) }
            val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodeUpright(photoPath) }
            if (bitmap == null) {
                _uiState.update { it.copy(isReadingLabel = false, labelError = "Couldn't read that photo — try again.") }
                return@launch
            }
            val food = runCatching { pipeline.analyze(bitmap, note = LABEL_NOTE) }.getOrNull()?.firstOrNull()
            // The model sets grams to the serving / package size it read — the amount you likely ate.
            val serving = food?.totalGrams ?: 0f
            if (food == null || serving <= 0f || food.totalCalories <= 0f) {
                _uiState.update { it.copy(isReadingLabel = false, labelError = "Couldn't read that label — try again, or type the values in.") }
                return@launch
            }
            // Keep a per-100 g basis so changing the amount rescales; pre-fill the amount + values for
            // the serving size straight from the label, so you rarely have to type or recalc anything.
            val basis = Per100(
                calories = food.totalCalories / serving * 100f,
                protein = food.totalProtein / serving * 100f,
                fat = food.totalFat / serving * 100f,
                carbs = food.totalCarbs / serving * 100f,
                fiber = food.totalFiber / serving * 100f
            )
            per100Basis = basis
            _uiState.update {
                it.copy(
                    isReadingLabel = false,
                    name = food.label.ifBlank { it.name },
                    amount = clean(serving),
                    calories = clean(food.totalCalories),
                    protein = clean(food.totalProtein),
                    fat = clean(food.totalFat),
                    carbs = clean(food.totalCarbs),
                    fiber = clean(food.totalFiber),
                    savedToGallery = false
                )
            }
        }
    }

    /** Decode a captured photo file, downsampled and rotated upright (from its EXIF orientation). */
    private fun decodeUpright(path: String, maxDim: Int = 1600): android.graphics.Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
        val bmp = android.graphics.BitmapFactory.decodeFile(
            path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        val orientation = runCatching {
            android.media.ExifInterface(path).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bmp
        val m = android.graphics.Matrix().apply { postRotate(degrees) }
        return android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    private companion object {
        const val LABEL_NOTE =
            "IMPORTANT: this photo is the NUTRITION FACTS panel of ONE packaged product — it is NOT a " +
                "plate of prepared food. Ignore any 'identify the foods on the plate' instructions. Read the " +
                "label and return EXACTLY ONE food item whose per-100 g values (kcalPer100g, proteinPer100g, " +
                "fatPer100g, carbsPer100g, fiberPer100g) come straight from the label. If the label lists " +
                "values per serving, convert to per 100 g using the serving size printed on it. Set \"grams\" " +
                "to the amount a person most likely ate: the serving size printed on the label, or the net " +
                "package weight if it's a single-serving pack; if no serving size is given, use 100. Use the " +
                "product's name from the label."
    }

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
