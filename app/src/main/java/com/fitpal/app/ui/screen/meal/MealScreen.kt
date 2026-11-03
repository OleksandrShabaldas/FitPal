package com.fitpal.app.ui.screen.meal

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.MealContext
import com.fitpal.app.domain.model.weightLabel
import com.fitpal.app.ui.component.AddIngredientDialog
import com.fitpal.app.ui.component.AddIngredientTab
import com.fitpal.app.ui.component.AddIngredientTabsDialog
import com.fitpal.app.ui.component.AiInsightsArea
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.BarcodeScannerView
import com.fitpal.app.ui.component.CoachingTipCard
import com.fitpal.app.ui.component.CustomEntryState
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.EditWithAiDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MealDetailsSheet
import com.fitpal.app.ui.component.MealInsightsSection
import com.fitpal.app.ui.component.MealItemCard
import com.fitpal.app.ui.component.MealItemContent
import com.fitpal.app.ui.component.MealPhoto
import com.fitpal.app.ui.component.MealTimeLine
import com.fitpal.app.ui.component.MealTypePills
import com.fitpal.app.ui.component.MicronutrientCard
import com.fitpal.app.ui.component.NutritionCard
import com.fitpal.app.ui.component.PhotoCaptureOverlay
import com.fitpal.app.ui.component.RenameDialog
import com.fitpal.app.ui.component.copyLabelToCache
import com.fitpal.app.ui.component.dishesTitle
import com.fitpal.app.ui.component.kit.FpAlertDialog
import com.fitpal.app.ui.component.rememberFastingGuard
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Meal page (docs/redesign/16): one page for every logged meal, built like the analysis it came
 * from — the name as the title, the photo with the model on it, the time as a line of text, the
 * nutrition card, the coach's note, a card per dish (all open), vitamins, insights, and the meal
 * pills at the bottom. Replaced the meal group overview and the single-entry screen.
 */
