package com.fitpal.app.ui.component

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * A live barcode-scanning camera view (ML Kit + CameraX) with flash, pinch-zoom, a zoom slider and a
 * "pick a photo instead" button. Self-contained: it binds the camera on entry and unbinds on
 * disposal, so it's safe to show/hide (e.g. behind a tab or overlay). Shared by the standalone
 * Barcode screen and the meal builder's Barcode tab.
 */
@Composable
fun BarcodeScannerView(
    onBarcode: (String) -> Unit,
    onPickFromGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val camControl = rememberCameraControlState()
    var camera by remember { mutableStateOf<Camera?>(null) }
    var hasFlash by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().pinchZoom(camControl))

        DisposableEffect(Unit) {
            val executor = Executors.newSingleThreadExecutor()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor, BarcodeAnalyzer(onBarcode))
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                try {
                    provider.unbindAll()
                    val cam = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    camera = cam
                    hasFlash = cam.cameraInfo.hasFlashUnit()
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                executor.shutdown()
                try { providerFuture.get().unbindAll() } catch (_: Exception) {}
            }
        }

        CameraControlEffect(camera, camControl)
        FlashToggle(camControl, hasFlash, Modifier.align(Alignment.TopEnd).padding(16.dp))
        ZoomSlider(camControl, Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))

        // Pick an existing photo of a barcode instead of scanning live.
        IconButton(
            onClick = onPickFromGallery,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 24.dp).size(52.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick barcode from gallery", tint = Color.White, modifier = Modifier.size(26.dp))
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
