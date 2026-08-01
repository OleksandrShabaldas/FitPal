package com.fitpal.app.ui.screen.trail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.fitpal.app.domain.PropKind

/**
 * The diorama prop library — every restored project drawn with plain Compose Canvas shapes.
 * Deliberately small and reused across sites (see GAME_DESIGN.md §8), which is what keeps the
 * scene from becoming an endless art project.
 *
 * Each prop has **three variants** the player chooses when building. A variant changes both the
 * material tint *and* one structural feature (a roof shape, a canopy, a rail), so the choice
 * reads as a genuinely different build rather than a recolour.
 *
 * Everything is drawn relative to a ground point (bottom-centre) and a unit size `u`, so the
 * same prop works at any depth in the scene.
 */

/** Palette for a prop — vegetation vs built structure, plus the warm glow for lights. */
data class PropPalette(
    val leaf: Color,
    val structure: Color,
    val glow: Color,
    val stone: Color
)

/** Material tint for a variant: timber, stone, or lime-washed. */
private fun material(p: PropPalette, variant: Int, alpha: Float): Color = when (variant) {
    0 -> p.structure.copy(alpha = p.structure.alpha * alpha)
    1 -> p.stone.copy(alpha = p.stone.alpha * alpha * 1.5f)
    else -> Color(0xFFEFE4D2).copy(alpha = 0.72f * alpha)
}

fun DrawScope.drawProp(
    kind: PropKind,
    ground: Offset,
    u: Float,
    alpha: Float,
    palette: PropPalette,
    variant: Int = 0
) {
    if (alpha <= 0.01f || u <= 0.5f) return
    val v = variant.coerceIn(0, 2)
    when (kind) {
        PropKind.SOIL -> soil(ground, u, alpha, palette, v)
        PropKind.SPROUTS -> sprouts(ground, u, alpha, palette, v)
        PropKind.FLOWERS -> flowers(ground, u, alpha, palette, v)
        PropKind.HERBS -> herbs(ground, u, alpha, palette, v)
        PropKind.TREE -> tree(ground, u, alpha, palette, v)
        PropKind.FENCE -> fence(ground, u, alpha, palette, v)
        PropKind.PATH -> pathStones(ground, u, alpha, palette, v)
        PropKind.STONE -> stones(ground, u, alpha, palette, v)
        PropKind.POST -> post(ground, u, alpha, palette, v)
        PropKind.LANTERN -> lantern(ground, u, alpha, palette, v)
        PropKind.BIN -> bin(ground, u, alpha, palette, v)
        PropKind.SHED -> shed(ground, u, alpha, palette, v)
        PropKind.GREENHOUSE -> greenhouse(ground, u, alpha, palette, v)
        PropKind.WELL -> well(ground, u, alpha, palette, v)
        PropKind.HIVE -> hive(ground, u, alpha, palette, v)
        PropKind.POND -> pond(ground, u, alpha, palette, v)
        PropKind.BRIDGE -> bridge(ground, u, alpha, palette, v)
        PropKind.ARCH -> arch(ground, u, alpha, palette, v)
        PropKind.BENCH -> bench(ground, u, alpha, palette, v)
    }
}

// ---------------- vegetation ----------------

private fun DrawScope.soil(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val c = p.stone.copy(alpha = 0.5f * a)
    when (v) {
        // Straight furrows.
        0 -> for (i in 0..2) {
            val y = g.y - i * u * 0.16f
            drawLine(c, Offset(g.x - u * 0.7f, y), Offset(g.x + u * 0.7f, y), strokeWidth = u * 0.09f)
        }
        // Mulched: one soft mound.
        1 -> drawArc(
            color = c, startAngle = 180f, sweepAngle = 180f, useCenter = true,
            topLeft = Offset(g.x - u * 0.7f, g.y - u * 0.34f), size = Size(u * 1.4f, u * 0.68f)
        )
        // Terraced: stepped.
        else -> for (i in 0..2) {
            val w = u * (0.72f - i * 0.18f)
            drawRect(c, Offset(g.x - w, g.y - u * (0.14f * (i + 1))), Size(w * 2, u * 0.11f))
        }
    }
}

