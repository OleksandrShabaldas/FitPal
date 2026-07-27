package com.fitpal.app.ui.screen.analysis

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.logDateLabel
import com.fitpal.app.ui.component.MacroBar
import com.fitpal.app.ui.component.MealInsightsSection
import com.fitpal.app.ui.component.AddIngredientDialog
import com.fitpal.app.ui.component.MealTypeSelector
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisScreen(
    onMealLogged: () -> Unit,
    onSetupModel: () -> Unit,
    onBack: () -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mealType by viewModel.mealType.collectAsStateWithLifecycle()
    val logDate by viewModel.logDate.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Which food card (index) is currently adding an ingredient, if any.
    var addIngredientFor by remember { mutableStateOf<Int?>(null) }
    // Which food card (index) has the "Edit with AI" correction dialog open, if any.
    var editWithAiFor by remember { mutableStateOf<Int?>(null) }
    // Open date pickers: one to set the log date, one to copy the meal to another date.
    var showDatePicker by remember { mutableStateOf(false) }
    var showCopyPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onMealLogged()
    }

    // One-shot toast after "copy to another date".
    LaunchedEffect(state.copyConfirmation) {
        state.copyConfirmation?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearCopyConfirmation()
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            initialDate = logDate,
            title = "Log to which day?",
            confirmLabel = "Log here",
            onConfirm = { date -> showDatePicker = false; viewModel.setLogDate(date) },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showCopyPicker) {
        DatePickerDialog(
            initialDate = logDate,
            title = "Copy to which day?",
            confirmLabel = "Copy here",
            mealTypeChooser = true,
            initialMealType = mealType,
            copiesChooser = true,
            onConfirmMeal = { date, meal, copies -> showCopyPicker = false; viewModel.copyToDate(date, meal, copies) },
            onConfirm = {},
            onDismiss = { showCopyPicker = false }
        )
    }

    // Cancelling the on-device confirm prompt leaves the screen.
    LaunchedEffect(state.cancelled) {
        if (state.cancelled) onBack()
    }

    // Leaving the screen (back) without logging a finished result discards it, so it isn't
    // auto-saved on app close. Backgrounding the whole app doesn't dispose this, so that flow
    // (finish in background / resume from the notification) is unaffected.
    DisposableEffect(Unit) {
        onDispose { viewModel.onLeaveWithoutLogging() }
    }

    addIngredientFor?.let { foodIndex ->
        // Close the dialog automatically once an AI "add ingredient" finishes.
        var aiStarted by remember { mutableStateOf(false) }
        LaunchedEffect(state.isAiAddingIngredient) {
            if (state.isAiAddingIngredient) aiStarted = true
            else if (aiStarted) { aiStarted = false; addIngredientFor = null }
        }
        AddIngredientDialog(
            query = state.searchQuery,
            results = state.searchResults,
            isDrink = state.detectedFoods.getOrNull(foodIndex)?.isDrink == true,
            aiLoading = state.isAiAddingIngredient,
            onQueryChange = viewModel::onSearchQueryChange,
            onPick = { food -> viewModel.addIngredient(foodIndex, food); addIngredientFor = null },
            onAiAdd = { text -> viewModel.addIngredientWithAi(foodIndex, text) },
            onDismiss = { viewModel.clearSearch(); addIngredientFor = null }
        )
    }

    editWithAiFor?.let { foodIndex ->
        // Close automatically once the re-evaluation finishes (mirrors the AI add-ingredient flow).
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(state.aiEditingFoodIndex) {
            if (state.aiEditingFoodIndex == foodIndex) started = true
            else if (started) { started = false; editWithAiFor = null }
        }
        EditWithAiDialog(
            foodLabel = state.detectedFoods.getOrNull(foodIndex)?.label ?: "this item",
            loading = state.aiEditingFoodIndex == foodIndex,
            onSubmit = { text -> viewModel.refineFoodWithAi(foodIndex, text) },
            onDismiss = { editWithAiFor = null }
        )
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Analysis", onBack = onBack)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.needsModel -> CenteredMessage {
                        Text(
                            "The AI model needs to be downloaded before FitPal can recognise food.",
                            style = MaterialTheme.typography.bodyLarge, color = Cream, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onSetupModel) { Text("Set up AI model") }
                    }

                    state.needsImage -> CenteredMessage {
                        Text(
                            "No photo to analyse. Use Add, then From Gallery or Take Photo to pick one.",
                            style = MaterialTheme.typography.bodyLarge, color = CreamMuted, textAlign = TextAlign.Center
                        )
                    }

                    state.readyToAnalyze -> {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            state.imageUri?.let { uri ->
                                AsyncImage(
                                    model = uri, contentDescription = "Food photo",
                                    modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                                Text("Add a note for the AI", style = MaterialTheme.typography.titleMedium, color = Cream)
                                Text(
                                    "Optional — clarify ingredients, size or grams so the estimate is more accurate.",
                                    style = MaterialTheme.typography.bodySmall, color = CreamMuted
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = state.note,
                                    onValueChange = viewModel::setNote,
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("e.g. large bowl, ~350 g, olive oil and feta") },
                                    minLines = 2,
                                    maxLines = 4
                                )
                            }
                            MealTypeSelector(
                                selected = mealType,
                                onSelected = viewModel::setMealType,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Button(onClick = { viewModel.startAnalysis() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Analyse")
                            }
                        }
                    }

                    state.onlineFailedReason != null -> CenteredMessage {
                        state.imageUri?.let { uri ->
                            AsyncImage(
                                model = uri, contentDescription = "Food photo",
                                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                        Text(
                            "Online AI isn't available",
                            style = MaterialTheme.typography.titleMedium, color = Cream, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            (state.onlineFailedReason ?: "") + "\n\nContinue with the on-device model, or try online again?",
                            style = MaterialTheme.typography.bodyMedium, color = CreamMuted, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
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

                    state.isAnalyzing -> CenteredMessage {
                        state.imageUri?.let { uri ->
                            AsyncImage(
                                model = uri, contentDescription = "Food photo",
                                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                        AiSourceBadge(state.aiSource)
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            state.progressMessage.ifBlank { "Analysing..." },
                            style = MaterialTheme.typography.bodyLarge, color = Cream, textAlign = TextAlign.Center
                        )
                    }

                    state.error != null && state.detectedFoods.isEmpty() -> CenteredMessage {
                        Text(
                            state.error ?: "Something went wrong.",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            state.imageUri?.let { uri ->
                                item {
                                    AsyncImage(
                                        model = uri, contentDescription = "Food photo",
                                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            if (state.aiSource != null && state.detectedFoods.isNotEmpty()) {
                                item { AiSourceBadge(state.aiSource, reason = state.onlineError) }
                            }

                            if (state.dishCandidates.isNotEmpty()) {
                                item {
                                    Column {
                                        Text("Looks like...", style = MaterialTheme.typography.titleMedium, color = Cream)
                                        Text(
                                            "Pick the closest match — its ingredients are below, and you can add anything it's missing.",
                                            style = MaterialTheme.typography.bodySmall, color = CreamMuted
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            state.dishCandidates.forEachIndexed { i, candidate ->
                                                FilterChip(
                                                    selected = i == state.selectedDishIndex,
                                                    onClick = { viewModel.selectDish(i) },
                                                    label = { Text(candidate.label) }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (state.detectedFoods.isNotEmpty()) {
                                item {
                                    val count = state.detectedFoods.size
                                    val totalKcal = state.detectedFoods.sumOf { it.totalCalories.toDouble() }.toInt()
                                    Column {
                                        Text(
                                            if (count == 1) "Found 1 item" else "Found $count items on your plate",
                                            style = MaterialTheme.typography.titleMedium, color = Cream
                                        )
                                        Text(
                                            "$totalKcal kcal total — adjust amounts, and remove anything that isn't yours.",
                                            style = MaterialTheme.typography.bodySmall, color = CreamMuted
                                        )
                                    }
                                }
                            }

                            itemsIndexed(state.detectedFoods) { foodIndex, food ->
                                DetectedFoodCard(
                                    food = food,
                                    isSaved = state.savedFoodIndices.contains(foodIndex),
                                    onGramsChanged = { ii, g -> viewModel.updateIngredientGrams(foodIndex, ii, g) },
                                    onTotalGramsChanged = { g -> viewModel.scaleFoodToGrams(foodIndex, g) },
                                    onServingsChanged = { n -> viewModel.setServings(foodIndex, n) },
                                    onWaterChanged = { ml -> viewModel.setFoodWater(foodIndex, ml) },
                                    onToggleVariation = { vi -> viewModel.toggleVariation(foodIndex, vi) },
                                    onIngredientRemoved = { ii -> viewModel.removeIngredient(foodIndex, ii) },
                                    onAddIngredient = { addIngredientFor = foodIndex },
                                    onEditWithAi = { editWithAiFor = foodIndex },
                                    onRemove = { viewModel.removeFood(foodIndex) },
                                    onSaveToGallery = { viewModel.saveToGallery(foodIndex) },
                                    onRemoveFromGallery = { viewModel.removeFromGallery(foodIndex) }
                                )
                            }

                            when {
                                state.isLoadingInsights -> item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Text("Analysing health impact...", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                                    }
                                }
                                state.insights != null -> {
                                    item { MealInsightsSection(state.insights!!, breakdown = mealBreakdown(state.detectedFoods)) }
                                    item {
                                        TextButton(onClick = { viewModel.generateInsights() }) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Refresh AI analysis")
                                        }
                                    }
                                }
                                else -> item {
                                    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("AI analysis", style = MaterialTheme.typography.titleSmall, color = Cream)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "See this meal's health score, energy & mood impact and swap ideas before you log it.",
                                            style = MaterialTheme.typography.bodySmall, color = CreamMuted
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        OutlinedButton(onClick = { viewModel.generateInsights() }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Generate AI analysis")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Bottom action ----
            if (state.detectedFoods.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    MealTypeSelector(
                        selected = mealType,
                        onSelected = viewModel::setMealType,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    // Day to log to + copy-to-another-date (#2/#3).
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(logDateLabel(logDate))
                        }
                        OutlinedButton(onClick = { showCopyPicker = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy to date")
                        }
                    }
                    Button(
                        onClick = { viewModel.logMeal() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSaving
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isSaving) "Saving..." else "Log meal")
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectedFoodCard(
    food: DetectedFood,
    isSaved: Boolean,
    onGramsChanged: (ingredientIndex: Int, newGrams: Float) -> Unit,
    onTotalGramsChanged: (newTotalGrams: Float) -> Unit,
    onServingsChanged: (newServings: Int) -> Unit,
    onWaterChanged: (newWaterMl: Float) -> Unit,
    onIngredientRemoved: (ingredientIndex: Int) -> Unit,
    onToggleVariation: (variationIndex: Int) -> Unit,
    onAddIngredient: () -> Unit,
    onEditWithAi: () -> Unit,
    onRemove: () -> Unit,
    onSaveToGallery: () -> Unit,
    onRemoveFromGallery: () -> Unit
) {
    val unit = if (food.isDrink) "ml" else "g"
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.label, style = MaterialTheme.typography.titleLarge, color = Cream)
                Text(
                    text = "${food.totalGrams.toInt()} $unit" +
                        if (food.isDrink && food.totalWaterMl > 0f) " + ${food.totalWaterMl.toInt()} ml water" else "",
                    style = MaterialTheme.typography.labelSmall, color = CreamMuted
                )
            }
            Text("${food.totalCalories.toInt()} kcal", style = MaterialTheme.typography.titleMedium, color = CalorieColor)
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove ${food.label}", tint = CreamMuted)
            }
        }

        Spacer(Modifier.height(12.dp))
        MacroBar(protein = food.totalProtein, fat = food.totalFat, carbs = food.totalCarbs, fiber = food.totalFiber)
        Spacer(Modifier.height(16.dp))

        // How many of this item — a quick +/− that scales the whole thing (e.g. 2 peaches).
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Amount", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { onServingsChanged(food.servings - 1) }, enabled = food.servings > 1) {
                Icon(Icons.Default.Remove, contentDescription = "One fewer")
            }
            Text(
                "${food.servings}",
                style = MaterialTheme.typography.titleMedium, color = Cream,
                textAlign = TextAlign.Center, modifier = Modifier.width(40.dp)
            )
            FilledTonalIconButton(onClick = { onServingsChanged(food.servings + 1) }) {
                Icon(Icons.Default.Add, contentDescription = "One more")
            }
        }

        // Water content the AI identified — editable (counts toward hydration).
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💧 Water", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f))
            com.fitpal.app.ui.component.GramsField(
                grams = food.totalWaterMl,
                onGramsChanged = onWaterChanged,
                modifier = Modifier.width(110.dp),
                unit = "ml",
                commitZero = true
            )
        }

        // Whole-dish weight: scales every ingredient at once (only useful for real dishes).
        if (food.ingredients.size > 1) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Total weight", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f))
                com.fitpal.app.ui.component.GramsField(
                    grams = food.totalGrams,
                    onGramsChanged = onTotalGramsChanged,
                    modifier = Modifier.width(100.dp),
                    unit = unit
                )
            }
        }

        Text("Ingredients", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(bottom = 8.dp))
        food.ingredients.forEachIndexed { index, ingredient ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(ingredient.name, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f))
                com.fitpal.app.ui.component.GramsField(
                    grams = ingredient.grams,
                    onGramsChanged = { onGramsChanged(index, it) },
                    modifier = Modifier.width(80.dp),
                    unit = unit
                )
                Spacer(Modifier.width(8.dp))
                Text("${ingredient.calories.toInt()} kcal", style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.width(56.dp))
                IconButton(onClick = { onIngredientRemoved(index) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${ingredient.name}", tint = CreamMuted)
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onAddIngredient) { Text("+ Add ingredient") }
            TextButton(onClick = onEditWithAi) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit with AI")
            }
        }

        if (food.variations.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Variations", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                food.variations.forEachIndexed { index, variation ->
                    FilterChip(selected = variation.isSelected, onClick = { onToggleVariation(index) }, label = { Text(variation.description) })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { if (isSaved) onRemoveFromGallery() else onSaveToGallery() }) {
            Icon(
                if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = null, modifier = Modifier.padding(end = 4.dp)
            )
            Text(if (isSaved) "Saved" else "Save to collection")
        }
    }
}

/**
 * "Edit with AI" — the user describes a correction in plain language and the AI re-evaluates the
 * whole dish (rebalancing amounts), rather than just appending an ingredient.
 */
@Composable
private fun EditWithAiDialog(
    foodLabel: String,
    loading: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Edit \"$foodLabel\" with AI") },
        text = {
            Column {
                Text(
                    "Say what's wrong or missing — the AI re-checks the whole dish and rebalances the " +
                        "amounts to fit, instead of just adding on top.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. there are also beans; no cheese; it's grilled not fried") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !loading
                )
                if (loading) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Re-checking the dish…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text) }, enabled = text.isNotBlank() && !loading) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Update")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") } }
    )
}

/** Full per-dimension health breakdown for the detected foods (for the score view). */
private fun mealBreakdown(foods: List<DetectedFood>): List<HealthScorer.Dimension> {
    if (foods.isEmpty()) return emptyList()
    val micros = foods.fold(Micronutrients()) { acc, f -> acc + f.totalMicros }
    return HealthScorer.breakdown(
        calories = foods.sumOf { it.totalCalories.toDouble() }.toFloat(),
        protein = foods.sumOf { it.totalProtein.toDouble() }.toFloat(),
        fat = foods.sumOf { it.totalFat.toDouble() }.toFloat(),
        carbs = foods.sumOf { it.totalCarbs.toDouble() }.toFloat(),
        fiber = foods.sumOf { it.totalFiber.toDouble() }.toFloat(),
        grams = foods.sumOf { it.totalGrams.toDouble() }.toFloat(),
        isDrink = foods.all { it.isDrink },
        micros = micros
    )
}
