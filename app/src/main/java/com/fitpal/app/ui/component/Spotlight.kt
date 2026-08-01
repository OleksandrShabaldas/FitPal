package com.fitpal.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass

/**
 * A coach-mark: dims the screen, cuts a hole around one element, rings it, and explains it.
 *
 * Used to teach the trail one mechanic at a time instead of dropping the whole game on the
 * player at once. Elements register their position with [spotlightTarget]; the overlay looks
 * up the target by id at draw time.
 */
class SpotlightState {
    internal val targets = mutableStateMapOf<String, Rect>()
    fun bounds(id: String?): Rect? = id?.let { targets[it] }
}

@Composable
fun rememberSpotlightState(): SpotlightState = remember { SpotlightState() }

/** Mark this element as spotlight-able under [id]. */
fun Modifier.spotlightTarget(state: SpotlightState, id: String): Modifier =
    this.onGloballyPositioned { state.targets[id] = it.boundsInRoot() }

/**
 * Full-screen coach-mark. Pass a [targetId] to highlight an element, or null for a plain
 * centred message (used for the welcome step).
 */
@Composable
fun SpotlightOverlay(
    state: SpotlightState,
    targetId: String?,
    title: String,
    body: String,
    actionLabel: String = "Got it",
    onDismiss: () -> Unit
) {
    val target = state.bounds(targetId)
    val density = LocalDensity.current
    val padPx = with(density) { 8.dp.toPx() }
    val radiusPx = with(density) { 20.dp.toPx() }

    val pulse by rememberInfiniteTransition(label = "spot").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val appear by animateFloatAsState(
        targetValue = 1f, animationSpec = tween(260), label = "appear"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Swallow taps so the highlighted UI can't be used until the mark is dismissed.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // Scrim with a hole punched in it.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color.Black.copy(alpha = 0.78f * appear))
            target?.let { r ->
                val hole = Rect(
                    left = r.left - padPx,
                    top = r.top - padPx,
                    right = r.right + padPx,
                    bottom = r.bottom + padPx
                )
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(hole.left, hole.top),
                    size = Size(hole.width, hole.height),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    blendMode = BlendMode.Clear
                )
                drawRoundRect(
                    color = GoldLight.copy(alpha = (0.45f + 0.35f * pulse) * appear),
                    topLeft = Offset(hole.left, hole.top),
                    size = Size(hole.width, hole.height),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = with(density) { 2.dp.toPx() })
                )
            }
        }

        // Caption — below the target if it sits high on screen, above if it sits low.
        val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
        val below = target == null || target.top < screenH * 0.45f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = if (below) Arrangement.Bottom else Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(26.dp))
                    .padding(20.dp)
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Cream)
                Spacer(Modifier.height(6.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Gold
                    )
                }
            }
        }
    }
}
