package com.fitpal.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The signature glowing line chart: a smooth spline with a gradient fill, an
 * optional shaded "usual range" band, an optional dashed target line, a glowing
 * "now" dot, and min/max + start/end axis labels.
 *
 * [values] is one entry per day in the window; null = no data (gaps are bridged
 * by the smooth curve). Needs at least two real points to draw.
 */
@Composable
fun TrendChart(
    values: List<Float?>,
    accent: Color,
    modifier: Modifier = Modifier,
    target: Float? = null,
    showBand: Boolean = true,
    startLabel: String = "",
    endLabel: String = "",
    height: Dp = 150.dp,
    minOverride: Float? = null,
    maxOverride: Float? = null,
    valueFormatter: (Float) -> String = { it.roundToInt().toString() }
) {
    val labelArgb = CreamFaint.toArgb()
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val n = values.size
        val idxVals = values.mapIndexedNotNull { i, v -> if (v != null) i to v else null }
        if (idxVals.size < 2 || n < 2) return@Canvas

        val rightGutter = 42f
        val bottomGutter = 22f
        val top = 8f
        val chartLeft = 4f
        val chartRight = size.width - rightGutter
        val chartTop = top
        val chartBottom = size.height - bottomGutter
        val chartW = (chartRight - chartLeft).coerceAtLeast(1f)
        val chartH = (chartBottom - chartTop).coerceAtLeast(1f)
        val denom = (n - 1).coerceAtLeast(1)

        // ---- band (rolling mean ± a fraction of spread) ----
        val raw = idxVals.map { it.second }
        val mean = raw.average().toFloat()
        val variance = raw.map { (it - mean) * (it - mean) }.average().toFloat()
        val sd = sqrt(variance)
        val half = 0.7f * sd
        val band = showBand && idxVals.size >= 4 && sd > 0.0001f

        val centers = idxVals.indices.map { i ->
            val lo = (i - 2).coerceAtLeast(0)
            val hi = (i + 2).coerceAtMost(idxVals.size - 1)
            (lo..hi).map { raw[it] }.average().toFloat()
        }

        // ---- y scale ----
        val candidates = buildList {
            addAll(raw)
            if (band) centers.forEach { add(it + half); add(it - half) }
            target?.let { add(it) }
        }
        var yMin = candidates.min()
        var yMax = candidates.max()
        if (yMax - yMin < 1f) { yMax += 1f; yMin -= 1f }
        val pad = (yMax - yMin) * 0.08f
        yMin -= pad; yMax += pad
        minOverride?.let { yMin = it }
        maxOverride?.let { yMax = it }
        if (yMax - yMin < 1f) yMax = yMin + 1f

        fun xFor(i: Int) = chartLeft + i.toFloat() / denom * chartW
        fun yFor(v: Float) = chartTop + (1f - (v - yMin) / (yMax - yMin)) * chartH

        val pts = idxVals.map { Offset(xFor(it.first), yFor(it.second)) }

        // ---- usual-range band ----
        if (band) {
            val upper = idxVals.indices.map { Offset(xFor(idxVals[it].first), yFor(centers[it] + half)) }
            val lower = idxVals.indices.map { Offset(xFor(idxVals[it].first), yFor(centers[it] - half)) }
            val bandPath = Path().apply {
                moveTo(upper[0].x, upper[0].y)
                appendSmooth(upper)
                val rl = lower.reversed()
                lineTo(rl[0].x, rl[0].y)
                appendSmooth(rl)
                close()
            }
            drawPath(bandPath, accent.copy(alpha = 0.10f))
        }

        // ---- area fill under the line ----
        val linePath = smoothPath(pts)
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(pts.last().x, chartBottom)
            lineTo(pts.first().x, chartBottom)
            close()
        }
        drawPath(
            areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.32f), accent.copy(alpha = 0f)),
                startY = chartTop,
                endY = chartBottom
            )
        )

        // ---- target reference line ----
        if (target != null) {
            val ty = yFor(target)
            drawLine(
                color = accent.copy(alpha = 0.45f),
                start = Offset(chartLeft, ty),
                end = Offset(chartRight, ty),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f))
            )
        }

        // ---- glow + line ----
        drawPath(linePath, accent.copy(alpha = 0.22f), style = Stroke(width = 7f))
        drawPath(linePath, accent, style = Stroke(width = 3f))

        // ---- now dot ----
        val now = pts.last()
        drawCircle(accent.copy(alpha = 0.20f), radius = 11f, center = now)
        drawCircle(Color(0xFF120D0B), radius = 5.5f, center = now)
        drawCircle(accent, radius = 5.5f, center = now, style = Stroke(width = 3f))

        // ---- axis labels ----
        val paint = android.graphics.Paint().apply {
            color = labelArgb
            textSize = 26f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.LEFT
        }
        drawContext.canvas.nativeCanvas.drawText(valueFormatter(yMax), chartRight + 6f, chartTop + 10f, paint)
        drawContext.canvas.nativeCanvas.drawText(valueFormatter(yMin), chartRight + 6f, chartBottom, paint)
        if (startLabel.isNotEmpty()) {
            drawContext.canvas.nativeCanvas.drawText(startLabel, chartLeft, size.height - 2f, paint)
        }
        if (endLabel.isNotEmpty()) {
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(endLabel, chartRight, size.height - 2f, paint)
        }
    }
}

