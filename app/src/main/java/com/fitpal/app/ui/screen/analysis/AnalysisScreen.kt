package com.fitpal.app.ui.screen.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.DetectedFood
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.ui.component.AnalysisPhase
import com.fitpal.app.ui.component.InlineMessageCard
import com.fitpal.app.ui.component.LogBar
import com.fitpal.app.ui.component.MealDetailsPanel
import com.fitpal.app.ui.component.MealDetailsRow
import com.fitpal.app.ui.component.MealDetailsSheet
import com.fitpal.app.ui.component.MealHeader
import com.fitpal.app.ui.component.MealPhoto
import com.fitpal.app.ui.component.NoteLine
import com.fitpal.app.ui.component.SkeletonCard
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.EditWithAiDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.DietaryWarningDialog
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.AnalysingStrip
import com.fitpal.app.ui.component.rememberFastingGuard
import com.fitpal.app.ui.component.MealInsightsSection
import com.fitpal.app.ui.component.MealItemCard
import com.fitpal.app.ui.component.toMealItemContent
import com.fitpal.app.ui.component.AddIngredientDialog
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
    val eatenAt by viewModel.eatenAt.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val fastingGuard = rememberFastingGuard()
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

    state.dietaryWarning?.let { warning ->
        DietaryWarningDialog(
            warning = warning,
            onConfirm = viewModel::confirmDietaryWarning,
            onDismiss = viewModel::dismissDietaryWarning
        )
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
            onConfirmMeal = { date, meal, copies ->
                showCopyPicker = false
                fastingGuard.attempt(
                    isForToday = date == java.time.LocalDate.now(),
                    intake = com.fitpal.app.domain.model.FastingIntake.ofFoods(state.detectedFoods)
                ) { decision ->
                    viewModel.copyToDate(date, meal, copies, decision)
                }
            },
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

    // One page from start to finish (docs/redesign/08): the photo, the details line and the log bar
    // stay put; the phases fill the space between them in place.
    val phase = state.phase
    val reduceMotion = com.fitpal.app.ui.theme.LookState.appearance.reduceMotion
    var showDetails by remember { mutableStateOf(false) }
    var renamingMeal by remember { mutableStateOf(false) }
    // One dish open at a time; all folded to start with.
    var expanded by remember { mutableStateOf<Int?>(null) }
    val totalKcal = state.detectedFoods.sumOf { it.totalCalories.toDouble() }.toInt()

    if (showDetails) {
        MealDetailsSheet(
            mealType = mealType, onMealType = viewModel::setMealType,
            logDate = logDate, onPickDay = { showDatePicker = true },
            eatenAt = eatenAt, onPickTime = viewModel::pickEatenTime,
            tags = tags, onToggleTag = viewModel::toggleTag,
            onUsePhotoSuggestion = viewModel::usePhotoSuggestion,
            onDismiss = { showDetails = false }
        )
    }
    if (renamingMeal) {
        com.fitpal.app.ui.component.RenameDialog(
            currentName = state.mealName.orEmpty(),
            title = "Name this meal",
            label = "Meal name",
            placeholder = "e.g. Sunday roast",
            hint = "Names the whole meal on your day. Leave it empty to show the meal type instead.",
            allowEmpty = true,
            onConfirm = { viewModel.renameMeal(it); renamingMeal = false },
            onDismiss = { renamingMeal = false }
        )
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Analysis", onBack = onBack)

            when {
                state.needsModel -> Box(Modifier.weight(1f)) {
                    CenteredMessage {
                        Text(
                            "The AI model needs to be downloaded before FitPal can recognise food.",
                            style = MaterialTheme.typography.bodyLarge, color = Cream, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        com.fitpal.app.ui.component.GoldPill("Set up AI model", onSetupModel)
                    }
                }

                state.needsImage -> Box(Modifier.weight(1f)) {
                    CenteredMessage {
                        Text(
                            "No photo to analyse. Use Add, then From Gallery or Take Photo to pick one.",
                            style = MaterialTheme.typography.bodyLarge, color = CreamMuted, textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val fade: @Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Modifier =
                            { if (reduceMotion) Modifier else Modifier.animateItem() }

                        item(key = "photo") {
                            MealPhoto(
                                model = state.imageUri,
                                source = state.aiSource.takeIf { phase == AnalysisPhase.RESULTS || phase == AnalysisPhase.SAVING },
                                reason = state.onlineError
                            )
                        }

                        // The details: open inline while the AI works (the wait isn't dead time), one line otherwise.
                        item(key = "details") {
                            if (phase == AnalysisPhase.WORKING) {
                                Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                                    MealDetailsPanel(
                                        mealType = mealType, onMealType = viewModel::setMealType,
                                        logDate = logDate, onPickDay = { showDatePicker = true },
                                        eatenAt = eatenAt, onPickTime = viewModel::pickEatenTime,
                                        tags = tags, onToggleTag = viewModel::toggleTag,
                                        onUsePhotoSuggestion = viewModel::usePhotoSuggestion
                                    )
                                }
                            } else {
                                MealDetailsRow(mealType, logDate, eatenAt, tags, onClick = { showDetails = true })
                            }
                        }

                        when (phase) {
                            AnalysisPhase.READY -> item(key = "note") {
                                NoteLine(
                                    value = state.note,
                                    onValueChange = viewModel::setNote,
                                    prompt = "Add a note for the AI",
                                    placeholder = "e.g. large bowl, ~350 g, olive oil and feta",
                                    minHeight = 60.dp
                                )
                            }

                            AnalysisPhase.WORKING -> {
                                item(key = "progress") { AnalysingStrip(message = state.progressMessage, source = state.aiSource, modifier = fade()) }
                                items(2, key = { "skeleton_$it" }) { SkeletonCard(fade()) }
                            }

                            AnalysisPhase.FAILED -> item(key = "failed") {
                                if (state.onlineFailedReason != null) {
                                    InlineMessageCard(
                                        title = "Online AI isn't available",
                                        reason = state.onlineFailedReason,
                                        primary = "Try online again" to { viewModel.retryOnline() },
                                        secondary = "Use on-device" to { viewModel.useOnDevice() },
                                        tertiary = "Cancel" to { viewModel.cancelAnalysis() },
                                        modifier = fade()
                                    )
                                } else {
                                    InlineMessageCard(
                                        title = "Something went wrong",
                                        reason = state.error,
                                        primary = "Try again" to { viewModel.startAnalysis() },
                                        modifier = fade()
                                    )
                                }
                            }

                            AnalysisPhase.RESULTS, AnalysisPhase.SAVING -> {
                                // Level 1: the meal. Level 2: its dishes, folded. Level 3: ingredients, inside.
                                item(key = "header") {
                                    MealHeader(
                                        title = state.mealName ?: com.fitpal.app.domain.model.MealTypes.label(mealType),
                                        kcal = totalKcal,
                                        onRename = { renamingMeal = true },
                                        modifier = fade()
                                    )
                                }
                                itemsIndexed(state.detectedFoods, key = { i, _ -> "dish_$i" }) { foodIndex, food ->
                                    // "Looks like…" lives on the dish it's about: the plate's one dish (drinks are never candidates).
                                    val alternatives = if (state.dishCandidates.isNotEmpty() && !food.isDrink && foodIndex == 0) {
                                        state.dishCandidates.mapIndexedNotNull { i, c -> if (i == state.selectedDishIndex) null else i to c.label }
                                    } else emptyList()
                                    MealItemCard(
                                        content = food.toMealItemContent(),
                                        modifier = fade(),
                                        collapsed = expanded != foodIndex,
                                        onExpand = { expanded = if (expanded == foodIndex) null else foodIndex },
                                        alternatives = alternatives.map { it.second },
                                        onPickAlternative = { i -> alternatives.getOrNull(i)?.let { viewModel.selectDish(it.first) } },
                                        onIngredientGramsChanged = { ii, g -> viewModel.updateIngredientGrams(foodIndex, ii, g) },
                                        onIngredientRemoved = { ii -> viewModel.removeIngredient(foodIndex, ii) },
                                        onTotalGramsChanged = { g -> viewModel.scaleFoodToGrams(foodIndex, g) },
                                        onServingsChanged = { n -> viewModel.setServings(foodIndex, n) },
                                        onWaterChanged = { ml -> viewModel.setFoodWater(foodIndex, ml) },
                                        onToggleVariation = { vi -> viewModel.toggleVariation(foodIndex, vi) },
                                        onAddIngredient = { addIngredientFor = foodIndex },
                                        onEditWithAi = { editWithAiFor = foodIndex },
                                        onRename = { viewModel.renameFood(foodIndex, it) },
                                        onSetDrink = { viewModel.setFoodDrink(foodIndex, it) },
                                        onRemove = { expanded = null; viewModel.removeFood(foodIndex) },
                                        onToggleSave = {
                                            if (state.savedFoodIndices.contains(foodIndex)) viewModel.removeFromGallery(foodIndex)
                                            else viewModel.saveToGallery(foodIndex)
                                        },
                                        saved = state.savedFoodIndices.contains(foodIndex)
                                    )
                                }

                                // The meal's insights — every one of them stays (the user's note on P14).
                                when {
                                    state.isLoadingInsights -> item(key = "insights_loading") {
                                        AnalysingStrip(message = "Working out the health impact…", source = null, modifier = fade())
                                    }
                                    state.insights != null -> {
                                        item(key = "insights") { MealInsightsSection(state.insights!!, breakdown = mealBreakdown(state.detectedFoods)) }
                                        item(key = "insights_refresh") {
                                            com.fitpal.app.ui.component.SetupPill("✦ Refresh AI analysis", selected = false) { viewModel.generateInsights() }
                                        }
                                    }
                                    else -> item(key = "insights_offer") {
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
                                            com.fitpal.app.ui.component.GoldPill("Generate AI analysis", { viewModel.generateInsights() })
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ---- The log bar: the same place in every phase ----
                    LogBar(
                        totalKcal = totalKcal.takeIf { phase == AnalysisPhase.RESULTS },
                        status = when (phase) {
                            AnalysisPhase.WORKING -> "Working…"
                            AnalysisPhase.SAVING -> "Saving…"
                            else -> null
                        },
                        actionLabel = when (phase) {
                            AnalysisPhase.READY -> "Analyse"
                            AnalysisPhase.SAVING -> "Saving…"
                            else -> "Log meal"
                        },
                        actionEnabled = phase == AnalysisPhase.READY || phase == AnalysisPhase.RESULTS,
                        onAction = {
                            if (phase == AnalysisPhase.READY) viewModel.startAnalysis()
                            else fastingGuard.attempt(
                                isForToday = logDate == java.time.LocalDate.now(),
                                eatenAt = eatenAt.time,
                                photoTime = eatenAt.isPhotoTime,
                                intake = com.fitpal.app.domain.model.FastingIntake.ofFoods(state.detectedFoods)
                            ) { decision -> viewModel.logMeal(decision) }
                        },
                        menu = if (phase == AnalysisPhase.RESULTS) listOf(
                            "Copy to another day" to { showCopyPicker = true },
                            "Save all to collection" to { viewModel.saveAllToGallery() }
                        ) else emptyList()
                    )
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