private fun DrawScope.sprouts(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val c = p.leaf.copy(alpha = a)
    if (v == 2) {
        // Raised bed: a box with sprouts inside.
        drawRect(material(p, 0, a), Offset(g.x - u * 0.6f, g.y - u * 0.26f), Size(u * 1.2f, u * 0.26f))
    }
    val spread = if (v == 1) 0.42f else 0.32f
    for (i in -1..1) {
        val x = g.x + i * u * spread + if (v == 1) u * 0.08f * i else 0f
        val h = u * (0.5f + if (i == 0) 0.18f else 0f)
        val base = if (v == 2) g.y - u * 0.24f else g.y
        drawLine(c, Offset(x, base), Offset(x, base - h), strokeWidth = u * 0.08f)
        drawCircle(c.copy(alpha = 0.75f * a), u * 0.13f, Offset(x - u * 0.11f, base - h))
        drawCircle(c.copy(alpha = 0.75f * a), u * 0.13f, Offset(x + u * 0.11f, base - h * 0.85f))
    }
}

private fun DrawScope.flowers(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val stem = p.leaf.copy(alpha = 0.9f * a)
    val head = p.glow.copy(alpha = a)
    val count = if (v == 2) 5 else 3
    val range = if (v == 1) 0.44f else 0.34f
    for (i in 0 until count) {
        val t = if (count == 1) 0f else (i / (count - 1f)) * 2f - 1f
        val x = g.x + t * u * range
        val h = u * (0.62f + if (v == 1) 0.12f * kotlin.math.sin(i * 1.7f) else t * 0.06f)
        drawLine(stem, Offset(x, g.y), Offset(x, g.y - h), strokeWidth = u * 0.07f)
        drawCircle(head.copy(alpha = 0.22f * a), u * 0.28f, Offset(x, g.y - h))
        drawCircle(head, u * 0.13f, Offset(x, g.y - h))
    }
    // Formal beds get an edging.
    if (v == 2) drawLine(
        material(p, 1, a), Offset(g.x - u * 0.6f, g.y), Offset(g.x + u * 0.6f, g.y),
        strokeWidth = u * 0.06f
    )
}

private fun DrawScope.herbs(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val c = if (v == 1) Color(0xFFB9AFD6).copy(alpha = 0.85f * a) else p.leaf.copy(alpha = 0.85f * a)
    if (v == 2) {
        // Climbing vine: up a cane.
        drawLine(material(p, 0, a), Offset(g.x, g.y), Offset(g.x, g.y - u * 1.0f), strokeWidth = u * 0.07f)
        for (i in 0..4) {
            val y = g.y - u * (0.18f + i * 0.19f)
            val side = if (i % 2 == 0) 1f else -1f
            drawCircle(c, u * 0.14f, Offset(g.x + side * u * 0.16f, y))
        }
        return
    }
    drawCircle(c.copy(alpha = 0.25f * a), u * 0.55f, Offset(g.x, g.y - u * 0.34f))
    for (i in -2..2) {
        val x = g.x + i * u * 0.17f
        val h = u * (0.34f + (2 - kotlin.math.abs(i)) * 0.12f)
        drawLine(c, Offset(x, g.y), Offset(x + i * u * 0.06f, g.y - h), strokeWidth = u * 0.07f)
    }
}

private fun DrawScope.tree(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val trunk = p.structure.copy(alpha = 0.85f * a)
    val leaf = p.leaf.copy(alpha = a)
    when (v) {
        // Orchard apple — broad and low.
        0 -> {
            drawLine(trunk, Offset(g.x, g.y), Offset(g.x, g.y - u * 0.7f), strokeWidth = u * 0.15f)
            drawCircle(leaf.copy(alpha = 0.20f * a), u * 0.9f, Offset(g.x, g.y - u * 1.0f))
            drawCircle(leaf, u * 0.5f, Offset(g.x, g.y - u * 1.02f))
            drawCircle(leaf.copy(alpha = 0.85f * a), u * 0.36f, Offset(g.x - u * 0.4f, g.y - u * 0.82f))
            drawCircle(leaf.copy(alpha = 0.85f * a), u * 0.34f, Offset(g.x + u * 0.4f, g.y - u * 0.86f))
        }
        // Wild cherry — slender, blossom on top.
        1 -> {
            drawLine(trunk, Offset(g.x, g.y), Offset(g.x, g.y - u * 1.05f), strokeWidth = u * 0.1f)
            val blossom = Color(0xFFE9B7C8).copy(alpha = a)
            drawCircle(blossom.copy(alpha = 0.20f * a), u * 0.72f, Offset(g.x, g.y - u * 1.3f))
            drawCircle(blossom, u * 0.34f, Offset(g.x, g.y - u * 1.34f))
            drawCircle(blossom.copy(alpha = 0.8f * a), u * 0.22f, Offset(g.x - u * 0.28f, g.y - u * 1.14f))
        }
        // Old oak — wide, heavy crown.
        else -> {
            drawLine(trunk, Offset(g.x, g.y), Offset(g.x, g.y - u * 0.6f), strokeWidth = u * 0.22f)
            drawCircle(leaf.copy(alpha = 0.20f * a), u * 1.05f, Offset(g.x, g.y - u * 1.0f))
            drawCircle(leaf, u * 0.46f, Offset(g.x - u * 0.34f, g.y - u * 0.94f))
            drawCircle(leaf, u * 0.5f, Offset(g.x + u * 0.3f, g.y - u * 1.02f))
            drawCircle(leaf, u * 0.42f, Offset(g.x, g.y - u * 1.24f))
        }
    }
}

