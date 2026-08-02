package com.fitpal.app.ui.screen.entrydetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealTypes
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ui.component.AddIngredientDialog
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.EditWithAiDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MealInsightsSection
import com.fitpal.app.ui.component.MicronutrientBars
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.CarbColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.FatColor
import com.fitpal.app.ui.theme.FiberColor
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.ProteinColor
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

@Composable
fun EntryDetailScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    val context = androidx.compose.ui.platform.LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var replacingIndex by remember { mutableStateOf<Int?>(null) }
    var showCopyPicker by remember { mutableStateOf(false) }
    var showEditWithAi by remember { mutableStateOf(false) }

    // Close the AI-edit dialog once the re-check lands (or report that it came back empty).
    LaunchedEffect(state.isRefiningWithAi) {
        if (!state.isRefiningWithAi) showEditWithAi = false
    }
    LaunchedEffect(state.refineError) {
        state.refineError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearRefineError()
        }
    }

    // One-shot toast after "copy to another date".
    LaunchedEffect(state.copyConfirmation) {
        state.copyConfirmation?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearCopyConfirmation()
        }
    }

    if (showCopyPicker) {
        com.fitpal.app.ui.component.DatePickerDialog(
            initialDate = state.entryDate,
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

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "Meal",
                onBack = onBack,
                actions = {
                    if (state.item != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(40.dp).glass(CircleShape).clickable { showCopyPicker = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy to another date",
                                    tint = Cream,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.size(40.dp).glass(CircleShape).clickable { viewModel.saveToCollection() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (state.savedToCollection) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Save to collection",
                                    tint = if (state.savedToCollection) GoldLight else Cream,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.size(40.dp).glass(CircleShape).clickable { showDeleteDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Cream, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    }
                    state.item != null -> {
                        val item = state.item!!
                        val micros = Micronutrients(
                            vitaminAMcg = item.vitaminA, vitaminCMg = item.vitaminC, vitaminDMcg = item.vitaminD,
                            calciumMg = item.calcium, ironMg = item.iron, potassiumMg = item.potassium, sodiumMg = item.sodium,
                            vitaminB12Mcg = item.vitaminB12, folateMcg = item.folate, vitaminB6Mg = item.vitaminB6,
                            magnesiumMg = item.magnesium, zincMg = item.zinc, vitaminEMg = item.vitaminE
                        )
                        val breakdown = HealthScorer.breakdown(
                            item.calories, item.protein, item.fat, item.carbs, item.fiber, item.grams, item.isDrink, micros
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (!item.photoPath.isNullOrBlank()) {
                                item {
                                    AsyncImage(
                                        model = item.photoPath, contentDescription = item.name,
                                        modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(20.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            item { HeaderCard(item, state.mealType, onMealTypeSelected = viewModel::setMealType) }
                            item {
                                IngredientsCard(
                                    ingredients = state.ingredients,
                                    isDrink = item.isDrink,
                                    totalGrams = item.grams,
                                    onScaleTo = viewModel::scaleMealTo,
                                    onGramsChanged = viewModel::updateIngredientGrams,
                                    onRemove = viewModel::removeIngredient,
                                    onReplace = { index -> viewModel.clearSearch(); replacingIndex = index },
                                    onAdd = { showAddDialog = true },
                                    onEditWithAi = { showEditWithAi = true }
                                )
                            }
                            item { MacroCard(item) }
                            if (!micros.isEmpty) {
                                item { MicronutrientCard(micros) }
                            }
                            item {
                                AiInsightsArea(
                                    state = state,
                                    breakdown = breakdown,
                                    onGenerate = viewModel::generateAiInsights,
                                    onRegenerate = viewModel::regenerateInsights
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditWithAi) {
        EditWithAiDialog(
            foodLabel = state.item?.name.orEmpty(),
            loading = state.isRefiningWithAi,
            onSubmit = { viewModel.refineWithAi(it) },
            onDismiss = { showEditWithAi = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete entry?") },
            text = { Text("This will remove \"${state.item?.name}\" from your log.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.deleteEntry() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
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
            isDrink = state.item?.isDrink == true,
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
            isDrink = state.item?.isDrink == true,
            aiLoading = state.isAiAddingIngredient,
            onQueryChange = viewModel::onSearchQueryChange,
            onPick = { food -> viewModel.replaceIngredient(idx, food); replacingIndex = null },
            onAiAdd = { text -> viewModel.replaceIngredientWithAi(idx, text) },
            onDismiss = { viewModel.clearSearch(); replacingIndex = null },
            title = "Replace ingredient",
            aiVerb = "Use"
        )
    }
}

/** Vitamins & minerals for this meal — collapsed by default, only the ones actually present. */
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
private fun HeaderCard(item: MealLogItemEntity, mealType: String, onMealTypeSelected: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().glass().padding(18.dp)) {
        Text(item.name, style = MaterialTheme.typography.headlineMedium, color = Cream)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${item.grams.toInt()} ${if (item.isDrink) "ml" else "g"}", style = MaterialTheme.typography.bodyLarge, color = CreamMuted)
            Text("${item.calories.toInt()} kcal", style = MaterialTheme.typography.bodyLarge, color = CalorieColor)
        }
        // Meal category, compact and on one line, right in the header.
        Spacer(Modifier.height(14.dp))
        val mealIndex = MealTypes.ALL.indexOf(mealType).coerceAtLeast(0)
        SegmentedPills(
            labels = MealTypes.ALL.map { MealTypes.label(it) },
            selectedIndex = mealIndex,
            accent = GoldLight,
            onSelect = { i -> onMealTypeSelected(MealTypes.ALL[i]) }
        )
        // Which AI logged this meal (only on entries saved after the online/offline split).
        AiSource.fromName(item.aiSource)?.let {
            Spacer(Modifier.height(10.dp))
            AiSourceBadge(it)
        }
    }
}

@Composable
private fun IngredientsCard(
    ingredients: List<Ingredient>,
    isDrink: Boolean,
    totalGrams: Float,
    onScaleTo: (Float) -> Unit,
    onGramsChanged: (index: Int, newGrams: Float) -> Unit,
    onRemove: (index: Int) -> Unit,
    onReplace: (index: Int) -> Unit,
    onAdd: () -> Unit,
    onEditWithAi: () -> Unit
) {
    val unit = if (isDrink) "ml" else "g"
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Ingredients", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(bottom = 8.dp))
        // Whole-meal size lives here now: edit the total to scale every ingredient together.
        if (ingredients.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total weight", style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f))
                com.fitpal.app.ui.component.GramsField(
                    grams = totalGrams,
                    onGramsChanged = onScaleTo,
                    modifier = Modifier.width(100.dp),
                    unit = unit
                )
            }
        }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.glassSoft(CircleShape).clickable(onClick = onAdd).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add ingredient", style = MaterialTheme.typography.labelLarge, color = Cream)
            }
            // The same whole-dish correction the pre-log screen offers — a mistake shouldn't
            // become permanent just because the meal was already saved.
            Row(
                modifier = Modifier.glassSoft(CircleShape).clickable(onClick = onEditWithAi).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit with AI", style = MaterialTheme.typography.labelLarge, color = Cream)
            }
        }
    }
}

@Composable
private fun MacroCard(item: MealLogItemEntity) {
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Macros", style = MaterialTheme.typography.titleSmall, color = Cream)
        Spacer(Modifier.height(12.dp))
        MacroRow("Protein", item.protein, ProteinColor, kcalPerGram = 4)
        MacroRow("Fat", item.fat, FatColor, kcalPerGram = 9)
        MacroRow("Carbs", item.carbs, CarbColor, kcalPerGram = 4)
        // Fibre is part of carbs — show its grams but no separate kcal (it'd double-count).
        MacroRow("Fiber", item.fiber, FiberColor, kcalPerGram = null)
        if (item.isDrink && item.waterMl > 0) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Water content", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                Text("${item.waterMl.toInt()} ml", style = MaterialTheme.typography.bodyMedium, color = Cream)
            }
        }
    }
}

@Composable
private fun MacroRow(label: String, grams: Float, color: Color, kcalPerGram: Int?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f))
        Text(String.format("%.1fg", grams), style = MaterialTheme.typography.bodyMedium, color = Cream)
        if (kcalPerGram != null) {
            Text(
                "  (${(grams * kcalPerGram).toInt()} kcal)",
                style = MaterialTheme.typography.bodySmall, color = CreamMuted
            )
        }
    }
}

@Composable
private fun AiInsightsArea(
    state: EntryDetailUiState,
    breakdown: List<HealthScorer.Dimension>,
    onGenerate: () -> Unit,
    onRegenerate: () -> Unit
) {
    when {
        state.isLoadingAi -> {
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
        state.insights != null -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MealInsightsSection(state.insights, breakdown = breakdown)
                state.insightsSource?.let { AiSourceBadge(it) }
                if (state.modelReady) {
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
                if (state.modelReady) {
                    Row(
                        modifier = Modifier.glassSoft(CircleShape).clickable(onClick = onGenerate).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generate full AI analysis", style = MaterialTheme.typography.labelLarge, color = Cream)
                    }
                } else {
                    Text("Set up the AI model to analyse this meal.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                }
            }
        }
    }
}

