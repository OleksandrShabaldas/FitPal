package com.fitpal.app.widget

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.fitpal.app.domain.WeightTrend
import com.fitpal.app.ui.component.clockLabel
import com.fitpal.app.ui.component.fastingClockLabel
import com.fitpal.app.ui.component.fastingCountdownLabel
import com.fitpal.app.ui.component.minuteOfDayNow
import com.fitpal.app.ui.navigation.Screen
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where a tap on a widget (or one of its buttons) goes. */
internal sealed interface WidgetTarget {
    /** Open the app on [route]; [focusToday] also brings Home back to today if it was browsing. */
    data class Open(val route: String, val focusToday: Boolean = false) : WidgetTarget
    /** Log [ml] of water right away, without opening the app. */
    data class AddWater(val ml: Int) : WidgetTarget
}

/** A button under the main area. With no [icon] it's just its [label] (e.g. "+250"). */
internal class SceneAction(val label: String, val icon: WidgetIcon?, val primary: Boolean, val target: WidgetTarget)

/** A live countdown the launcher ticks by itself (a Chronometer), centred on the main area, in the look's ink. */
internal class SceneTimer(val untilMillis: Long, val sizeDp: Float)

/**
 * One widget laid out for one size: what the main area shows ([draw], in pixels), an optional row of
 * buttons under it, an optional live countdown in its centre, and where a tap anywhere else goes.
 */
internal class WidgetScene(
    val description: String,
    val tap: WidgetTarget,
    val actions: List<SceneAction> = emptyList(),
    /** The button row's height (dp). With [actionsFill], the buttons take the whole widget instead. */
    val actionHeightDp: Float = 0f,
    val actionsFill: Boolean = false,
    val timer: SceneTimer? = null,
    val draw: (WidgetPainter, Canvas, Float, Float) -> Unit = { _, _, _, _ -> }
) {
    /** The main area's height (dp) inside a content area [contentHeightDp] tall. */
    fun mainHeightDp(contentHeightDp: Float): Float = when {
        actionsFill -> 0f
        actions.isEmpty() -> contentHeightDp
        else -> contentHeightDp - actionHeightDp - WidgetScenes.ACTION_GAP_DP
    }
}

/**
 * Lays out each [WidgetKind] for the space it's been given ([w]×[h] dp of content, padding already
 * taken off): a small square, a wide strip, or a roomy card each get their own arrangement.
 */
internal object WidgetScenes {
    const val ACTION_GAP_DP = 10f
    /** Between buttons — must match the start margins in the widget layouts. */
    const val ACTION_SPACING_DP = 8f

    fun build(kind: WidgetKind, d: WidgetData, w: Float, h: Float): WidgetScene = when (kind) {
        WidgetKind.TODAY -> today(d, w, h)
        WidgetKind.QUICK_LOG -> quickLog(d, w, h)
        WidgetKind.MACROS -> macros(d, w, h)
        WidgetKind.WATER -> water(d, w, h)
        WidgetKind.FASTING -> fasting(d, w, h)
        WidgetKind.WEEK -> week(d, w, h)
        WidgetKind.WEIGHT -> weight(d, w, h)
        WidgetKind.CAFFEINE -> caffeine(d, w, h)
    }

    private val home = WidgetTarget.Open(Screen.Home.route, focusToday = true)

    // ======================== TODAY ========================

    private fun today(d: WidgetData, w: Float, h: Float): WidgetScene {
        val consumed = d.today.caloriesConsumed
        val budget = d.calorieBudget
        val left = d.caloriesLeft
        val over = d.hasGoal && left < 0
        val frac = if (budget > 0) consumed.toFloat() / budget else 0f
        val description = when {
            !d.hasGoal -> "$consumed calories eaten today"
            over -> "${-left} calories over today's budget of $budget"
            else -> "$left calories left of $budget today"
        }
        val wide = w >= h * 1.55f && w >= 200f
        val tall = !wide && h >= w * 1.35f && h >= 210f
        return WidgetScene(description, home) { p, c, W, H ->
            val ring = CalorieRingSpec(frac, left, consumed, over, d.hasGoal)
            when {
                wide -> {
                    val dia = min(H, W * 0.42f)
                    calorieRing(p, c, dia / 2f, H / 2f, dia, ring)
                    val x0 = dia + p.dp(18f)
                    macroRows(p, c, d, x0, 0f, W - x0, H, title = "Today", withFiber = false)
                }
                tall -> {
                    val dia = min(W, H * 0.56f)
                    calorieRing(p, c, W / 2f, dia / 2f, dia, ring)
                    val top = dia + p.dp(12f)
                    macroRows(p, c, d, 0f, top, W, H - top, title = null, withFiber = false)
                }
                else -> calorieRing(p, c, W / 2f, H / 2f, min(W, H), ring)
            }
        }
    }

    private class CalorieRingSpec(val frac: Float, val left: Int, val consumed: Int, val over: Boolean, val hasGoal: Boolean)