@Composable
fun MealScreen(
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit = {},
    viewModel: MealViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    // A copy onto today is eating now, so it goes through the same fasting gate as a fresh log.
    val fastingGuard = rememberFastingGuard()
    val dishes = state.dishes
    val oneDish = dishes.size == 1

    // Leave once the meal is gone (deleted, or its last dish removed). Guarded so it fires once.
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading, dishes, state.deleted, state.notFound) {
        if (!popped && (state.deleted || state.notFound || (!state.isLoading && dishes.isEmpty()))) {
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
    LaunchedEffect(state.addMessage) {
        state.addMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearAddMessage()
        }
    }
    LaunchedEffect(state.refineError) {
        state.refineError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearRefineError()
        }
    }

    // What's open: the details sheet, a copy picker (the meal, or one dish), rename, delete, and the
    // per-dish dialogs (add / replace an ingredient, Edit with AI).
    var showDetails by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf<Long?>(null) }   // MEAL = the whole meal, else a dish's item id
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addingTo by remember { mutableStateOf<Long?>(null) }
    var replacing by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var editingWithAi by remember { mutableStateOf<Long?>(null) }
    // Every dish starts open; these are the ones folded by hand.
    var folded by remember { mutableStateOf(emptySet<Long>()) }

    if (showDetails) {
        MealDetailsSheet(
            // A logged meal moves to another day by copying, so no day choice here.
            logDate = null, onPickDay = null,
            eatenAt = state.eatenAt, onPickTime = viewModel::setMealTime,
            tags = MealContext.parse(state.context), onToggleTag = viewModel::toggleMealContext,
            onDismiss = { showDetails = false }
        )
    }
    copying?.let { target ->
        DatePickerDialog(
            initialDate = state.date,
            title = if (target == MEAL || oneDish) "Copy to which day?" else "Copy this dish to which day?",
            confirmLabel = "Copy here",
            mealTypeChooser = true,
            initialMealType = state.mealType,
            copiesChooser = true,
            onConfirmMeal = { date, meal, copies ->
                copying = null
                val copied = if (target == MEAL) dishes.map { it.item } else dishes.filter { it.item.id == target }.map { it.item }
                fastingGuard.attempt(
                    isForToday = date == java.time.LocalDate.now(),
                    intake = com.fitpal.app.domain.model.FastingIntake.of(copied.map { it.isDrink to it.calories })
                ) { decision ->
                    if (target == MEAL) viewModel.copyToDate(date, meal, copies, decision)
                    else viewModel.copyDishToDate(target, date, meal, copies, decision)
                }
            },
            onConfirm = {},
            onDismiss = { copying = null }
        )
    }
    if (renaming) {
        if (oneDish) {
            RenameDialog(
                currentName = dishes.first().item.name,
                title = "Rename this meal",
                label = "Name",
                hint = "Only the name changes — the amount, ingredients and nutrition stay as logged.",
                onConfirm = { viewModel.renameDish(dishes.first().item.id, it); renaming = false },
                onDismiss = { renaming = false }
            )
        } else {
            RenameDialog(
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
    if (confirmDelete) {
        FpAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (oneDish) "Delete this meal?" else "Delete this whole meal?") },
            text = {
                Text(
                    if (oneDish) "This removes \"${dishes.firstOrNull()?.item?.name}\" from your log."
                    else "This removes all ${dishes.size} dishes in this meal from your log."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteMeal() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    editingWithAi?.let { itemId ->
        // Close once the re-read lands (an error toasts instead).
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(state.refiningItemId) {
            if (state.refiningItemId == itemId) started = true
            else if (started) { started = false; editingWithAi = null }
        }
        EditWithAiDialog(
            foodLabel = dishes.firstOrNull { it.item.id == itemId }?.item?.name.orEmpty(),
            loading = state.refiningItemId == itemId,
            onSubmit = { viewModel.refineWithAi(itemId, it) },
            onDismiss = { editingWithAi = null }
        )
    }

    // "Add ingredient": which tab, the Custom form, and the two full-screen cameras (barcode scan +
    // label snap). Hoisted so they survive the popup closing while a camera is open, then reopening.
    val customForm = remember { CustomEntryState() }
    var addTab by remember { mutableStateOf(AddIngredientTab.DATABASE) }
    var showScanner by remember { mutableStateOf(false) }
    var showLabelCamera by remember { mutableStateOf(false) }
    var reopenAddFor by remember { mutableStateOf<Long?>(null) }
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        if (granted) showScanner = true
    }
    fun openScanner() { if (hasCamera) showScanner = true else cameraPermission.launch(Manifest.permission.CAMERA) }
    fun readLabel(path: String) {
        scope.launch {
            customForm.isReadingLabel = true
            val food = viewModel.readNutritionLabel(path)
            customForm.isReadingLabel = false
            if (food != null && food.totalGrams > 0f && food.totalCalories > 0f) customForm.fillFromLabel(food)
            else android.widget.Toast.makeText(context, "Couldn't read that label — try again, or type the values in.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val pickLabel = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) { copyLabelToCache(context, uri) }
            addingTo = reopenAddFor
            if (path != null) readLabel(path)
            else android.widget.Toast.makeText(context, "Couldn't open that image", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val pickBarcode = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = reopenAddFor
        if (uri != null && target != null) {
            val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            if (image == null) android.widget.Toast.makeText(context, "Couldn't open that image", android.widget.Toast.LENGTH_SHORT).show()
            else BarcodeScanning.getClient().process(image)
                .addOnSuccessListener { codes ->
                    val code = codes.firstOrNull()?.rawValue
                    if (!code.isNullOrBlank()) viewModel.scanBarcodeIngredient(target, code)
                    else android.widget.Toast.makeText(context, "No barcode found in that image", android.widget.Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { android.widget.Toast.makeText(context, "Couldn't read that image", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }

    addingTo?.let { itemId ->
        var aiStarted by remember { mutableStateOf(false) }
        LaunchedEffect(state.isAiAddingIngredient) {
            if (state.isAiAddingIngredient) aiStarted = true
            else if (aiStarted) { aiStarted = false; addingTo = null }
        }
        AddIngredientTabsDialog(
            selectedTab = addTab,
            onTabChange = { addTab = it },
            query = state.searchQuery,
            results = state.searchResults,
            isDrink = dishes.firstOrNull { it.item.id == itemId }?.item?.isDrink == true,
            aiLoading = state.isAiAddingIngredient,
            onQueryChange = viewModel::onSearchQueryChange,
            onPick = { food -> viewModel.addIngredient(itemId, food); addingTo = null },
            onAiAdd = { text -> viewModel.addIngredientWithAi(itemId, text) },
            customForm = customForm,
            onAddCustom = { viewModel.addCustomIngredient(itemId, it); addingTo = null },
            onSnapLabel = { reopenAddFor = itemId; addingTo = null; showLabelCamera = true },
            barcodeLookingUp = state.barcodeLookingUp,
            onScanBarcode = { reopenAddFor = itemId; addingTo = null; openScanner() },
            onDismiss = { viewModel.clearSearch(); addingTo = null }
        )
    }
    replacing?.let { (itemId, index) ->
        var aiStarted by remember { mutableStateOf(false) }
        LaunchedEffect(state.isAiAddingIngredient) {
            if (state.isAiAddingIngredient) aiStarted = true
            else if (aiStarted) { aiStarted = false; replacing = null }
        }
        AddIngredientDialog(
            query = state.searchQuery,
            results = state.searchResults,
            isDrink = dishes.firstOrNull { it.item.id == itemId }?.ingredients?.getOrNull(index)?.isDrink == true,
            aiLoading = state.isAiAddingIngredient,
            onQueryChange = viewModel::onSearchQueryChange,
            onPick = { food -> viewModel.replaceIngredient(itemId, index, food); replacing = null },
            onAiAdd = { text -> viewModel.replaceIngredientWithAi(itemId, index, text) },
            onDismiss = { viewModel.clearSearch(); replacing = null },
            title = "Replace ingredient",
            aiVerb = "Use"
        )
    }

    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        GradientBackdrop(theme = BackdropTheme.TODAY) {
            Column(Modifier.fillMaxSize()) {
                GlassTopBar(
                    title = when {
                        dishes.isEmpty() -> "Meal"
                        oneDish -> dishes.first().item.name
                        else -> state.mealName ?: dishesTitle(dishes.map { it.item.name })
                    },
                    onBack = onBack,
                    titleMaxLines = 2,
                    onTitleClick = if (dishes.isNotEmpty()) ({ renaming = true }) else null,
                    actions = {
                        if (dishes.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BarButton(Icons.Default.ContentCopy, "Copy to another day", Cream) { copying = MEAL }
                                Spacer(Modifier.width(8.dp))
                                val allSaved = dishes.all { state.saved(it.item.id) }
                                BarButton(
                                    if (allSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    if (oneDish) "Save to collection" else "Save every dish to collection",
                                    if (allSaved) GoldLight else Cream
                                ) { viewModel.saveAllToCollection() }
                                Spacer(Modifier.width(8.dp))
                                BarButton(Icons.Default.Delete, "Delete", Cream) { confirmDelete = true }
                            }
                        }
                    }
                )

                if (state.isLoading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    // Opened at a dish (a single row on Home, the Water page…): scroll to it once.
                    var focused by remember { mutableStateOf(false) }
                    val photo = state.photoPath
                    val lead = (if (photo != null || state.aiSource != null) 1 else 0) + 1 + (if (state.plannedMealId != null) 1 else 0) +
                        1 + (if (state.coachingTip != null) 1 else 0)
                    LaunchedEffect(state.focusItemId, dishes.size) {
                        val i = dishes.indexOfFirst { it.item.id == state.focusItemId }
                        if (!focused && !oneDish && i >= 0) { focused = true; listState.animateScrollToItem(lead + i) }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (photo != null) {
                            item(key = "photo") { MealPhoto(model = photo, source = state.aiSource) }
                        } else state.aiSource?.let { source ->
                            // No photo to carry it: the model's badge on its own line.
                            item(key = "source") { AiSourceBadge(source) }
                        }
                        item(key = "time") {
                            MealTimeLine(
                                logDate = state.date, eatenAt = state.eatenAt,
                                tags = MealContext.parse(state.context), onClick = { showDetails = true }
                            )
                        }
                        state.plannedMealId?.let { pid -> item(key = "recipe") { ViewRecipeRow { onOpenRecipe(pid) } } }
                        item(key = "nutrition") {
                            NutritionCard(
                                weight = weightLabel(dishes.flatMap { it.ingredients }) + if (dishes.size > 1) " · ${dishes.size} dishes" else "",
                                kcal = state.totalCalories.toInt(),
                                protein = state.totalProtein, fat = state.totalFat, carbs = state.totalCarbs, fiber = state.totalFiber
                            )
                        }
                        state.coachingTip?.let { tip -> item(key = "coach") { CoachingTipCard(tip) } }
                        itemsIndexed(dishes, key = { _, d -> d.item.id }) { _, d ->
                            val id = d.item.id
                            MealItemCard(
                                content = d.toMealItemContent(),
                                showName = !oneDish,
                                collapsed = !oneDish && id in folded,
                                onExpand = if (oneDish) null else ({ folded = if (id in folded) folded - id else folded + id }),
                                onIngredientGramsChanged = { i, g -> viewModel.updateIngredientGrams(id, i, g) },
                                onIngredientRemoved = { i -> viewModel.removeIngredient(id, i) },
                                onIngredientDrinkChanged = { i, drink -> viewModel.setIngredientDrink(id, i, drink) },
                                onReplaceIngredient = { i -> viewModel.clearSearch(); replacing = id to i },
                                onTotalGramsChanged = { g -> viewModel.scaleDishToGrams(id, g) },
                                onServingsChanged = { n -> viewModel.setServings(id, n) },
                                onAddIngredient = { viewModel.clearSearch(); addingTo = id },
                                onEditWithAi = { editingWithAi = id },
                                onRename = { viewModel.renameDish(id, it) },
                                onRemove = if (oneDish) null else ({ folded = folded - id; viewModel.removeDish(id) }),
                                onToggleSave = if (oneDish) null else ({ viewModel.saveDishToCollection(id) }),
                                saved = state.saved(id),
                                onCopy = if (oneDish) null else ({ copying = id })
                            )
                        }
                        // Vitamins and minerals for the whole meal (only the ones it has).
                        val micros = dishes.fold(com.fitpal.app.domain.model.Micronutrients()) { acc, d -> acc + microsOf(d.item) }
                        if (!micros.isEmpty) item(key = "micros") { MicronutrientCard(micros) }
                        // The overview: the meal's own when every dish carries it (logged from Analysis),
                        // else each dish's.
                        val shared = state.sharedInsights
                        when {
                            shared != null -> item(key = "insights") {
                                MealInsightsSection(shared, breakdown = mealBreakdown(dishes))
                            }
                            else -> dishes.forEach { d ->
                                item(key = "insights_${d.item.id}") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (!oneDish) Text(d.item.name, style = MaterialTheme.typography.titleSmall, color = CreamMuted)
                                        AiInsightsArea(
                                            isLoadingAi = d.loadingInsights,
                                            insights = d.insights,
                                            insightsSource = d.insightsSource,
                                            modelReady = state.modelReady,
                                            breakdown = breakdownOf(d),
                                            onGenerate = { viewModel.generateInsights(d.item.id) },
                                            onRegenerate = { viewModel.regenerateInsights(d.item.id) },
                                            subject = if (oneDish) "meal" else "dish"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Which meal it was: the same pills as before logging, in the same place.
                    MealTypePills(state.mealType, viewModel::setMealType, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp))
                }
            }
        }

        // Full-screen cameras for the Add-ingredient popup (barcode scan + label snap), drawn above the
        // page while the popup is closed; the popup reopens afterwards.
        if (showScanner) {
            Box(Modifier.fillMaxSize()) {
                BarcodeScannerView(
                    onBarcode = { code -> showScanner = false; reopenAddFor?.let { viewModel.scanBarcodeIngredient(it, code) } },
                    onPickFromGallery = {
                        showScanner = false
                        pickBarcode.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                IconButton(
                    onClick = { showScanner = false; addingTo = reopenAddFor },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(48.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close scanner", tint = Color.White)
                }
            }
        }
        if (showLabelCamera) {
            PhotoCaptureOverlay(
                tip = "Point at the product's nutrition facts label",
                onCaptured = { path -> showLabelCamera = false; addingTo = reopenAddFor; readLabel(path) },
                onClose = { showLabelCamera = false; addingTo = reopenAddFor },
                onPickFromGallery = {
                    showLabelCamera = false
                    pickLabel.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }
    }
}

/** The whole meal, in the copy picker's "which" (vs a dish's item id). */
private const val MEAL = -1L

@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).glass(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** "View recipe ›" on a meal logged from the planner (docs/redesign/10). */
@Composable
internal fun ViewRecipeRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().glass().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("View recipe", style = MaterialTheme.typography.bodyLarge, color = Cream, modifier = Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleLarge, color = CreamMuted)
    }
}

/** A logged dish for the shared card. */
private fun MealDish.toMealItemContent(): MealItemContent = MealItemContent(
    name = item.name,
    grams = item.grams,
    calories = item.calories,
    protein = item.protein,
    fat = item.fat,
    carbs = item.carbs,
    fiber = item.fiber,
    isDrink = item.isDrink,
    waterMl = item.waterMl,
    servings = item.servings.coerceAtLeast(1),
    ingredients = ingredients
)

private fun breakdownOf(d: MealDish): List<HealthScorer.Dimension> =
    HealthScorer.breakdown(d.item.calories, d.item.protein, d.item.fat, d.item.carbs, d.item.fiber, d.item.grams, d.item.isDrink, microsOf(d.item))

private fun mealBreakdown(dishes: List<MealDish>): List<HealthScorer.Dimension> = HealthScorer.breakdown(
    calories = dishes.sumOf { it.item.calories.toDouble() }.toFloat(),
    protein = dishes.sumOf { it.item.protein.toDouble() }.toFloat(),
    fat = dishes.sumOf { it.item.fat.toDouble() }.toFloat(),
    carbs = dishes.sumOf { it.item.carbs.toDouble() }.toFloat(),
    fiber = dishes.sumOf { it.item.fiber.toDouble() }.toFloat(),
    grams = dishes.sumOf { it.item.grams.toDouble() }.toFloat(),
    isDrink = dishes.all { it.item.isDrink },
    micros = dishes.fold(com.fitpal.app.domain.model.Micronutrients()) { acc, d -> acc + microsOf(d.item) }
)
