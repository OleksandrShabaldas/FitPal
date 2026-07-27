package com.fitpal.app.ui.component

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A small drag-to-reorder Column for short lists (a screen's widgets). Each row is a fixed
 * [rowHeight] so we can reorder by a simple threshold: drag a row past half a row-height and it
 * swaps with its neighbour. Long-press a row (anywhere not interactive) to start dragging.
 *
 * [onMove] is called with adjacent indices as the drag crosses each slot, so the caller mutates
 * its list live. [rowContent] receives a [Modifier] to attach to the draggable surface and whether
 * the row is currently being dragged (for lift styling).
 */
@Composable
fun ReorderableColumn(
    count: Int,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 64.dp,
    rowContent: @Composable (index: Int, dragHandle: Modifier, isDragging: Boolean) -> Unit
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }

    Column(modifier = modifier) {
        for (i in 0 until count) {
            val isDragging = draggingIndex == i
            val rowModifier = Modifier
                .height(rowHeight)
                .then(
                    if (isDragging) Modifier
                        .zIndex(1f)
                        .graphicsLayer { translationY = dragOffset }
                        .alpha(0.92f)
                    else Modifier
                )

            Box(modifier = rowModifier) {
                val dragHandle = Modifier.pointerInput(count) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingIndex = i; dragOffset = 0f },
                        onDragEnd = { draggingIndex = null; dragOffset = 0f },
                        onDragCancel = { draggingIndex = null; dragOffset = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val cur = draggingIndex
                            if (cur != null) {
                                when {
                                    dragOffset > rowPx / 2f && cur < count - 1 -> {
                                        onMove(cur, cur + 1); draggingIndex = cur + 1; dragOffset -= rowPx
                                    }
                                    dragOffset < -rowPx / 2f && cur > 0 -> {
                                        onMove(cur, cur - 1); draggingIndex = cur - 1; dragOffset += rowPx
                                    }
                                }
                            }
                        }
                    )
                }
                rowContent(i, dragHandle, isDragging)
            }
        }
    }
}