    /** The Home ring in miniature: filled by what's eaten, calories left in the middle. */
    private fun calorieRing(p: WidgetPainter, c: Canvas, cx: Float, cy: Float, dia: Float, s: CalorieRingSpec) {
        val color = p.tone(if (s.over) Tone.OVER else Tone.CALORIE)
        val stroke = max(p.dp(4f), dia * 0.08f)
        val r = dia / 2f - stroke
        p.ring(c, cx, cy, r, stroke, if (s.over) 1f else s.frac, color)
        val number = "%,d".format(if (s.hasGoal) abs(s.left) else s.consumed)
        val caption = when {
            !s.hasGoal -> "kcal today"
            s.over -> "kcal over"
            else -> "kcal left"
        }
        val numberPaint = p.text(dia * 0.2f, if (s.over) color else p.ink.main, weight = 800, tnum = true, align = Paint.Align.CENTER)
        val capPaint = p.text(max(p.dp(9f), dia * 0.074f), if (s.over) color else p.ink.muted, weight = 500, align = Paint.Align.CENTER)
        val showOf = s.hasGoal && dia >= p.dp(118f)
        val ofPaint = p.text(max(p.dp(8.5f), dia * 0.06f), p.ink.faint, weight = 500, tnum = true, align = Paint.Align.CENTER)
        stackCentered(
            p, c, cx, cy, r * 1.4f,
            listOfNotNull(
                number to numberPaint,
                caption to capPaint,
                if (showOf) "${"%,d".format(s.consumed)} of ${"%,d".format(s.left + s.consumed)}" to ofPaint else null
            )
        )
    }

    /**
     * Protein / carbs / fat (+ fibre) as labelled bars in the box [x], [y], [w], [h]: name on the left,
     * "82 / 140 g" on the right, the bar under both. Fat and carbs are limits (red once passed).
     */
    private fun macroRows(p: WidgetPainter, c: Canvas, d: WidgetData, x: Float, y: Float, w: Float, h: Float, title: String?, withFiber: Boolean) {
        val t = d.today
        val rows = listOfNotNull(
            MacroRow("Protein", t.proteinG, t.proteinTargetG, Tone.PROTEIN, cap = false),
            MacroRow("Carbs", t.carbsG, t.carbsTargetG, Tone.CARBS, cap = true),
            MacroRow("Fat", t.fatG, t.fatTargetG, Tone.FAT, cap = true),
            if (withFiber) MacroRow("Fiber", t.fiberG, t.fiberTargetG, Tone.FIBER, cap = false) else null
        )
        var top = y
        if (title != null) {
            val tp = p.text(min(p.dp(16f), h * 0.16f), weight = 600, serif = true)
            p.drawFit(c, title, x, top + tp.textSize * 0.92f, tp, w)
            top += tp.textSize * 1.45f
        }
        val rowH = (y + h - top) / rows.size
        val size = min(p.dp(11.5f), rowH * 0.34f)
        val label = p.text(size, p.ink.muted, weight = 500)
        val value = p.text(size, p.ink.main, weight = 600, tnum = true, align = Paint.Align.RIGHT)
        val barH = max(p.dp(3f), min(p.dp(5f), rowH * 0.14f))
        rows.forEach { r ->
            val baseline = top + rowH * 0.5f - barH * 0.2f
            val valueText = if (r.target > 0) "${r.value} / ${r.target} g" else "${r.value} g"
            val vw = value.measureText(valueText)
            p.drawFit(c, r.name, x, baseline, label, w - vw - p.dp(8f))
            c.drawText(valueText, x + w, baseline, value)
            val over = r.cap && r.target > 0 && r.value > r.target
            p.bar(c, x, baseline + p.dp(5f), w, barH, if (r.target > 0) r.value.toFloat() / r.target else 0f,
                p.tone(if (over) Tone.OVER else r.tone))
            top += rowH
        }
    }

    private class MacroRow(val name: String, val value: Int, val target: Int, val tone: Tone, val cap: Boolean)

    // ======================== QUICK LOG ========================

    private fun quickLog(d: WidgetData, w: Float, h: Float): WidgetScene {
        val all = listOf(
            SceneAction("Snap", WidgetIcon.CAMERA, primary = true, WidgetTarget.Open(Screen.Camera.route)),
            SceneAction("Describe", WidgetIcon.CHAT, primary = false, WidgetTarget.Open(Screen.DescribeFood.route)),
            SceneAction("Search", WidgetIcon.SEARCH, primary = false, WidgetTarget.Open(Screen.ManualEntry.route)),
            SceneAction("Add food", WidgetIcon.ADD, primary = false, WidgetTarget.Open(Screen.AddFood.route))
        )
        val actions = when {
            w >= 270f -> all
            w >= 190f -> all.take(3)
            else -> listOf(all[0], all[3])
        }
        val description = "Log a meal: " + actions.joinToString { it.label }
        val tap = WidgetTarget.Open(Screen.AddFood.route)
        if (h < 104f) return WidgetScene(description, tap, actions, actionsFill = true)

        val actionHeight = min(60f, h * 0.5f)
        return WidgetScene(description, tap, actions, actionHeightDp = actionHeight) { p, c, W, H ->
            val showRing = W >= p.dp(160f)
            val ringD = min(H, p.dp(46f))
            val textW = if (showRing) W - ringD - p.dp(14f) else W
            val title = p.text(min(p.dp(18f), H * 0.42f), weight = 600, serif = true)
            val sub = p.text(min(p.dp(11.5f), H * 0.26f), p.ink.muted, weight = 500)
            val block = title.textSize * 0.95f + sub.textSize * 1.6f
            val titleBase = H / 2f - block / 2f + title.textSize * 0.9f
            p.drawFit(c, "Log a meal", 0f, titleBase, title, textW)
            val fasting = d.fasting.takeIf { it.enabled }?.stateAt(minuteOfDayNow())
            val status = when {
                fasting?.isFasting == true -> "Fasting · you can eat from ${fastingClockLabel(fasting.nextChangeMin)}"
                !d.hasGoal -> "What did you have?"
                d.caloriesLeft >= 0 -> "${"%,d".format(d.caloriesLeft)} kcal left today"
                else -> "${"%,d".format(-d.caloriesLeft)} kcal over today"
            }
            p.drawFit(c, status, 0f, titleBase + sub.textSize * 1.6f, sub, textW)
            if (showRing) {
                val over = d.hasGoal && d.caloriesLeft < 0
                val frac = if (d.calorieBudget > 0) d.today.caloriesConsumed.toFloat() / d.calorieBudget else 0f
                val stroke = p.dp(4f)
                p.ring(c, W - ringD / 2f, H / 2f, ringD / 2f - stroke, stroke, if (over) 1f else frac,
                    p.tone(if (over) Tone.OVER else Tone.CALORIE))
            }
        }
    }