/** Append Catmull-Rom-smoothed cubic segments through [points] (path must already be at points[0]). */
private fun Path.appendSmooth(points: List<Offset>) {
    for (i in 0 until points.size - 1) {
        val p0 = points[if (i - 1 >= 0) i - 1 else i]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[if (i + 2 < points.size) i + 2 else i + 1]
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
        cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
}

private fun smoothPath(points: List<Offset>): Path {
    val p = Path()
    if (points.isEmpty()) return p
    p.moveTo(points[0].x, points[0].y)
    p.appendSmooth(points)
    return p
}

/** Rounded-top bars with an optional target line — used where discrete daily totals read better. */
@Composable
fun BarTrendChart(
    values: List<Float>,
    accent: Color,
    modifier: Modifier = Modifier,
    target: Float? = null,
    overColor: Color = accent,
    startLabel: String = "",
    endLabel: String = "",
    height: Dp = 150.dp,
    onBarClick: ((Int) -> Unit)? = null,
    /** Optional per-bar labels (e.g. day-of-month). Drawn when the bars aren't too dense. */
    labels: List<String> = emptyList(),
    /** Index of "today" to mark with a dot above its bar. */
    highlightIndex: Int? = null
) {
    val labelArgb = CreamFaint.toArgb()
    val highlightArgb = Cream.toArgb()
    val tapModifier = if (onBarClick != null && values.isNotEmpty()) {
        Modifier.pointerInput(values.size) {
            detectTapGestures { offset ->
                val idx = (offset.x / (size.width.toFloat() / values.size)).toInt().coerceIn(0, values.size - 1)
                onBarClick(idx)
            }
        }
    } else Modifier
    Canvas(modifier = modifier.fillMaxWidth().height(height).then(tapModifier)) {
        if (values.isEmpty()) return@Canvas
        val bottomGutter = 22f
        val chartBottom = size.height - bottomGutter
        val chartTop = 12f
        val chartH = (chartBottom - chartTop).coerceAtLeast(1f)
        val maxV = (values.maxOrNull() ?: 0f).coerceAtLeast(target ?: 0f).coerceAtLeast(1f)
        val slot = size.width / values.size
        val barW = slot * 0.6f

        if (target != null) {
            val ty = chartBottom - (target / maxV) * chartH
            drawLine(
                accent.copy(alpha = 0.45f),
                Offset(0f, ty),
                Offset(size.width, ty),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f))
            )
        }
        values.forEachIndexed { i, v ->
            val barH = (v / maxV) * chartH
            val x = i * slot + (slot - barW) / 2
            val isToday = i == highlightIndex
            drawRoundRect(
                color = when {
                    target != null && v > target -> overColor
                    isToday -> accent
                    else -> accent.copy(alpha = 0.78f)
                },
                topLeft = Offset(x, chartBottom - barH),
                size = androidx.compose.ui.geometry.Size(barW, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2.5f, barW / 2.5f)
            )
            if (isToday) {
                drawCircle(Cream, radius = barW * 0.16f, center = Offset(x + barW / 2f, chartBottom - barH - 9f))
            }
        }
        // Per-bar day labels when there's room; otherwise fall back to start/end labels only.
        val showPerBar = labels.size == values.size && values.size <= 16
        if (showPerBar) {
            val paint = android.graphics.Paint().apply {
                color = labelArgb; textSize = 22f; isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            labels.forEachIndexed { i, lbl ->
                if (i == highlightIndex) paint.color = highlightArgb else paint.color = labelArgb
                drawContext.canvas.nativeCanvas.drawText(lbl, i * slot + slot / 2f, size.height - 4f, paint)
            }
        } else {
            drawRangeLabels(startLabel, endLabel, labelArgb)
        }
    }
}

private fun DrawScope.drawRangeLabels(startLabel: String, endLabel: String, labelArgb: Int) {
    val paint = android.graphics.Paint().apply {
        color = labelArgb
        textSize = 26f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.LEFT
    }
    if (startLabel.isNotEmpty()) {
        drawContext.canvas.nativeCanvas.drawText(startLabel, 2f, size.height - 2f, paint)
    }
    if (endLabel.isNotEmpty()) {
        paint.textAlign = android.graphics.Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText(endLabel, size.width - 2f, size.height - 2f, paint)
    }
}
