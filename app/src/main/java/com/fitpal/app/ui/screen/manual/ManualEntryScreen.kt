package com.fitpal.app.ui.screen.manual

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.local.entity.UsdaFoodEntity
import com.fitpal.app.domain.model.ServingPreset
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.BarcodeScannerView
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.DietaryWarningDialog
import com.fitpal.app.ui.component.FoodPortionEditor
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MealTotalRow
import com.fitpal.app.ui.component.MealTypeSelector
import com.fitpal.app.ui.component.PhotoCaptureOverlay
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.component.logDateLabel
import com.fitpal.app.ui.component.rememberFastingGuard
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glassSoft
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The meal builder: add one or more foods to a meal via three tabs — **Search** (the food database +
 * "describe to AI"), **Custom** (type your own values or snap a nutrition label), and **Barcode**
 * (scan a packaged product). Every added food lands in "Your meal" below, editable inline, then the
 * whole thing is logged as one meal. Reached from the "Search foods" and "Custom food" entry tiles
 * (each opening on its own tab).
 */
@Composable
fun ManualEntryScreen(
    onLogged: () -> Unit,
    onBack: () -> Unit,
    viewModel: ManualEntryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presets by viewModel.servingPresets.collectAsStateWithLifecycle()
    val drinkPresets by viewModel.drinkPresets.collectAsStateWithLifecycle()
    val mealType by viewModel.mealType.collectAsStateWithLifecycle()
    val logDate by viewModel.logDate.collectAsStateWithLifecycle()
    val fastingGuard = rememberFastingGuard()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    var foodToHide by remember { mutableStateOf<UsdaFoodEntity?>(null) }

    // Custom tab: form state + the (root-level, full-screen) label camera. Lifted here so the
    // camera overlay covers the whole screen, and the form survives switching tabs.
    val customForm = remember { CustomEntryState() }
    var showLabelCamera by remember { mutableStateOf(false) }
    fun readLabel(path: String) {
        scope.launch {
            customForm.isReadingLabel = true
            val food = viewModel.readNutritionLabel(path)
            customForm.isReadingLabel = false
            if (food != null && food.totalGrams > 0f && food.totalCalories > 0f) customForm.fillFromLabel(food)
            else Toast.makeText(context, "Couldn't read that label — try again, or type the values in.", Toast.LENGTH_LONG).show()
        }
    }
    val pickLabel = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) { copyLabelToCache(context, uri) }
            if (path != null) readLabel(path) else Toast.makeText(context, "Couldn't open that image", Toast.LENGTH_SHORT).show()
        }
    }

    // Barcode tab: a full-screen scanner overlay + a "pick a photo of a barcode" fallback.
    var showScanner by remember { mutableStateOf(false) }
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        if (granted) showScanner = true
    }
    fun openScanner() { if (hasCamera) showScanner = true else cameraPermission.launch(Manifest.permission.CAMERA) }
    val pickBarcode = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            if (image == null) Toast.makeText(context, "Couldn't open that image", Toast.LENGTH_SHORT).show()
            else BarcodeScanning.getClient().process(image)
                .addOnSuccessListener { codes ->
                    val code = codes.firstOrNull()?.rawValue
                    if (!code.isNullOrBlank()) viewModel.scanBarcode(code)
                    else Toast.makeText(context, "No barcode found in that image", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { Toast.makeText(context, "Couldn't read that image", Toast.LENGTH_SHORT).show() }
        }
    }

    LaunchedEffect(state.saved) { if (state.saved) onLogged() }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    foodToHide?.let { food ->
        AlertDialog(
            onDismissRequest = { foodToHide = null },
            title = { Text("Hide from search?") },
            text = { Text("\"${food.description}\" won't show up in food search again. You can restore hidden foods in Settings → Data & about.") },
            confirmButton = { TextButton(onClick = { viewModel.hideFood(food); foodToHide = null }) { Text("Hide") } },
            dismissButton = { TextButton(onClick = { foodToHide = null }) { Text("Cancel") } }
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

    state.dietaryWarning?.let { warning ->
        DietaryWarningDialog(
            warning = warning,
            onConfirm = viewModel::confirmDietaryWarning,
            onDismiss = viewModel::dismissDietaryWarning
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GradientBackdrop(theme = BackdropTheme.TODAY) {
            Column(modifier = Modifier.fillMaxSize()) {
                GlassTopBar(title = "Build a meal", onBack = onBack)

                SegmentedPills(
                    labels = listOf("Search", "Custom", "Barcode"),
                    selectedIndex = state.activeTab.ordinal,
                    accent = GoldLight,
                    onSelect = { viewModel.setTab(AddTab.entries[it]) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.activeTab) {
                        AddTab.SEARCH -> SearchTab(
                            state = state,
                            presets = presets,
                            drinkPresets = drinkPresets,
                            viewModel = viewModel,
                            onHide = { foodToHide = it },
                            onSaved = { Toast.makeText(context, "Saved to collection", Toast.LENGTH_SHORT).show() }
                        )

                        AddTab.CUSTOM -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            CustomFoodTab(
                                form = customForm,
                                onAdd = viewModel::addCustomIngredient,
                                onSnapLabel = { showLabelCamera = true }
                            )
                            MealDraftSection(state, presets, drinkPresets, viewModel) {
                                Toast.makeText(context, "Saved to collection", Toast.LENGTH_SHORT).show()
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        AddTab.BARCODE -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            BarcodeTab(
                                state = state,
                                onScan = { openScanner() },
                                onGoCustom = { viewModel.setTab(AddTab.CUSTOM) },
                                onScanAgain = { viewModel.dismissBarcodeResult() }
                            )
                            MealDraftSection(state, presets, drinkPresets, viewModel) {
                                Toast.makeText(context, "Saved to collection", Toast.LENGTH_SHORT).show()
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                if (state.draft.isNotEmpty()) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        MealTotalRow(totalCalories = state.totalCalories, itemCount = state.draft.size)
                        MealTypeSelector(selected = mealType, onSelected = viewModel::setMealType, modifier = Modifier.padding(bottom = 8.dp))
                        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Logging to: ${logDateLabel(logDate)}")
                        }
                        Button(
                            onClick = { fastingGuard.attempt(isForToday = logDate == java.time.LocalDate.now()) { viewModel.logMeal() } },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSaving
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.isSaving) "Saving…" else "Log meal")
                        }
                    }
                }
            }
        }

        // Full-screen overlays (siblings of the content so they cover the whole screen).
        if (showScanner) {
            Box(modifier = Modifier.fillMaxSize()) {
                BarcodeScannerView(
                    onBarcode = { code -> showScanner = false; viewModel.scanBarcode(code) },
                    onPickFromGallery = {
                        showScanner = false
                        pickBarcode.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                IconButton(
                    onClick = { showScanner = false },
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
                onCaptured = { path -> showLabelCamera = false; readLabel(path) },
                onClose = { showLabelCamera = false },
                onPickFromGallery = {
                    showLabelCamera = false
                    pickLabel.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }

        if (state.barcodeLookingUp || customForm.isReadingLabel) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = GoldLight)
                    Spacer(Modifier.height(12.dp))
                    Text(if (state.barcodeLookingUp) "Looking up product…" else "Reading the label…", color = Color.White)
                }
            }
        }
    }
}

/** The Search tab: the food database + a "describe to AI" fallback; the meal shows when not searching. */
@Composable
private fun SearchTab(
    state: ManualEntryUiState,
    presets: List<ServingPreset>,
    drinkPresets: List<ServingPreset>,
    viewModel: ManualEntryViewModel,
    onHide: (UsdaFoodEntity) -> Unit,
    onSaved: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            placeholder = { Text("Search foods (e.g. chicken, rice)…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        // Not in the database? Describe it to the AI instead — same one-tap add.
        if (state.query.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
                    .glassSoft()
                    .clickable(enabled = !state.isDescribing) { viewModel.describeToAi(state.query) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isDescribing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = GoldLight)
                else Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (state.isDescribing) "Asking the AI…" else "Describe \"${state.query}\" to the AI",
                    style = MaterialTheme.typography.bodyMedium, color = Cream
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.searchResults.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(state.searchResults, key = { it.fdcId }) { food ->
                        SearchResultRow(
                            food = food,
                            addedCount = state.draft.count { it.base.name == food.description },
                            onClick = { viewModel.pickFood(food) },
                            onLongClick = { onHide(food) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                state.draft.isNotEmpty() -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    MealDraftSection(state, presets, drinkPresets, viewModel, onSaved)
                    Spacer(Modifier.height(12.dp))
                }

                else -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Search for a food to add it to your meal — or describe it to the AI, type your own values (Custom), or scan a barcode.",
                        style = MaterialTheme.typography.bodyLarge, color = CreamMuted, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "For thousands more foods and European brands, download the full food database in " +
                            "Settings → AI model setup. Searches also check Open Food Facts online.",
                        style = MaterialTheme.typography.bodySmall, color = CreamFaint, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** The Barcode tab: a big scan button, and a "not found → add it as a custom food" fallback. */
@Composable
private fun BarcodeTab(
    state: ManualEntryUiState,
    onScan: () -> Unit,
    onGoCustom: () -> Unit,
    onScanAgain: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.barcodeNotFound) {
            Text("That barcode isn't in any database.", style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(
                "Add it on the Custom tab (type its values or snap the label) — it'll join your meal like anything else.",
                style = MaterialTheme.typography.bodyMedium, color = CreamMuted
            )
            Button(onClick = onGoCustom, modifier = Modifier.fillMaxWidth()) { Text("Add it as a custom food") }
            OutlinedButton(onClick = { onScanAgain(); onScan() }, modifier = Modifier.fillMaxWidth()) { Text("Scan again") }
        } else {
            Text(
                "Scan a packaged product's barcode to add it to your meal. Point your camera at the barcode, or pick a photo of one.",
                style = MaterialTheme.typography.bodyMedium, color = CreamMuted
            )
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Scan a barcode")
            }
        }
    }
}

/** The "Your meal" list of added foods, each editable inline. Shared by all three tabs. */
@Composable
private fun MealDraftSection(
    state: ManualEntryUiState,
    presets: List<ServingPreset>,
    drinkPresets: List<ServingPreset>,
    viewModel: ManualEntryViewModel,
    onSaved: () -> Unit
) {
    if (state.draft.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text("Your meal", style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        state.draft.forEachIndexed { index, item ->
            FoodPortionEditor(
                base = item.base,
                count = item.count,
                mealPresets = presets,
                drinkPresets = drinkPresets,
                onPortionChange = { viewModel.setDraftPortion(index, it) },
                onCountChange = { viewModel.setDraftCount(index, it) },
                onDrinkChange = { viewModel.setDraftDrink(index, it) },
                onNameChange = { viewModel.renameDraftItem(index, it) },
                onRemove = { viewModel.removeItem(index) },
                onSave = { viewModel.saveToGallery(index); onSaved() },
                saved = item.base.name in state.savedNames,
                maxCount = DraftItem.MAX_COUNT,
                modifier = Modifier.padding(vertical = 5.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SearchResultRow(food: UsdaFoodEntity, addedCount: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(food.description, style = MaterialTheme.typography.bodyLarge, color = Cream)
            Text(
                text = "${food.caloriesPer100g.toInt()} kcal / 100g" + (food.foodCategory?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium, color = CreamMuted
            )
        }
        if (addedCount > 0) {
            Text(
                "×$addedCount",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = GoldLight,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
        Icon(
            Icons.Default.Add,
            contentDescription = "Add ${food.description} to your meal",
            tint = GoldLight
        )
    }
}
