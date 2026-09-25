package com.fitpal.app.ui.screen.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.CarbColor
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.InkBlack
import com.fitpal.app.ui.theme.ProteinColor
import com.fitpal.app.ui.theme.ScoreFair
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/*
 * Hand-drawn, fully offline artwork for the intro — Canvas only, in the app's gold-on-dark style.
 */

/**
 * A big ring that draws itself to [target] on first show, with a soft breathing glow behind it —
 * the intro's echo of the Home calorie ring. [content] sits in the middle.
 */
@Composable
fun GlowRing(
    target: Float,
    diameter: Dp,
    modifier: Modifier = Modifier,
    durationMs: Int = 1400,
    content: @Composable () -> Unit = {}
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(target) {
        sweep.animateTo(target.coerceIn(0f, 1f), tween(durationMs, easing = FastOutSlowInEasing))
    }
    val breathe by rememberInfiniteTransition(label = "ringGlow").animateFloat(
        initialValue = 0.85f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "ringGlowPulse"
    )
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            // Warm halo behind the ring.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Gold.copy(alpha = 0.28f * breathe), Color.Transparent),
                    center = c, radius = size.minDimension * 0.62f * breathe
                ),
                radius = size.minDimension * 0.62f * breathe, center = c
            )
            val sw = size.minDimension * 0.075f
            val inset = sw / 2f
            val arcSize = Size(size.width - sw, size.height - sw)
            val tl = Offset(inset, inset)
            drawArc(Color.White.copy(alpha = 0.08f), -90f, 360f, false, tl, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(
                brush = Brush.sweepGradient(listOf(Gold, GoldLight, Gold), center = c),
                startAngle = -90f, sweepAngle = 360f * sweep.value, useCenter = false,
                topLeft = tl, size = arcSize, style = Stroke(sw, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

/** Two soft light orbs drifting slowly behind the intro — the backdrop, gently alive. */
@Composable
fun DriftingGlow(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label = "driftT"
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        val a = (t * 2 * PI).toFloat()
        val c1 = Offset(size.width * (0.25f + 0.12f * sin(a)), size.height * (0.30f + 0.06f * sin(a * 2)))
        val c2 = Offset(size.width * (0.80f - 0.10f * sin(a + 1.3f)), size.height * (0.72f + 0.05f * sin(a * 1.5f)))
        val r1 = size.minDimension * 0.55f
        val r2 = size.minDimension * 0.45f
        drawCircle(Brush.radialGradient(listOf(GoldLight.copy(alpha = 0.13f), Color.Transparent), c1, r1), r1, c1)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE89A4E).copy(alpha = 0.10f), Color.Transparent), c2, r2), r2, c2)
    }
}

/** The gold "+" from the bottom bar, pulsing gently — "everything starts here". */
@Composable
fun PulsingPlus(modifier: Modifier = Modifier, diameter: Dp = 52.dp) {
    val pulse by rememberInfiniteTransition(label = "plus").animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "plusPulse"
    )
    Box(modifier = modifier.size(diameter * 1.5f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val base = diameter.toPx() / 2f
            // An expanding, fading ring.
            drawCircle(Gold.copy(alpha = (1.35f - pulse) * 0.9f), radius = base * pulse, center = c, style = Stroke(2.dp.toPx()))
            drawCircle(Brush.radialGradient(listOf(GoldLight, Gold), center = c, radius = base), radius = base, center = c)
        }
        Icon(Icons.Default.Add, contentDescription = null, tint = InkBlack, modifier = Modifier.size(diameter * 0.5f))
    }
}

private data class Particle(
    val x: Float, val delay: Float, val speed: Float, val drift: Float, val sway: Float,
    val size: Float, val spin: Float, val color: Color
)

/** A one-off burst of confetti drifting down — the "you're all set" moment. */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier) {
    val particles = remember {
        val palette = listOf(Gold, GoldLight, ScoreFair, AccentTrends, ProteinColor, CarbColor)
        val rnd = Random(42)
        List(70) {
            Particle(
                x = rnd.nextFloat(),
                delay = rnd.nextFloat() * 0.35f,
                speed = 0.55f + rnd.nextFloat() * 0.6f,
                drift = (rnd.nextFloat() - 0.5f) * 0.15f,
                sway = 0.01f + rnd.nextFloat() * 0.03f,
                size = 5f + rnd.nextFloat() * 7f,
                spin = (rnd.nextFloat() - 0.5f) * 900f,
                color = palette[rnd.nextInt(palette.size)]
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(3200, easing = LinearEasing)) }
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        particles.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val y = -0.05f * size.height + (p.speed * local + 0.6f * local * local) * size.height
            val x = (p.x + p.drift * local) * size.width + sin(local * 12f + p.x * 10f) * p.sway * size.width
            val alpha = (1f - local).coerceIn(0f, 1f)
            rotate(degrees = p.spin * local, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.size / 2f, y - p.size / 4f),
                    size = Size(p.size, p.size / 2f)
                )
            }
        }
    }
}
