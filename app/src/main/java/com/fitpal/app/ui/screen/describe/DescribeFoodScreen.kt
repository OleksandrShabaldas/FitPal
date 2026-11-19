package com.fitpal.app.ui.screen.describe

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.AddIngredientDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Edit
import com.fitpal.app.ui.component.AnalysisPhase
import com.fitpal.app.ui.component.InlineMessageCard
import com.fitpal.app.ui.component.LogBar
import com.fitpal.app.ui.component.MealDetailsPanel
import com.fitpal.app.ui.component.MealDetailsSheet
import com.fitpal.app.ui.component.MealTimeLine
import com.fitpal.app.ui.component.MealTypePills
import com.fitpal.app.ui.component.NoteLine
import com.fitpal.app.ui.component.NutritionCard
import com.fitpal.app.ui.component.AnalysingStatus
import com.fitpal.app.ui.component.ScanSweep
import com.fitpal.app.ui.component.dishesTitle
import com.fitpal.app.domain.model.weightLabel
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.DietaryWarningDialog
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MealItemCard
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.component.toMealItemContent
import com.fitpal.app.ui.component.rememberFastingGuard
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
    val eatenAt by viewModel.eatenAt.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val fastingGuard = rememberFastingGuard()
    val context = androidx.compose.ui.platform.LocalContext.current
    var addIngredientFor by remember { mutableStateOf<Int?>(null) }
    var editWithAiFor by remember { mutableStateOf<Int?>(null) }
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

    state.dietaryWarning?.let { warning ->
        DietaryWarningDialog(
            warning = warning,
            onConfirm = viewModel::confirmDietaryWarning,
            onDismiss = viewModel::dismissDietaryWarning
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

    // The same page as the photo analysis (docs/redesign/16): the description sits where the photo
    // would, then the time line, the phase's content, and the meal pills + log bar at the bottom.
    val phase = state.phase
    val reduceMotion = com.fitpal.app.ui.theme.LookState.appearance.reduceMotion
    var showDetails by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // Every dish starts open; these are the ones folded by hand.
    var folded by remember { mutableStateOf(emptySet<Int>()) }
    // After results, tapping the description edits it again; the bar then offers "Analyse again".
    var editingDescription by remember { mutableStateOf(false) }
    val editing = phase == AnalysisPhase.READY || editingDescription
    val results = (phase == AnalysisPhase.RESULTS || phase == AnalysisPhase.SAVING) && state.foods.isNotEmpty()
    val oneDish = state.foods.size == 1

    if (showDetails) {
        MealDetailsSheet(
            logDate = logDate, onPickDay = { showDatePicker = true },
            eatenAt = eatenAt, onPickTime = viewModel::pickEatenTime,
            tags = tags, onToggleTag = viewModel::toggleTag,
            onDismiss = { showDetails = false }
        )
    }
    if (renaming) {
        if (oneDish) {
            com.fitpal.app.ui.component.RenameDialog(
                currentName = state.foods.firstOrNull()?.label.orEmpty(),
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

    editWithAiFor?.let { foodIndex ->
        // Closes itself once the correction is in (the same dialog and flow as the photo review, #5).
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(state.aiEditingFoodIndex) {
            if (state.aiEditingFoodIndex == foodIndex) started = true
            else if (started) { started = false; editWithAiFor = null }
        }
        com.fitpal.app.ui.component.EditWithAiDialog(
            foodLabel = state.foods.getOrNull(foodIndex)?.label ?: "this item",
            loading = state.aiEditingFoodIndex == foodIndex,
            onSubmit = { text -> viewModel.refineFoodWithAi(foodIndex, text) },
            onDismiss = { editWithAiFor = null }
        )
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = when {
                    !results -> "Describe to AI"
                    oneDish -> state.foods.first().label
                    else -> state.mealName ?: dishesTitle(state.foods.map { it.label })
                },
                onBack = onBack,
                marquee = true,
                onTitleClick = if (results) ({ renaming = true }) else null
            )

            // The list runs under the pills and the Log bar, fading where it meets them (#10).
            com.fitpal.app.ui.component.FloatingBottomBox(Modifier.weight(1f).fillMaxWidth(), content = { listFade, bottomPad ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().then(listFade),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val fade: @Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Modifier =
                    { if (reduceMotion) Modifier else Modifier.animateItem() }

                item(key = "description") {
                    if (editing) {
                        NoteLine(
                            value = state.description,
                            onValueChange = viewModel::onDescriptionChange,
                            prompt = "Describe your meal",
                            placeholder = "Describe your meal, e.g. \"chilli con carne\" or \"coca-cola\"",
                            startOpen = true,
                            minHeight = 90.dp
                        )
                    } else {
                        // The description as a quote — tap to change it. While the AI reads it, the
                        // gold light sweeps over it (as over a photo).
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
                            Row(
                                Modifier.fillMaxWidth().glass().clickable(enabled = phase != AnalysisPhase.WORKING) { editingDescription = true }.padding(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    "“${state.description.trim()}”",
                                    style = MaterialTheme.typography.titleLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                    color = Cream, modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.Default.Edit, contentDescription = "Change the description", tint = CreamMuted, modifier = Modifier.size(16.dp))
                            }
                            if (phase == AnalysisPhase.WORKING) ScanSweep(Modifier.matchParentSize())
                        }
                    }
                }

                // No photo to carry it, so the model's badge sits under the description.
                if (results) state.aiSource?.let { source ->
                    item(key = "source") { com.fitpal.app.ui.component.AiSourceBadge(source, reason = state.onlineError) }
                }

                if (phase == AnalysisPhase.WORKING) {
                    item(key = "status") {
                        AnalysingStatus(
                            message = state.progressMessage.ifBlank { "Thinking… online can take a moment, and the first on-device run is slower." },
                            source = state.aiSource,
                            modifier = fade()
                        )
                    }
                    item(key = "details") {
                        MealDetailsPanel(
                            logDate = logDate, onPickDay = { showDatePicker = true },
                            eatenAt = eatenAt, onPickTime = viewModel::pickEatenTime,
                            tags = tags, onToggleTag = viewModel::toggleTag,
                            modifier = fade()
                        )
                    }
                } else {
                    item(key = "time") { MealTimeLine(logDate, eatenAt, tags, onClick = { showDetails = true }) }
                }

                when {
                    state.needsModel -> item(key = "needs_model") {
                        InlineMessageCard(
                            title = "The AI model isn't downloaded yet",
                            reason = "Describe-to-AI can run on Google's online model, but it also needs the on-device model " +
                                "as a fallback for when you're offline. Download it once and it works without internet after that.",
                            primary = "Set up AI model" to onSetupModel
                        )
                    }

                    phase == AnalysisPhase.READY -> item(key = "tip") {
                        Text(
                            "Name what you ate or drank. Products like \"coca-cola\" come back as one item; dishes like " +
                                "\"chilli con carne\" show their ingredients, and anything the AI can't tell is yours to pick.",
                            style = MaterialTheme.typography.bodySmall, color = CreamMuted
                        )
                    }

                    phase == AnalysisPhase.WORKING -> Unit

                    phase == AnalysisPhase.FAILED -> item(key = "failed") {
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
                                title = "The AI couldn't pull foods from that",
                                reason = "Try rephrasing with the foods named directly, or add it by hand.",
                                primary = "Add by hand" to onGoToManual,
                                modifier = fade()
                            )
                        }
                    }

                    else -> {
                        item(key = "nutrition") {
                            NutritionCard(
                                weight = weightLabel(state.foods.flatMap { it.drinkAwareIngredients }) +
                                    if (state.foods.size > 1) " · ${state.foods.size} dishes" else "",
                                kcal = state.totalCalories.toInt(),
                                protein = state.foods.sumOf { it.totalProtein.toDouble() }.toFloat(),
                                fat = state.foods.sumOf { it.totalFat.toDouble() }.toFloat(),
                                carbs = state.foods.sumOf { it.totalCarbs.toDouble() }.toFloat(),
                                fiber = state.foods.sumOf { it.totalFiber.toDouble() }.toFloat(),
                                modifier = fade()
                            )
                        }
                        itemsIndexed(state.foods, key = { i, _ -> "dish_$i" }) { index, food ->
                            MealItemCard(
                                content = food.toMealItemContent(),
                                modifier = fade(),
                                showName = !oneDish,
                                collapsed = !oneDish && index in folded,
                                onExpand = if (oneDish) null else ({
                                    folded = if (index in folded) folded - index else folded + index
                                }),
                                onIngredientGramsChanged = { ii, g -> viewModel.updateIngredientGrams(index, ii, g) },
                                onIngredientRemoved = { ii -> viewModel.removeIngredient(index, ii) },
                                onIngredientDrinkChanged = { ii, drink -> viewModel.setIngredientDrink(index, ii, drink) },
                                onPickChoice = { g, o -> viewModel.pickChoice(index, g, o) },
                                onServingsChanged = { n -> viewModel.setServings(index, n) },
                                onWaterChanged = { ml -> viewModel.setFoodWater(index, ml) },
                                onEditWithAi = { editWithAiFor = index },
                                onRemove = if (oneDish) null else ({ folded = emptySet(); viewModel.removeFood(index) }),
                                onTotalGramsChanged = { g -> viewModel.scaleFoodToGrams(index, g) },
                                onAddIngredient = { addIngredientFor = index },
                                mealPresets = mealPresets,
                                drinkPresets = drinkPresets,
                                onToggleSave = if (oneDish) null else ({
                                    viewModel.saveToGallery(index)
                                    Toast.makeText(context, "Saved to collection", Toast.LENGTH_SHORT).show()
                                }),
                                saved = food.label in state.savedLabels,
                                onRename = { viewModel.renameFood(index, it) }
                            )
                        }
                    }
                }
            }
            }) {
            MealTypePills(mealType, viewModel::setMealType, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
            val analyse = editing || (phase == AnalysisPhase.FAILED && state.onlineFailedReason == null)
            LogBar(
                totalKcal = state.totalCalories.toInt().takeIf { phase == AnalysisPhase.RESULTS && !editingDescription },
                status = if (phase == AnalysisPhase.SAVING) "Saving…" else null,
                actionLabel = when {
                    phase == AnalysisPhase.SAVING -> "Saving…"
                    editingDescription && phase != AnalysisPhase.READY -> "✦ Analyse again"
                    analyse -> "✦ Analyse"
                    else -> "Log meal"
                },
                actionEnabled = when {
                    analyse -> state.description.isNotBlank() && phase != AnalysisPhase.WORKING
                    else -> phase == AnalysisPhase.RESULTS
                },
                wideAction = analyse && phase != AnalysisPhase.WORKING,
                onCancel = if (phase == AnalysisPhase.WORKING) viewModel::stopAnalysis else null,
                onAction = {
                    if (analyse) {
                        editingDescription = false
                        folded = emptySet()
                        viewModel.analyze()
                    } else {
                        fastingGuard.attempt(
                            isForToday = logDate == java.time.LocalDate.now(),
                            eatenAt = eatenAt.time,
                            intake = com.fitpal.app.domain.model.FastingIntake.ofFoods(state.foods)
                        ) { decision -> viewModel.logMeal(decision) }
                    }
                },
                menu = if (phase == AnalysisPhase.RESULTS && !editingDescription) listOf(
                    "Save all to collection" to { viewModel.saveAllToGallery() }
                ) else emptyList()
            )
            }
        }
    }
}