// ---------------- ground works ----------------

private fun DrawScope.pathStones(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val c = material(p, if (v == 2) 1 else v, a).copy(alpha = 0.6f * a)
    when (v) {
        // Stepping stones — spaced ovals.
        0 -> for (i in -2..2) drawOval(
            c, Offset(g.x + i * u * 0.36f - u * 0.15f, g.y - u * 0.06f), Size(u * 0.3f, u * 0.13f)
        )
        // Gravel — a scatter.
        1 -> for (i in 0..11) {
            val x = g.x + (kotlin.math.sin(i * 12.9f) * u * 0.75f)
            val y = g.y - kotlin.math.abs(kotlin.math.cos(i * 7.3f)) * u * 0.1f
            drawCircle(c, u * 0.045f, Offset(x, y))
        }
        // Brick — a laid band.
        else -> for (i in -3..3) drawRect(
            c, Offset(g.x + i * u * 0.24f - u * 0.1f, g.y - u * 0.1f), Size(u * 0.2f, u * 0.1f)
        )
    }
}

private fun DrawScope.stones(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val c = p.stone.copy(alpha = 0.75f * a)
    when (v) {
        // Cairn — stacked.
        0 -> {
            drawCircle(c, u * 0.26f, Offset(g.x, g.y - u * 0.2f))
            drawCircle(c.copy(alpha = 0.7f * a), u * 0.19f, Offset(g.x, g.y - u * 0.56f))
            drawCircle(c.copy(alpha = 0.6f * a), u * 0.13f, Offset(g.x, g.y - u * 0.82f))
        }
        // Cleared pile — heaped aside.
        1 -> {
            drawCircle(c, u * 0.26f, Offset(g.x - u * 0.22f, g.y - u * 0.2f))
            drawCircle(c.copy(alpha = 0.6f * a), u * 0.18f, Offset(g.x + u * 0.24f, g.y - u * 0.14f))
            drawCircle(c.copy(alpha = 0.5f * a), u * 0.13f, Offset(g.x + u * 0.02f, g.y - u * 0.36f))
        }
        // Standing stone — one upright.
        else -> {
            val path = Path().apply {
                moveTo(g.x - u * 0.2f, g.y)
                lineTo(g.x - u * 0.14f, g.y - u * 0.9f)
                lineTo(g.x + u * 0.16f, g.y - u * 1.0f)
                lineTo(g.x + u * 0.22f, g.y)
                close()
            }
            drawPath(path, c)
        }
    }
}

private fun DrawScope.pond(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val water = Color(0xFF6FA8FF).copy(alpha = 0.35f * a)
    when (v) {
        // Natural pool — soft, with reeds.
        0 -> {
            drawOval(p.glow.copy(alpha = 0.14f * a), Offset(g.x - u * 0.85f, g.y - u * 0.3f), Size(u * 1.7f, u * 0.55f))
            drawOval(water, Offset(g.x - u * 0.7f, g.y - u * 0.24f), Size(u * 1.4f, u * 0.42f))
            for (i in -1..1) drawLine(
                p.leaf.copy(alpha = 0.7f * a),
                Offset(g.x + i * u * 0.5f, g.y - u * 0.16f),
                Offset(g.x + i * u * 0.5f, g.y - u * 0.5f), strokeWidth = u * 0.05f
            )
        }
        // Stone basin — cut rim.
        1 -> {
            drawOval(material(p, 1, a), Offset(g.x - u * 0.75f, g.y - u * 0.3f), Size(u * 1.5f, u * 0.5f))
            drawOval(water, Offset(g.x - u * 0.6f, g.y - u * 0.25f), Size(u * 1.2f, u * 0.36f))
        }
        // Rill — a narrow channel.
        else -> {
            drawRect(material(p, 1, a), Offset(g.x - u * 0.9f, g.y - u * 0.2f), Size(u * 1.8f, u * 0.2f))
            drawRect(water, Offset(g.x - u * 0.85f, g.y - u * 0.16f), Size(u * 1.7f, u * 0.11f))
        }
    }
}

