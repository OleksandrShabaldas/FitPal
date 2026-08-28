package com.fitpal.app.ui.component

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.ui.theme.GoldLight

/** The three ways to add an ingredient to a meal, shown as tabs in the "Add ingredient" popup. */
enum class AddIngredientTab { DATABASE, CUSTOM, BARCODE }

/**
 * The "Add ingredient" popup, with three tabs for the three ways to add one food to a meal:
 *
 *  - **Database** — search the food database (with an "Add with AI" escape hatch), as before.
 *  - **Custom** — type your own values, or snap a nutrition label.
 *  - **Barcode** — scan a packaged product.
 *
 * The two camera flows (barcode scan, label snap) are full-screen, so the *screen* owns them and this
 * dialog only fires [onScanBarcode] / [onSnapLabel]; [customForm] and [selectedTab] are hoisted so the
 * form and tab survive the dialog closing while a camera is open, then reopening.
 */
@Composable
fun AddIngredientTabsDialog(
    selectedTab: AddIngredientTab,
    onTabChange: (AddIngredientTab) -> Unit,
    // Database tab
    query: String,
    results: List<UsdaFoodEntity>,
    isDrink: Boolean,
    aiLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onPick: (UsdaFoodEntity) -> Unit,
    onAiAdd: (String) -> Unit,
    // Custom tab
    customForm: CustomEntryState,
    onAddCustom: (Ingredient) -> Unit,
    onSnapLabel: () -> Unit,
    // Barcode tab
    barcodeLookingUp: Boolean,
    onScanBarcode: () -> Unit,
    // Common
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ingredient") },
        text = {
            Column {
                SegmentedPills(
                    labels = listOf("Database", "Custom", "Barcode"),
                    selectedIndex = selectedTab.ordinal,
                    accent = GoldLight,
                    onSelect = { onTabChange(AddIngredientTab.entries[it]) }
                )
                Spacer(Modifier.height(12.dp))
                when (selectedTab) {
                    AddIngredientTab.DATABASE -> DatabaseTab(query, results, isDrink, aiLoading, onQueryChange, onPick, onAiAdd)
                    AddIngredientTab.CUSTOM -> CustomTab(customForm, onAddCustom, onSnapLabel)
                    AddIngredientTab.BARCODE -> BarcodeTab(barcodeLookingUp, onScanBarcode)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun DatabaseTab(
    query: String,
    results: List<UsdaFoodEntity>,
    isDrink: Boolean,
    aiLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onPick: (UsdaFoodEntity) -> Unit,
    onAiAdd: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search the database") },
            singleLine = true
        )
        // AI escape hatch — describe an ingredient that isn't in the database.
        Spacer(Modifier.height(8.dp))
        when {
            aiLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Asking the AI…", style = MaterialTheme.typography.bodyMedium)
            }
            query.isNotBlank() -> TextButton(onClick = { onAiAdd(query) }) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add \"${query.take(24)}\" with AI")
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
            items(results, key = { it.fdcId }) { food ->
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(food) }.padding(vertical = 8.dp)
                ) {
                    Text(food.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${food.caloriesPer100g.toInt()} kcal / 100${if (isDrink) "ml" else "g"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomTab(form: CustomEntryState, onAdd: (Ingredient) -> Unit, onSnapLabel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onSnapLabel, modifier = Modifier.fillMaxWidth(), enabled = !form.isReadingLabel) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (form.isReadingLabel) "Reading…" else "Snap label")
        }
        OutlinedTextField(
            value = form.name, onValueChange = form::onName, modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") }, placeholder = { Text("e.g. Horalka") }, singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Amount (g/ml)", form.amount, form::onAmount, Modifier.weight(1f))
            NumberField("Calories", form.calories, form::onCalories, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Protein", form.protein, form::onProtein, Modifier.weight(1f))
            NumberField("Fat", form.fat, form::onFat, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Carbs", form.carbs, form::onCarbs, Modifier.weight(1f))
            NumberField("Fiber", form.fiber, form::onFiber, Modifier.weight(1f))
        }
        Button(onClick = { onAdd(form.buildIngredient()); form.reset() }, modifier = Modifier.fillMaxWidth(), enabled = form.canAdd) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add to meal")
        }
    }
}

@Composable
private fun BarcodeTab(lookingUp: Boolean, onScan: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Scan a packaged product's barcode to add it to this meal.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (lookingUp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Looking up product…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan a barcode")
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}

/** Copy a picked image Uri into a cache file so the label reader can decode it off a path. */
fun copyLabelToCache(context: Context, uri: Uri): String? = runCatching {
    val dest = java.io.File(context.cacheDir, "label_${System.currentTimeMillis()}.jpg")
    (context.contentResolver.openInputStream(uri) ?: return null).use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
    }
    dest.absolutePath
}.getOrNull()

/** Per-100 g values from a snapped label, so the amount field can rescale them to what you ate. */
private data class Per100(val calories: Float, val protein: Float, val fat: Float, val carbs: Float, val fiber: Float)

/**
 * The Custom tab's form state. Snapping a label sets a per-100 g basis so changing the amount rescales
 * the values ("enter the weight, the fields fill in"); hand-editing any value clears the basis, so a
 * typed number is never overwritten.
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
