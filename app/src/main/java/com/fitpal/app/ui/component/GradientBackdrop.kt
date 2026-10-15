package com.fitpal.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import com.fitpal.app.ui.theme.BackdropWorld
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * Per-section background "world" in the current look, all generated on the phone:
 *  - Glow: a warm gradient wash + soft glowing light orbs + a fine grain.
 *  - Liquid glass: big vivid colour blobs drifting slowly (still with reduce-motion) — the colour
 *    the thick glass on top has to catch.
 *  - Paper: a flat sheet with a faint wash and paper grain.
 *  - Material You: the plain surface colour.
 * Content sits on top. The world (Today / Trends / …) keeps each section its own colour where the
 * look has one.
 */
enum class BackdropTheme { TODAY, ACTIVITY, TRENDS, GARDEN }

private fun BackdropTheme.world() = when (this) {
    BackdropTheme.TODAY -> BackdropWorld.TODAY
    BackdropTheme.ACTIVITY -> BackdropWorld.ACTIVITY
    BackdropTheme.TRENDS -> BackdropWorld.TRENDS
    BackdropTheme.GARDEN -> BackdropWorld.GARDEN
}

@Composable
private fun rememberNoise() = remember {
    val n = 96
    val pixels = IntArray(n * n)
    val rnd = java.util.Random(11L)
    for (i in pixels.indices) {
        val v = rnd.nextInt(256)
        pixels[i] = android.graphics.Color.argb(255, v, v, v)
    }
    android.graphics.Bitmap
        .createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

@Composable
fun GradientBackdrop(
    modifier: Modifier = Modifier,
    theme: BackdropTheme = BackdropTheme.TODAY,
    content: @Composable () -> Unit
) {
    val look = LookState.look
    val colors = look.colors
    val spec = colors.backdrops[theme.world()] ?: colors.backdrops.values.first()
    val noise = rememberNoise()
    val noiseBrush = remember(noise) { ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)) }
    val grain = look.appearance.grain

    // Liquid glass's drift: one slow loop the blobs ride around (a still frame with reduce-motion).
    val drifting = look.style == LookStyle.LIQUID && !look.appearance.reduceMotion
    // Read only inside the Canvas below, so the drift redraws the backdrop without recomposing.
    val phaseState: State<Float>? = if (drifting) {
        val t = rememberInfiniteTransition(label = "liquidDrift")
        t.animateFloat(
            initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
            label = "phase"
        )
    } else null

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            when (look.style) {
                LookStyle.GLOW -> {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = spec.base,
                            center = Offset(size.width * 0.78f, -size.height * 0.06f),
                            radius = size.maxDimension * 1.15f
                        )
                    )
                    spec.orbs.forEach { o ->
                        val c = Offset(size.width * o.cx, size.height * o.cy)
                        val r = size.minDimension * o.rFrac
                        drawCircle(
                            brush = Brush.radialGradient(listOf(o.color.copy(alpha = o.alpha), Color.Transparent), center = c, radius = r),
                            radius = r, center = c
                        )
                    }
                    // Gentle global darkening so the frosted UI reads clearly on top — kept subtle so the
                    // glow still shows through at the edges.
                    drawRect(Color.Black, alpha = colors.backdropShade)
                    if (grain) drawRect(brush = noiseBrush, alpha = 0.05f, blendMode = BlendMode.Softlight)
                }
                LookStyle.LIQUID -> {
                    val phase = phaseState?.value ?: 0f
                    drawRect(colors.inkBlack)
                    spec.orbs.forEachIndexed { i, o ->
                        val a = phase + i * 2.1f
                        val c = Offset(
                            size.width * (o.cx + 0.10f * cos(a)),
                            size.height * (o.cy + 0.06f * sin(a * 0.8f))
                        )
                        val r = size.minDimension * o.rFrac * (1f + 0.08f * sin(a * 1.3f))
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(o.color.copy(alpha = o.alpha), o.color.copy(alpha = o.alpha * 0.35f), Color.Transparent),
                                center = c, radius = r * 1.6f
                            ),
                            radius = r * 1.6f, center = c
                        )
                    }
                    if (colors.backdropShade > 0f) drawRect(Color.Black, alpha = colors.backdropShade)
                }
                LookStyle.PAPER -> {
                    drawRect(colors.inkBlack)
                    spec.orbs.forEach { o ->
                        val c = Offset(size.width * o.cx, size.height * o.cy)
                        val r = size.maxDimension * o.rFrac
                        drawCircle(
                            brush = Brush.radialGradient(listOf(o.color.copy(alpha = o.alpha), Color.Transparent), center = c, radius = r),
                            radius = r, center = c
                        )
                    }
                    if (grain) {
                        drawRect(
                            brush = noiseBrush,
                            alpha = if (colors.isLight) 0.07f else 0.05f,
                            blendMode = if (colors.isLight) BlendMode.Multiply else BlendMode.Softlight
                        )
                    }
                }
                LookStyle.MATERIAL -> drawRect(colors.inkBlack)
            }
        }
        content()
    }
}