// ---------------- structures ----------------

private fun DrawScope.fence(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val c = material(p, v, a)
    when (v) {
        // Picket — many uprights, one rail.
        0 -> {
            for (i in -2..2) {
                val x = g.x + i * u * 0.3f
                drawLine(c, Offset(x, g.y), Offset(x, g.y - u * 0.5f), strokeWidth = u * 0.08f)
            }
            drawLine(c, Offset(g.x - u * 0.65f, g.y - u * 0.34f), Offset(g.x + u * 0.65f, g.y - u * 0.34f), strokeWidth = u * 0.06f)
        }
        // Post & rail — two posts, two long rails.
        1 -> {
            for (i in -1..1 step 2) {
                val x = g.x + i * u * 0.6f
                drawLine(c, Offset(x, g.y), Offset(x, g.y - u * 0.62f), strokeWidth = u * 0.11f)
            }
            drawLine(c, Offset(g.x - u * 0.7f, g.y - u * 0.28f), Offset(g.x + u * 0.7f, g.y - u * 0.28f), strokeWidth = u * 0.07f)
            drawLine(c, Offset(g.x - u * 0.7f, g.y - u * 0.52f), Offset(g.x + u * 0.7f, g.y - u * 0.52f), strokeWidth = u * 0.07f)
        }
        // Drystone — a low wall of rough blocks.
        else -> for (row in 0..2) {
            val yy = g.y - u * (0.14f * (row + 1))
            val offset = if (row % 2 == 0) 0f else u * 0.1f
            for (i in -3..2) drawRect(
                p.stone.copy(alpha = 0.8f * a),
                Offset(g.x + i * u * 0.24f + offset, yy), Size(u * 0.21f, u * 0.12f)
            )
        }
    }
}

private fun DrawScope.post(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val c = material(p, v, a)
    drawLine(c, Offset(g.x, g.y), Offset(g.x, g.y - u * 0.9f), strokeWidth = u * 0.1f)
    when (v) {
        // Waymarker — painted cap pointing on.
        0 -> drawRect(c, Offset(g.x - u * 0.05f, g.y - u * 0.95f), Size(u * 0.44f, u * 0.24f))
        // Bird table — a little roof and ledge.
        1 -> {
            drawRect(c, Offset(g.x - u * 0.3f, g.y - u * 0.94f), Size(u * 0.6f, u * 0.07f))
            val roof = Path().apply {
                moveTo(g.x - u * 0.34f, g.y - u * 0.98f)
                lineTo(g.x, g.y - u * 1.24f)
                lineTo(g.x + u * 0.34f, g.y - u * 0.98f)
                close()
            }
            drawPath(roof, c)
        }
        // Signpost — two blank arms.
        else -> {
            drawRect(c, Offset(g.x, g.y - u * 0.92f), Size(u * 0.42f, u * 0.12f))
            drawRect(c, Offset(g.x - u * 0.42f, g.y - u * 0.7f), Size(u * 0.42f, u * 0.12f))
        }
    }
}

private fun DrawScope.lantern(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val postC = material(p, 0, a)
    val head: Offset
    when (v) {
        // Iron hook — curved crook.
        0 -> {
            drawLine(postC, Offset(g.x, g.y), Offset(g.x, g.y - u * 0.95f), strokeWidth = u * 0.09f)
            drawArc(
                color = postC, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(g.x - u * 0.02f, g.y - u * 1.05f), size = Size(u * 0.4f, u * 0.24f),
                style = Stroke(width = u * 0.07f)
            )
            head = Offset(g.x + u * 0.38f, g.y - u * 0.9f)
        }
        // Glass globe — straight post, round lamp.
        1 -> {
            drawLine(postC, Offset(g.x, g.y), Offset(g.x, g.y - u * 0.95f), strokeWidth = u * 0.09f)
            head = Offset(g.x, g.y - u * 1.08f)
        }
        // Paper lamp — wide and soft.
        else -> {
            drawLine(postC, Offset(g.x, g.y), Offset(g.x, g.y - u * 0.8f), strokeWidth = u * 0.07f)
            head = Offset(g.x, g.y - u * 0.98f)
            drawOval(
                p.glow.copy(alpha = 0.5f * a),
                Offset(head.x - u * 0.34f, head.y - u * 0.2f), Size(u * 0.68f, u * 0.4f)
            )
        }
    }
    drawCircle(p.glow.copy(alpha = 0.10f * a), u * 0.95f, head)
    drawCircle(p.glow.copy(alpha = 0.18f * a), u * 0.52f, head)
    drawCircle(p.glow.copy(alpha = a), u * (if (v == 2) 0.14f else 0.19f), head)
}

