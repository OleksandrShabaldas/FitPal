package com.fitpal.app.ui.screen.analysis

import com.fitpal.app.ui.component.kit.aiGlow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import com.fitpal.app.domain.model.weightLabel
import com.fitpal.app.ui.component.AnalysisPhase
import com.fitpal.app.ui.component.AnalysingStatus
import com.fitpal.app.ui.component.InlineMessageCard
import com.fitpal.app.ui.component.LogBar
import com.fitpal.app.ui.component.MealDetailsPanel
import com.fitpal.app.ui.component.MealDetailsSheet
import com.fitpal.app.ui.component.MealPhoto
import com.fitpal.app.ui.component.MealTimeLine
import com.fitpal.app.ui.component.MealTypePills
import com.fitpal.app.ui.component.NOTE_PHRASES
import com.fitpal.app.ui.component.NoteBox
import com.fitpal.app.ui.component.NutritionCard
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.EditWithAiDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.DietaryWarningDialog
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.AnalysingStrip
import com.fitpal.app.ui.component.dishesTitle
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

    // One page from start to finish (docs/redesign/16): the meal's name as the title, the photo with
    // the model on it, the time as a line of text, then the phase's content; the meal pills and the
    // log bar stay at the bottom until it's logged.
    val phase = state.phase
    val reduceMotion = com.fitpal.app.ui.theme.LookState.appearance.reduceMotion
    val results = phase == AnalysisPhase.RESULTS || phase == AnalysisPhase.SAVING
    val foods = state.detectedFoods
    val oneDish = foods.size == 1
    var showDetails by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // Every dish starts open; these are the ones folded by hand.
    var folded by remember { mutableStateOf(emptySet<Int>()) }
    val totalKcal = foods.sumOf { it.totalCalories.toDouble() }.toInt()

    if (showDetails) {
        MealDetailsSheet(
            logDate = logDate, onPickDay = { showDatePicker = true },
            eatenAt = eatenAt, onPickTime = viewModel::pickEatenTime,
            tags = tags, onToggleTag = viewModel::toggleTag,
            onUsePhotoSuggestion = viewModel::usePhotoSuggestion,
            onDismiss = { showDetails = false }
        )
    }
    if (renaming) {
        if (oneDish) {
            com.fitpal.app.ui.component.RenameDialog(
                currentName = foods.firstOrNull()?.label.orEmpty(),
                title = "Rename this meal",
                label = "Name",
                hint = "Only the name changes — the amount and nutrition stay as they are.",
                onConfirm = { viewModel.renameFood(0, it); renaming = false },
                onDismiss = { renaming = false }
            )
        } else {
            com.fitpal.app.ui.component.RenameDialog(
                currentName = state.mealName.orEmpty(),
                title = "Name this meal",
                label = "Meal name",
                placeholder = "e.g. Sunday roast",
                hint = "Names the whole meal on your day. Leave it empty to name it by its dishes.",
                allowEmpty = true,
                onConfirm = { viewModel.renameMeal(it); renaming = false },
                onDismiss = { renaming = false }
            )
        }
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = when {
                    !results || foods.isEmpty() -> "Analysis"
                    oneDish -> foods.first().label
                    else -> state.mealName ?: dishesTitle(foods.map { it.label })
                },
                onBack = onBack,
                marquee = true,
                onTitleClick = if (results && foods.isNotEmpty()) ({ renaming = true }) else null
            )

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
                    // The list runs under the pills and the Log bar, fading where it meets them (#10).
                    com.fitpal.app.ui.component.FloatingBottomBox(Modifier.weight(1f).fillMaxWidth(), content = { listFade, bottomPad ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().then(listFade),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomPad),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val fade: @Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Modifier =
                            { if (reduceMotion) Modifier else Modifier.animateItem() }

                        item(key = "photo") {
                            MealPhoto(
                                model = state.imageUri,
                                source = state.aiSource.takeIf { results },
                                reason = state.onlineError,
                                working = phase == AnalysisPhase.WORKING
                            )
                        }

                        // While the AI works the details are open on the page (the wait isn't dead time);
                        // otherwise they're one line of text under the photo.
                        if (phase == AnalysisPhase.WORKING) {
                            item(key = "status") { AnalysingStatus(message = state.progressMessage, source = state.aiSource, modifier = fade()) }
                            item(key = "details") {
                                MealDetailsPanel(
                                    logDate = logDate, onPickDay = { showDatePicker = true },
                                    eatenAt = eatenAt, onPickTime = viewModel::pickEatenTime,
                                    tags = tags, onToggleTag = viewModel::toggleTag,
                                    onUsePhotoSuggestion = viewModel::usePhotoSuggestion,
                                    modifier = fade()
                                )
                            }
                        } else {
                            item(key = "time") {
                                MealTimeLine(
                                    logDate, eatenAt, tags, onClick = { showDetails = true },
                                    onUsePhotoSuggestion = viewModel::usePhotoSuggestion
                                )
                            }
                        }

                        when (phase) {
                            AnalysisPhase.READY -> item(key = "note") {
                                NoteBox(
                                    value = state.note,
                                    onValueChange = viewModel::setNote,
                                    placeholder = "e.g. large bowl, ~350 g, cooked in olive oil",
                                    phrases = NOTE_PHRASES
                                )
                            }

                            AnalysisPhase.WORKING -> Unit

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
                                // The meal's numbers, then a card per dish (all open), then the insights.
                                item(key = "nutrition") {
                                    NutritionCard(
                                        weight = weightLabel(foods.flatMap { it.drinkAwareIngredients }) +
                                            if (foods.size > 1) " · ${foods.size} dishes" else "",
                                        kcal = totalKcal,
                                        protein = foods.sumOf { it.totalProtein.toDouble() }.toFloat(),
                                        fat = foods.sumOf { it.totalFat.toDouble() }.toFloat(),
                                        carbs = foods.sumOf { it.totalCarbs.toDouble() }.toFloat(),
                                        fiber = foods.sumOf { it.totalFiber.toDouble() }.toFloat(),
                                        modifier = fade()
                                    )
                                }
                                itemsIndexed(foods, key = { i, _ -> "dish_$i" }) { foodIndex, food ->
                                    // "Not X? …" lives on the dish it's about: the plate's one dish (drinks are never candidates).
                                    val alternatives = if (state.dishCandidates.isNotEmpty() && !food.isDrink && foodIndex == 0) {
                                        state.dishCandidates.mapIndexedNotNull { i, c -> if (i == state.selectedDishIndex) null else i to c.label }
                                    } else emptyList()
                                    MealItemCard(
                                        content = food.toMealItemContent(),
                                        modifier = fade(),
                                        showName = !oneDish,
                                        collapsed = !oneDish && foodIndex in folded,
                                        onExpand = if (oneDish) null else ({
                                            folded = if (foodIndex in folded) folded - foodIndex else folded + foodIndex
                                        }),
                                        alternatives = alternatives.map { it.second },
                                        onPickAlternative = { i -> alternatives.getOrNull(i)?.let { viewModel.selectDish(it.first) } },
                                        onIngredientGramsChanged = { ii, g -> viewModel.updateIngredientGrams(foodIndex, ii, g) },
                                        onIngredientRemoved = { ii -> viewModel.removeIngredient(foodIndex, ii) },
                                        onIngredientDrinkChanged = { ii, drink -> viewModel.setIngredientDrink(foodIndex, ii, drink) },
                                        onTotalGramsChanged = { g -> viewModel.scaleFoodToGrams(foodIndex, g) },
                                        onServingsChanged = { n -> viewModel.setServings(foodIndex, n) },
                                        onWaterChanged = { ml -> viewModel.setFoodWater(foodIndex, ml) },
                                        onPickChoice = { g, o -> viewModel.pickChoice(foodIndex, g, o) },
                                        onAddIngredient = { addIngredientFor = foodIndex },
                                        onEditWithAi = { editWithAiFor = foodIndex },
                                        onRename = { viewModel.renameFood(foodIndex, it) },
                                        onRemove = if (oneDish) null else ({ folded = emptySet(); viewModel.removeFood(foodIndex) }),
                                        onToggleSave = if (oneDish) null else ({
                                            if (state.savedFoodIndices.contains(foodIndex)) viewModel.removeFromGallery(foodIndex)
                                            else viewModel.saveToGallery(foodIndex)
                                        }),
                                        saved = state.savedFoodIndices.contains(foodIndex)
                                    )
                                }

                                // The meal's insights — every one of them stays (the user's note on P14).
                                when {
                                    state.isLoadingInsights -> item(key = "insights_loading") {
                                        AnalysingStrip(message = "Working out the health impact…", source = null, modifier = fade())
                                    }
                                    state.insights != null -> {
                                        item(key = "insights") { MealInsightsSection(state.insights!!, breakdown = mealBreakdown(foods)) }
                                        item(key = "insights_refresh") {
                                            com.fitpal.app.ui.component.SetupPill("✦ Refresh AI analysis", selected = false, modifier = Modifier.aiGlow()) { viewModel.generateInsights() }
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
                                            com.fitpal.app.ui.component.GoldPill("Generate AI analysis", { viewModel.generateInsights() }, modifier = Modifier.aiGlow())
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }) {
                    // ---- The meal pills and the log bar: the same place in every phase ----
                    MealTypePills(mealType, viewModel::setMealType, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
                    LogBar(
                        totalKcal = totalKcal.takeIf { phase == AnalysisPhase.RESULTS },
                        status = if (phase == AnalysisPhase.SAVING) "Saving…" else null,
                        actionLabel = when (phase) {
                            AnalysisPhase.READY -> "✦ Analyse"
                            AnalysisPhase.SAVING -> "Saving…"
                            else -> "Log meal"
                        },
                        actionEnabled = phase == AnalysisPhase.READY || phase == AnalysisPhase.RESULTS,
                        wideAction = phase == AnalysisPhase.READY,
                        onCancel = if (phase == AnalysisPhase.WORKING) viewModel::stopAnalysis else null,
                        onAction = {
                            if (phase == AnalysisPhase.READY) viewModel.startAnalysis()
                            else fastingGuard.attempt(
                                isForToday = logDate == java.time.LocalDate.now(),
                                eatenAt = eatenAt.time,
                                photoTime = eatenAt.isPhotoTime,
                                intake = com.fitpal.app.domain.model.FastingIntake.ofFoods(foods)
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
