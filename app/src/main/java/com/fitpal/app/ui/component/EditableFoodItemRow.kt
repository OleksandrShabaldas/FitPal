package com.fitpal.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.ServingPreset
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.GoldLight

/**
 * The one amount (g/ml) input used everywhere a food's weight is edited live. It keeps its own
 * text state so that:
 *  - clearing the field shows EMPTY, never a phantom "0" that later collides with typed digits
 *    (the old "type 50, get 500" bug);
 *  - leading zeros are stripped as you type ("050" → "50");
 *  - the text is only reset from [grams] when the value genuinely changed elsewhere (a preset tap,
 *    a whole-meal rescale) — never mid-typing, so the cursor can't jump.
 *
 * [commitZero] controls what an emptied field reports: true → commit 0 (live totals drop to 0
 * while cleared), false → report nothing until a positive number is typed.
 */
@Composable
fun GramsField(
    grams: Float,
    onGramsChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    unit: String = "g",
    commitZero: Boolean = false
) {
    var text by remember { mutableStateOf(formatAmount(grams)) }
    LaunchedEffect(grams) {
        val shown = text.toFloatOrNull() ?: 0f
        if (shown.toInt() != grams.toInt()) text = formatAmount(grams)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            var s = raw.filter { it.isDigit() || it == '.' }
            // Keep only the first decimal point, then strip leading zeros before a digit.
            val dot = s.indexOf('.')
            if (dot >= 0) s = s.substring(0, dot + 1) + s.substring(dot + 1).replace(".", "")
            s = s.replace(Regex("^0+(?=\\d)"), "")
            text = s
            val parsed = s.toFloatOrNull()
            when {
                parsed != null && parsed > 0f -> onGramsChanged(parsed)
                commitZero -> onGramsChanged(0f)
            }
        },
        modifier = modifier,
        suffix = { Text(unit) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

private fun formatAmount(v: Float): String = if (v <= 0f) "" else v.toInt().toString()

/**
 * A single editable food item: name, an editable grams field, live calorie count,
 * a remove button, and optional quick-tap serving-size presets. Shared by Manual
 * Entry, Describe-to-AI, and Barcode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditableFoodItemRow(
    ingredient: Ingredient,
    onGramsChanged: (Float) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    presets: List<ServingPreset> = emptyList(),
    unit: String = "g",
    onSave: (() -> Unit)? = null,
    /** When true, the save button shows a filled bookmark to confirm it's in the collection. */
    saved: Boolean = false,
    /** When given, the name is tappable and can be rewritten before the meal is logged. */
    onRename: ((String) -> Unit)? = null
) {
    var renaming by remember { mutableStateOf(false) }
    if (renaming && onRename != null) {
        RenameDialog(
            currentName = ingredient.name,
            title = "Rename this item",
            hint = "Only the name changes — the amount and nutrition stay as they are.",
            onConfirm = { onRename(it); renaming = false },
            onDismiss = { renaming = false }
        )
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Name + live calories
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (onRename != null) Modifier.clickable { renaming = true } else Modifier)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ingredient.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (onRename != null) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename ${ingredient.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    text = "${ingredient.calories.toInt()} kcal",
                    style = MaterialTheme.typography.labelMedium,
                    color = CalorieColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Step the amount without opening the keyboard — the common case is nudging,
            // not typing an exact number.
            val step = if (unit == "ml") 25f else 10f
            StepButton(
                icon = Icons.Default.Remove,
                description = "Less ${ingredient.name}",
                enabled = ingredient.grams > 0f,
                onClick = { onGramsChanged((ingredient.grams - step).coerceAtLeast(0f)) }
            )

            // Editable grams — clears to empty (not "0"), commits live.
            GramsField(
                grams = ingredient.grams,
                onGramsChanged = onGramsChanged,
                modifier = Modifier.width(88.dp),
                unit = unit,
                commitZero = true
            )

            StepButton(
                icon = Icons.Default.Add,
                description = "More ${ingredient.name}",
                enabled = true,
                onClick = { onGramsChanged(ingredient.grams + step) }
            )

            if (onSave != null) {
                IconButton(onClick = onSave) {
                    Icon(
                        if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (saved) "${ingredient.name} saved to collection" else "Save ${ingredient.name} to collection",
                        tint = if (saved) GoldLight else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove ${ingredient.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Quick-tap serving presets
        if (presets.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { preset ->
                    SuggestionChip(
                        onClick = { onGramsChanged(preset.grams) },
                        label = { Text("${preset.label} · ${preset.grams.toInt()} $unit") }
                    )
                }
            }
        }
    }
}

/** A small round +/− next to the amount field. Shared with [FoodPortionSheet]. */
@Composable
internal fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) GoldLight else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

/**
 * Running total row shown above the Log Meal button.
 */
@Composable
fun MealTotalRow(
    totalCalories: Float,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (itemCount == 1) "1 item" else "$itemCount items",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${totalCalories.toInt()} kcal",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = CalorieColor
        )
    }
}