private fun DrawScope.bin(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val c = material(p, v, a)
    when (v) {
        // Slatted box.
        0 -> {
            drawRect(c.copy(alpha = c.alpha * 0.75f), Offset(g.x - u * 0.34f, g.y - u * 0.55f), Size(u * 0.68f, u * 0.55f))
            for (i in 0..2) drawLine(
                c, Offset(g.x - u * 0.34f, g.y - u * (0.16f + i * 0.16f)),
                Offset(g.x + u * 0.34f, g.y - u * (0.16f + i * 0.16f)), strokeWidth = u * 0.04f
            )
            drawRect(c, Offset(g.x - u * 0.4f, g.y - u * 0.66f), Size(u * 0.8f, u * 0.12f))
        }
        // Barrel — round, lidded.
        1 -> {
            drawOval(c, Offset(g.x - u * 0.32f, g.y - u * 0.6f), Size(u * 0.64f, u * 0.6f))
            drawOval(c.copy(alpha = a), Offset(g.x - u * 0.32f, g.y - u * 0.66f), Size(u * 0.64f, u * 0.18f))
        }
        // Woven — tapered basket.
        else -> {
            val path = Path().apply {
                moveTo(g.x - u * 0.3f, g.y)
                lineTo(g.x - u * 0.4f, g.y - u * 0.58f)
                lineTo(g.x + u * 0.4f, g.y - u * 0.58f)
                lineTo(g.x + u * 0.3f, g.y)
                close()
            }
            drawPath(path, c)
        }
    }
}

private fun DrawScope.shed(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val c = material(p, v, a)
    drawRect(c.copy(alpha = c.alpha * 0.6f), Offset(g.x - u * 0.5f, g.y - u * 0.72f), Size(u, u * 0.72f))
    when (v) {
        // Weatherboard — steep peak.
        0 -> {
            val roof = Path().apply {
                moveTo(g.x - u * 0.62f, g.y - u * 0.72f)
                lineTo(g.x, g.y - u * 1.18f)
                lineTo(g.x + u * 0.62f, g.y - u * 0.72f)
                close()
            }
            drawPath(roof, c)
        }
        // Stone bothy — low, flat-ish roof.
        1 -> drawRect(c, Offset(g.x - u * 0.6f, g.y - u * 0.86f), Size(u * 1.2f, u * 0.16f))
        // Lean-to — one slanted plane.
        else -> {
            val roof = Path().apply {
                moveTo(g.x - u * 0.6f, g.y - u * 0.72f)
                lineTo(g.x + u * 0.6f, g.y - u * 1.06f)
                lineTo(g.x + u * 0.6f, g.y - u * 0.9f)
                lineTo(g.x - u * 0.6f, g.y - u * 0.58f)
                close()
            }
            drawPath(roof, c)
        }
    }
    drawRect(p.glow.copy(alpha = 0.35f * a), Offset(g.x - u * 0.12f, g.y - u * 0.42f), Size(u * 0.24f, u * 0.42f))
}

