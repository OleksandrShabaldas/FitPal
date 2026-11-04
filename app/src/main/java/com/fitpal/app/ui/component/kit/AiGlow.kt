package com.fitpal.app.ui.component.kit

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import kotlin.math.max
import kotlin.math.sin

/**
 * An AI action button's glow (docs/redesign/12, #15): a soft halo around the pill that slowly
 * brightens and fades (4 s), and three tiny ✦ sparkles that twinkle one after another around it.
 * With Reduce motion the halo stays still and nothing sparkles. Gold in Glow and Paper, white in
 * dark Liquid glass, the colour scheme's primary in Material You. Put it first in the button's
 * modifier chain (`Modifier.aiGlow().glassSoft(…)`), or wrap the button in [AiGlow]. Only for
 * buttons that start an AI generation — not headers, badges or navigation.
 *
 * Pass the button's [enabled]: a greyed-out button doesn't glow (the 4 Nov review, #7), and the glow
 * fades in when it can be pressed. [halo] false = no halo, only the sparkles — for a text-only button,
 * whose light goes on its words instead ([AiGlowLabel]).
 */
@Composable
fun Modifier.aiGlow(enabled: Boolean = true, halo: Boolean = true): Modifier {
    val still = LookState.appearance.reduceMotion
    val color = aiGlowColor()
    val strength = animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(if (still) 0 else 250),
        label = "aiGlowOn"
    )
    val breathe: State<Float>?
    val cycle: State<Float>?
    if (still) {
        breathe = null
        cycle = null
    } else {
        val t = rememberInfiniteTransition(label = "aiGlow")
        breathe = t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
        cycle = t.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "sparkles")
    }
    val star = remember { sparklePath() }
    return this.drawWithContent {
        // Read in the draw phase only: the animation redraws without recomposing.
        val k = strength.value
        if (k > 0f && halo) drawHalo(color, breathe?.value ?: 0.6f, k)
        drawContent()
        if (k > 0f) cycle?.let { drawSparkles(color, it.value, star, k) }
    }
}

/** [aiGlow] around a button that takes no modifier. */
@Composable
fun AiGlow(modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(modifier.aiGlow(enabled)) { content() }
}

/**
 * A text-only AI action — Update in Edit with AI, "Add "…" with AI", "Write it anyway": ✦ and the words,
 * with the glow **on the words** (the 4 Nov review, round 2, the user's pick): a soft light hugging the
 * letters and behind the ✦, no outline (a halo around a button you can't see looked wrong), plus the
 * sparkles. Greyed out = no glow. Put it inside a `TextButton` or a clickable row; [color] defaults to the
 * button's content colour.
 */
@Composable
fun AiGlowLabel(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    style: TextStyle = LocalTextStyle.current
) {
    val still = LookState.appearance.reduceMotion
    val glow = aiGlowColor()
    val on by animateFloatAsState(if (enabled) 1f else 0f, tween(if (still) 0 else 250), label = "aiWordsOn")
    val breathe = if (still) null else rememberInfiniteTransition(label = "aiWords")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "aiWordsBreathe")
    val blur = with(LocalDensity.current) { 9.dp.toPx() }
    Row(modifier.aiGlow(enabled, halo = false), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.AutoAwesome, contentDescription = null, tint = color,
            modifier = Modifier.size(18.dp).drawBehind {
                // The ✦'s own light: faint at the middle and fading to nothing, so it reads as light, not a
                // disc. Read in the draw phase, so it breathes without recomposing.
                val a = (0.20f + 0.14f * (breathe?.value ?: 0.6f)) * on
                if (a > 0f) {
                    val r = size.minDimension * 0.85f
                    drawCircle(
                        Brush.radialGradient(
                            0f to glow.copy(alpha = a), 0.45f to glow.copy(alpha = a * 0.45f), 1f to Color.Transparent,
                            center = center, radius = r
                        ),
                        radius = r, center = center
                    )
                }
            }
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = style.copy(shadow = if (on > 0f) Shadow(glow.copy(alpha = 0.95f * on), Offset.Zero, blur) else null),
            color = color, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun aiGlowColor(): Color = when (LookState.style) {
    LookStyle.MATERIAL -> MaterialTheme.colorScheme.primary
    LookStyle.LIQUID -> if (LookState.colors.isLight) GoldLight else Color.White
    else -> GoldLight
}

/** A pill-shaped halo: thin rings stepping outward with falling alpha (no blur needed, every API). */
private fun DrawScope.drawHalo(color: Color, breathe: Float, strength: Float) {
    val steps = 7
    val step = 1.5.dp.toPx()
    val peak = (0.10f + 0.14f * breathe) * strength
    val baseRadius = size.height / 2f
    for (i in 0 until steps) {
        val spread = step * (i + 0.5f) * (0.8f + 0.4f * breathe)
        val fall = 1f - i / steps.toFloat()
        drawRoundRect(
            color = color.copy(alpha = peak * fall * fall),
            topLeft = Offset(-spread, -spread),
            size = Size(size.width + spread * 2f, size.height + spread * 2f),
            cornerRadius = CornerRadius(baseRadius + spread),
            style = Stroke(width = step * 1.2f)
        )
    }
}

/** Three sparkles at fixed spots around the button, each twinkling in its own slice of the cycle. */
private fun DrawScope.drawSparkles(color: Color, cycle: Float, star: Path, strength: Float) {
    val spots = listOf(
        Triple(Offset(14.dp.toPx(), -6.dp.toPx()), 0.05f, 4.5.dp.toPx()),
        Triple(Offset(size.width + 2.dp.toPx(), 4.dp.toPx()), 0.38f, 3.5.dp.toPx()),
        Triple(Offset(size.width * 0.46f, size.height + 5.dp.toPx()), 0.69f, 4.dp.toPx())
    )
    spots.forEach { (at, start, radius) ->
        // Each one is visible for about a quarter of the cycle: in, a little turn, out.
        val local = ((cycle - start) % 1f + 1f) % 1f
        if (local > 0.26f) return@forEach
        val k = sin(Math.PI * (local / 0.26f)).toFloat()
        if (k <= 0f) return@forEach
        val r = radius * (0.4f + 0.6f * k)
        val sparkle = Color(
            red = color.red + (1f - color.red) * 0.55f,
            green = color.green + (1f - color.green) * 0.55f,
            blue = color.blue + (1f - color.blue) * 0.55f,
            alpha = k * strength
        )
        rotate(degrees = 45f * (local / 0.26f), pivot = at) {
            drawStar(star, at, max(r, 0.5f), sparkle)
        }
    }
}

private fun DrawScope.drawStar(star: Path, at: Offset, radius: Float, color: Color) {
    val p = Path().apply {
        addPath(star)
        transform(androidx.compose.ui.graphics.Matrix().apply {
            scale(radius, radius)
        })
        translate(at)
    }
    drawPath(p, color)
}

/** A four-pointed sparkle in a unit square around the origin. */
private fun sparklePath(): Path = Path().apply {
    moveTo(0f, -1f)
    quadraticTo(0.12f, -0.12f, 1f, 0f)
    quadraticTo(0.12f, 0.12f, 0f, 1f)
    quadraticTo(-0.12f, 0.12f, -1f, 0f)
    quadraticTo(-0.12f, -0.12f, 0f, -1f)
    close()
}
