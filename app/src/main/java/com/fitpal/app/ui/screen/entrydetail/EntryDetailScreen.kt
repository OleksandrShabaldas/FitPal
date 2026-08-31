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
import androidx.compose.material.icons.filled.Edit
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
import com.fitpal.app.ui.component.AiInsightsArea
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.CoachingTipCard
import com.fitpal.app.ui.component.EditWithAiDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.MealContextSelector
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.IngredientsCard
import com.fitpal.app.ui.component.MealInsightsSection
import com.fitpal.app.ui.component.MicronutrientCard
import com.fitpal.app.ui.component.MicronutrientBars
import com.fitpal.app.ui.component.RenameDialog
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.theme.CarbColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.FatColor
import com.fitpal.app.ui.theme.FiberColor
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.ProteinColor
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import com.fitpal.app.ui.component.AddIngredientTab
import com.fitpal.app.ui.component.AddIngredientTabsDialog
import com.fitpal.app.ui.component.BarcodeScannerView
import com.fitpal.app.ui.component.CustomEntryState
import com.fitpal.app.ui.component.PhotoCaptureOverlay
import com.fitpal.app.ui.component.copyLabelToCache
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var showRename by remember { mutableStateOf(false) }

    // "Add ingredient" popup: which tab, the Custom form, and the two full-screen cameras (barcode
    // scan + label snap). The form/tab are hoisted so they survive the popup closing while a camera
    // is open, then reopening. The cameras are rendered at the screen root (below), so the popup is
    // closed while one is up and reopened after.
    val scope = rememberCoroutineScope()
    val customForm = remember { CustomEntryState() }
    var addTab by remember { mutableStateOf(AddIngredientTab.DATABASE) }
    var showScanner by remember { mutableStateOf(false) }
    var showLabelCamera by remember { mutableStateOf(false) }
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
            showAddDialog = true
            if (path != null) readLabel(path)
            else android.widget.Toast.makeText(context, "Couldn't open that image", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val pickBarcode = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            if (image == null) android.widget.Toast.makeText(context, "Couldn't open that image", android.widget.Toast.LENGTH_SHORT).show()
            else BarcodeScanning.getClient().process(image)
                .addOnSuccessListener { codes ->
                    val code = codes.firstOrNull()?.rawValue
                    if (!code.isNullOrBlank()) viewModel.scanBarcodeIngredient(code)
                    else android.widget.Toast.makeText(context, "No barcode found in that image", android.widget.Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { android.widget.Toast.makeText(context, "Couldn't read that image", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }

    LaunchedEffect(state.addMessage) {
        state.addMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearAddMessage()
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
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
                            item {
                                HeaderCard(
                                    item = item,
                                    mealType = state.mealType,
                                    onMealTypeSelected = viewModel::setMealType,
                                    onRename = { showRename = true }
                                )
                            }
                            state.coachingTip?.let { tip ->
                                item { CoachingTipCard(tip) }
                            }
                            item {
                                Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                                    Text(
                                        "Where was this meal? (optional)",
                                        style = MaterialTheme.typography.labelMedium, color = CreamMuted
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    MealContextSelector(selected = state.context, onSelected = viewModel::setMealContext)
                                }
                            }
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
                                    onEditWithAi = { showEditWithAi = true },
                                    servings = state.servings,
                                    onServingsChanged = viewModel::setServings
                                )
                            }
                            item { MacroCard(item) }
                            if (!micros.isEmpty) {
                                item { MicronutrientCard(micros) }
                            }
                            item {
                                AiInsightsArea(
                                    isLoadingAi = state.isLoadingAi,
                                    insights = state.insights,
                                    insightsSource = state.insightsSource,
                                    modelReady = state.modelReady,
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

        // Full-screen cameras for the Add-ingredient popup (barcode scan + label snap). Rendered here,
        // above the content, while the popup is closed; the popup reopens afterward.
        if (showScanner) {
            Box(modifier = Modifier.fillMaxSize()) {
                BarcodeScannerView(
                    onBarcode = { code -> showScanner = false; viewModel.scanBarcodeIngredient(code) },
                    onPickFromGallery = {
                        showScanner = false
                        pickBarcode.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                IconButton(
                    onClick = { showScanner = false; showAddDialog = true },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(48.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close scanner", tint = Color.White)
                }
            }
        }
        if (showLabelCamera) {
            PhotoCaptureOverlay(
                tip = "Point at the product's nutrition facts label",
                onCaptured = { path -> showLabelCamera = false; showAddDialog = true; readLabel(path) },
                onClose = { showLabelCamera = false; showAddDialog = true },
                onPickFromGallery = {
                    showLabelCamera = false
                    pickLabel.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }
    }

    if (showRename) {
        RenameDialog(
            currentName = state.item?.name.orEmpty(),
            title = "Rename this entry",
            label = "Dish name",
            hint = "Only the name changes — the amount, ingredients and nutrition stay as logged.",
            onConfirm = { viewModel.renameEntry(it); showRename = false },
            onDismiss = { showRename = false }
        )
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
        AddIngredientTabsDialog(
            selectedTab = addTab,
            onTabChange = { addTab = it },
            query = state.searchQuery,
            results = state.searchResults,
            isDrink = state.item?.isDrink == true,
            aiLoading = state.isAiAddingIngredient,
            onQueryChange = viewModel::onSearchQueryChange,
            onPick = { food -> viewModel.addIngredient(food); showAddDialog = false },
            onAiAdd = { text -> viewModel.addIngredientWithAi(text) },
            customForm = customForm,
            onAddCustom = { viewModel.addCustomIngredient(it); showAddDialog = false },
            onSnapLabel = { showAddDialog = false; showLabelCamera = true },
            barcodeLookingUp = state.barcodeLookingUp,
            onScanBarcode = { showAddDialog = false; openScanner() },
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
private fun HeaderCard(
    item: MealLogItemEntity,
    mealType: String,
    onMealTypeSelected: (String) -> Unit,
    onRename: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().glass().padding(18.dp)) {
        // The name is the one thing here you edit by tapping it — everything the app can name
        // (the AI, the food database, a barcode) can get it wrong or word it oddly.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onRename)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Cream,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.Edit,
                contentDescription = "Rename ${item.name}",
                tint = CreamMuted,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        // Prominent weight + calories, matching the multi-dish meal screen's summary card.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${item.grams.toInt()} ${if (item.isDrink) "ml" else "g"}",
                style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.weight(1f)
            )
            Text(
                "${item.calories.toInt()} kcal",
                style = MaterialTheme.typography.titleLarge, color = GoldLight
            )
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
        AiSource.fromName(item.aiSource, item.aiModel)?.let {
            Spacer(Modifier.height(10.dp))
            AiSourceBadge(it)
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