private fun DrawScope.greenhouse(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val frame = material(p, if (v == 2) 0 else v, a)
    val glass = p.glow.copy(alpha = 0.14f * a)
    when (v) {
        // Victorian — tall peak.
        0 -> {
            drawRect(glass, Offset(g.x - u * 0.6f, g.y - u * 0.8f), Size(u * 1.2f, u * 0.8f))
            drawRect(frame, Offset(g.x - u * 0.6f, g.y - u * 0.8f), Size(u * 1.2f, u * 0.8f), style = Stroke(width = u * 0.06f))
            val roof = Path().apply {
                moveTo(g.x - u * 0.6f, g.y - u * 0.8f)
                lineTo(g.x, g.y - u * 1.28f)
                lineTo(g.x + u * 0.6f, g.y - u * 0.8f)
                close()
            }
            drawPath(roof, glass); drawPath(roof, frame, style = Stroke(width = u * 0.06f))
            for (i in -1..1) drawLine(
                frame.copy(alpha = frame.alpha * 0.5f),
                Offset(g.x + i * u * 0.3f, g.y - u * 0.8f), Offset(g.x + i * u * 0.3f, g.y), strokeWidth = u * 0.04f
            )
        }
        // Cold frame — low and wide.
        1 -> {
            drawRect(glass, Offset(g.x - u * 0.75f, g.y - u * 0.4f), Size(u * 1.5f, u * 0.4f))
            val lid = Path().apply {
                moveTo(g.x - u * 0.78f, g.y - u * 0.4f)
                lineTo(g.x + u * 0.78f, g.y - u * 0.58f)
                lineTo(g.x + u * 0.78f, g.y - u * 0.46f)
                lineTo(g.x - u * 0.78f, g.y - u * 0.28f)
                close()
            }
            drawPath(lid, glass); drawPath(lid, frame, style = Stroke(width = u * 0.05f))
        }
        // Iron & glass — dark frame, big panes.
        else -> {
            val dark = Color(0xFF2B2622).copy(alpha = 0.9f * a)
            drawRect(glass, Offset(g.x - u * 0.62f, g.y - u * 0.9f), Size(u * 1.24f, u * 0.9f))
            drawRect(dark, Offset(g.x - u * 0.62f, g.y - u * 0.9f), Size(u * 1.24f, u * 0.9f), style = Stroke(width = u * 0.08f))
            drawArc(
                color = dark, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(g.x - u * 0.62f, g.y - u * 1.2f), size = Size(u * 1.24f, u * 0.6f),
                style = Stroke(width = u * 0.08f)
            )
        }
    }
}

private fun DrawScope.well(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val body = material(p, if (v == 0) 1 else v, a)
    val wood = material(p, 0, a)
    // The shaft
    drawRect(body, Offset(g.x - u * 0.42f, g.y - u * 0.44f), Size(u * 0.84f, u * 0.44f))
    drawOval(Color(0xFF6FA8FF).copy(alpha = 0.4f * a), Offset(g.x - u * 0.36f, g.y - u * 0.52f), Size(u * 0.72f, u * 0.2f))

    when (v) {
        // Fieldstone — rough courses + a peaked shingle roof.
        0 -> {
            for (row in 0..1) for (i in -2..1) drawRect(
                p.stone.copy(alpha = 0.35f * a),
                Offset(g.x + i * u * 0.22f, g.y - u * (0.2f + row * 0.2f)), Size(u * 0.19f, u * 0.17f)
            )
            drawLine(wood, Offset(g.x - u * 0.34f, g.y - u * 0.44f), Offset(g.x - u * 0.34f, g.y - u * 1f), strokeWidth = u * 0.08f)
            drawLine(wood, Offset(g.x + u * 0.34f, g.y - u * 0.44f), Offset(g.x + u * 0.34f, g.y - u * 1f), strokeWidth = u * 0.08f)
            val roof = Path().apply {
                moveTo(g.x - u * 0.52f, g.y - u * 1f); lineTo(g.x, g.y - u * 1.3f)
                lineTo(g.x + u * 0.52f, g.y - u * 1f); close()
            }
            drawPath(roof, wood)
        }
        // Timber frame — A-frame with a visible winch.
        1 -> {
            drawLine(wood, Offset(g.x - u * 0.4f, g.y - u * 0.44f), Offset(g.x, g.y - u * 1.1f), strokeWidth = u * 0.08f)
            drawLine(wood, Offset(g.x + u * 0.4f, g.y - u * 0.44f), Offset(g.x, g.y - u * 1.1f), strokeWidth = u * 0.08f)
            drawLine(wood, Offset(g.x - u * 0.28f, g.y - u * 0.86f), Offset(g.x + u * 0.28f, g.y - u * 0.86f), strokeWidth = u * 0.06f)
            drawCircle(wood, u * 0.1f, Offset(g.x, g.y - u * 0.86f))
            drawLine(wood.copy(alpha = 0.6f * a), Offset(g.x, g.y - u * 0.86f), Offset(g.x, g.y - u * 0.5f), strokeWidth = u * 0.03f)
        }
        // Whitewashed — clean drum, flat cap.
        else -> {
            drawOval(body, Offset(g.x - u * 0.46f, g.y - u * 0.56f), Size(u * 0.92f, u * 0.22f))
            drawLine(wood, Offset(g.x - u * 0.3f, g.y - u * 0.5f), Offset(g.x - u * 0.3f, g.y - u * 0.96f), strokeWidth = u * 0.07f)
            drawLine(wood, Offset(g.x + u * 0.3f, g.y - u * 0.5f), Offset(g.x + u * 0.3f, g.y - u * 0.96f), strokeWidth = u * 0.07f)
            drawRect(body, Offset(g.x - u * 0.46f, g.y - u * 1.06f), Size(u * 0.92f, u * 0.12f))
        }
    }
}

