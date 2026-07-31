package com.fitpal.app.ui.screen.trail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.fitpal.app.domain.PropKind

/**
 * The diorama prop library — every restored project drawn with plain Compose Canvas
 * shapes. Deliberately small and reused across sites (see GAME_DESIGN.md §8), which is
 * what keeps the scene from becoming an endless art project.
 *
 * Everything is drawn relative to a ground point (bottom-centre) and a unit size `u`,
 * so the same prop works at any depth in the scene.
 */

/** Palette for a prop — vegetation vs built structure, plus the warm glow for lights. */
data class PropPalette(
    val leaf: Color,
    val structure: Color,
    val glow: Color,
    val stone: Color
)

fun DrawScope.drawProp(
    kind: PropKind,
    ground: Offset,
    u: Float,
    alpha: Float,
    palette: PropPalette
) {
    if (alpha <= 0.01f || u <= 0.5f) return
    when (kind) {
        PropKind.SOIL -> soil(ground, u, alpha, palette)
        PropKind.SPROUTS -> sprouts(ground, u, alpha, palette)
        PropKind.FLOWERS -> flowers(ground, u, alpha, palette)
        PropKind.HERBS -> herbs(ground, u, alpha, palette)
        PropKind.TREE -> tree(ground, u, alpha, palette)
        PropKind.FENCE -> fence(ground, u, alpha, palette)
        PropKind.PATH -> pathStones(ground, u, alpha, palette)
        PropKind.STONE -> stones(ground, u, alpha, palette)
        PropKind.POST -> post(ground, u, alpha, palette)
        PropKind.LANTERN -> lantern(ground, u, alpha, palette)
        PropKind.BIN -> bin(ground, u, alpha, palette)
        PropKind.SHED -> shed(ground, u, alpha, palette)
        PropKind.GREENHOUSE -> greenhouse(ground, u, alpha, palette)
        PropKind.WELL -> well(ground, u, alpha, palette)
        PropKind.HIVE -> hive(ground, u, alpha, palette)
        PropKind.POND -> pond(ground, u, alpha, palette)
        PropKind.BRIDGE -> bridge(ground, u, alpha, palette)
        PropKind.ARCH -> arch(ground, u, alpha, palette)
        PropKind.BENCH -> bench(ground, u, alpha, palette)
    }
}

// ---------------- vegetation ----------------

private fun DrawScope.soil(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.stone.copy(alpha = 0.5f * a)
    for (i in 0..2) {
        val y = g.y - i * u * 0.16f
        drawLine(c, Offset(g.x - u * 0.7f, y), Offset(g.x + u * 0.7f, y), strokeWidth = u * 0.09f)
    }
}

private fun DrawScope.sprouts(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.leaf.copy(alpha = a)
    for (i in -1..1) {
        val x = g.x + i * u * 0.32f
        val h = u * (0.5f + if (i == 0) 0.18f else 0f)
        drawLine(c, Offset(x, g.y), Offset(x, g.y - h), strokeWidth = u * 0.08f)
        drawCircle(c.copy(alpha = 0.75f * a), u * 0.13f, Offset(x - u * 0.11f, g.y - h))
        drawCircle(c.copy(alpha = 0.75f * a), u * 0.13f, Offset(x + u * 0.11f, g.y - h * 0.85f))
    }
}

private fun DrawScope.flowers(g: Offset, u: Float, a: Float, p: PropPalette) {
    val stem = p.leaf.copy(alpha = 0.9f * a)
    val head = p.glow.copy(alpha = a)
    for (i in -1..1) {
        val x = g.x + i * u * 0.34f
        val h = u * (0.62f + i * 0.06f)
        drawLine(stem, Offset(x, g.y), Offset(x, g.y - h), strokeWidth = u * 0.07f)
        drawCircle(head.copy(alpha = 0.22f * a), u * 0.28f, Offset(x, g.y - h))
        drawCircle(head, u * 0.13f, Offset(x, g.y - h))
    }
}

private fun DrawScope.herbs(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.leaf.copy(alpha = 0.85f * a)
    drawCircle(c.copy(alpha = 0.25f * a), u * 0.55f, Offset(g.x, g.y - u * 0.34f))
    for (i in -2..2) {
        val x = g.x + i * u * 0.17f
        val h = u * (0.34f + (2 - kotlin.math.abs(i)) * 0.12f)
        drawLine(c, Offset(x, g.y), Offset(x + i * u * 0.06f, g.y - h), strokeWidth = u * 0.07f)
    }
}

private fun DrawScope.tree(g: Offset, u: Float, a: Float, p: PropPalette) {
    val trunk = p.structure.copy(alpha = 0.85f * a)
    val leaf = p.leaf.copy(alpha = a)
    drawLine(trunk, Offset(g.x, g.y), Offset(g.x, g.y - u * 0.75f), strokeWidth = u * 0.14f)
    drawCircle(leaf.copy(alpha = 0.20f * a), u * 0.86f, Offset(g.x, g.y - u * 1.05f))
    drawCircle(leaf, u * 0.46f, Offset(g.x, g.y - u * 1.12f))
    drawCircle(leaf.copy(alpha = 0.85f * a), u * 0.33f, Offset(g.x - u * 0.36f, g.y - u * 0.9f))
    drawCircle(leaf.copy(alpha = 0.85f * a), u * 0.31f, Offset(g.x + u * 0.36f, g.y - u * 0.94f))
}

