package com.fitpal.app.ui.screen.mealgroup

import androidx.compose.foundation.layout.padding

import com.fitpal.app.ui.theme.CreamMuted

import com.fitpal.app.ui.theme.GoldLight

import androidx.compose.material.icons.filled.RestaurantMenu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.CoachingTipCard
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MealItemCard
import com.fitpal.app.ui.component.MealItemContent
import com.fitpal.app.ui.component.RenameDialog
import com.fitpal.app.ui.component.rememberFastingGuard
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.glass
import java.time.LocalDate

/**
 * Multi-dish overview of one logged meal (a single photo/describe generation). Mirrors the
 * pre-log analysis review: every dish on one screen. Tapping a dish opens it for full editing.
 */
@Composable
fun MealGroupScreen(
    onBack: () -> Unit,
    onDishClick: (Long) -> Unit,
    onOpenRecipe: (Long) -> Unit = {},
    viewModel: MealGroupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showCopyPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameMeal by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    // One dish open at a time; all folded to start with (docs/redesign/08).
    var expanded by remember { mutableStateOf<Long?>(null) }
    // A copy onto today is eating now, so it goes through the same fasting gate as a fresh log.
    val fastingGuard = rememberFastingGuard()

    // Pop back once the whole meal is deleted (or all dishes were removed individually). Guarded
    // so it fires exactly once (delete sets the flag AND empties the flow — both would pop).
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading, state.dishes, state.deleted) {
        if (!popped && (state.deleted || (!state.isLoading && state.dishes.isEmpty()))) {
            popped = true
            onBack()
        }
    }

    LaunchedEffect(state.copyConfirmation) {
        state.copyConfirmation?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearCopyConfirmation()
        }
    }

    if (showCopyPicker) {
        DatePickerDialog(
            initialDate = state.date,
            title = "Copy to which day?",
            confirmLabel = "Copy here",
            mealTypeChooser = true,
            initialMealType = state.mealType,
            copiesChooser = true,
            onConfirmMeal = { date, meal, copies ->
                showCopyPicker = false
                fastingGuard.attempt(
                    isForToday = date == java.time.LocalDate.now(),
                    intake = com.fitpal.app.domain.model.FastingIntake.of(state.dishes.map { it.item.isDrink to it.item.calories })
                ) { decision ->
                    viewModel.copyToDate(date, meal, copies, decision)
                }
            },
            onConfirm = {},
            onDismiss = { showCopyPicker = false }
        )
    }
    if (showRenameMeal) {
        RenameDialog(
            currentName = state.mealName.orEmpty(),
            title = "Name this meal",
            label = "Meal name",
            placeholder = "e.g. Sunday roast",
            hint = "Names the whole meal on your day. Leave it empty to go back to listing its dishes.",
            allowEmpty = true,
            onConfirm = { viewModel.renameMeal(it); showRenameMeal = false },
            onDismiss = { showRenameMeal = false }
        )
    }
    if (showDetails) {
        com.fitpal.app.ui.component.MealDetailsSheet(
            mealType = state.mealType, onMealType = viewModel::setMealType,
            // A logged meal moves to another day by copying (top bar), so no day choice here.
            logDate = null, onPickDay = null,
            eatenAt = state.eatenAt, onPickTime = viewModel::setMealTime,
            tags = com.fitpal.app.domain.model.MealContext.parse(state.context),
            onToggleTag = viewModel::toggleMealContext,
            onDismiss = { showDetails = false }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this meal?") },
            text = { Text("This removes all ${state.dishes.size} dishes in this meal from your log.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.deleteMeal() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = state.mealName ?: "Meal",
                onBack = onBack,
                actions = {
                    if (state.dishes.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(40.dp).glass(CircleShape).clickable { showCopyPicker = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy to another date", tint = Cream, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.size(40.dp).glass(CircleShape).clickable { showDeleteDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete meal", tint = Cream, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // The same page as before logging: photo, details line, the meal, its dishes.
                        state.photoPath?.let { photo ->
                            item { com.fitpal.app.ui.component.MealPhoto(model = photo, source = state.aiSource) }
                        }
                        item {
                            com.fitpal.app.ui.component.MealDetailsRow(
                                mealType = state.mealType,
                                logDate = state.date,
                                eatenAt = state.eatenAt,
                                tags = com.fitpal.app.domain.model.MealContext.parse(state.context),
                                onClick = { showDetails = true }
                            )
                        }
                        item {
                            Column {
                                com.fitpal.app.ui.component.MealHeader(
                                    title = state.mealName ?: com.fitpal.app.domain.model.MealTypes.label(state.mealType),
                                    kcal = state.totalCalories.toInt(),
                                    onRename = { showRenameMeal = true }
                                )
                                // Which AI read this meal, when there's no photo to carry the badge.
                                if (state.photoPath == null) {
                                    state.aiSource?.let {
                                        Spacer(Modifier.height(8.dp))
                                        AiSourceBadge(it)
                                    }
                                }
                            }
                        }
                        state.plannedMealId?.let { pid -> item { ViewRecipeRow { onOpenRecipe(pid) } } }
                        state.coachingTip?.let { tip ->
                            item { CoachingTipCard(tip) }
                        }
                        itemsIndexed(state.dishes, key = { _, dish -> dish.item.id }) { _, dish ->
                            MealItemCard(
                                content = dish.toMealItemContent(),
                                collapsed = expanded != dish.item.id,
                                onExpand = { expanded = if (expanded == dish.item.id) null else dish.item.id },
                                onIngredientGramsChanged = { i, g -> viewModel.updateIngredientGrams(dish.item.id, i, g) },
                                onIngredientRemoved = { i -> viewModel.removeIngredient(dish.item.id, i) },
                                onTotalGramsChanged = { g -> viewModel.scaleDishToGrams(dish.item.id, g) },
                                onRemove = { viewModel.removeDish(dish.item.id) },
                                onRename = { viewModel.renameDish(dish.item.id, it) },
                                onSetDrink = { viewModel.setDishDrink(dish.item.id, it) },
                                onOpenDetails = { onDishClick(dish.item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Adapter from a saved dish (a logged item + its parsed ingredients) to the shared card. */
private fun MealGroupDish.toMealItemContent(): MealItemContent = MealItemContent(
    name = item.name,
    grams = item.grams,
    calories = item.calories,
    protein = item.protein,
    fat = item.fat,
    carbs = item.carbs,
    fiber = item.fiber,
    isDrink = item.isDrink,
    waterMl = item.waterMl,
    servings = 1,
    ingredients = ingredients
)

/** "View recipe ›" on a meal logged from the planner (docs/redesign/10). */
@androidx.compose.runtime.Composable
internal fun ViewRecipeRow(onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().glass().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
        Text("View recipe", style = MaterialTheme.typography.bodyLarge, color = Cream, modifier = Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleLarge, color = CreamMuted)
    }
}
