package com.fitpal.app.ui.screen.trail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.TrailDisplay
import com.fitpal.app.ui.theme.AccentGarden
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import kotlin.math.sin

/**
 * The diorama: a lit landscape that fills in as you restore the site, and gets
 * reclaimed by the wilderness when you stop logging.
 *
 * Deliberately *not* cartoon art — minimal glowing shapes on dark, matching the app's
 * aesthetic (see DESIGN_SYSTEM.md). Props come from [drawProp]; this file owns the
 * landscape, the depth layout and the motion.
 */

private data class Slot(val x: Float, val y: Float, val depth: Float)

/**
 * Where each prop stands. Three depth bands (back/mid/front) with a little wobble, so
 * the place looks composed rather than like a row of icons. Deterministic, so a site
 * always looks the same.
 */
private fun slotFor(index: Int, total: Int): Slot {
    val t = (index + 0.5f) / total.coerceAtLeast(1)
    // Scatter consecutive builds across bands so the scene fills in organically.
    val band = index % 3
    val baseY = when (band) {
        0 -> 0.50f   // back
        1 -> 0.88f   // front
        else -> 0.69f
    }
    val depth = when (band) {
        0 -> 0.48f
        1 -> 1.00f
        else -> 0.74f
    }
    val wobbleX = sin(index * 2.399f) * 0.035f
    val wobbleY = sin(index * 1.77f) * 0.018f
    return Slot(
        x = (0.08f + t * 0.84f + wobbleX).coerceIn(0.05f, 0.95f),
        y = baseY + wobbleY,
        depth = depth
    )
}

/**
 * @param highlightId project to single out — its prop gets a ring and a name label, and the
 *   rest of the scene steps back. This is how tapping a row in the list answers "which one
 *   is the well I just fixed?".
 * @param onPropTap fired with a built project's id when you tap near its prop, so the scene
 *   is something you can poke at rather than only look at.
 */
@Composable
fun TrailScene(
    display: TrailDisplay,
    modifier: Modifier = Modifier,
    highlightId: String? = null,
    onPropTap: (String) -> Unit = {}
) {
    val projects = display.projects
    val overgrowth = display.overgrowth

    // Each prop grows in when it's built. animateFloatAsState starts *at* its target on
    // first composition, so opening the screen doesn't replay every build — only new ones.
    val appear: List<Float> = projects.map { pv ->
        key(pv.project.id) {
            val v by animateFloatAsState(
                targetValue = if (pv.built) 1f else 0f,
                animationSpec = tween(700, easing = FastOutSlowInEasing),
                label = "prop"
            )
            v
        }
    }

    val ambient = rememberInfiniteTransition(label = "scene")
    val sway by ambient.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "sway"
    )
    // Lanterns and glows breathe very slightly.
    val pulse by ambient.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // Vitality dims and desaturates the whole place.
    val lifeFade = 1f - overgrowth * 0.45f
    val theme = themePalette(display.themeId)
    val palette = PropPalette(
        leaf = lerpColor(theme.leaf, Color(0xFF4A5B45), overgrowth),
        structure = Cream.copy(alpha = 0.55f),
        glow = lerpColor(theme.glow, Color(0xFF7A6A45), overgrowth * 0.8f),
        stone = Cream.copy(alpha = 0.40f)
    )

    val tappable = modifier
        .fillMaxWidth()
        .height(260.dp)
        .pointerInput(projects.size) {
            detectTapGestures { tap ->
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                var bestId: String? = null
                var bestDistance = Float.MAX_VALUE
                projects.forEachIndexed { i, pv ->
                    if (!pv.built) return@forEachIndexed
                    val slot = slotFor(i, projects.size)
                    val u = h * 0.13f * slot.depth
                    val centre = Offset(w * slot.x, h * slot.y - u * 0.5f)
                    val d = (centre - tap).getDistance()
                    if (d < bestDistance) {
                        bestDistance = d
                        bestId = pv.project.id
                    }
                }
                bestId?.let { if (bestDistance < h * 0.22f) onPropTap(it) }
            }
        }

    Canvas(modifier = tappable) {
        val w = size.width
        val h = size.height

        drawHills(w, h, overgrowth)
        drawTrailPath(w, h, overgrowth)

        // Back-to-front so nearer props overlap the far ones.
        val order = projects.indices.sortedBy { slotFor(it, projects.size).y }
        val highlighting = highlightId != null && projects.any { it.project.id == highlightId && it.built }

        order.forEach { i ->
            val pv = projects[i]
            val a = appear[i]
            if (a <= 0.01f) {
                drawGhostSlot(slotFor(i, projects.size), w, h)
                return@forEach
            }
            val slot = slotFor(i, projects.size)
            val u = h * 0.13f * slot.depth * (0.6f + 0.4f * a)
            val drift = sin(sway + i * 0.7f) * u * 0.035f
            val ground = Offset(w * slot.x + drift, h * slot.y)

            val keystone = pv.project.isKeystone
            val picked = pv.project.id == highlightId
            val propPalette = if (keystone)
                palette.copy(glow = lerpColor(theme.keystone, Color(0xFF7A6A45), overgrowth * 0.8f))
            else palette
            // Depth haze: things further back read fainter. When one prop is picked out,
            // everything else steps back so the eye lands on the right thing.
            var depthAlpha = (0.55f + 0.45f * slot.depth) * lifeFade * a
            if (highlighting && !picked) depthAlpha *= 0.35f

            if (keystone) {
                drawCircle(
                    color = theme.keystone.copy(alpha = 0.07f * depthAlpha * pulse),
                    radius = u * 1.6f,
                    center = Offset(ground.x, ground.y - u * 0.7f)
                )
            }
            if (picked) {
                drawCircle(
                    color = theme.glow.copy(alpha = 0.13f * pulse),
                    radius = u * 1.7f,
                    center = Offset(ground.x, ground.y - u * 0.6f)
                )
                drawCircle(
                    color = theme.glow.copy(alpha = 0.55f),
                    radius = u * 1.45f * pulse,
                    center = Offset(ground.x, ground.y - u * 0.6f),
                    style = Stroke(width = h * 0.006f)
                )
            }
            drawProp(pv.project.prop, ground, u, depthAlpha, propPalette, pv.variantIndex)

            if (picked) drawPropLabel(pv.project.name, ground, u, h)
        }

        if (overgrowth > 0.02f) drawOvergrowth(w, h, overgrowth)
    }
}