// ---------------- ground works ----------------

private fun DrawScope.pathStones(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.stone.copy(alpha = 0.6f * a)
    for (i in -2..2) {
        drawOval(
            color = c,
            topLeft = Offset(g.x + i * u * 0.36f - u * 0.15f, g.y - u * 0.06f),
            size = Size(u * 0.3f, u * 0.13f)
        )
    }
}

private fun DrawScope.stones(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.stone.copy(alpha = 0.75f * a)
    drawCircle(c, u * 0.26f, Offset(g.x - u * 0.22f, g.y - u * 0.2f))
    drawCircle(c.copy(alpha = 0.6f * a), u * 0.18f, Offset(g.x + u * 0.24f, g.y - u * 0.14f))
    drawCircle(c.copy(alpha = 0.5f * a), u * 0.13f, Offset(g.x + u * 0.02f, g.y - u * 0.36f))
}

private fun DrawScope.pond(g: Offset, u: Float, a: Float, p: PropPalette) {
    drawOval(
        color = p.glow.copy(alpha = 0.14f * a),
        topLeft = Offset(g.x - u * 0.85f, g.y - u * 0.3f),
        size = Size(u * 1.7f, u * 0.55f)
    )
    drawOval(
        color = Color(0xFF6FA8FF).copy(alpha = 0.35f * a),
        topLeft = Offset(g.x - u * 0.7f, g.y - u * 0.24f),
        size = Size(u * 1.4f, u * 0.42f)
    )
    drawLine(
        Color.White.copy(alpha = 0.25f * a),
        Offset(g.x - u * 0.35f, g.y - u * 0.1f), Offset(g.x + u * 0.15f, g.y - u * 0.1f),
        strokeWidth = u * 0.05f
    )
}

// ---------------- structures ----------------

private fun DrawScope.fence(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.structure.copy(alpha = 0.8f * a)
    for (i in -2..2) {
        val x = g.x + i * u * 0.3f
        drawLine(c, Offset(x, g.y), Offset(x, g.y - u * 0.5f), strokeWidth = u * 0.08f)
    }
    drawLine(c, Offset(g.x - u * 0.65f, g.y - u * 0.34f), Offset(g.x + u * 0.65f, g.y - u * 0.34f), strokeWidth = u * 0.06f)
}

private fun DrawScope.post(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.structure.copy(alpha = 0.85f * a)
    drawLine(c, Offset(g.x, g.y), Offset(g.x, g.y - u * 0.9f), strokeWidth = u * 0.1f)
    drawRect(
        color = c,
        topLeft = Offset(g.x - u * 0.05f, g.y - u * 0.95f),
        size = Size(u * 0.44f, u * 0.24f)
    )
}

private fun DrawScope.lantern(g: Offset, u: Float, a: Float, p: PropPalette) {
    val postC = p.structure.copy(alpha = 0.85f * a)
    drawLine(postC, Offset(g.x, g.y), Offset(g.x, g.y - u * 0.95f), strokeWidth = u * 0.09f)
    val head = Offset(g.x, g.y - u * 1.08f)
    // The glow is the point of a lantern — three falloff rings.
    drawCircle(p.glow.copy(alpha = 0.10f * a), u * 0.95f, head)
    drawCircle(p.glow.copy(alpha = 0.18f * a), u * 0.52f, head)
    drawCircle(p.glow.copy(alpha = a), u * 0.19f, head)
}

private fun DrawScope.bin(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.structure.copy(alpha = 0.8f * a)
    drawRect(c, Offset(g.x - u * 0.34f, g.y - u * 0.55f), Size(u * 0.68f, u * 0.55f))
    drawRect(c.copy(alpha = a), Offset(g.x - u * 0.4f, g.y - u * 0.66f), Size(u * 0.8f, u * 0.12f))
}

private fun DrawScope.shed(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.structure.copy(alpha = 0.85f * a)
    drawRect(c.copy(alpha = 0.55f * a), Offset(g.x - u * 0.5f, g.y - u * 0.72f), Size(u, u * 0.72f))
    val roof = Path().apply {
        moveTo(g.x - u * 0.62f, g.y - u * 0.72f)
        lineTo(g.x, g.y - u * 1.15f)
        lineTo(g.x + u * 0.62f, g.y - u * 0.72f)
        close()
    }
    drawPath(roof, c)
    drawRect(p.glow.copy(alpha = 0.35f * a), Offset(g.x - u * 0.12f, g.y - u * 0.42f), Size(u * 0.24f, u * 0.42f))
}