private fun DrawScope.hive(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val c = p.glow.copy(alpha = 0.75f * a)
    when (v) {
        // National — square stacked boxes.
        0 -> for (i in 0..2) {
            val h = u * 0.24f
            val w = u * (0.7f - i * 0.06f)
            drawRect(c.copy(alpha = (0.55f + i * 0.12f) * a), Offset(g.x - w / 2, g.y - h * (i + 1)), Size(w, h * 0.86f))
        }
        // Skep — woven straw dome.
        1 -> {
            for (i in 0..3) drawArc(
                color = c.copy(alpha = (0.5f + i * 0.1f) * a),
                startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(g.x - u * (0.42f - i * 0.07f), g.y - u * (0.22f + i * 0.16f)),
                size = Size(u * (0.84f - i * 0.14f), u * 0.34f),
                style = Stroke(width = u * 0.08f)
            )
        }
        // Top-bar — long and low on legs.
        else -> {
            drawRect(c, Offset(g.x - u * 0.55f, g.y - u * 0.5f), Size(u * 1.1f, u * 0.26f))
            drawLine(material(p, 0, a), Offset(g.x - u * 0.4f, g.y - u * 0.24f), Offset(g.x - u * 0.4f, g.y), strokeWidth = u * 0.06f)
            drawLine(material(p, 0, a), Offset(g.x + u * 0.4f, g.y - u * 0.24f), Offset(g.x + u * 0.4f, g.y), strokeWidth = u * 0.06f)
        }
    }
    drawCircle(p.structure.copy(alpha = 0.7f * a), u * 0.05f, Offset(g.x, g.y - u * 0.12f))
}

private fun DrawScope.bridge(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val c = material(p, if (v == 0) 1 else 0, a)
    when (v) {
        // Humpback — a high stone curve.
        0 -> {
            drawArc(
                color = c, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(g.x - u * 0.8f, g.y - u * 0.72f), size = Size(u * 1.6f, u * 1.0f),
                style = Stroke(width = u * 0.11f)
            )
            drawArc(
                color = c.copy(alpha = c.alpha * 0.5f), startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(g.x - u * 0.42f, g.y - u * 0.3f), size = Size(u * 0.84f, u * 0.6f),
                style = Stroke(width = u * 0.05f)
            )
        }
        // Plank crossing — flat with a rope rail.
        1 -> {
            drawRect(c, Offset(g.x - u * 0.85f, g.y - u * 0.36f), Size(u * 1.7f, u * 0.1f))
            drawLine(c, Offset(g.x - u * 0.8f, g.y - u * 0.36f), Offset(g.x - u * 0.8f, g.y - u * 0.7f), strokeWidth = u * 0.05f)
            drawLine(c, Offset(g.x + u * 0.8f, g.y - u * 0.36f), Offset(g.x + u * 0.8f, g.y - u * 0.7f), strokeWidth = u * 0.05f)
            drawArc(
                color = c.copy(alpha = c.alpha * 0.7f), startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(g.x - u * 0.8f, g.y - u * 0.86f), size = Size(u * 1.6f, u * 0.36f),
                style = Stroke(width = u * 0.035f)
            )
        }
        // Clapper — slabs straight across on piers.
        else -> {
            drawRect(c, Offset(g.x - u * 0.85f, g.y - u * 0.42f), Size(u * 1.7f, u * 0.12f))
            for (i in -1..1) drawRect(
                c.copy(alpha = c.alpha * 0.8f),
                Offset(g.x + i * u * 0.5f - u * 0.07f, g.y - u * 0.32f), Size(u * 0.14f, u * 0.32f)
            )
        }
    }
}