    // ======================== MACROS ========================

    private fun macros(d: WidgetData, w: Float, h: Float): WidgetScene {
        val t = d.today
        val items = listOf(
            MacroRow("Protein", t.proteinG, t.proteinTargetG, Tone.PROTEIN, cap = false),
            MacroRow("Fat", t.fatG, t.fatTargetG, Tone.FAT, cap = true),
            MacroRow("Carbs", t.carbsG, t.carbsTargetG, Tone.CARBS, cap = true),
            MacroRow("Fiber", t.fiberG, t.fiberTargetG, Tone.FIBER, cap = false)
        )
        val description = items.joinToString(". ") { "${it.name} ${it.value} of ${it.target} grams" }
        val rings = w >= h * 1.9f
        return WidgetScene(description, home) { p, c, W, H ->
            if (!rings) {
                macroRows(p, c, d, 0f, 0f, W, H, title = if (H >= p.dp(150f)) "Macros" else null, withFiber = true)
                return@WidgetScene
            }
            val colW = W / items.size
            val label = p.text(min(p.dp(11.5f), H * 0.13f), p.ink.muted, weight = 500, align = Paint.Align.CENTER)
            val foot = p.text(min(p.dp(10.5f), H * 0.12f), p.ink.faint, weight = 500, tnum = true, align = Paint.Align.CENTER)
            val labelH = p.lineHeight(label)
            val footH = p.lineHeight(foot)
            val dia = min(colW * 0.74f, H - labelH - footH - p.dp(8f))
            val stroke = max(p.dp(3f), dia * 0.1f)
            val block = labelH + p.dp(4f) + dia + p.dp(4f) + footH
            val top = (H - block) / 2f
            items.forEachIndexed { i, m ->
                val cx = colW * i + colW / 2f
                val over = m.cap && m.target > 0 && m.value > m.target
                val color = p.tone(if (over) Tone.OVER else m.tone)
                c.drawText(m.name, cx, top + labelH * 0.8f, label)
                val cy = top + labelH + p.dp(4f) + dia / 2f
                p.ring(c, cx, cy, dia / 2f - stroke, stroke, if (m.target > 0) m.value.toFloat() / m.target else 0f, color)
                val num = p.text(dia * 0.3f, if (over) color else p.ink.main, weight = 700, tnum = true, align = Paint.Align.CENTER)
                c.drawText(m.value.toString(), cx, p.baselineFor(num, cy), num)
                c.drawText(if (m.target > 0) "of ${m.target}g" else "—", cx, cy + dia / 2f + p.dp(4f) + footH * 0.8f, foot)
            }
        }
    }

    // ======================== WATER ========================