private fun DrawScope.greenhouse(g: Offset, u: Float, a: Float, p: PropPalette) {
    val frame = p.structure.copy(alpha = 0.9f * a)
    val glass = p.glow.copy(alpha = 0.14f * a)
    drawRect(glass, Offset(g.x - u * 0.6f, g.y - u * 0.8f), Size(u * 1.2f, u * 0.8f))
    drawRect(frame, Offset(g.x - u * 0.6f, g.y - u * 0.8f), Size(u * 1.2f, u * 0.8f), style = Stroke(width = u * 0.06f))
    val roof = Path().apply {
        moveTo(g.x - u * 0.6f, g.y - u * 0.8f)
        lineTo(g.x, g.y - u * 1.2f)
        lineTo(g.x + u * 0.6f, g.y - u * 0.8f)
        close()
    }
    drawPath(roof, glass)
    drawPath(roof, frame, style = Stroke(width = u * 0.06f))
    drawLine(frame.copy(alpha = 0.5f * a), Offset(g.x, g.y - u * 0.8f), Offset(g.x, g.y), strokeWidth = u * 0.05f)
}

private fun DrawScope.well(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.stone.copy(alpha = 0.85f * a)
    val wood = p.structure.copy(alpha = 0.85f * a)
    drawRect(c, Offset(g.x - u * 0.42f, g.y - u * 0.44f), Size(u * 0.84f, u * 0.44f))
    drawOval(
        color = Color(0xFF6FA8FF).copy(alpha = 0.4f * a),
        topLeft = Offset(g.x - u * 0.36f, g.y - u * 0.52f),
        size = Size(u * 0.72f, u * 0.2f)
    )
    drawLine(wood, Offset(g.x - u * 0.34f, g.y - u * 0.44f), Offset(g.x - u * 0.34f, g.y - u * 1f), strokeWidth = u * 0.08f)
    drawLine(wood, Offset(g.x + u * 0.34f, g.y - u * 0.44f), Offset(g.x + u * 0.34f, g.y - u * 1f), strokeWidth = u * 0.08f)
    val roof = Path().apply {
        moveTo(g.x - u * 0.52f, g.y - u * 1f)
        lineTo(g.x, g.y - u * 1.3f)
        lineTo(g.x + u * 0.52f, g.y - u * 1f)
        close()
    }
    drawPath(roof, wood)
}

private fun DrawScope.hive(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.glow.copy(alpha = 0.75f * a)
    for (i in 0..2) {
        val h = u * 0.24f
        val y = g.y - h * (i + 1)
        val w = u * (0.7f - i * 0.06f)
        drawRect(c.copy(alpha = (0.55f + i * 0.12f) * a), Offset(g.x - w / 2, y), Size(w, h * 0.86f))
    }
    drawCircle(p.structure.copy(alpha = 0.7f * a), u * 0.05f, Offset(g.x, g.y - u * 0.12f))
}

private fun DrawScope.bridge(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.structure.copy(alpha = 0.85f * a)
    val arcRect = Rect(
        left = g.x - u * 0.8f, top = g.y - u * 0.62f,
        right = g.x + u * 0.8f, bottom = g.y + u * 0.3f
    )
    drawArc(
        color = c, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(arcRect.left, arcRect.top),
        size = Size(arcRect.width, arcRect.height),
        style = Stroke(width = u * 0.09f)
    )
    drawLine(c, Offset(g.x - u * 0.85f, g.y - u * 0.5f), Offset(g.x + u * 0.85f, g.y - u * 0.5f), strokeWidth = u * 0.07f)
}

private fun DrawScope.arch(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.stone.copy(alpha = 0.85f * a)
    drawRect(c, Offset(g.x - u * 0.55f, g.y - u * 0.9f), Size(u * 0.18f, u * 0.9f))
    drawRect(c, Offset(g.x + u * 0.37f, g.y - u * 0.9f), Size(u * 0.18f, u * 0.9f))
    drawArc(
        color = c, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(g.x - u * 0.55f, g.y - u * 1.25f),
        size = Size(u * 1.1f, u * 0.7f),
        style = Stroke(width = u * 0.18f)
    )
}

private fun DrawScope.bench(g: Offset, u: Float, a: Float, p: PropPalette) {
    val c = p.structure.copy(alpha = 0.85f * a)
    drawRect(c, Offset(g.x - u * 0.5f, g.y - u * 0.32f), Size(u, u * 0.09f))
    drawRect(c.copy(alpha = 0.7f * a), Offset(g.x - u * 0.5f, g.y - u * 0.58f), Size(u, u * 0.08f))
    drawLine(c, Offset(g.x - u * 0.4f, g.y - u * 0.3f), Offset(g.x - u * 0.4f, g.y), strokeWidth = u * 0.07f)
    drawLine(c, Offset(g.x + u * 0.4f, g.y - u * 0.3f), Offset(g.x + u * 0.4f, g.y), strokeWidth = u * 0.07f)
}
