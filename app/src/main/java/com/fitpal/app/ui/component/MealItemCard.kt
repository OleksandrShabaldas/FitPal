package com.fitpal.app.ui.component

import com.fitpal.app.ui.component.kit.aiGlow
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.fitpal.app.domain.model.ChoiceGroup
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.ServingPreset
import com.fitpal.app.domain.model.weightLabel
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
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
    /** Number of servings (the amount stepper); 1 when it doesn't apply. */
    val servings: Int,
    val ingredients: List<Ingredient>,
    /** What the AI couldn't tell, as choices that replace an ingredient (pre-log only). */
    val choices: List<ChoiceGroup> = emptyList()
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
    ingredients = drinkAwareIngredients,
    choices = choices
)

/**
 * The one card that renders a dish everywhere it appears — the photo review, the describe review and
 * the Meal page (docs/redesign/16). Every action is an optional slot: pass the callbacks a screen
 * supports and the matching controls appear.
 *
 * Open by default (the user folds what they like). Its header is the dish's name, kcal, a ⋯ with the
 * dish's own actions and the fold chevron; a meal's only dish ([showName] false) is just "What's in
 * it", since its name is the page's title and its numbers are in the nutrition card above. Each
 * ingredient row has its own food/drink cup.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealItemCard(
    content: MealItemContent,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    onIngredientGramsChanged: (index: Int, grams: Float) -> Unit = { _, _ -> },
    onIngredientRemoved: (index: Int) -> Unit = {},
    /** The cup on an ingredient: [drink] = it becomes a drink (ml, counts as drinks), else food. */
    onIngredientDrinkChanged: ((index: Int, drink: Boolean) -> Unit)? = null,
    /** Tap an ingredient's name to swap it for another, keeping the amount (a logged meal). */
    onReplaceIngredient: ((index: Int) -> Unit)? = null,
    onTotalGramsChanged: ((grams: Float) -> Unit)? = null,
    onServingsChanged: ((servings: Int) -> Unit)? = null,
    onWaterChanged: ((ml: Float) -> Unit)? = null,
    onAddIngredient: (() -> Unit)? = null,
    onEditWithAi: (() -> Unit)? = null,
    /** "Not sure about": put option [option] of choice [group] in the dish (pre-log only). */
    onPickChoice: ((group: Int, option: Int) -> Unit)? = null,
    onRename: ((String) -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onToggleSave: (() -> Unit)? = null,
    saved: Boolean = false,
    /** Copy just this dish to another day (a logged meal with several dishes). */
    onCopy: (() -> Unit)? = null,
    mealPresets: List<ServingPreset> = emptyList(),
    drinkPresets: List<ServingPreset> = emptyList(),
    /** Folded: name · kcal · one line of what's in it; tap → [onExpand], which also folds it again. */
    collapsed: Boolean = false,
    onExpand: (() -> Unit)? = null,
    /** Other likely names for this dish ("Not fish & chips? Fish tacos · Fish burger"), and the pick. */
    alternatives: List<String> = emptyList(),
    onPickAlternative: ((Int) -> Unit)? = null
) {
    var renaming by remember { mutableStateOf(false) }
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
                itemSummary(content), style = MaterialTheme.typography.bodySmall, color = CreamMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            AlternativesLine(content.name, alternatives, onPickAlternative)
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth().glass().padding(16.dp)) {
        // Header — the dish's name, kcal, its ⋯ and the fold chevron.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showName) {
                Text(
                    content.name, style = MaterialTheme.typography.titleMedium, color = Cream,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("${content.calories.toInt()} kcal", style = MaterialTheme.typography.titleSmall, color = CalorieColor)
            } else {
                Text("What's in it", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f))
            }
            DishMenu(
                name = content.name,
                onRename = if (showName && onRename != null) ({ renaming = true }) else null,
                onToggleSave = onToggleSave, saved = saved, onCopy = onCopy, onRemove = onRemove
            )
            if (onExpand != null) CardIcon(Icons.Default.KeyboardArrowUp, "Fold ${content.name}", CreamMuted, onExpand)
        }
        AlternativesLine(content.name, alternatives, onPickAlternative)

        // Its weight ("320 g", "170 g + 200 ml") and how many of it. A meal's only dish has its weight in
        // the nutrition card above, so its row is just the amount.
        if (showName || onServingsChanged != null) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (showName) weightLabel(content.ingredients, content.grams, content.isDrink) else "Amount",
                style = if (showName) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall,
                color = if (showName) CreamMuted else Cream, modifier = Modifier.weight(1f)
            )
            if (onServingsChanged != null) {
                RoundStepButton(Icons.Default.Remove, "One fewer", enabled = content.servings > 1) { onServingsChanged(content.servings - 1) }
                Text(
                    "${content.servings}",
                    style = MaterialTheme.typography.titleMedium, color = Cream,
                    textAlign = TextAlign.Center, modifier = Modifier.width(36.dp)
                )
                RoundStepButton(Icons.Default.Add, "One more") { onServingsChanged(content.servings + 1) }
            }
        }
        Spacer(Modifier.height(6.dp))

        // Presets only make sense for a single-item food/drink (under a dish's ingredients they'd be noise).
        val single = content.ingredients.size == 1
        content.ingredients.forEachIndexed { index, ingredient ->
            EditableFoodItemRow(
                ingredient = ingredient,
                onGramsChanged = { onIngredientGramsChanged(index, it) },
                onRemove = { onIngredientRemoved(index) },
                presets = if (single) (if (ingredient.isDrink) drinkPresets else mealPresets) else emptyList(),
                unit = if (ingredient.isDrink) "ml" else "g",
                // Edits may hit the database (the Meal page) — don't write a phantom 0 on clear.
                commitZero = false,
                onToggleDrink = onIngredientDrinkChanged?.let { change -> { change(index, !ingredient.isDrink) } },
                onReplace = onReplaceIngredient?.let { replace -> { replace(index) } }
            )
        }

        // Not sure about — pre-log only (docs/redesign/15): a part of the dish the AI couldn't tell,
        // one row each; a chip swaps that ingredient in place, nothing is ever added. A row whose
        // ingredient was removed or renamed since goes away.
        if (content.choices.isNotEmpty() && onPickChoice != null) {
            val shown = content.choices.withIndex().filter { (_, g) ->
                content.ingredients.any { it.name.equals(g.current?.name, ignoreCase = true) }
            }
            if (shown.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Not sure about", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(bottom = 6.dp))
                shown.forEach { (g, group) ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            group.label, style = MaterialTheme.typography.labelMedium, color = CreamFaint, maxLines = 2,
                            modifier = Modifier.width(68.dp).padding(top = 5.dp, end = 6.dp)
                        )
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            group.options.forEachIndexed { o, option ->
                                ChoiceChip(option.name, selected = o == group.selected) { onPickChoice(g, o) }
                            }
                        }
                    }
                }
            }
        }

        if (onAddIngredient != null || onEditWithAi != null) {
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onAddIngredient != null) SetupPill("+ Add ingredient", selected = false, onClick = onAddIngredient)
                if (onEditWithAi != null) SetupPill("✦ Edit with AI", selected = false, modifier = Modifier.aiGlow(), onClick = onEditWithAi)
            }
        }

        // The whole dish's weight (scales every ingredient) and the water the AI found, side by side.
        val total = content.ingredients.size > 1 && onTotalGramsChanged != null
        if (total || onWaterChanged != null) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (total) {
                    Text("Total", style = MaterialTheme.typography.labelLarge, color = CreamMuted)
                    Spacer(Modifier.width(8.dp))
                    GramsField(
                        grams = content.grams, onGramsChanged = onTotalGramsChanged!!, modifier = Modifier.width(100.dp),
                        unit = if (content.ingredients.all { it.isDrink }) "ml" else "g"
                    )
                }
                Spacer(Modifier.weight(1f))
                if (onWaterChanged != null) {
                    Text("💧", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    GramsField(grams = content.waterMl, onGramsChanged = onWaterChanged, modifier = Modifier.width(100.dp), unit = "ml", commitZero = true)
                }
            }
        }
    }
}

