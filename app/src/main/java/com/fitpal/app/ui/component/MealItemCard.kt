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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.FoodVariation
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.ServingPreset
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

/**
 * Neutral content for one food/dish, so the same card renders a pre-log draft (`DetectedFood`) or a
 * saved item (`MealLogItemEntity`). The screens differ (draft vs. database, different actions), but
 * the card doesn't have to.
 */
data class MealItemContent(
    val name: String,
    val grams: Float,
    val calories: Float,
    val protein: Float,
    val fat: Float,
    val carbs: Float,
    val fiber: Float,
    val isDrink: Boolean,
    val waterMl: Float,
    /** Number of servings (pre-log photo only); 1 when it doesn't apply. */
    val servings: Int,
    val ingredients: List<Ingredient>,
    val variations: List<FoodVariation> = emptyList()
)

/** Adapter from the pre-log draft model. */
fun DetectedFood.toMealItemContent(): MealItemContent = MealItemContent(
    name = label,
    grams = totalGrams,
    calories = totalCalories,
    protein = totalProtein,
    fat = totalFat,
    carbs = totalCarbs,
    fiber = totalFiber,
    isDrink = isDrink,
    waterMl = totalWaterMl,
    servings = servings,
    ingredients = ingredients,
    variations = variations
)

/**
 * The one card that renders a food/dish everywhere it appears — the photo review, the describe
 * review, and the saved-meal screen. Every action is an optional slot: pass the callbacks a given
 * screen supports and the matching controls appear. The shared skeleton (name, macros, total
 * weight, ingredient rows) is identical in all three, so "before logging" and "after logging"
 * finally look the same and live in one file.
 *
 * It owns the per-item rename dialog (via [onRename]); everything else is wired by the caller to
 * either draft edits or database writes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealItemCard(
    content: MealItemContent,
    modifier: Modifier = Modifier,
    onIngredientGramsChanged: (index: Int, grams: Float) -> Unit = { _, _ -> },
    onIngredientRemoved: (index: Int) -> Unit = {},
    onTotalGramsChanged: ((grams: Float) -> Unit)? = null,
    onServingsChanged: ((servings: Int) -> Unit)? = null,
    onWaterChanged: ((ml: Float) -> Unit)? = null,
    onAddIngredient: (() -> Unit)? = null,
    onEditWithAi: (() -> Unit)? = null,
    onToggleVariation: ((index: Int) -> Unit)? = null,
    onRename: ((String) -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onToggleSave: (() -> Unit)? = null,
    saved: Boolean = false,
    onOpenDetails: (() -> Unit)? = null,
    /** The Food / Drink switch — when the AI (or a database name) got it wrong. */
    onSetDrink: ((Boolean) -> Unit)? = null,
    mealPresets: List<ServingPreset> = emptyList(),
    drinkPresets: List<ServingPreset> = emptyList(),
    /**
     * Folded (docs/redesign/08): name · kcal · one line of what's in it; tap → [onExpand]. The screen
     * keeps one dish open at a time. [onExpand] also gets a chevron on the open card to fold it again.
     */
    collapsed: Boolean = false,
    onExpand: (() -> Unit)? = null,
    /** Other likely names for this dish ("Not fish & chips? Fish tacos · Fish burger"), and the pick. */
    alternatives: List<String> = emptyList(),
    onPickAlternative: ((Int) -> Unit)? = null
) {
    val unit = if (content.isDrink) "ml" else "g"
    var renaming by remember { mutableStateOf(false) }

    if (collapsed) {
        Column(
            modifier = modifier.fillMaxWidth().glass()
                .then(if (onExpand != null) Modifier.clickable(onClick = onExpand) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    content.name, style = MaterialTheme.typography.titleMedium, color = Cream,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("${content.calories.toInt()} kcal", style = MaterialTheme.typography.titleSmall, color = CalorieColor)
                if (onExpand != null) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Open ${content.name}", tint = CreamMuted, modifier = Modifier.padding(start = 4.dp).size(20.dp))
                }
            }
            Text(
                itemSummary(content, unit), style = MaterialTheme.typography.bodySmall, color = CreamMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            AlternativesLine(content.name, alternatives, onPickAlternative)
        }
        return
    }

    if (renaming && onRename != null) {
        RenameDialog(
            currentName = content.name,
            title = "Rename this dish",
            label = "Dish name",
            hint = "Only the name changes — the amount and nutrition stay as they are.",
            onConfirm = { onRename(it); renaming = false },
            onDismiss = { renaming = false }
        )
    }

    Column(modifier = modifier.fillMaxWidth().glass().padding(16.dp)) {
        // Header — name (tap to rename), amount subtitle, calories, save, remove.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(if (onRename != null) Modifier.clickable { renaming = true } else Modifier)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        content.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Cream,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (onRename != null) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename ${content.name}",
                            tint = CreamMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    text = "${content.grams.toInt()} $unit" +
                        if (content.isDrink && content.waterMl > 0f) " + ${content.waterMl.toInt()} ml water" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = CreamMuted
                )
            }
            Text("${content.calories.toInt()} kcal", style = MaterialTheme.typography.titleMedium, color = CalorieColor)
            if (onToggleSave != null) {
                CardIcon(
                    if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    if (saved) "${content.name} saved to collection" else "Save ${content.name} to collection",
                    if (saved) GoldLight else CreamMuted, onToggleSave
                )
            }
            if (onRemove != null) CardIcon(Icons.Default.Close, "Remove ${content.name}", CreamMuted, onRemove)
            if (onExpand != null) CardIcon(Icons.Default.KeyboardArrowUp, "Fold ${content.name}", CreamMuted, onExpand)
        }
        AlternativesLine(content.name, alternatives, onPickAlternative)

        Spacer(Modifier.height(12.dp))
        MacroBar(protein = content.protein, fat = content.fat, carbs = content.carbs, fiber = content.fiber)
        Spacer(Modifier.height(16.dp))

        // Food or drink — a drink is measured in ml, counts toward water and the fasting drink rule.
        if (onSetDrink != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("It's a", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f))
                SegmentedPills(
                    labels = listOf("Food", "Drink"),
                    selectedIndex = if (content.isDrink) 1 else 0,
                    accent = GoldLight,
                    onSelect = { onSetDrink(it == 1) },
                    modifier = Modifier.width(170.dp)
                )
            }
        }

        // Amount (servings) — pre-log photo only.
        if (onServingsChanged != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Amount", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f))
                RoundStepButton(Icons.Default.Remove, "One fewer", enabled = content.servings > 1) { onServingsChanged(content.servings - 1) }
                Text(
                    "${content.servings}",
                    style = MaterialTheme.typography.titleMedium, color = Cream,
                    textAlign = TextAlign.Center, modifier = Modifier.width(40.dp)
                )
                RoundStepButton(Icons.Default.Add, "One more") { onServingsChanged(content.servings + 1) }
            }
        }

        // Water the AI identified — editable, counts toward hydration (pre-log photo only).
        if (onWaterChanged != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💧 Water", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f))
                GramsField(grams = content.waterMl, onGramsChanged = onWaterChanged, modifier = Modifier.width(110.dp), unit = "ml", commitZero = true)
            }
        }

        // Whole-dish weight — scales every ingredient at once (only for real multi-item dishes).
        if (content.ingredients.size > 1 && onTotalGramsChanged != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Total weight", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f))
                GramsField(grams = content.grams, onGramsChanged = onTotalGramsChanged, modifier = Modifier.width(100.dp), unit = unit)
            }
        }

        Text("Ingredients", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(bottom = 4.dp))
        // Presets only make sense for a single-item food/drink (under a dish's ingredients they'd be noise).
        val rowPresets = if (content.ingredients.size == 1) (if (content.isDrink) drinkPresets else mealPresets) else emptyList()
        content.ingredients.forEachIndexed { index, ingredient ->
            EditableFoodItemRow(
                ingredient = ingredient,
                onGramsChanged = { onIngredientGramsChanged(index, it) },
                onRemove = { onIngredientRemoved(index) },
                presets = rowPresets,
                unit = unit,
                // Edits may hit the database (saved-meal screen) — don't write a phantom 0 on clear.
                commitZero = false
            )
        }

        // Add ingredient / Edit with AI — pre-log only.
        if (onAddIngredient != null || onEditWithAi != null) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onAddIngredient != null) SetupPill("+ Add ingredient", selected = false, onClick = onAddIngredient)
                if (onEditWithAi != null) SetupPill("✦ Edit with AI", selected = false, onClick = onEditWithAi)
            }
        }

        // Variations — pre-log only.
        if (content.variations.isNotEmpty() && onToggleVariation != null) {
            Spacer(Modifier.height(12.dp))
            Text("Variations — tap to add", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content.variations.forEachIndexed { index, variation ->
                    SetupPill(variation.description, selected = variation.isSelected) { onToggleVariation(index) }
                }
            }
        }

        // Open details & AI insights — saved-meal screen only.
        if (onOpenDetails != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.glassSoft(CircleShape).clickable(onClick = onOpenDetails).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open details & AI insights", style = MaterialTheme.typography.labelLarge, color = Cream)
            }
        }
    }
}

