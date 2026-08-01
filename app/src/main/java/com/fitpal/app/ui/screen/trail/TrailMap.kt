package com.fitpal.app.ui.screen.trail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.TrailCatalog
import com.fitpal.app.domain.TrailDisplay
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.glass
import kotlin.math.sin

/**
 * The trail map — where you've been, where you are, and what's still ahead.
 *
 * A winding road across the canvas with one node per site: filled and named behind you,
 * a pulsing ring on the one you're restoring (with its completion arc), faint markers
 * ahead. Gives the journey a shape you can actually see.
 */
@Composable
fun TrailMapCard(display: TrailDisplay, modifier: Modifier = Modifier) {
    val total = TrailCatalog.region1Size
    val current = display.siteIndex
    // Show the handcrafted region, plus a hint that the road continues past it.
    val nodeCount = maxOf(total, current + 1)

    val pulse by rememberInfiniteTransition(label = "map").animateFloat(
        initialValue = 0.85f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Reverse),
        label = "mapPulse"
    )
    val siteProgress by animateFloatAsState(
        targetValue = if (display.totalCount == 0) 0f
        else display.builtCount.toFloat() / display.totalCount,
        animationSpec = tween(600), label = "siteProgress"
    )

    val roadColor = Color.White.copy(alpha = 0.10f)
    val doneColor = themePalette(display.themeId).leaf
    val aheadColor = Color.White.copy(alpha = 0.18f)

    Column(modifier = modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("The trail", style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(
                text = if (current >= total) "Beyond the map" else "Site ${current + 1} of $total",
                style = MaterialTheme.typography.labelMedium,
                color = CreamMuted
            )
        }

        Spacer(Modifier.height(10.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(132.dp)) {
            val w = size.width
            val h = size.height
            val nodes = (0 until nodeCount).map { nodeCenter(it, nodeCount, w, h) }

            // The road: a soft ribbon threaded through every node.
            val road = Path().apply {
                nodes.forEachIndexed { i, p ->
                    if (i == 0) moveTo(p.x, p.y)
                    else {
                        val prev = nodes[i - 1]
                        val midX = (prev.x + p.x) / 2f
                        cubicTo(midX, prev.y, midX, p.y, p.x, p.y)
                    }
                }
            }
            drawPath(road, roadColor, style = Stroke(width = h * 0.075f))

            // The part already walked, drawn brighter over the top.
            if (current > 0) {
                val walked = Path().apply {
                    nodes.take(current + 1).forEachIndexed { i, p ->
                        if (i == 0) moveTo(p.x, p.y)
                        else {
                            val prev = nodes[i - 1]
                            val midX = (prev.x + p.x) / 2f
                            cubicTo(midX, prev.y, midX, p.y, p.x, p.y)
                        }
                    }
                }
                drawPath(walked, doneColor.copy(alpha = 0.32f), style = Stroke(width = h * 0.05f))
            }

            nodes.forEachIndexed { i, p ->
                val r = h * 0.085f
                when {
                    i < current -> {
                        // Restored: filled, with a soft halo.
                        drawCircle(doneColor.copy(alpha = 0.16f), r * 2.2f, p)
                        drawCircle(doneColor, r, p)
                    }
                    i == current -> {
                        // Where you are: pulsing ring + an arc of this site's completion.
                        drawCircle(Gold.copy(alpha = 0.14f), r * 3.2f * pulse, p)
                        drawCircle(Gold.copy(alpha = 0.9f), r * 0.85f, p)
                        drawArc(
                            color = Gold,
                            startAngle = -90f,
                            sweepAngle = 360f * siteProgress,
                            useCenter = false,
                            topLeft = Offset(p.x - r * 1.9f, p.y - r * 1.9f),
                            size = Size(r * 3.8f, r * 3.8f),
                            style = Stroke(width = h * 0.028f)
                        )
                    }
                    else -> {
                        // Ahead: an unlit marker.
                        drawCircle(aheadColor, r * 0.62f, p, style = Stroke(width = h * 0.016f))
                    }
                }
            }

            drawNodeLabels(nodes, current, nodeCount, h)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                display.canAdvance -> "${display.site.name} is restored — the way onward is open."
                current >= total -> "Past the last waymarker. The road keeps going."
                else -> "Restoring ${display.site.name} · ${display.builtCount} of ${display.totalCount} done"
            },
            style = MaterialTheme.typography.bodySmall,
            color = CreamMuted
        )
    }
}

/** Nodes march left→right on a gentle S so the road reads as a journey, not a ruler. */
private fun nodeCenter(index: Int, count: Int, w: Float, h: Float): Offset {
    val t = if (count <= 1) 0.5f else index.toFloat() / (count - 1)
    val x = w * (0.09f + t * 0.82f)
    val y = h * (0.52f + 0.26f * sin(t * Math.PI.toFloat() * 1.6f + 0.4f))
    return Offset(x, y)
}

/** Name the site you're on, plus the one just behind, without crowding the map. */
private fun DrawScope.drawNodeLabels(nodes: List<Offset>, current: Int, count: Int, h: Float) {
    val paint = android.graphics.Paint().apply {
        textSize = h * 0.105f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    fun label(i: Int, color: Int, alpha: Int) {
        if (i !in 0 until count) return
        val name = TrailCatalog.siteAt(i).name
        val short = if (name.length > 18) name.take(17) + "…" else name
        paint.color = color
        paint.alpha = alpha
        val p = nodes[i]
        drawContext.canvas.nativeCanvas.drawText(short, p.x, p.y + h * 0.30f, paint)
    }
    label(current, android.graphics.Color.WHITE, 235)
    label(current - 1, android.graphics.Color.WHITE, 90)
}
