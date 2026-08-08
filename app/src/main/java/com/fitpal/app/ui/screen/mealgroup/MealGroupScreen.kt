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
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MacroBar
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
    // The dish being renamed (id + its current name), or null when no rename is open.
    var renamingDish by remember { mutableStateOf<Pair<Long, String>?>(null) }

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
    renamingDish?.let { (dishId, dishName) ->
        RenameDialog(
            currentName = dishName,
            title = "Rename this dish",
            label = "Dish name",
            hint = "Only the name changes — the amount and nutrition stay as logged.",
            onConfirm = { viewModel.renameDish(dishId, it); renamingDish = null },
            onDismiss = { renamingDish = null }
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
                                Spacer(Modifier.height(12.dp))
                                MealTypeSelector(selected = state.mealType, onSelected = viewModel::setMealType)
                            }
                        }
                        itemsIndexed(state.dishes, key = { _, dish -> dish.item.id }) { _, dish ->
                            DishCard(
                                dish = dish,
                                onGramsChange = { i, g -> viewModel.updateIngredientGrams(dish.item.id, i, g) },
                                onTotalGramsChange = { g -> viewModel.scaleDishToGrams(dish.item.id, g) },
                                onIngredientRemove = { i -> viewModel.removeIngredient(dish.item.id, i) },
                                onRemoveDish = { viewModel.removeDish(dish.item.id) },
                                onRename = { renamingDish = dish.item.id to dish.item.name },
                                onOpenDetails = { onDishClick(dish.item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DishCard(
    dish: MealGroupDish,
    onGramsChange: (ingredientIndex: Int, newGrams: Float) -> Unit,
    onTotalGramsChange: (newTotalGrams: Float) -> Unit,
    onIngredientRemove: (ingredientIndex: Int) -> Unit,
    onRemoveDish: () -> Unit,
    onRename: () -> Unit,
    onOpenDetails: () -> Unit
) {
    val item = dish.item
    val unit = if (item.isDrink) "ml" else "g"
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Tap the dish name to rename it — the AI's wording (or a database catalogue name)
            // is a starting point, not something to live with.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRename)
                    .padding(vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = Cream,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename ${item.name}",
                        tint = CreamMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text("${item.grams.toInt()} $unit", style = MaterialTheme.typography.labelSmall, color = CreamMuted)
            }
            Text("${item.calories.toInt()} kcal", style = MaterialTheme.typography.titleMedium, color = CalorieColor)
            IconButton(onClick = onRemoveDish) {
                Icon(Icons.Default.Close, contentDescription = "Remove ${item.name}", tint = CreamMuted)
            }
        }

        Spacer(Modifier.height(12.dp))
        MacroBar(protein = item.protein, fat = item.fat, carbs = item.carbs, fiber = item.fiber)
        Spacer(Modifier.height(16.dp))

        // Whole-dish weight: scales every ingredient at once (only useful for real dishes).
        if (dish.ingredients.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total weight", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f))
                com.fitpal.app.ui.component.GramsField(
                    grams = item.grams,
                    onGramsChanged = onTotalGramsChange,
                    modifier = Modifier.width(100.dp),
                    unit = unit
                )
            }
        }

        Text("Ingredients", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(bottom = 8.dp))
        dish.ingredients.forEachIndexed { index, ingredient ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ingredient.name, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f))
                com.fitpal.app.ui.component.GramsField(
                    grams = ingredient.grams,
                    onGramsChanged = { onGramsChange(index, it) },
                    modifier = Modifier.width(82.dp),
                    unit = unit
                )
                Spacer(Modifier.width(8.dp))
                Text("${ingredient.calories.toInt()} kcal", style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.width(56.dp))
                IconButton(onClick = { onIngredientRemove(index) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${ingredient.name}", tint = CreamMuted)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
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
