package com.fitpal.app.ui.screen.gallery

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.ui.component.AddIngredientDialog
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MacroBar
import com.fitpal.app.ui.component.MealInsightsSection
import com.fitpal.app.ui.component.MealTypeSelector
import com.fitpal.app.ui.component.MicronutrientBars
import com.fitpal.app.ui.component.logDateLabel
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

@Composable
fun GalleryFoodDetailScreen(
    onBack: () -> Unit,
    onLogged: () -> Unit,
    viewModel: GalleryFoodDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mealType by viewModel.mealType.collectAsStateWithLifecycle()
    val logDate by viewModel.logDate.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var replacingIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.logged) { if (state.logged) onLogged() }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    if (showDatePicker) {
        DatePickerDialog(
            initialDate = logDate,
            title = "Log to which day?",
            confirmLabel = "Log here",
            onConfirm = { date -> showDatePicker = false; viewModel.setLogDate(date) },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showAddDialog) {
        var aiStarted by remember { mutableStateOf(false) }
        LaunchedEffect(state.isAiAddingIngredient) {
            if (state.isAiAddingIngredient) aiStarted = true
            else if (aiStarted) { aiStarted = false; showAddDialog = false }
        }
        AddIngredientDialog(
            query = state.searchQuery,
            results = state.searchResults,
            isDrink = state.food?.isDrink == true,
            aiLoading = state.isAiAddingIngredient,
            onQueryChange = viewModel::onSearchQueryChange,
            onPick = { food -> viewModel.addIngredient(food); showAddDialog = false },
            onAiAdd = { text -> viewModel.addIngredientWithAi(text) },
            onDismiss = { viewModel.clearSearch(); showAddDialog = false }
        )
    }

    replacingIndex?.let { idx ->
        var aiStarted by remember { mutableStateOf(false) }
        LaunchedEffect(state.isAiAddingIngredient) {
            if (state.isAiAddingIngredient) aiStarted = true
            else if (aiStarted) { aiStarted = false; replacingIndex = null }
        }
        AddIngredientDialog(
            query = state.searchQuery,
            results = state.searchResults,
            isDrink = state.food?.isDrink == true,
            aiLoading = state.isAiAddingIngredient,
            onQueryChange = viewModel::onSearchQueryChange,
            onPick = { food -> viewModel.replaceIngredient(idx, food); replacingIndex = null },
            onAiAdd = { text -> viewModel.replaceIngredientWithAi(idx, text) },
            onDismiss = { viewModel.clearSearch(); replacingIndex = null },
            title = "Replace ingredient",
            aiVerb = "Use"
        )
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "Saved food",
                onBack = onBack,
                actions = {
                    if (state.food != null) {
                        Box(
                            modifier = Modifier.size(40.dp).glass(CircleShape).clickable { viewModel.delete() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Cream, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val food = state.food
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (food == null) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("This saved food is no longer available.", color = CreamMuted)
                    }
                } else {
                    val micros = food.totalMicros
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!state.photoPath.isNullOrBlank()) {
                            item {
                                AsyncImage(
                                    model = state.photoPath, contentDescription = state.name,
                                    modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(20.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        item {
                            Column(modifier = Modifier.fillMaxWidth().glass().padding(18.dp)) {
                                Text(state.name, style = MaterialTheme.typography.headlineSmall, color = Cream)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("${food.totalGrams.toInt()} ${if (food.isDrink) "ml" else "g"}", style = MaterialTheme.typography.bodyLarge, color = CreamMuted)
                                    Text("${food.totalCalories.toInt()} kcal", style = MaterialTheme.typography.bodyLarge, color = CalorieColor)
                                }
                                Spacer(Modifier.height(12.dp))
                                MacroBar(protein = food.totalProtein, fat = food.totalFat, carbs = food.totalCarbs, fiber = food.totalFiber)
                                if (food.isDrink && food.totalWaterMl > 0) {
                                    Spacer(Modifier.height(10.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Water content", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                                        Text("${food.totalWaterMl.toInt()} ml", style = MaterialTheme.typography.bodyMedium, color = Cream)
                                    }
                                }
                                state.aiSource?.let {
                                    Spacer(Modifier.height(10.dp))
                                    AiSourceBadge(it)
                                }
                            }
                        }
                        item {
                            IngredientsCard(
                                ingredients = state.ingredients,
                                isDrink = food.isDrink,
                                onGramsChanged = viewModel::updateIngredientGrams,
                                onRemove = viewModel::removeIngredient,
                                onReplace = { index -> viewModel.clearSearch(); replacingIndex = index },
                                onAdd = { showAddDialog = true }
                            )
                        }
                        if (!micros.isEmpty) {
                            item { MicronutrientCard(micros) }
                        }
                        item {
                            AiInsightsArea(
                                isLoadingAi = state.isLoadingAi,
                                insights = state.insights,
                                insightsSource = state.insightsSource,
                                modelReady = state.modelReady,
                                breakdown = breakdownFor(food),
                                onGenerate = viewModel::generateAiInsights,
                                onRegenerate = viewModel::regenerateInsights
                            )
                        }
                    }
                }
            }

            // ---- Log action ----
            if (state.food != null) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    MealTypeSelector(selected = mealType, onSelected = viewModel::setMealType, modifier = Modifier.padding(vertical = 6.dp))
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text("Logging to: ${logDateLabel(logDate)}")
                    }
                    Button(onClick = { viewModel.logIt() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Log this food")
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientsCard(
    ingredients: List<Ingredient>,
    isDrink: Boolean,
    onGramsChanged: (index: Int, newGrams: Float) -> Unit,
    onRemove: (index: Int) -> Unit,
    onReplace: (index: Int) -> Unit,
    onAdd: () -> Unit
) {
    val unit = if (isDrink) "ml" else "g"
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Ingredients", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(bottom = 8.dp))
        ingredients.forEachIndexed { index, ingredient ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Tap the name to swap this ingredient for a different one (keeps the amount).
                Row(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onReplace(index) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(ingredient.name, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Change ${ingredient.name}", tint = CreamMuted, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(6.dp))
                com.fitpal.app.ui.component.GramsField(
                    grams = ingredient.grams,
                    onGramsChanged = { onGramsChanged(index, it) },
                    modifier = Modifier.width(82.dp),
                    unit = unit
                )
                Spacer(Modifier.width(8.dp))
                Text("${ingredient.calories.toInt()} kcal", style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.width(56.dp))
                IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${ingredient.name}", tint = CreamMuted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.glassSoft(CircleShape).clickable(onClick = onAdd).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add ingredient", style = MaterialTheme.typography.labelLarge, color = Cream)
        }
    }
}

/** Vitamins & minerals for this food — collapsed by default, only the ones actually present. */
@Composable
private fun MicronutrientCard(micros: Micronutrients) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().glass().clickable { expanded = !expanded }.padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vitamins & minerals", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Show vitamins & minerals",
                tint = CreamMuted
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(14.dp))
                MicronutrientBars(micros, showAll = false)
            }
        }
    }
}

@Composable
private fun AiInsightsArea(
    isLoadingAi: Boolean,
    insights: com.fitpal.app.domain.model.MealInsights?,
    insightsSource: com.fitpal.app.ml.AiSource?,
    modelReady: Boolean,
    breakdown: List<HealthScorer.Dimension>,
    onGenerate: () -> Unit,
    onRegenerate: () -> Unit
) {
    when {
        isLoadingAi -> {
            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("AI analysis", style = MaterialTheme.typography.titleSmall, color = Cream)
                }
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("Analysing...", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
        }
        insights != null -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MealInsightsSection(insights, breakdown = breakdown)
                insightsSource?.let { AiSourceBadge(it) }
                if (modelReady) {
                    Row(
                        modifier = Modifier.glassSoft(CircleShape).clickable(onClick = onRegenerate).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh analysis", style = MaterialTheme.typography.labelLarge, color = Cream)
                    }
                }
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("AI analysis", style = MaterialTheme.typography.titleSmall, color = Cream)
                }
                Spacer(Modifier.height(10.dp))
                if (modelReady) {
                    Row(
                        modifier = Modifier.glassSoft(CircleShape).clickable(onClick = onGenerate).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generate full AI analysis", style = MaterialTheme.typography.labelLarge, color = Cream)
                    }
                } else {
                    Text("Set up the AI model to analyse this food.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                }
            }
        }
    }
}

private fun breakdownFor(food: DetectedFood): List<HealthScorer.Dimension> =
    HealthScorer.breakdown(
        calories = food.totalCalories,
        protein = food.totalProtein,
        fat = food.totalFat,
        carbs = food.totalCarbs,
        fiber = food.totalFiber,
        grams = food.totalGrams,
        isDrink = food.isDrink,
        micros = food.totalMicros
    )
