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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
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
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.ServingPreset
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

/**
 * "How much of this?" — the inline editor for one food the moment it's added to a manual meal,
 * instead of a separate portion popup. It settles the same three things the old sheet did, but
 * right in the "Your meal" list so there's no extra screen and nothing has to be found again:
 *
 *  - the **portion** — one helping, in g or ml, with the usual quick-tap presets;
 *  - the **amount** — how many of those helpings (two cans is one stepper tap, not a second search);
 *  - **food or drink** — the database only guesses this from the name, and it decides the unit,
 *    which presets show, and whether the item counts toward the day's water.
 *
 * [base] carries one helping (its `grams` is the portion, not the total); the caller owns the state
 * and reads back the multiplied item. [onRemove] takes the item back out of the meal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodPortionEditor(
    base: Ingredient,
    count: Int,
    mealPresets: List<ServingPreset>,
    drinkPresets: List<ServingPreset>,
    onPortionChange: (Float) -> Unit,
    onCountChange: (Int) -> Unit,
    onDrinkChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onSave: (() -> Unit)? = null,
    /** When true, the save button shows a filled bookmark to confirm it's in the collection. */
    saved: Boolean = false,
    /** Plenty for "12 eggs", low enough that a stuck finger can't log a month of food. */
    maxCount: Int = 99
) {
    val unit = if (base.isDrink) "ml" else "g"
    val accent = if (base.isDrink) AccentTrends else GoldLight
    val presets = if (base.isDrink) drinkPresets else mealPresets
    val step = if (base.isDrink) 25f else 10f
    val total = base.withGrams(base.grams * count)

    Column(modifier = modifier.fillMaxWidth().glass().padding(16.dp)) {
        // Header: name (tap to rename — database foods arrive as "Cheese, cheddar, sharp"), save, remove.
        Row(verticalAlignment = Alignment.CenterVertically) {
            var editingName by remember { mutableStateOf(false) }
            if (editingName) {
                var nameText by remember { mutableStateOf(base.name) }
                OutlinedTextField(
                    value = nameText,
                    // Keep the last real name if the field is emptied — a nameless food helps nobody.
                    onValueChange = { nameText = it; if (it.isNotBlank()) onNameChange(it) },
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(1f).clickable { editingName = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = base.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Cream,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Edit, contentDescription = "Rename ${base.name}", tint = CreamMuted, modifier = Modifier.size(15.dp))
                        }
                        Text(
                            text = "${base.caloriesPer100g.toInt()} kcal per 100 $unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = CreamMuted
                        )
                    }
                }
                if (onSave != null) {
                    IconButton(onClick = onSave) {
                        Icon(
                            if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (saved) "${base.name} saved to collection" else "Save ${base.name} to collection",
                            tint = if (saved) GoldLight else CreamMuted
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${base.name}", tint = CreamMuted)
                }
            }
        }

        // The database guesses food-vs-drink from the name, and it gets things like "cream" or
        // "protein shake" wrong — so the guess is only ever the starting position.
        Spacer(Modifier.height(14.dp))
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
            GramsField(grams = base.grams, onGramsChanged = onPortionChange, modifier = Modifier.width(104.dp), unit = unit, commitZero = true)
            StepButton(icon = Icons.Default.Add, description = "Bigger portion", enabled = true, onClick = { onPortionChange(base.grams + step) })
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
            StepButton(icon = Icons.Default.Remove, description = "One fewer", enabled = count > 1, onClick = { onCountChange(count - 1) })
            Text(
                text = "×$count",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(104.dp)
            )
            StepButton(icon = Icons.Default.Add, description = "One more", enabled = count < maxCount, onClick = { onCountChange(count + 1) })
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
                Text(text = "${total.grams.toInt()} $unit", style = MaterialTheme.typography.bodyLarge, color = Cream)
            }
            if (count > 1) {
                Text(text = "$count × ${base.grams.toInt()} $unit", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
            }
            Spacer(Modifier.height(10.dp))
            MacroBar(protein = total.protein, fat = total.fat, carbs = total.carbs, fiber = total.fiber)
            if (base.isDrink && total.waterMl > 0f) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
