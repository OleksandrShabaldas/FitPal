package com.fitpal.app.ui.screen.barcode

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.EditableFoodItemRow
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MealTotalRow
import com.fitpal.app.ui.component.MealTypeSelector
import com.fitpal.app.ui.component.logDateLabel
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun BarcodeScreen(
    onLogged: () -> Unit,
    onBack: () -> Unit,
    onAddManually: (String) -> Unit = {},
    viewModel: BarcodeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presets by viewModel.servingPresets.collectAsStateWithLifecycle()
    val drinkPresets by viewModel.drinkPresets.collectAsStateWithLifecycle()
    val mealType by viewModel.mealType.collectAsStateWithLifecycle()
    val logDate by viewModel.logDate.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onLogged() }

    if (showDatePicker) {
        DatePickerDialog(
            initialDate = logDate,
            title = "Log to which day?",
            confirmLabel = "Log here",
            onConfirm = { date -> showDatePicker = false; viewModel.setLogDate(date) },
            onDismiss = { showDatePicker = false }
        )
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Scan barcode", onBack = onBack)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !hasPermission -> CenteredMessage("Camera access is needed to scan barcodes.")

                    state.product != null -> Column(modifier = Modifier.padding(20.dp)) {
                        Text("Found it — adjust the amount and log:", style = MaterialTheme.typography.titleMedium, color = Cream)
                        Spacer(Modifier.height(8.dp))
                        EditableFoodItemRow(
                            ingredient = state.product!!,
                            onGramsChanged = { viewModel.updateGrams(it) },
                            onRemove = { viewModel.scanAgain() },
                            presets = if (state.product!!.isDrink) drinkPresets else presets,
                            unit = if (state.product!!.isDrink) "ml" else "g",
                            onSave = {
                                viewModel.saveToGallery()
                                Toast.makeText(context, "Saved to collection", Toast.LENGTH_SHORT).show()
                            },
                            saved = state.savedToGallery
                        )
                    }

                    state.lookingUp -> Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Looking up product…", color = Cream)
                    }

                    state.notFound -> Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("That barcode isn't in the database.", style = MaterialTheme.typography.bodyLarge, color = CreamMuted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        state.scannedCode?.let { code ->
                            Button(onClick = { onAddManually(code) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Add it manually")
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedButton(onClick = { viewModel.scanAgain() }) { Text("Scan again") }
                    }

                    else -> ScannerCamera(
                        onBarcode = viewModel::onBarcodeScanned,
                        bindProvider = { previewView, analysis -> bindCamera(context, lifecycleOwner, previewView, analysis) }
                    )
                }
            }

            val product = state.product
            if (product != null) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    MealTotalRow(totalCalories = product.calories, itemCount = 1)
                    MealTypeSelector(selected = mealType, onSelected = viewModel::setMealType, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Logging to: ${logDateLabel(logDate)}")
                    }
                    Button(onClick = { viewModel.logMeal() }, modifier = Modifier.fillMaxWidth(), enabled = !state.isSaving) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isSaving) "Saving…" else "Log meal")
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = CreamMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ScannerCamera(
    onBarcode: (String) -> Unit,
    bindProvider: (PreviewView, ImageAnalysis) -> AutoCloseable
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor, BarcodeAnalyzer(onBarcode))
        val binding = bindProvider(previewView, analysis)
        onDispose {
            executor.shutdown()
            try {
                binding.close()
            } catch (_: Exception) {
            }
        }
    }
}

private fun bindCamera(
    context: android.content.Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    analysis: ImageAnalysis
): AutoCloseable {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = providerFuture.get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        } catch (_: Exception) {
        }
    }, ContextCompat.getMainExecutor(context))

    return AutoCloseable {
        try {
            providerFuture.get().unbindAll()
        } catch (_: Exception) {
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private class BarcodeAnalyzer(
    private val onBarcode: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient()

    @Volatile
    private var done = false

    override fun analyze(imageProxy: ImageProxy) {
        if (done) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val code = barcodes.firstOrNull()?.rawValue
                if (!code.isNullOrBlank() && !done) {
                    done = true
                    onBarcode(code)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
