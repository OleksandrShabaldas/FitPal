package com.fitpal.app.ui.screen.custom

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MealTypeSelector
import com.fitpal.app.ui.component.PhotoCaptureOverlay
import com.fitpal.app.ui.component.logDateLabel
import com.fitpal.app.ui.component.rememberFastingGuard
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Copy a picked image Uri into a cache file so [CustomFoodViewModel.readLabel] can decode it off a path. */
private fun copyLabelToCache(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
    val dest = java.io.File(context.cacheDir, "label_${System.currentTimeMillis()}.jpg")
    (context.contentResolver.openInputStream(uri) ?: return null).use { input ->
        dest.outputStream().use { output -> input.copyTo(output) }
    }
    dest.absolutePath
}.getOrNull()

@Composable
fun CustomFoodScreen(
    onLogged: () -> Unit,
    onBack: () -> Unit,
    viewModel: CustomFoodViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mealType by viewModel.mealType.collectAsStateWithLifecycle()
    val logDate by viewModel.logDate.collectAsStateWithLifecycle()
    val fastingGuard = rememberFastingGuard()
    var showDatePicker by remember { mutableStateOf(false) }
    var showLabelCamera by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val labelPickScope = rememberCoroutineScope()
    // Pick a photo of the nutrition label from the gallery, then read it exactly like a fresh snap.
    val pickLabel = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) labelPickScope.launch {
            val path = withContext(Dispatchers.IO) { copyLabelToCache(context, uri) }
            if (path != null) viewModel.readLabel(path)
            else Toast.makeText(context, "Couldn't open that image", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(state.saved) { if (state.saved) onLogged() }
    LaunchedEffect(state.labelError) {
        state.labelError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearLabelError()
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

    Box(modifier = Modifier.fillMaxSize()) {
    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Custom food", onBack = onBack)

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Type in a food and its values for the amount you ate. Only a name and calories are required.",
                    style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                )
                viewModel.barcode?.let { code ->
                    Text(
                        "Linked to barcode $code — once you log it, scanning this product again will find it automatically.",
                        style = MaterialTheme.typography.bodySmall, color = GoldLight
                    )
                }

                // Snap the product's nutrition label — or pick a photo of it — and let the AI fill the values below.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showLabelCamera = true },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isReadingLabel
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isReadingLabel) "Reading…" else "Snap label")
                    }
                    OutlinedButton(
                        onClick = { pickLabel.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isReadingLabel
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gallery")
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().glass().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onName,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        placeholder = { Text("e.g. Horalka") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Amount (g/ml)", state.amount, viewModel::onAmount, Modifier.weight(1f))
                        NumberField("Calories (kcal)", state.calories, viewModel::onCalories, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Protein (g)", state.protein, viewModel::onProtein, Modifier.weight(1f))
                        NumberField("Fat (g)", state.fat, viewModel::onFat, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Carbs (g)", state.carbs, viewModel::onCarbs, Modifier.weight(1f))
                        NumberField("Fiber (g)", state.fiber, viewModel::onFiber, Modifier.weight(1f))
                    }
                }
                Text(
                    "Amount defaults to one serving if left blank. The calories and macros you enter are logged exactly.",
                    style = MaterialTheme.typography.labelSmall, color = CreamFaint
                )
                TextButton(onClick = { viewModel.saveToGallery() }, enabled = state.canSave) {
                    Icon(
                        if (state.savedToGallery) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = if (state.savedToGallery) GoldLight else LocalContentColor.current,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(if (state.savedToGallery) "Saved to collection" else "Save to collection")
                }
            }

            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MealTypeSelector(selected = mealType, onSelected = viewModel::setMealType)
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Logging to: ${logDateLabel(logDate)}")
                }
                Button(
                    onClick = { fastingGuard.attempt(isForToday = logDate == java.time.LocalDate.now()) { viewModel.log() } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSave && !state.isSaving
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isSaving) "Saving…" else "Log food")
                }
            }
        }
    }

        if (showLabelCamera) {
            PhotoCaptureOverlay(
                tip = "Point at the product's nutrition facts label",
                onCaptured = { path -> showLabelCamera = false; viewModel.readLabel(path) },
                onClose = { showLabelCamera = false }
            )
        }
        if (state.isReadingLabel) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = GoldLight)
                    Spacer(Modifier.height(12.dp))
                    Text("Reading the label…", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.bodySmall, color = CreamMuted) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true
    )
}
