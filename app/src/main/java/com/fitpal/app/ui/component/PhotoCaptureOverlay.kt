package com.fitpal.app.ui.component

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.GoldLight
import java.io.File

/**
 * A full-screen camera modal that captures one photo and returns its file path. Shared so any
 * screen can "snap a photo" with the same zoom + flashlight controls; it handles the camera
 * permission itself and calls [onClose] if that's declined.
 */
@Composable
fun PhotoCaptureOverlay(
    tip: String,
    onCaptured: (photoPath: String) -> Unit,
    onClose: () -> Unit,
    /** When set, a "pick from gallery" button appears next to the shutter so the user can choose an
     *  existing photo instead of taking one. The caller launches its own image picker here. */
    onPickFromGallery: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) onClose()
    }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val imageCapture = remember { ImageCapture.Builder().build() }
    var isCapturing by remember { mutableStateOf(false) }
    val camControl = rememberCameraControlState()
    var camera by remember { mutableStateOf<Camera?>(null) }
    var hasFlash by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().pinchZoom(camControl))

            LaunchedEffect(previewView) {
                val providerFuture = ProcessCameraProvider.getInstance(context)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    try {
                        provider.unbindAll()
                        val cam = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                        camera = cam
                        hasFlash = cam.cameraInfo.hasFlashUnit()
                    } catch (_: Exception) {
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            Text(
                text = tip,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp, start = 64.dp, end = 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(44.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close camera", tint = Color.White)
            }

            CameraControlEffect(camera, camControl)
            FlashToggle(camControl, hasFlash, Modifier.align(Alignment.TopEnd).padding(16.dp))
            ZoomSlider(camControl, Modifier.align(Alignment.BottomCenter).padding(bottom = 124.dp))

            FloatingActionButton(
                onClick = {
                    if (isCapturing) return@FloatingActionButton
                    isCapturing = true
                    val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                    val output = ImageCapture.OutputFileOptions.Builder(file).build()
                    imageCapture.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                                isCapturing = false
                                onCaptured(file.absolutePath)
                            }

                            override fun onError(exc: ImageCaptureException) {
                                isCapturing = false
                            }
                        }
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).size(72.dp),
                containerColor = GoldLight,
                contentColor = Color(0xFF3A2406)
            ) {
                Icon(Icons.Default.Camera, contentDescription = "Capture", modifier = Modifier.size(36.dp))
            }

            // Optional "pick from gallery" — sits beside the shutter so choosing an existing photo
            // lives in the same place as taking one (no separate button on the screen behind).
            if (onPickFromGallery != null) {
                IconButton(
                    onClick = onPickFromGallery,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 54.dp).size(52.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Pick from gallery", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text("Camera access is needed to take the photo.", color = Cream, textAlign = TextAlign.Center)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Grant permission")
                }
            }
        }
    }
}