private fun DrawScope.arch(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    when (v) {
        // Stone arch — heavy, with a keystone.
        0 -> {
            val c = p.stone.copy(alpha = 0.85f * a)
            drawRect(c, Offset(g.x - u * 0.55f, g.y - u * 0.9f), Size(u * 0.18f, u * 0.9f))
            drawRect(c, Offset(g.x + u * 0.37f, g.y - u * 0.9f), Size(u * 0.18f, u * 0.9f))
            drawArc(
                color = c, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(g.x - u * 0.55f, g.y - u * 1.25f), size = Size(u * 1.1f, u * 0.7f),
                style = Stroke(width = u * 0.18f)
            )
            drawRect(c, Offset(g.x - u * 0.08f, g.y - u * 1.24f), Size(u * 0.16f, u * 0.16f))
        }
        // Iron hoop — thin, with roses.
        1 -> {
            val c = Color(0xFF3A342E).copy(alpha = 0.9f * a)
            drawLine(c, Offset(g.x - u * 0.45f, g.y), Offset(g.x - u * 0.45f, g.y - u * 0.8f), strokeWidth = u * 0.05f)
            drawLine(c, Offset(g.x + u * 0.45f, g.y), Offset(g.x + u * 0.45f, g.y - u * 0.8f), strokeWidth = u * 0.05f)
            drawArc(
                color = c, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(g.x - u * 0.45f, g.y - u * 1.2f), size = Size(u * 0.9f, u * 0.8f),
                style = Stroke(width = u * 0.05f)
            )
            for (i in 0..3) drawCircle(
                Color(0xFFE9B7C8).copy(alpha = 0.8f * a), u * 0.07f,
                Offset(g.x - u * 0.36f + i * u * 0.24f, g.y - u * (0.98f + 0.06f * kotlin.math.sin(i * 2f)))
            )
        }
        // Timber gate — squared posts and a lintel.
        else -> {
            val c = material(p, 0, a)
            drawRect(c, Offset(g.x - u * 0.55f, g.y - u * 0.95f), Size(u * 0.16f, u * 0.95f))
            drawRect(c, Offset(g.x + u * 0.39f, g.y - u * 0.95f), Size(u * 0.16f, u * 0.95f))
            drawRect(c, Offset(g.x - u * 0.62f, g.y - u * 1.06f), Size(u * 1.24f, u * 0.14f))
        }
    }
}

private fun DrawScope.bench(g: Offset, u: Float, a: Float, p: PropPalette, v: Int) {
    val c = material(p, if (v == 2) 1 else 0, a)
    drawRect(c, Offset(g.x - u * 0.5f, g.y - u * 0.32f), Size(u, u * 0.09f))
    when (v) {
        // Plank — two boards, four legs.
        0 -> {
            drawRect(c.copy(alpha = c.alpha * 0.8f), Offset(g.x - u * 0.5f, g.y - u * 0.58f), Size(u, u * 0.08f))
            drawLine(c, Offset(g.x - u * 0.4f, g.y - u * 0.3f), Offset(g.x - u * 0.4f, g.y), strokeWidth = u * 0.07f)
            drawLine(c, Offset(g.x + u * 0.4f, g.y - u * 0.3f), Offset(g.x + u * 0.4f, g.y), strokeWidth = u * 0.07f)
        }
        // Carved — patterned backrest.
        1 -> {
            drawRect(c.copy(alpha = c.alpha * 0.8f), Offset(g.x - u * 0.5f, g.y - u * 0.66f), Size(u, u * 0.1f))
            for (i in -2..2) drawLine(
                c.copy(alpha = c.alpha * 0.7f),
                Offset(g.x + i * u * 0.2f, g.y - u * 0.56f), Offset(g.x + i * u * 0.2f, g.y - u * 0.34f),
                strokeWidth = u * 0.04f
            )
            drawLine(c, Offset(g.x - u * 0.4f, g.y - u * 0.3f), Offset(g.x - u * 0.4f, g.y), strokeWidth = u * 0.07f)
            drawLine(c, Offset(g.x + u * 0.4f, g.y - u * 0.3f), Offset(g.x + u * 0.4f, g.y), strokeWidth = u * 0.07f)
        }
        // Stone seat — a solid slab on blocks.
        else -> {
            drawRect(c, Offset(g.x - u * 0.38f, g.y - u * 0.26f), Size(u * 0.16f, u * 0.26f))
            drawRect(c, Offset(g.x + u * 0.22f, g.y - u * 0.26f), Size(u * 0.16f, u * 0.26f))
        }
    }
}