/** Names the highlighted prop right under it, so the list row and the scene line up. */
private fun DrawScope.drawPropLabel(name: String, ground: Offset, u: Float, h: Float) {
    val paint = android.graphics.Paint().apply {
        textSize = h * 0.052f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        alpha = 225
        setShadowLayer(h * 0.02f, 0f, 0f, android.graphics.Color.BLACK)
    }
    val short = if (name.length > 22) name.take(21) + "…" else name
    drawContext.canvas.nativeCanvas.drawText(short, ground.x, ground.y + h * 0.07f, paint)
}

/** Faint marker for a project not yet built — a place waiting for something. */
private fun DrawScope.drawGhostSlot(slot: Slot, w: Float, h: Float) {
    val u = h * 0.13f * slot.depth
    drawCircle(
        color = Color.White.copy(alpha = 0.07f),
        radius = u * 0.22f,
        center = Offset(w * slot.x, h * slot.y - u * 0.22f),
        style = Stroke(width = 1.5f)
    )
}

private fun DrawScope.drawHills(w: Float, h: Float, overgrowth: Float) {
    val far = Color.White.copy(alpha = 0.05f * (1f - overgrowth * 0.4f))
    val near = Color.White.copy(alpha = 0.07f * (1f - overgrowth * 0.4f))

    val back = Path().apply {
        moveTo(0f, h * 0.46f)
        quadraticBezierTo(w * 0.25f, h * 0.30f, w * 0.52f, h * 0.44f)
        quadraticBezierTo(w * 0.78f, h * 0.58f, w, h * 0.38f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(back, far)

    val front = Path().apply {
        moveTo(0f, h * 0.62f)
        quadraticBezierTo(w * 0.35f, h * 0.50f, w * 0.66f, h * 0.63f)
        quadraticBezierTo(w * 0.85f, h * 0.70f, w, h * 0.60f)
        lineTo(w, h); lineTo(0f, h); close()
    }
    drawPath(front, near)
}

/** The path itself — the "journey" running through the site. */
private fun DrawScope.drawTrailPath(w: Float, h: Float, overgrowth: Float) {
    val path = Path().apply {
        moveTo(w * 0.02f, h * 0.97f)
        cubicTo(w * 0.30f, h * 0.92f, w * 0.20f, h * 0.70f, w * 0.52f, h * 0.66f)
        cubicTo(w * 0.82f, h * 0.62f, w * 0.74f, h * 0.50f, w * 0.99f, h * 0.46f)
    }
    drawPath(
        path,
        color = Color.White.copy(alpha = 0.10f * (1f - overgrowth * 0.5f)),
        style = Stroke(width = h * 0.045f)
    )
    drawPath(
        path,
        color = GoldLight.copy(alpha = 0.06f * (1f - overgrowth)),
        style = Stroke(width = h * 0.02f)
    )
}

/** Neglect: vines creep back in from the edges. Never destroys anything — just covers it. */
private fun DrawScope.drawOvergrowth(w: Float, h: Float, amount: Float) {
    val vine = Color(0xFF2E3D2A).copy(alpha = (0.55f * amount).coerceAtMost(0.6f))
    val leaf = Color(0xFF3E5636).copy(alpha = (0.6f * amount).coerceAtMost(0.65f))

    // A few creepers from each side, growing further in as it gets worse.
    for (i in 0..3) {
        val fromLeft = i % 2 == 0
        val yStart = h * (0.30f + i * 0.19f)
        val reach = w * (0.12f + 0.30f * amount)
        val x0 = if (fromLeft) 0f else w
        val x1 = if (fromLeft) reach else w - reach

        val p = Path().apply {
            moveTo(x0, yStart)
            quadraticBezierTo(
                (x0 + x1) / 2f, yStart - h * 0.10f,
                x1, yStart + h * 0.05f
            )
        }
        drawPath(p, vine, style = Stroke(width = h * 0.014f))

        for (k in 1..3) {
            val t = k / 4f
            val lx = x0 + (x1 - x0) * t
            val ly = yStart - h * 0.05f * sin(t * Math.PI.toFloat())
            drawCircle(leaf, h * 0.016f, Offset(lx, ly))
        }
    }
}

/**
 * A bought scene theme, resolved to colours. Kept in the UI layer so the domain
 * catalogue stays free of Compose types.
 */
data class ScenePalette(
    val leaf: Color,
    val glow: Color,
    val keystone: Color,
    val path: Color
)

/**
 * The prop colours for a theme at full health. The scene dims these as vitality drops;
 * the build previews use them straight, so you see what you're choosing at its best.
 */
fun propPaletteFor(themeId: String, keystone: Boolean = false): PropPalette {
    val t = themePalette(themeId)
    return PropPalette(
        leaf = t.leaf,
        structure = Cream.copy(alpha = 0.55f),
        glow = if (keystone) t.keystone else t.glow,
        stone = Cream.copy(alpha = 0.40f)
    )
}

fun themePalette(themeId: String): ScenePalette = when (themeId) {
    "dusk" -> ScenePalette(
        leaf = Color(0xFF8E7BD0), glow = Color(0xFFF0B457),
        keystone = Color(0xFFFFD08A), path = Color(0xFFB49BE0)
    )
    "autumn" -> ScenePalette(
        leaf = Color(0xFFD98A44), glow = Color(0xFFF2B25C),
        keystone = Color(0xFFFFC46B), path = Color(0xFFE0A163)
    )
    "frost" -> ScenePalette(
        leaf = Color(0xFF8FC9E8), glow = Color(0xFFDCEEFF),
        keystone = Color(0xFFBFE4FF), path = Color(0xFFA9D6EF)
    )
    "moonlit" -> ScenePalette(
        leaf = Color(0xFF7E93C4), glow = Color(0xFFC9D6F0),
        keystone = Color(0xFFDDE6FA), path = Color(0xFF93A7D4)
    )
    "ember" -> ScenePalette(
        leaf = Color(0xFFC4644A), glow = Color(0xFFFF9A5C),
        keystone = Color(0xFFFFB877), path = Color(0xFFD2735A)
    )
    else -> ScenePalette(
        leaf = AccentGarden, glow = GoldLight,
        keystone = Gold, path = GoldLight
    )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = a.alpha + (b.alpha - a.alpha) * f
    )
}