/** The folded card's one line: what's in it ("Cod in batter · fries · tartar sauce"), or its amount. */
private fun itemSummary(content: MealItemContent, unit: String): String = when {
    content.isDrink -> "${content.grams.toInt()} ml · drink"
    content.ingredients.size > 1 -> content.ingredients.joinToString(" · ") { it.name }
    else -> "${content.grams.toInt()} $unit"
}

/** "Not {current}? alt1 · alt2" — the other likely names, each a gold link that switches to it. */
@Composable
private fun AlternativesLine(current: String, alternatives: List<String>, onPick: ((Int) -> Unit)?) {
    if (alternatives.isEmpty() || onPick == null) return
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Not ${current}? ", style = MaterialTheme.typography.bodySmall, color = CreamMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        alternatives.forEachIndexed { i, name ->
            if (i > 0) Text(" · ", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            Text(
                name, style = MaterialTheme.typography.bodySmall, color = GoldLight, maxLines = 1,
                modifier = Modifier.clickable { onPick(i) }
            )
        }
    }
}

@Composable
private fun CardIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Icon(
        icon, contentDescription = description, tint = tint,
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick).padding(9.dp)
    )
}

@Composable
private fun RoundStepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).glassSoft(CircleShape).then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) Cream else CreamMuted, modifier = Modifier.size(18.dp))
    }
}
