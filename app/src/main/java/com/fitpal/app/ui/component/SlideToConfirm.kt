package com.fitpal.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamDim
import com.fitpal.app.ui.theme.GlassBorder
import com.fitpal.app.ui.theme.InkBlack
import kotlin.math.roundToInt

/**
 * "Slide to confirm": drag the round thumb all the way right to go ahead; let go early and it springs
 * back. Deliberate friction for a choice that shouldn't be one careless tap (logging food during an
 * intense fast). The label shimmers left-to-right as the hint. Screen readers get a plain click action
 * with the same label, so the control never locks anyone out.
 */
@Composable
fun SlideToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    thumbColor: Color = Cream
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thumb = 52.dp
    val pad = 4.dp
    val shape = RoundedCornerShape(50)
    var trackPx by remember { mutableIntStateOf(0) }
    val maxOffset = with(density) { (trackPx - (thumb + pad * 2).toPx()).coerceAtLeast(1f) }
    var offset by remember { mutableFloatStateOf(0f) }
    var confirmed by remember { mutableStateOf(false) }
    val confirm by rememberUpdatedState(onConfirm)
    val progress = (offset / maxOffset).coerceIn(0f, 1f)
    val dragState = rememberDraggableState { delta ->
        if (!confirmed) offset = (offset + delta).coerceIn(0f, maxOffset)
    }
    val shimmer by rememberInfiniteTransition(label = "slideShimmer").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "slideShimmerX"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumb + pad * 2)
            .onSizeChanged { trackPx = it.width }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, GlassBorder, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = text
                onClick(label = text) {
                    if (!confirmed) {
                        confirmed = true
                        confirm()
                    }
                    true
                }
            }
    ) {
        // The trail behind the thumb brightens as it travels.
        Box(
            Modifier
                .padding(pad)
                .width(with(density) { offset.toDp() } + thumb)
                .fillMaxHeight()
                .clip(shape)
                .background(thumbColor.copy(alpha = 0.08f + 0.22f * progress))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = CreamDim,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = thumb)
                .graphicsLayer {
                    alpha = (1f - progress * 1.6f).coerceIn(0f, 1f)
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    // A soft highlight sweeping across the letters only (SrcAtop).
                    val w = size.width
                    val x = -w * 0.3f + shimmer * w * 1.6f
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.8f), Color.Transparent),
                            start = Offset(x - w * 0.25f, 0f),
                            end = Offset(x, 0f)
                        ),
                        blendMode = BlendMode.SrcAtop
                    )
                }
        )
        Box(
            Modifier
                .padding(pad)
                .offset { IntOffset(offset.roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(thumbColor)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = !confirmed,
                    onDragStopped = {
                        if (offset >= maxOffset * 0.85f) {
                            confirmed = true
                            animate(offset, maxOffset, animationSpec = tween(120)) { v, _ -> offset = v }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            confirm()
                        } else {
                            animate(
                                offset, 0f,
                                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                            ) { v, _ -> offset = v }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = InkBlack,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