    private fun water(d: WidgetData, w: Float, h: Float): WidgetScene {
        val ml = d.today.waterMl
        val goal = d.today.waterGoalMl.coerceAtLeast(1)
        val frac = ml.toFloat() / goal
        // Two buttons on a narrow widget — three would squeeze "+500" down to "+5…".
        val presets = d.today.waterPresets.filter { it in 1..3000 }.distinct()
            .take(if (w < 150f) 2 else 3).ifEmpty { listOf(250) }
        val first = presets.first()
        val description = "${volume(ml)} of ${volume(goal)} of water today"

        if (h < 110f) {
            // Small: the whole widget is the "+" — one tap logs a glass.
            return WidgetScene("$description. Tap to add $first millilitres.", WidgetTarget.AddWater(first)) { p, c, W, H ->
                val water = p.tone(Tone.WATER)
                if (W < H * 1.45f) {
                    val size = min(W, H) * 0.64f
                    waterDrop(p, c, W / 2f, H * 0.42f, size, frac)
                    val cap = p.text(min(p.dp(11f), H * 0.14f), water, weight = 700, align = Paint.Align.CENTER)
                    c.drawText("+$first ml", W / 2f, H - p.dp(2f), cap)
                } else {
                    val size = H * 0.92f
                    waterDrop(p, c, size / 2f, H / 2f, size, frac)
                    val pill = RectF(W - min(W * 0.36f, p.dp(84f)), H / 2f - p.dp(15f), W, H / 2f + p.dp(15f))
                    c.drawRoundRect(pill, pill.height() / 2f, pill.height() / 2f, p.fill(water))
                    val pillText = p.text(p.dp(12f), if (p.style.isLight) 0xFFFFFFFF.toInt() else 0xFF0E1830.toInt(), weight = 700, align = Paint.Align.CENTER)
                    c.drawText("+$first ml", pill.centerX(), p.baselineFor(pillText, pill.centerY()), pillText)
                    val x0 = size + p.dp(12f)
                    val big = p.text(min(p.dp(24f), H * 0.36f), weight = 800, tnum = true)
                    val small = p.text(min(p.dp(11f), H * 0.17f), p.ink.muted, weight = 500)
                    val maxW = pill.left - x0 - p.dp(8f)
                    p.drawFit(c, volume(ml), x0, H / 2f + big.textSize * 0.2f, big, maxW)
                    p.drawFit(c, "of ${volume(goal)}", x0, H / 2f + big.textSize * 0.2f + small.textSize * 1.5f, small, maxW)
                }
            }
        }

        // Text-only buttons — three identical "+" icons wouldn't say which amount is which.
        val actions = presets.mapIndexed { i, amount ->
            SceneAction("+$amount", null, primary = i == 0, WidgetTarget.AddWater(amount))
        }
        val tap = WidgetTarget.Open(Screen.WaterDetail.buildRoute(LocalDate.now().toString()))
        return WidgetScene(description, tap, actions, actionHeightDp = min(46f, h * 0.3f)) { p, c, W, H ->
            if (W < p.dp(160f)) {
                // Too narrow for the drop beside the numbers: a small drop by the title, a bar below.
                val title = p.text(min(p.dp(15f), H * 0.2f), weight = 600, serif = true)
                val dropSize = title.textSize * 1.35f
                p.drawFit(c, "Water", 0f, title.textSize * 0.95f, title, W - dropSize - p.dp(6f))
                waterDrop(p, c, W - dropSize / 2f, title.textSize * 0.55f, dropSize, frac)
                val big = p.text(min(p.dp(28f), H * 0.3f), weight = 800, tnum = true)
                val small = p.text(min(p.dp(11f), H * 0.13f), p.ink.muted, weight = 500)
                val bigBase = H * 0.5f + big.textSize * 0.3f
                p.drawFit(c, volume(ml), 0f, bigBase, big, W, minScale = 0.5f)
                p.drawFit(c, "of ${volume(goal)}", 0f, bigBase + small.textSize * 1.6f, small, W)
                p.bar(c, 0f, H - p.dp(5f), W, p.dp(5f), frac, p.tone(Tone.WATER))
                return@WidgetScene
            }
            val dropSize = min(H * 0.98f, W * 0.4f)
            waterDrop(p, c, W - dropSize / 2f, H / 2f, dropSize, frac)
            val textW = W - dropSize - p.dp(12f)
            val title = p.text(min(p.dp(15f), H * 0.2f), weight = 600, serif = true)
            val big = p.text(min(p.dp(30f), H * 0.34f), weight = 800, tnum = true)
            val small = p.text(min(p.dp(11.5f), H * 0.15f), p.ink.muted, weight = 500)
            p.drawFit(c, "Water", 0f, title.textSize * 0.95f, title, textW)
            val bigBase = H * 0.5f + big.textSize * 0.36f
            p.drawFit(c, volume(ml), 0f, bigBase, big, textW, minScale = 0.5f)
            val pct = (frac * 100).roundToInt()
            val withPct = "of ${volume(goal)} · $pct%"
            val sub = if (small.measureText(withPct) <= textW) withPct else "of ${volume(goal)}"
            p.drawFit(c, sub, 0f, bigBase + small.textSize * 1.7f, small, textW)
        }
    }

