package com.fitpal.app.ui.component

import androidx.camera.core.Camera
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.GoldLight

/**
 * Zoom + flashlight (torch) for any CameraX camera, shared by every camera screen (meal photo,
 * barcode scanner, nutrition-label snap) so they all behave the same and there's one place to change
 * it. [linearZoom] is 0..1 (min..max zoom, independent of the lens's actual ratio range).
 */
class CameraControlState {
    var linearZoom by mutableFloatStateOf(0f)
    var torchOn by mutableStateOf(false)
}

@Composable
fun rememberCameraControlState(): CameraControlState = remember { CameraControlState() }

/** Push the current zoom/torch onto [camera] whenever they change (and when the camera binds). */
@Composable
fun CameraControlEffect(camera: Camera?, state: CameraControlState) {
    LaunchedEffect(camera, state.linearZoom) {
        runCatching { camera?.cameraControl?.setLinearZoom(state.linearZoom.coerceIn(0f, 1f)) }
    }
    LaunchedEffect(camera, state.torchOn) {
        runCatching { camera?.cameraControl?.enableTorch(state.torchOn) }
    }
}

/** Pinch anywhere on the preview to zoom, on top of the slider. */
fun Modifier.pinchZoom(state: CameraControlState): Modifier = this.pointerInput(Unit) {
    detectTransformGestures { _, _, zoom, _ ->
        state.linearZoom = (state.linearZoom + (zoom - 1f)).coerceIn(0f, 1f)
    }
}

/**
 * The flashlight (torch) toggle — place inside the preview Box, e.g. `Modifier.align(TopEnd)`.
 * Renders nothing when the lens has no flash. Pair the preview with [Modifier.pinchZoom] and
 * [CameraControlEffect], and add a [ZoomSlider] for the zoom.
 */
@Composable
fun FlashToggle(state: CameraControlState, hasFlash: Boolean, modifier: Modifier = Modifier) {
    if (!hasFlash) return
    IconButton(
        onClick = { state.torchOn = !state.torchOn },
        modifier = modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
    ) {
        Icon(
            if (state.torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
            contentDescription = if (state.torchOn) "Turn flashlight off" else "Turn flashlight on",
            tint = if (state.torchOn) GoldLight else Color.White
        )
    }
}

/** The zoom slider, drawn at the bottom of the preview above whatever capture control sits there. */
@Composable
fun ZoomSlider(state: CameraControlState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp)
    ) {
        Slider(
            value = state.linearZoom,
            onValueChange = { state.linearZoom = it },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = GoldLight,
                activeTrackColor = GoldLight,
                inactiveTrackColor = Color.White.copy(alpha = 0.4f)
            )
        )
    }
}
