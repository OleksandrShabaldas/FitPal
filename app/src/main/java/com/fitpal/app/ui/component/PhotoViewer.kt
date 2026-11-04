package com.fitpal.app.ui.component

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The way into [PhotoViewer] (the 4 Nov review, #5): two small arrows pointing out to opposite corners
 * (↖ ↘), white with a soft shadow and no background, for a photo's top-left corner. The photo itself
 * no longer opens on a tap. A 20 dp mark in a 44 dp tap target.
 */
@Composable
fun PhotoOpenButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).clip(CircleShape)
            .clickable(onClickLabel = "Open the whole photo", onClick = onClick)
            .semantics { contentDescription = "Open the whole photo" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            val u = size.minDimension / 24f
            fun p(x: Float, y: Float) = Offset(x * u, y * u)
            val arrows = Path().apply {
                // ↖ from the middle out to the top-left corner, its head along the two edges
                moveTo(p(10f, 10f).x, p(10f, 10f).y); lineTo(p(4.5f, 4.5f).x, p(4.5f, 4.5f).y)
                moveTo(p(4.5f, 9.5f).x, p(4.5f, 9.5f).y); lineTo(p(4.5f, 4.5f).x, p(4.5f, 4.5f).y); lineTo(p(9.5f, 4.5f).x, p(9.5f, 4.5f).y)
                // ↘ the same, out to the bottom-right corner
                moveTo(p(14f, 14f).x, p(14f, 14f).y); lineTo(p(19.5f, 19.5f).x, p(19.5f, 19.5f).y)
                moveTo(p(19.5f, 14.5f).x, p(19.5f, 14.5f).y); lineTo(p(19.5f, 19.5f).x, p(19.5f, 19.5f).y); lineTo(p(14.5f, 19.5f).x, p(14.5f, 19.5f).y)
            }
            fun stroke(width: Float) = Stroke(width = width * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            // A soft shadow under the white so it reads on a light plate as well as a dark one.
            translate(top = 0.6.dp.toPx()) {
                drawPath(arrows, Color.Black.copy(alpha = 0.18f), style = stroke(6.5f))
                drawPath(arrows, Color.Black.copy(alpha = 0.35f), style = stroke(4f))
            }
            drawPath(arrows, Color.White, style = stroke(2.1f))
        }
    }
}

/**
 * A meal's whole photo, uncropped (docs/redesign/16): the corner icon ([PhotoOpenButton]) on Analysis,
 * the Meal page or a saved food opens it on black, fitted to the screen. Pinch or double-tap to zoom
 * and drag to look around; swipe down, Back or × to close. Black in every look, like any photo viewer.
 */
@Composable
fun PhotoViewer(model: Any?, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val scope = rememberCoroutineScope()
        var size by remember { mutableStateOf(IntSize.Zero) }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        // Unzoomed, a vertical drag moves the photo; far enough and it closes.
        var dragY by remember { mutableFloatStateOf(0f) }

        fun clamp(o: Offset): Offset {
            val maxX = size.width * (scale - 1f) / 2f
            val maxY = size.height * (scale - 1f) / 2f
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }

        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - abs(dragY) / 900f).coerceIn(0.35f, 1f)))
                .onSizeChanged { size = it }
        ) {
            AsyncImage(
                model = model,
                contentDescription = "Meal photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { tap ->
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero }
                            else {
                                scale = DOUBLE_TAP_ZOOM
                                // Zoom towards where the tap was.
                                offset = clamp(Offset(size.width / 2f - tap.x, size.height / 2f - tap.y) * (DOUBLE_TAP_ZOOM - 1f))
                            }
                        })
                    }
                    .pointerInput(Unit) {
                        val closeAt = 160.dp.toPx()
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f || scale > 1f) {
                                    scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                    offset = if (scale > 1f) clamp(offset + pan) else Offset.Zero
                                    dragY = 0f
                                } else {
                                    dragY += pan.y
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } while (event.changes.any { it.pressed })
                            if (scale <= 1f) {
                                if (abs(dragY) > closeAt) onDismiss()
                                else scope.launch { animate(dragY, 0f) { v, _ -> dragY = v } }
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y + dragY
                    }
            )
            Icon(
                Icons.Default.Close, contentDescription = "Close photo", tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp).size(40.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onDismiss).padding(8.dp)
            )
        }
    }
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
