package com.fitpal.app.ui.screen.describe

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.fitpal.app.ui.component.AddIngredientDialog
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.FoodResultCard
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MealTotalRow
import com.fitpal.app.ui.component.MealTypeSelector
import com.fitpal.app.ui.component.logDateLabel
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted

@Composable
fun DescribeFoodScreen(
    onLogged: () -> Unit,
    onGoToManual: () -> Unit,
    onSetupModel: () -> Unit,
    onBack: () -> Unit,
    viewModel: DescribeFoodViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mealPresets by viewModel.mealPresets.collectAsStateWithLifecycle()
    val drinkPresets by viewModel.drinkPresets.collectAsStateWithLifecycle()
    val mealType by viewModel.mealType.collectAsStateWithLifecycle()
    val logDate by viewModel.logDate.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var addIngredientFor by remember { mutableStateOf<Int?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onLogged() }

    // Cancelling the on-device confirm prompt leaves the screen.
    LaunchedEffect(state.cancelled) { if (state.cancelled) onBack() }

    if (showDatePicker) {
        DatePickerDialog(
            initialDate = logDate,
            title = "Log to which day?",
            confirmLabel = "Log here",
            onConfirm = { date -> showDatePicker = false; viewModel.setLogDate(date) },
            onDismiss = { showDatePicker = false }
        )
    }

    // Backing out without logging a finished result discards it (see AnalysisScreen).
    DisposableEffect(Unit) {
        onDispose { viewModel.onLeaveWithoutLogging() }
    }

    addIngredientFor?.let { foodIndex ->
        var aiStarted by remember { mutableStateOf(false) }
        LaunchedEffect(state.isAiAddingIngredient) {
            if (state.isAiAddingIngredient) aiStarted = true
            else if (aiStarted) { aiStarted = false; addIngredientFor = null }
        }
        AddIngredientDialog(
            query = state.searchQuery,
            results = state.searchResults,
            isDrink = state.foods.getOrNull(foodIndex)?.isDrink == true,
            aiLoading = state.isAiAddingIngredient,
            onQueryChange = viewModel::onSearchQueryChange,
            onPick = { food -> viewModel.addIngredient(foodIndex, food); addIngredientFor = null },
            onAiAdd = { text -> viewModel.addIngredientWithAi(foodIndex, text) },
            onDismiss = { viewModel.clearSearch(); addIngredientFor = null }
        )
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Describe to AI", onBack = onBack)

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                    placeholder = { Text("Describe your meal, e.g. \"chilli con carne\" or \"coca-cola\"") }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.analyze() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.description.isNotBlank() && !state.isAnalyzing
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Analyse")
                }
                MealTypeSelector(
                    selected = mealType,
                    onSelected = viewModel::setMealType,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Logging to: ${logDateLabel(logDate)}")
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
                when {
                    state.needsModel -> Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text("The AI model isn't downloaded yet.", style = MaterialTheme.typography.titleMedium, color = Cream)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Describe-to-AI can run on Google's online model, but it also needs the on-device model " +
                                "as a fallback for when you're offline. Download it once and it works without internet after that.",
                            style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onSetupModel) { Text("Set up AI model") }
                    }

                    state.onlineFailedReason != null -> Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text("Online AI isn't available", style = MaterialTheme.typography.titleMedium, color = Cream)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (state.onlineFailedReason ?: "") + "\n\nContinue with the on-device model, or try online again?",
                            style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.retryOnline() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Try online again")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.useOnDevice() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Continue on-device")
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.cancelAnalysis() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancel")
                        }
                    }

                    state.isAnalyzing -> Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        AiSourceBadge(state.aiSource)
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("Thinking… online can take a moment, and the first on-device run is slower.", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                    }

                    state.noMatchesFound -> Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Text("The AI couldn't pull foods from that description.", style = MaterialTheme.typography.titleMedium, color = Cream)
                        Spacer(Modifier.height(4.dp))
                        Text("Try rephrasing with the foods named directly, or add it by hand.", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onGoToManual) { Text("Add manually instead") }
                    }

                    state.foods.isNotEmpty() -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.aiSource?.let { AiSourceBadge(it, reason = state.onlineError) }
                                Text("Found these — adjust amounts and pick variations", style = MaterialTheme.typography.titleMedium, color = Cream)
                            }
                        }
                        itemsIndexed(state.foods) { index, food ->
                            FoodResultCard(
                                food = food,
                                onIngredientGramsChanged = { ii, g -> viewModel.updateIngredientGrams(index, ii, g) },
                                onIngredientRemoved = { ii -> viewModel.removeIngredient(index, ii) },
                                onToggleVariation = { vi -> viewModel.toggleVariation(index, vi) },
                                onRemove = { viewModel.removeFood(index) },
                                onTotalGramsChanged = { g -> viewModel.scaleFoodToGrams(index, g) },
                                onAddIngredient = { addIngredientFor = index },
                                mealPresets = mealPresets,
                                drinkPresets = drinkPresets,
                                onSave = {
                                    viewModel.saveToGallery(index)
                                    Toast.makeText(context, "Saved to collection", Toast.LENGTH_SHORT).show()
                                },
                                saved = food.label in state.savedLabels
                            )
                        }
                    }

                    else -> Text(
                        "Tip: name what you ate or drank. Products like \"coca-cola\" come back as one item; dishes like \"chilli con carne\" show their ingredients and variations you can pick. Uses the online AI when available, the on-device model when not.",
                        style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            if (state.foods.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    MealTotalRow(totalCalories = state.totalCalories, itemCount = state.foods.size)
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