    /** A water drop filled from the bottom to [frac], with a soft wave on top. */
    private fun waterDrop(p: WidgetPainter, c: Canvas, cx: Float, cy: Float, size: Float, frac: Float) {
        val src = WidgetIcon.DROP.parsed.firstOrNull() ?: return
        val m = Matrix().apply { setScale(size / 24f, size / 24f); postTranslate(cx - size / 2f, cy - size / 2f) }
        val drop = Path(src).apply { transform(m) }
        c.drawPath(drop, p.fill(p.ink.track))
        val f = frac.coerceIn(0f, 1f)
        if (f <= 0f) return
        // The drop spans x 6–18, y 2–20 of its 24-unit icon box.
        val s = size / 24f
        val x0 = cx - size / 2f
        val y0 = cy - size / 2f
        val b = RectF(x0 + 6f * s, y0 + 2f * s, x0 + 18f * s, y0 + 20f * s)
        val level = b.bottom - b.height() * f
        val water = p.tone(Tone.WATER)
        val layer = c.saveLayer(RectF(b.left - 1f, b.top - 1f, b.right + 1f, b.bottom + 1f), null)
        val amp = if (f >= 0.98f) 0f else size * 0.025f
        val wave = Path().apply {
            moveTo(b.left - size, level)
            var x = b.left - size
            val len = size * 0.5f
            while (x < b.right + size) {
                quadTo(x + len / 4f, level - amp, x + len / 2f, level)
                quadTo(x + len * 3f / 4f, level + amp, x + len, level)
                x += len
            }
            lineTo(b.right + size, b.bottom + 1f)
            lineTo(b.left - size, b.bottom + 1f)
            close()
        }
        c.drawPath(wave, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, level, 0f, b.bottom, WidgetPainter.withAlpha(water, 0xB0), water, Shader.TileMode.CLAMP)
        })
        WidgetPainter.clearOutside(c, drop)
        c.restoreToCount(layer)
    }

    // ======================== FASTING ========================

    private fun fasting(d: WidgetData, w: Float, h: Float): WidgetScene {
        val s = d.fasting
        if (!s.enabled) {
            return WidgetScene("Fasting is off. Tap to set your eating window.", WidgetTarget.Open(Screen.SettingsCategory.buildRoute("fasting"))) { p, c, W, H ->
                message(p, c, W, H, WidgetIcon.HOURGLASS, p.tone(Tone.FASTING), "Fasting is off", "Tap to set your eating window")
            }
        }
        val st = s.stateAt(minuteOfDayNow())
        val tone = if (st.isFasting) Tone.FASTING else Tone.EATING
        val until = nextOccurrence(st.nextChangeMin, d.nowMillis)
        val label = if (st.isFasting) "Fasting" else "Eating window"
        val untilLine = if (st.isFasting) "until ${fastingClockLabel(st.nextChangeMin)}" else "closes ${fastingClockLabel(st.nextChangeMin)}"
        val phaseStart = if (st.isFasting) s.eatEndMin else s.eatStartMin
        val description = "$label — ${fastingCountdownLabel(st.minutesLeftInPhase)} left, $untilLine"

        return when {
            w < h * 1.6f -> {
                val dia = min(w, h)
                val timerSize = dia * 0.17f
                WidgetScene(description, home, timer = SceneTimer(until, timerSize)) { p, c, W, H ->
                    val color = p.tone(tone)
                    val D = min(W, H)
                    val stroke = max(p.dp(4f), D * 0.07f)
                    p.ring(c, W / 2f, H / 2f, D / 2f - stroke, stroke, st.progressFraction, color)
                    val half = p.dp(timerSize) * 0.62f
                    val lab = p.text(max(p.dp(9f), D * 0.075f), color, weight = 700, align = Paint.Align.CENTER)
                    c.drawText(label, W / 2f, H / 2f - half - p.dp(4f), lab)
                    val u = p.text(max(p.dp(8.5f), D * 0.066f), p.ink.muted, weight = 500, align = Paint.Align.CENTER)
                    p.drawFit(c, untilLine, W / 2f, H / 2f + half + p.dp(4f) + u.textSize * 0.85f, u, D * 0.62f)
                }
            }
            h < 110f -> {
                val timerSize = min(h * 0.34f, 28f)
                WidgetScene(description, home, timer = SceneTimer(until, timerSize)) { p, c, W, H ->
                    val color = p.tone(tone)
                    val side = (W - p.dp(timerSize) * 4.4f) / 2f - p.dp(6f)
                    val iconSize = min(H * 0.3f, p.dp(18f))
                    val mid = H / 2f - p.dp(3f)
                    p.icon(c, if (st.isFasting) WidgetIcon.HOURGLASS else WidgetIcon.FORK_KNIFE, iconSize / 2f, mid - iconSize * 0.6f, iconSize, color)
                    val lab = p.text(min(p.dp(12.5f), H * 0.2f), color, weight = 700)
                    p.drawFit(c, label, 0f, mid + lab.textSize * 1.25f, lab, side)
                    val right = p.text(min(p.dp(11.5f), H * 0.18f), p.ink.muted, weight = 500, align = Paint.Align.RIGHT)
                    p.drawFit(c, untilLine, W, mid + right.textSize * 0.35f, right, side)
                    p.bar(c, 0f, H - p.dp(4f), W, p.dp(4f), st.progressFraction, color)
                }
            }
            else -> {
                val timerSize = min(h * 0.22f, w * 0.11f).coerceAtMost(40f)
                WidgetScene(description, home, timer = SceneTimer(until, timerSize)) { p, c, W, H ->
                    val color = p.tone(tone)
                    val title = p.text(min(p.dp(15f), H * 0.12f), weight = 600, serif = true)
                    val iconSize = title.textSize * 1.1f
                    p.icon(c, if (st.isFasting) WidgetIcon.HOURGLASS else WidgetIcon.FORK_KNIFE, iconSize / 2f, title.textSize * 0.62f, iconSize, color)
                    p.drawFit(c, label, iconSize + p.dp(6f), title.textSize * 0.95f, title, W * 0.6f)
                    val ratio = p.text(min(p.dp(11f), H * 0.1f), p.ink.muted, weight = 600, align = Paint.Align.RIGHT)
                    c.drawText(eatRatio(s.fastingLengthMin()), W, title.textSize * 0.95f, ratio)
                    val times = p.text(min(p.dp(10.5f), H * 0.09f), p.ink.muted, weight = 500, tnum = true)
                    val barY = H - times.textSize * 1.6f - p.dp(6f)
                    p.bar(c, 0f, barY, W, p.dp(5f), st.progressFraction, color)
                    c.drawText(fastingClockLabel(phaseStart), 0f, H - p.dp(1f), times)
                    times.textAlign = Paint.Align.RIGHT
                    c.drawText(fastingClockLabel(st.nextChangeMin), W, H - p.dp(1f), times)
                }
            }
        }
    }

    /** "16:8" from the fast's length — the familiar fast:eat ratio. */
    private fun eatRatio(fastMin: Int): String {
        val fastH = (fastMin / 60f).roundToInt()
        return "$fastH:${24 - fastH}"
    }

    // ======================== WEEK ========================

    private fun week(d: WidgetData, w: Float, h: Float): WidgetScene {
        val target = d.today.caloriesTarget
        val logged = d.week.filter { it.kcal > 0 }
        val avg = if (logged.isEmpty()) 0 else logged.sumOf { it.kcal } / logged.size
        val description = "This week you averaged $avg calories a day" +
            (if (target > 0) " against a goal of $target" else "") + ". Logging streak: ${d.streak} days."
        return WidgetScene(description, WidgetTarget.Open(Screen.Analytics.route)) { p, c, W, H ->
            val compact = W < p.dp(200f)
            val title = p.text(min(p.dp(15f), H * 0.13f), weight = 600, serif = true)
            val titleBase = title.textSize * 0.95f
            p.drawFit(c, if (compact) "Week" else "This week", 0f, titleBase, title, W * 0.5f)
            // The streak, top right, with its flame.
            val streakText = when {
                d.streak <= 0 -> if (compact) "" else "No streak yet"
                compact -> "${d.streak}"
                else -> "${d.streak}-day streak"
            }
            if (streakText.isNotEmpty()) {
                val sp = p.text(min(p.dp(11.5f), H * 0.1f), p.ink.main, weight = 600, align = Paint.Align.RIGHT)
                c.drawText(streakText, W, titleBase, sp)
                if (d.streak > 0) {
                    val fs = sp.textSize * 1.25f
                    p.icon(c, WidgetIcon.FLAME, W - sp.measureText(streakText) - fs * 0.62f, titleBase - sp.textSize * 0.36f, fs, p.tone(Tone.GOLD))
                }
            }
            var top = titleBase + p.dp(8f)
            if (!compact && H >= p.dp(120f) && avg > 0) {
                val sub = p.text(min(p.dp(11f), H * 0.09f), p.ink.muted, weight = 500, tnum = true)
                val line = "avg ${"%,d".format(avg)} kcal" + if (target > 0) " · goal ${"%,d".format(target)}" else ""
                p.drawFit(c, line, 0f, top + sub.textSize, sub, W)
                top += sub.textSize * 1.6f
            }
            val dayPaint = p.text(min(p.dp(10f), H * 0.085f), p.ink.faint, weight = 600, align = Paint.Align.CENTER)
            val dayH = p.lineHeight(dayPaint)
            val chartTop = top + p.dp(4f)
            val chartBottom = H - dayH - p.dp(2f)
            val chartH = (chartBottom - chartTop).coerceAtLeast(1f)
            val maxV = max(d.week.maxOfOrNull { it.kcal } ?: 0, (target * 1.15f).roundToInt()).coerceAtLeast(1)
            val slot = W / 7f
            val barW = min(slot * 0.56f, p.dp(22f))
            val today = LocalDate.now()
            d.week.forEachIndexed { i, day ->
                val cx = slot * i + slot / 2f
                val isToday = day.date == today
                val overGoal = target > 0 && day.kcal > target * 1.05f
                val base = p.tone(if (overGoal) Tone.OVER else Tone.CALORIE)
                val color = if (isToday) base else WidgetPainter.withAlpha(base, 0x99)
                val bh = if (day.kcal > 0) max(p.dp(4f), chartH * day.kcal / maxV) else p.dp(3f)
                val rect = RectF(cx - barW / 2f, chartBottom - bh, cx + barW / 2f, chartBottom)
                c.drawRoundRect(rect, min(barW / 2f, p.dp(6f)), min(barW / 2f, p.dp(6f)), p.fill(if (day.kcal > 0) color else p.ink.track))
                dayPaint.color = if (isToday) p.ink.main else p.ink.faint
                c.drawText(day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), cx, H - p.dp(1f), dayPaint)
            }
            if (target > 0) {
                val y = chartBottom - chartH * target / maxV
                c.drawLine(0f, y, W, y, p.stroke(p.ink.faint, max(1f, p.dp(1f)), round = false).apply {
                    pathEffect = DashPathEffect(floatArrayOf(p.dp(4f), p.dp(4f)), 0f)
                })
            }
        }
    }

    // ======================== WEIGHT ========================

    private fun weight(d: WidgetData, w: Float, h: Float): WidgetScene {
        val latest = d.weights.lastOrNull()
        val weighIn = WidgetTarget.Open(Screen.WeighIn.route)
        if (latest == null) {
            return WidgetScene("No weigh-ins yet. Tap to add one.", weighIn) { p, c, W, H ->
                message(p, c, W, H, WidgetIcon.ADD, p.tone(Tone.GOOD), "No weigh-ins yet", "Tap to add one")
            }
        }
        val change = d.weightChange30
        val onTrack = change?.let { WeightTrend.changeIsOnTrack(it, d.goal) }
        val description = "Weight ${kg(latest.second)} kilograms" + (change?.let { ", ${signedKg(it)} over 30 days" } ?: "")
        return WidgetScene(description, weighIn) { p, c, W, H ->
            val showTitle = H >= p.dp(100f)
            val showChart = H >= p.dp(120f) && d.weights.size >= 2
            var y = 0f
            if (showTitle) {
                val title = p.text(min(p.dp(15f), H * 0.13f), weight = 600, serif = true)
                p.drawFit(c, "Weight", 0f, title.textSize * 0.95f, title, W)
                y = title.textSize * 1.35f
            }
            val numberArea = if (showChart) H * 0.42f else H - y
            val big = p.text(min(p.dp(34f), numberArea * 0.62f), weight = 800, tnum = true)
            val unit = p.text(big.textSize * 0.42f, p.ink.muted, weight = 500)
            val numText = kg(latest.second)
            val chipH = min(p.dp(11f), H * 0.1f) * 1.9f
            val base = if (showChart) y + big.textSize * 0.9f
                // No chart: the number and its chip sit together, centred in the space left.
                else y + ((H - y) - (big.textSize * 0.74f + p.dp(8f) + chipH)) / 2f + big.textSize * 0.74f
            val nw = p.drawFit(c, numText, 0f, base, big, W * 0.7f, minScale = 0.5f)
            c.drawText("kg", nw + p.dp(4f), base, unit)
            val chipColor = when (onTrack) {
                true -> p.tone(Tone.GOOD)
                false -> p.tone(Tone.OVER)
                null -> p.ink.muted
            }
            val chipText = change?.let { "${signedKg(it)} kg · 30 days" } ?: "Keep weighing in"
            val cp = p.text(min(p.dp(11f), H * 0.1f), chipColor, weight = 600, tnum = true)
            val cw = cp.measureText(chipText) + p.dp(16f)
            val ch = cp.textSize * 1.9f
            val chipTop = base + p.dp(8f)
            val chip = RectF(0f, chipTop, min(cw, W), chipTop + ch)
            c.drawRoundRect(chip, ch / 2f, ch / 2f, p.fill(WidgetPainter.withAlpha(chipColor, 0x2E)))
            p.drawFit(c, chipText, p.dp(8f), p.baselineFor(cp, chip.centerY()), cp, W - p.dp(16f))
            if (showChart) sparkline(p, c, d.weights, RectF(0f, chip.bottom + p.dp(10f), W, H), p.tone(Tone.GOOD))
        }
    }

    /** The weigh-ins as a smooth line with a soft fill under it and a dot on the latest. */
    private fun sparkline(p: WidgetPainter, c: Canvas, pts: List<Pair<LocalDate, Float>>, box: RectF, color: Int) {
        if (pts.size < 2 || box.height() < p.dp(12f)) return
        val x0 = pts.first().first.toEpochDay()
        val span = (pts.last().first.toEpochDay() - x0).coerceAtLeast(1)
        val lo = pts.minOf { it.second }
        val hi = pts.maxOf { it.second }
        val range = max(hi - lo, 0.6f)
        val mid = (hi + lo) / 2f
        val inset = p.dp(4f)
        fun x(dp: LocalDate) = box.left + inset + (box.width() - inset * 2) * (dp.toEpochDay() - x0) / span
        fun y(v: Float) = box.bottom - inset - (box.height() - inset * 2) * ((v - (mid - range / 2f)) / range)
        val line = Path()
        pts.forEachIndexed { i, (date, v) ->
            val px = x(date)
            val py = y(v)
            if (i == 0) line.moveTo(px, py) else {
                val (pd, pv) = pts[i - 1]
                val mx = (x(pd) + px) / 2f
                line.quadTo(x(pd), y(pv), mx, (y(pv) + py) / 2f)
                if (i == pts.lastIndex) line.lineTo(px, py)
            }
        }
        val fillPath = Path(line).apply {
            lineTo(x(pts.last().first), box.bottom)
            lineTo(x(pts.first().first), box.bottom)
            close()
        }
        c.drawPath(fillPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, box.top, 0f, box.bottom, WidgetPainter.withAlpha(color, 0x55), WidgetPainter.withAlpha(color, 0x00), Shader.TileMode.CLAMP)
        })
        c.drawPath(line, p.stroke(color, max(p.dp(2f), 1.5f)))
        val (ld, lv) = pts.last()
        c.drawCircle(x(ld), y(lv), p.dp(4f), p.fill(color))
        c.drawCircle(x(ld), y(lv), p.dp(1.6f), p.fill(p.ink.main))
    }

    // ======================== CAFFEINE ========================

    private fun caffeine(d: WidgetData, w: Float, h: Float): WidgetScene {
        val cf = d.caffeine
            ?: return WidgetScene("The caffeine tracker is off. Tap to turn it on.", WidgetTarget.Open(Screen.SettingsCategory.buildRoute("caffeine"))) { p, c, W, H ->
                message(p, c, W, H, WidgetIcon.COFFEE, p.tone(Tone.CAFFEINE), "Caffeine tracker is off", "Tap to turn it on")
            }
        val limit = cf.settings.dailyLimitMg.coerceAtLeast(1)
        val now = cf.nowMg.roundToInt()
        val sleepLine = cf.sleepFriendlyAt?.let { "Easy on sleep from ${clockLabel(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime())}" }
            ?: if (now <= 50) "Low enough for sleep" else "Still high for sleep"
        val description = "About $now milligrams of caffeine in you now. $sleepLine."
        val square = w < h * 1.6f
        return WidgetScene(description, home) { p, c, W, H ->
            val color = p.tone(if (cf.overLimit) Tone.OVER else Tone.CAFFEINE)
            if (square) {
                val lineP = p.text(min(p.dp(10.5f), H * 0.08f), p.tone(if (now <= 50) Tone.GOOD else Tone.CAFFEINE), weight = 600, align = Paint.Align.CENTER)
                val lineH = p.lineHeight(lineP)
                val D = min(W, H - lineH - p.dp(6f))
                val cy = D / 2f
                val stroke = max(p.dp(4f), D * 0.075f)
                p.ring(c, W / 2f, cy, D / 2f - stroke, stroke, cf.nowMg / limit, color)
                val num = p.text(D * 0.25f, if (cf.overLimit) color else p.ink.main, weight = 800, tnum = true, align = Paint.Align.CENTER)
                val cap = p.text(max(p.dp(9f), D * 0.078f), p.ink.muted, weight = 500, align = Paint.Align.CENTER)
                stackCentered(p, c, W / 2f, cy, D * 0.6f, listOf("$now" to num, (if (cf.overLimit) "mg · over limit" else "mg in you") to cap))
                p.drawFit(c, sleepLine, W / 2f, H - p.dp(2f), lineP, W)
            } else {
                val iconSize = min(H * 0.42f, p.dp(28f))
                p.icon(c, WidgetIcon.COFFEE, iconSize / 2f, H * 0.4f, iconSize, color)
                val x0 = iconSize + p.dp(12f)
                val big = p.text(min(p.dp(26f), H * 0.34f), if (cf.overLimit) color else p.ink.main, weight = 800, tnum = true)
                val unit = p.text(big.textSize * 0.44f, p.ink.muted, weight = 500)
                val base = H * 0.4f + big.textSize * 0.36f
                val nw = p.drawFit(c, "$now", x0, base, big, W * 0.3f)
                c.drawText("mg in you", x0 + nw + p.dp(5f), base, unit)
                val sp = p.text(min(p.dp(11f), H * 0.16f), p.tone(if (now <= 50) Tone.GOOD else Tone.CAFFEINE), weight = 600)
                p.drawFit(c, sleepLine, x0, base + sp.textSize * 1.7f, sp, W - x0)
                p.bar(c, 0f, H - p.dp(4f), W, p.dp(4f), cf.nowMg / limit, color)
            }
        }
    }

    // ======================== shared bits ========================

    /** A centred icon + title + hint, for "set this up first" states. */
    private fun message(p: WidgetPainter, c: Canvas, W: Float, H: Float, icon: WidgetIcon, color: Int, title: String, hint: String) {
        val iconSize = min(H * 0.26f, p.dp(28f))
        val tp = p.text(min(p.dp(13.5f), H * 0.13f), weight = 600, align = Paint.Align.CENTER)
        val hp = p.text(min(p.dp(11f), H * 0.1f), p.ink.muted, weight = 500, align = Paint.Align.CENTER)
        val block = iconSize + p.dp(8f) + tp.textSize * 1.2f + hp.textSize * 1.4f
        val top = (H - block) / 2f
        p.icon(c, icon, W / 2f, top + iconSize / 2f, iconSize, color)
        val tBase = top + iconSize + p.dp(8f) + tp.textSize
        p.drawFit(c, title, W / 2f, tBase, tp, W)
        p.drawFit(c, hint, W / 2f, tBase + hp.textSize * 1.45f, hp, W)
    }

    /**
     * Stacks centred lines (first one big) around [cy]: visually centred as a block, each line shrunk
     * to fit [maxWidth].
     */
    private fun stackCentered(p: WidgetPainter, c: Canvas, cx: Float, cy: Float, maxWidth: Float, lines: List<Pair<String, android.text.TextPaint>>) {
        if (lines.isEmpty()) return
        // Cap height ≈ 0.73 em for Inter; gaps scale with the line under them.
        val heights = lines.map { it.second.textSize * 0.73f }
        val gaps = lines.drop(1).mapIndexed { i, l -> if (i == 0) l.second.textSize * 0.62f else l.second.textSize * 0.55f }
        val total = heights.sum() + gaps.sum()
        var y = cy - total / 2f
        lines.forEachIndexed { i, (text, paint) ->
            if (i > 0) y += gaps[i - 1]
            y += heights[i]
            // The first line is the hero number: shrink it further rather than cut it.
            p.drawFit(c, text, cx, y, paint, maxWidth, minScale = if (i == 0) 0.5f else 0.7f)
        }
    }

    /** The next wall-clock time [minuteOfDay] comes round (epoch ms). */
    private fun nextOccurrence(minuteOfDay: Int, nowMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        var at = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        return at.atZone(zone).toInstant().toEpochMilli()
    }

    private fun volume(ml: Int): String = if (ml >= 1000) "${"%.2f".format(ml / 1000f).trimEnd('0').trimEnd('.', ',')} L" else "$ml ml"
    private fun kg(v: Float): String = "%.1f".format(v)
    private fun signedKg(v: Float): String = (if (v > 0f) "+" else if (v < 0f) "−" else "±") + "%.1f".format(abs(v))
}
