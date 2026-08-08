package com.fitpal.app.ui.screen.manual

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.EditableFoodItemRow
import com.fitpal.app.ui.component.FoodPortionSheet
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MealTotalRow
import com.fitpal.app.ui.component.MealTypeSelector
import com.fitpal.app.ui.component.logDateLabel
import androidx.compose.ui.text.font.FontWeight
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glassSoft

@Composable
fun ManualEntryScreen(
    onLogged: () -> Unit,
    onBack: () -> Unit,
    viewModel: ManualEntryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presets by viewModel.servingPresets.collectAsStateWithLifecycle()
    val drinkPresets by viewModel.drinkPresets.collectAsStateWithLifecycle()
    val mealType by viewModel.mealType.collectAsStateWithLifecycle()
    val logDate by viewModel.logDate.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onLogged() }

    if (showDatePicker) {
        DatePickerDialog(
            initialDate = logDate,
            title = "Log to which day?",
            confirmLabel = "Log here",
            onConfirm = { date -> showDatePicker = false; viewModel.setLogDate(date) },
            onDismiss = { showDatePicker = false }
        )
    }

    // Picking a food opens its portion sheet straight away — size it, say how many, and only
    // then does it join the meal.
    state.picked?.let { picked ->
        FoodPortionSheet(
            base = picked.base,
            count = picked.count,
            mealPresets = presets,
            drinkPresets = drinkPresets,
            onPortionChange = viewModel::setPickedPortion,
            onCountChange = viewModel::setPickedCount,
            onDrinkChange = viewModel::setPickedDrink,
            onNameChange = viewModel::setPickedName,
            onConfirm = viewModel::confirmPicked,
            onDismiss = viewModel::dismissPicked,
            maxCount = PickedFood.MAX_COUNT
        )
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Manual entry", onBack = onBack)

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                placeholder = { Text("Search foods (e.g. chicken, rice)…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            // Searching hides the meal you've already built, so keep its running total in view
            // and offer a way back to it without adding anything.
            if (state.searchResults.isNotEmpty() && state.draft.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .glassSoft()
                        .clickable { viewModel.clearSearch() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${state.draft.size} in your meal · ${state.totalCalories.toInt()} kcal",
                        style = MaterialTheme.typography.labelLarge, color = Cream
                    )
                    Text("Done", style = MaterialTheme.typography.labelLarge, color = GoldLight)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.searchResults.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(state.searchResults, key = { it.fdcId }) { food ->
                            SearchResultRow(
                                food = food,
                                addedCount = state.draft.count { it.name == food.description },
                                onClick = { viewModel.pickFood(food) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }

                    state.draft.isNotEmpty() -> {
                        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            item {
                                Text("Your meal", style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                            }
                            itemsIndexed(state.draft) { index, ingredient ->
                                EditableFoodItemRow(
                                    ingredient = ingredient,
                                    onGramsChanged = { viewModel.updateGrams(index, it) },
                                    onRemove = { viewModel.removeItem(index) },
                                    presets = if (ingredient.isDrink) drinkPresets else presets,
                                    unit = if (ingredient.isDrink) "ml" else "g",
                                    onSave = {
                                        viewModel.saveToGallery(ingredient)
                                        Toast.makeText(context, "Saved to collection", Toast.LENGTH_SHORT).show()
                                    },
                                    saved = ingredient.name in state.savedNames,
                                    onRename = { viewModel.renameDraftItem(index, it) }
                                )
                            }
                        }
                    }

                    else -> Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Search for a food above to add it to your meal",
                            style = MaterialTheme.typography.bodyLarge, color = CreamMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "For thousands more foods (incl. apricots) and European brands, download the " +
                                "full food database in Settings → AI model setup. Searches also check Open " +
                                "Food Facts online.",
                            style = MaterialTheme.typography.bodySmall, color = CreamFaint,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            if (state.draft.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    MealTotalRow(totalCalories = state.totalCalories, itemCount = state.draft.size)
                    MealTypeSelector(selected = mealType, onSelected = viewModel::setMealType, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Logging to: ${logDateLabel(logDate)}")
                    }
                    Button(onClick = { viewModel.logMeal() }, modifier = Modifier.fillMaxWidth(), enabled = !state.isSaving) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isSaving) "Saving…" else "Log meal")
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(food: UsdaFoodEntity, addedCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(food.description, style = MaterialTheme.typography.bodyLarge, color = Cream)
            Text(
                text = "${food.caloriesPer100g.toInt()} kcal / 100g" + (food.foodCategory?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium, color = CreamMuted
            )
        }
        // Already in the meal from an earlier search — worth knowing before adding it twice.
        if (addedCount > 0) {
            Text(
                "×$addedCount",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = GoldLight,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
        // A chevron, not a "+": tapping opens the portion sheet rather than adding blind.
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "Choose an amount of ${food.description}",
            tint = GoldLight
        )
    }
}
