package com.fitpal.app.ui.screen.manual

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.glass

/**
 * The "Custom" tab of the meal builder: type a food's own values (or snap its nutrition label and let
 * the AI fill them), then add it to the meal as one ingredient. Mirrors the standalone Custom food
 * screen's form, but instead of logging it adds to the draft — where portion, amount and food/drink
 * are then editable inline.
 *
 * Pure UI bound to a hoisted [form]; the screen owns the label camera/gallery (so its full-screen
 * overlay can sit at the screen root) and calls [onSnapLabel] when "Snap label" is tapped.
 */
@Composable
fun CustomFoodTab(
    form: CustomEntryState,
    onAdd: (Ingredient) -> Unit,
    onSnapLabel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Type a food and its values for the amount you ate. Only a name and calories are required — it's added to the meal below.",
            style = MaterialTheme.typography.bodyMedium, color = CreamMuted
        )

        OutlinedButton(
            onClick = onSnapLabel,
            modifier = Modifier.fillMaxWidth(),
            enabled = !form.isReadingLabel
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (form.isReadingLabel) "Reading…" else "Snap label")
        }

        Column(
            modifier = Modifier.fillMaxWidth().glass().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = form::onName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                placeholder = { Text("e.g. Horalka") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Amount (g/ml)", form.amount, form::onAmount, Modifier.weight(1f))
                NumberField("Calories (kcal)", form.calories, form::onCalories, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Protein (g)", form.protein, form::onProtein, Modifier.weight(1f))
                NumberField("Fat (g)", form.fat, form::onFat, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Carbs (g)", form.carbs, form::onCarbs, Modifier.weight(1f))
                NumberField("Fiber (g)", form.fiber, form::onFiber, Modifier.weight(1f))
            }
        }
        Text(
            "Amount defaults to one serving if left blank. Add it, then set the portion or flip it to a drink in your meal below.",
            style = MaterialTheme.typography.labelSmall, color = CreamFaint
        )
        Button(
            onClick = { onAdd(form.buildIngredient()); form.reset() },
            modifier = Modifier.fillMaxWidth(),
            enabled = form.canAdd
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add to meal")
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.bodySmall, color = CreamMuted) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

/** Copy a picked image Uri into a cache file so the label reader can decode it off a path. */
internal fun copyLabelToCache(context: Context, uri: Uri): String? = runCatching {
    val dest = java.io.File(context.cacheDir, "label_${System.currentTimeMillis()}.jpg")
    (context.contentResolver.openInputStream(uri) ?: return null).use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
    }
    dest.absolutePath
}.getOrNull()

/** Per-100 g values from a snapped label, so the amount field can rescale them to what you ate. */
private data class Per100(val calories: Float, val protein: Float, val fat: Float, val carbs: Float, val fiber: Float)

/**
 * The Custom tab's form state. Snapping a label sets a per-100 g basis so changing the amount
 * rescales the values ("enter the weight, the fields fill in"); hand-editing any value clears the
 * basis, so a typed number is never overwritten. (This mirrors the standalone Custom food screen's
 * value logic; kept here so the tab is self-contained.)
 */
class CustomEntryState {
    var name by mutableStateOf("")
    var amount by mutableStateOf("")
    var calories by mutableStateOf("")
    var protein by mutableStateOf("")
    var fat by mutableStateOf("")
    var carbs by mutableStateOf("")
    var fiber by mutableStateOf("")
    var isReadingLabel by mutableStateOf(false)
    private var basis: Per100? = null

    val canAdd: Boolean get() = name.isNotBlank() && (calories.toFloatOrNull() ?: 0f) > 0f

    fun onName(v: String) { name = v }

    /** Changing the amount rescales snapped-label values; typed-in values (no basis) just change. */
    fun onAmount(v: String) {
        val a = v.decimals(); amount = a
        val b = basis; val g = a.toFloatOrNull()
        if (b != null && g != null && g > 0f) {
            calories = clean(b.calories * g / 100f)
            protein = clean(b.protein * g / 100f)
            fat = clean(b.fat * g / 100f)
            carbs = clean(b.carbs * g / 100f)
            fiber = clean(b.fiber * g / 100f)
        }
    }

    // Hand-editing a value makes it exact: drop the label basis so the amount field stops rescaling it.
    fun onCalories(v: String) { calories = v.decimals(); basis = null }
    fun onProtein(v: String) { protein = v.decimals(); basis = null }
    fun onFat(v: String) { fat = v.decimals(); basis = null }
    fun onCarbs(v: String) { carbs = v.decimals(); basis = null }
    fun onFiber(v: String) { fiber = v.decimals(); basis = null }

    fun fillFromLabel(food: DetectedFood) {
        val serving = food.totalGrams.takeIf { it > 0f } ?: return
        basis = Per100(
            calories = food.totalCalories / serving * 100f,
            protein = food.totalProtein / serving * 100f,
            fat = food.totalFat / serving * 100f,
            carbs = food.totalCarbs / serving * 100f,
            fiber = food.totalFiber / serving * 100f
        )
        name = food.label.ifBlank { name }
        amount = clean(serving)
        calories = clean(food.totalCalories)
        protein = clean(food.totalProtein)
        fat = clean(food.totalFat)
        carbs = clean(food.totalCarbs)
        fiber = clean(food.totalFiber)
    }

    /** Values are for the amount eaten; store per-100 g so a later portion edit scales correctly. */
    fun buildIngredient(): Ingredient {
        val grams = amount.toFloatOrNull()?.takeIf { it > 0f } ?: 100f
        fun per100(v: String) = (v.toFloatOrNull() ?: 0f) / grams * 100f
        return Ingredient(
            name = name.trim(),
            grams = grams,
            caloriesPer100g = per100(calories),
            proteinPer100g = per100(protein),
            fatPer100g = per100(fat),
            carbsPer100g = per100(carbs),
            fiberPer100g = per100(fiber)
        )
    }

    fun reset() {
        name = ""; amount = ""; calories = ""; protein = ""; fat = ""; carbs = ""; fiber = ""
        basis = null
    }

    private fun String.decimals() = filter { it.isDigit() || it == '.' }

    private fun clean(v: Float): String {
        if (v <= 0f) return ""
        val r = kotlin.math.round(v * 10f) / 10f
        return if (r == r.toLong().toFloat()) r.toLong().toString() else r.toString()
    }
}