/** The dish's own actions behind a ⋯: rename, save to collection, copy, remove. Nothing to show → no ⋯. */
@Composable
private fun DishMenu(
    name: String,
    onRename: (() -> Unit)?,
    onToggleSave: (() -> Unit)?,
    saved: Boolean,
    onCopy: (() -> Unit)?,
    onRemove: (() -> Unit)?
) {
    val items = buildList<Pair<String, () -> Unit>> {
        onRename?.let { add("Rename" to it) }
        onToggleSave?.let { add((if (saved) "Saved to collection ✓" else "Save to collection") to it) }
        onCopy?.let { add("Copy to another day" to it) }
        onRemove?.let { add("Remove" to it) }
    }
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        CardIcon(Icons.Default.MoreHoriz, "More for $name", CreamMuted) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            }
        }
    }
}

/** The folded card's one line: what's in it ("Cod in batter · fries · tartar sauce"), or its amount. */
private fun itemSummary(content: MealItemContent): String = when {
    content.ingredients.size > 1 -> content.ingredients.joinToString(" · ") { it.name }
    else -> weightLabel(content.ingredients, content.grams, content.isDrink) + if (content.isDrink) " · drink" else ""
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
                name, style = MaterialTheme.typography.bodySmall, color = com.fitpal.app.ui.theme.GoldLight, maxLines = 1,
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
        Modifier.size(34.dp).glassSoft(CircleShape).then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) Cream else CreamMuted, modifier = Modifier.size(18.dp))
    }
}
