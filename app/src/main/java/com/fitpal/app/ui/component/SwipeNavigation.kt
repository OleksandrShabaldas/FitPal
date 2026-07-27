package com.fitpal.app.ui.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Detect a horizontal swipe and call [onSwipeLeft] (dragged left → next screen) or [onSwipeRight]
 * (dragged right → previous screen). Used to move between the main tab screens by swiping. Child
 * scrollables (e.g. a horizontally-scrolling row) consume their own drags first, so this only fires
 * for swipes on the surrounding content.
 *
 * It fires the *moment* the accumulated horizontal travel passes [threshold] and then latches until
 * the finger lifts — so a clear swipe always registers instead of depending on exactly where the
 * gesture happens to end. The threshold is deliberately small for a responsive feel.
 */
fun Modifier.swipeNavigation(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    threshold: Float = 90f
): Modifier = composed {
    var dragX by remember { mutableFloatStateOf(0f) }
    var fired by remember { mutableStateOf(false) }
    pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { dragX = 0f; fired = false },
            onDragEnd = { dragX = 0f; fired = false },
            onDragCancel = { dragX = 0f; fired = false },
            onHorizontalDrag = { _, amount ->
                dragX += amount
                if (!fired) {
                    if (dragX > threshold) { fired = true; onSwipeRight() }
                    else if (dragX < -threshold) { fired = true; onSwipeLeft() }
                }
            }
        )
    }
}
