package com.fitpal.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.ServingPreset
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glassOverlay
import com.fitpal.app.ui.theme.glassSoft

/**
 * "How much of this?" — the panel that opens the moment a database food is picked, instead of
 * quietly dropping a default helping into the meal. It settles three things in one place:
 *
 *  - the **portion** — one helping, in g or ml, with the usual quick-tap presets;
 *  - the **amount** — how many of those helpings, so two cans is one stepper tap rather than
 *    hunting the same food twice;
 *  - **food or drink** — the database only guesses this from the name, and it decides the unit,
 *    which presets show, and whether the item counts toward the day's water.
 *
 * [base] carries one helping (its `grams` is the portion, not the total); the caller owns the
 * state and gets the multiplied item back on confirm.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodPortionSheet(
    base: Ingredient,
    count: Int,
    mealPresets: List<ServingPreset>,
    drinkPresets: List<ServingPreset>,
    onPortionChange: (Float) -> Unit,
    onCountChange: (Int) -> Unit,
    onDrinkChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Add to meal",
    /** Plenty for "12 eggs", low enough that a stuck finger can't log a month of food. */
    maxCount: Int = 99
) {
    val unit = if (base.isDrink) "ml" else "g"
    val accent = if (base.isDrink) AccentTrends else GoldLight
    val presets = if (base.isDrink) drinkPresets else mealPresets
    val step = if (base.isDrink) 25f else 10f
    val total = base.withGrams(base.grams * count)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassOverlay()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // The name is editable here because database foods arrive under catalogue names
            // ("Cheese, cheddar, sharp"). Tap it to write what you'd actually call it — this is
            // the one point every database pick passes through, so it never has to be fixed later.
            var editingName by remember { mutableStateOf(false) }
            if (editingName) {
                var nameText by remember { mutableStateOf(base.name) }
                OutlinedTextField(
                    value = nameText,
                    // Keep the last real name if the field is emptied — a nameless food helps nobody.
                    onValueChange = { nameText = it; if (it.isNotBlank()) onNameChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { editingName = false }) {
                            Icon(Icons.Default.Check, contentDescription = "Done renaming", tint = accent)
                        }
                    }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { editingName = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = base.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Cream,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename ${base.name}",
                        tint = CreamMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${base.caloriesPer100g.toInt()} kcal per 100 $unit",
                style = MaterialTheme.typography.labelMedium,
                color = CreamMuted
            )

            // The database guesses food-vs-drink from the name, and it gets things like "cream"
            // or "protein shake" wrong — so the guess is only ever the starting position.
            Spacer(Modifier.height(16.dp))
            SegmentedPills(
                labels = listOf("Food", "Drink"),
                selectedIndex = if (base.isDrink) 1 else 0,
                accent = accent,
                onSelect = { onDrinkChange(it == 1) }
            )

            Spacer(Modifier.height(16.dp))
            SheetLabel("Portion", "one helping")
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton(
                    icon = Icons.Default.Remove,
                    description = "Smaller portion",
                    enabled = base.grams > 0f,
                    onClick = { onPortionChange((base.grams - step).coerceAtLeast(0f)) }
                )
                GramsField(
                    grams = base.grams,
                    onGramsChanged = onPortionChange,
                    modifier = Modifier.width(104.dp),
                    unit = unit,
                    commitZero = true
                )
                StepButton(
                    icon = Icons.Default.Add,
                    description = "Bigger portion",
                    enabled = true,
                    onClick = { onPortionChange(base.grams + step) }
                )
            }
            if (presets.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { preset ->
                        SuggestionChip(
                            onClick = { onPortionChange(preset.grams) },
                            label = { Text("${preset.label} · ${preset.grams.toInt()} $unit") }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SheetLabel("Amount", "how many of them")
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton(
                    icon = Icons.Default.Remove,
                    description = "One fewer",
                    enabled = count > 1,
                    onClick = { onCountChange(count - 1) }
                )
                Text(
                    text = "×$count",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(104.dp)
                )
                StepButton(
                    icon = Icons.Default.Add,
                    description = "One more",
                    enabled = count < maxCount,
                    onClick = { onCountChange(count + 1) }
                )
            }

            // What actually lands in the meal, so the multiplication is never a surprise.
            Spacer(Modifier.height(16.dp))
            Column(modifier = Modifier.fillMaxWidth().glassSoft().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${total.calories.toInt()} kcal",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CalorieColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${total.grams.toInt()} $unit",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Cream
                    )
                }
                if (count > 1) {
                    Text(
                        text = "$count × ${base.grams.toInt()} $unit",
                        style = MaterialTheme.typography.labelMedium,
                        color = CreamMuted
                    )
                }
                Spacer(Modifier.height(10.dp))
                MacroBar(
                    protein = total.protein,
                    fat = total.fat,
                    carbs = total.carbs,
                    fiber = total.fiber
                )
                if (base.isDrink && total.waterMl > 0f) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Adds to water", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                        Text(
                            text = "+${total.waterMl.toInt()} ml",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentTrends
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = total.grams > 0f
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(confirmLabel)
                }
            }
        }
    }
}

/** Two-part section heading: the name, then a quiet word on what the number means. */
@Composable
private fun SheetLabel(title: String, hint: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Cream)
        Spacer(Modifier.width(6.dp))
        Text(hint, style = MaterialTheme.typography.labelSmall, color = CreamFaint)
    }
}
