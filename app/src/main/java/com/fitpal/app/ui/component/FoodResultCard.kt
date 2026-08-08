package com.fitpal.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.ServingPreset
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass

/**
 * A whole-food result card: name + live total, each ingredient directly editable
 * (with quick-tap presets for single-item foods/drinks), and selectable
 * variations. Used by Describe-to-AI.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodResultCard(
    food: DetectedFood,
    onIngredientGramsChanged: (ingredientIndex: Int, grams: Float) -> Unit,
    onIngredientRemoved: (ingredientIndex: Int) -> Unit,
    onToggleVariation: (variationIndex: Int) -> Unit,
    onRemove: () -> Unit,
    onTotalGramsChanged: (newTotalGrams: Float) -> Unit = {},
    onAddIngredient: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    mealPresets: List<ServingPreset> = emptyList(),
    drinkPresets: List<ServingPreset> = emptyList(),
    onSave: (() -> Unit)? = null,
    /** When true, the save button shows a filled bookmark to confirm it's in the collection. */
    saved: Boolean = false,
    /** When given, the name is tappable and can be rewritten before the meal is logged. */
    onRename: ((String) -> Unit)? = null
) {
    // Drinks are measured in millilitres and tap the drink presets; meals use grams.
    val unit = if (food.isDrink) "ml" else "g"
    val activePresets = if (food.isDrink) drinkPresets else mealPresets
    var renaming by remember { mutableStateOf(false) }

    if (renaming && onRename != null) {
        RenameDialog(
            currentName = food.label,
            title = "Rename this dish",
            label = "Dish name",
            hint = "Only the name changes — the ingredients and amounts stay as they are.",
            onConfirm = { onRename(it); renaming = false },
            onDismiss = { renaming = false }
        )
    }

    Box(modifier = modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: name + live total (grams + calories) + remove the whole food
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (onRename != null) Modifier.clickable { renaming = true } else Modifier)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = food.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (onRename != null) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Rename ${food.label}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Text(
                        text = "${food.totalGrams.toInt()} $unit · ${food.totalCalories.toInt()} kcal" +
                            if (food.isDrink && food.totalWaterMl > 0f)
                                " · ${food.totalWaterMl.toInt()} ml water" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = CalorieColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (onSave != null) {
                    IconButton(onClick = onSave) {
                        Icon(
                            if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (saved) "${food.label} saved to collection" else "Save ${food.label} to collection",
                            tint = if (saved) GoldLight else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove ${food.label}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Whole-dish weight: scales every ingredient at once (only for real dishes).
            if (food.ingredients.size > 1) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        text = "Total weight",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    GramsField(
                        grams = food.totalGrams,
                        onGramsChanged = onTotalGramsChanged,
                        modifier = Modifier.width(110.dp),
                        unit = unit
                    )
                }
            }

            // Each ingredient is directly editable. Presets only make sense for a
            // single-item food/drink (under a dish's ingredients they'd be noise).
            val rowPresets = if (food.ingredients.size == 1) activePresets else emptyList()
            food.ingredients.forEachIndexed { index, ingredient ->
                EditableFoodItemRow(
                    ingredient = ingredient,
                    onGramsChanged = { onIngredientGramsChanged(index, it) },
                    onRemove = { onIngredientRemoved(index) },
                    presets = rowPresets,
                    unit = unit
                )
            }

            if (onAddIngredient != null) {
                TextButton(onClick = onAddIngredient) { Text("+ Add ingredient") }
            }

            // Variations — tap to add/remove
            if (food.variations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Variations — tap to add",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    food.variations.forEachIndexed { index, variation ->
                        FilterChip(
                            selected = variation.isSelected,
                            onClick = { onToggleVariation(index) },
                            label = { Text(variation.description) }
                        )
                    }
                }
            }
        }
    }
}
