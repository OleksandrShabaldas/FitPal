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
import com.fitpal.app.ui.component.MealDetailsRow
import com.fitpal.app.ui.component.MealDetailsSheet
import com.fitpal.app.ui.component.MealHeader
import com.fitpal.app.ui.component.NoteLine
import com.fitpal.app.ui.component.SkeletonCard
import com.fitpal.app.ui.component.AnalysingStrip
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

    // The same page as the photo analysis (docs/redesign/08): the description sits where the photo
    // would, then the details line, the phase's content, and the log bar — nothing moves between phases.
    val phase = state.phase
    val reduceMotion = com.fitpal.app.ui.theme.LookState.appearance.reduceMotion
    var showDetails by remember { mutableStateOf(false) }
    var renamingMeal by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    // After results, tapping the description edits it again; the bar then offers "Analyse again".
    var editingDescription by remember { mutableStateOf(false) }
    val editing = phase == AnalysisPhase.READY || editingDescription

    if (showDetails) {
        MealDetailsSheet(
            mealType = mealType, onMealType = viewModel::setMealType,
            logDate = logDate, onPickDay = { showDatePicker = true },
            eatenAt = eatenAt, onPickTime = viewModel::pickEatenTime,
            tags = tags, onToggleTag = viewModel::toggleTag,
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
            GlassTopBar(title = "Describe to AI", onBack = onBack)

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
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
                        // The description as a quote — tap to change it.
                        Row(
                            Modifier.fillMaxWidth().glass().clickable { editingDescription = true }.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "“${state.description.trim()}”",
                                style = MaterialTheme.typography.titleLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                color = Cream, modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.Edit, contentDescription = "Change the description", tint = CreamMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                item(key = "details") {
                    if (phase == AnalysisPhase.WORKING) {
                        Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                            MealDetailsPanel(
                                mealType = mealType, onMealType = viewModel::setMealType,
                                logDate = logDate, onPickDay = { showDatePicker = true },
                                eatenAt = eatenAt, onPickTime = viewModel::pickEatenTime,
                                tags = tags, onToggleTag = viewModel::toggleTag
                            )
                        }
                    } else {
                        MealDetailsRow(mealType, logDate, eatenAt, tags, onClick = { showDetails = true })
                    }
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
                                "\"chilli con carne\" show their ingredients and variations you can pick.",
                            style = MaterialTheme.typography.bodySmall, color = CreamMuted
                        )
                    }

                    phase == AnalysisPhase.WORKING -> {
                        item(key = "progress") {
                            AnalysingStrip(
                                message = state.progressMessage.ifBlank {
                                    "Thinking… online can take a moment, and the first on-device run is slower."
                                },
                                source = state.aiSource,
                                modifier = fade()
                            )
                        }
                        items(2, key = { "skeleton_$it" }) { SkeletonCard(fade()) }
                    }

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
                        item(key = "header") {
                            MealHeader(
                                title = state.mealName ?: com.fitpal.app.domain.model.MealTypes.label(mealType),
                                kcal = state.totalCalories.toInt(),
                                onRename = { renamingMeal = true },
                                modifier = fade()
                            )
                        }
                        // No photo to carry it, so the model's badge sits under the header.
                        state.aiSource?.let { source ->
                            item(key = "source") { com.fitpal.app.ui.component.AiSourceBadge(source, reason = state.onlineError) }
                        }
                        itemsIndexed(state.foods, key = { i, _ -> "dish_$i" }) { index, food ->
                            MealItemCard(
                                content = food.toMealItemContent(),
                                modifier = fade(),
                                collapsed = expanded != index,
                                onExpand = { expanded = if (expanded == index) null else index },
                                onIngredientGramsChanged = { ii, g -> viewModel.updateIngredientGrams(index, ii, g) },
                                onIngredientRemoved = { ii -> viewModel.removeIngredient(index, ii) },
                                onToggleVariation = { vi -> viewModel.toggleVariation(index, vi) },
                                onRemove = { expanded = null; viewModel.removeFood(index) },
                                onSetDrink = { viewModel.setFoodDrink(index, it) },
                                onTotalGramsChanged = { g -> viewModel.scaleFoodToGrams(index, g) },
                                onAddIngredient = { addIngredientFor = index },
                                mealPresets = mealPresets,
                                drinkPresets = drinkPresets,
                                onToggleSave = {
                                    viewModel.saveToGallery(index)
                                    Toast.makeText(context, "Saved to collection", Toast.LENGTH_SHORT).show()
                                },
                                saved = food.label in state.savedLabels,
                                onRename = { viewModel.renameFood(index, it) }
                            )
                        }
                    }
                }
            }

            LogBar(
                totalKcal = state.totalCalories.toInt().takeIf { phase == AnalysisPhase.RESULTS && !editingDescription },
                status = when (phase) {
                    AnalysisPhase.WORKING -> "Working…"
                    AnalysisPhase.SAVING -> "Saving…"
                    else -> null
                },
                actionLabel = when {
                    phase == AnalysisPhase.SAVING -> "Saving…"
                    editingDescription && phase != AnalysisPhase.READY -> "Analyse again"
                    phase == AnalysisPhase.READY || phase == AnalysisPhase.FAILED && state.onlineFailedReason == null -> "Analyse"
                    else -> "Log meal"
                },
                actionEnabled = when {
                    editing || (phase == AnalysisPhase.FAILED && state.onlineFailedReason == null) ->
                        state.description.isNotBlank() && phase != AnalysisPhase.WORKING
                    else -> phase == AnalysisPhase.RESULTS
                },
                onAction = {
                    if (editing || (phase == AnalysisPhase.FAILED && state.onlineFailedReason == null)) {
                        editingDescription = false
                        expanded = null
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
