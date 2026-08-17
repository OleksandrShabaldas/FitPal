package com.fitpal.app.ui.screen.mealgroup

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MealItemCard
import com.fitpal.app.ui.component.MealItemContent
import com.fitpal.app.ui.component.MealTypeSelector
import com.fitpal.app.ui.component.RenameDialog
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft
import java.time.LocalDate

/**
 * Multi-dish overview of one logged meal (a single photo/describe generation). Mirrors the
 * pre-log analysis review: every dish on one screen. Tapping a dish opens it for full editing.
 */
@Composable
fun MealGroupScreen(
    onBack: () -> Unit,
    onDishClick: (Long) -> Unit,
    viewModel: MealGroupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showCopyPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameMeal by remember { mutableStateOf(false) }

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
            onConfirmMeal = { date, meal, copies -> showCopyPicker = false; viewModel.copyToDate(date, meal, copies) },
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
                                modifier = Modifier.size(40.dp).glass(CircleShape).clickable { showRenameMeal = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Name this meal", tint = Cream, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(8.dp))
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
                        state.photoPath?.let { photo ->
                            item {
                                AsyncImage(
                                    model = photo, contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        item {
                            Column {
                                val n = state.dishes.size
                                Text(
                                    if (n == 1) "1 dish" else "$n dishes",
                                    style = MaterialTheme.typography.titleMedium, color = Cream
                                )
                                Text(
                                    "${state.totalCalories.toInt()} kcal total",
                                    style = MaterialTheme.typography.bodySmall, color = CreamMuted
                                )
                                Text(
                                    "Tap a dish to edit it, or its name to rename it.",
                                    style = MaterialTheme.typography.bodySmall, color = CreamMuted
                                )
                                // Which AI read this meal — one generation, so one badge for it all.
                                state.aiSource?.let {
                                    Spacer(Modifier.height(8.dp))
                                    AiSourceBadge(it)
                                }
                                Spacer(Modifier.height(12.dp))
                                MealTypeSelector(selected = state.mealType, onSelected = viewModel::setMealType)
                            }
                        }
                        itemsIndexed(state.dishes, key = { _, dish -> dish.item.id }) { _, dish ->
                            MealItemCard(
                                content = dish.toMealItemContent(),
                                onIngredientGramsChanged = { i, g -> viewModel.updateIngredientGrams(dish.item.id, i, g) },
                                onIngredientRemoved = { i -> viewModel.removeIngredient(dish.item.id, i) },
                                onTotalGramsChanged = { g -> viewModel.scaleDishToGrams(dish.item.id, g) },
                                onRemove = { viewModel.removeDish(dish.item.id) },
                                onRename = { viewModel.renameDish(dish.item.id, it) },
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
