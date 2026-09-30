package com.fitpal.app.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.FastingSchedule
import com.fitpal.app.domain.model.FastingState
import com.fitpal.app.domain.model.FastingStyle
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.OverlaySurface
import com.fitpal.app.ui.theme.glassOverlay
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The fasting timer's faces, shared by Home (the strip / the arc around the calorie ring / the
 * intense-mode reminder), the Add-food stop (FastingTakeover) and the Settings style tiles. Everything
 * reads one live [FastingState] from [rememberFastingState], so every surface flips at the same minute.
 */

/** Minutes since midnight, now. */
fun minuteOfDayNow(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

/**
 * The live fasting phase for [schedule], re-evaluated on every minute boundary (so a countdown never
 * sits a minute stale the way a fixed 30 s tick could).
 */
@Composable
fun rememberFastingState(schedule: FastingSchedule): FastingState {
    var nowMin by remember { mutableIntStateOf(minuteOfDayNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            nowMin = now.hour * 60 + now.minute
            val toNextMinute = (60 - now.second) * 1000L - now.nano / 1_000_000L
            delay(toNextMinute.coerceIn(200L, 60_000L) + 50L)
        }
    }
    return remember(schedule, nowMin) { schedule.stateAt(nowMin) }
}

/** Fasting reads in the Trends blue; the eating window in the Today gold. */
fun fastingAccent(state: FastingState): Color = if (state.isFasting) AccentTrends else GoldLight

/** How long into the current fast (minutes) — the fast's full length minus what's left of it. */
fun fastingElapsedMin(schedule: FastingSchedule, state: FastingState): Int {
    val len = schedule.fastingLengthMin()
    return (len - state.minutesLeftInPhase).coerceIn(0, len)
}

/** "Fasting · 3h 12m until 12:00 PM" / "Eating window · 5h left", with the time picked out in [accent]. */
@Composable
private fun FastingStatusText(state: FastingState, accent: Color, modifier: Modifier = Modifier) {
    val countdown = fastingCountdownLabel(state.minutesLeftInPhase)
    Text(
        text = buildAnnotatedString {
            if (state.isFasting) {
                append("Fasting · ")
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(countdown) }
                append(" until ${fastingClockLabel(state.nextChangeMin)}")
            } else {
                append("Eating window · ")
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(countdown) }
                append(" left")
            }
        },
        style = MaterialTheme.typography.labelLarge,
        color = Cream,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

// ======================== HORIZONTAL (the original strip) ========================

/**
 * [FastingStyle.BAR]: the slim timer above the calorie ring — one line plus a thin bar that fills as
 * the current phase completes. Deliberately quiet so it never competes with the hero calorie number.
 */
@Composable
fun FastingStrip(schedule: FastingSchedule, modifier: Modifier = Modifier) {
    val state = rememberFastingState(schedule)
    val fasting = state.isFasting
    val accent = fastingAccent(state)
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (fasting) Icons.Default.HourglassEmpty else Icons.Default.Restaurant,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(8.dp))
            FastingStatusText(state, accent, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (fasting) "can't eat yet" else "go ahead",
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                Modifier.fillMaxWidth(state.progressFraction.coerceIn(0f, 1f)).height(4.dp)
                    .clip(RoundedCornerShape(50)).background(accent)
            )
        }
    }
}

// ======================== CIRCLE (the arc around the calorie ring) ========================

/** Where the arc starts (bottom-left) and how far it runs clockwise over the top to bottom-right. */
private const val ARC_START_DEG = 125f
private const val ARC_SWEEP_DEG = 290f

/**
 * [FastingStyle.RING]: the calorie ring wrapped in a thin arc that opens at the bottom — it starts
 * bottom-left, beside the "base + activity" line, runs over the top and ends bottom-right. A glowing
 * knob rides it as the current phase (fast or eating window) completes; one line above says how long
 * is left. [ring] is the calorie ring itself (drawn [ringDiameter] wide, centred in the arc);
 * [inGap] sits in the opening under it.
 */
@Composable
fun FastingRingAround(
    schedule: FastingSchedule,
    ringDiameter: Dp,
    modifier: Modifier = Modifier,
    inGap: (@Composable () -> Unit)? = null,
    ring: @Composable () -> Unit
) {
    val state = rememberFastingState(schedule)
    val accent = fastingAccent(state)
    // Draws itself in on first show, then glides minute to minute.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(state.progressFraction) {
        progress.animateTo(state.progressFraction.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing))
    }
    val pulse by rememberInfiniteTransition(label = "fastKnob").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "fastKnobPulse"
    )

    val arcGap = 14.dp          // ring's outer edge → arc centre line
    val halo = 12.dp            // the knob's glow radius
    val radius = ringDiameter / 2 + arcGap
    val gapHeight = if (inGap != null) 26.dp else 6.dp

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (state.isFasting) Icons.Default.HourglassEmpty else Icons.Default.Restaurant,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            FastingStatusText(state, accent)
        }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.size(width = (radius + halo) * 2, height = radius + halo + ringDiameter / 2 + gapHeight)) {
            Canvas(Modifier.matchParentSize()) {
                val c = Offset(size.width / 2f, (radius + halo).toPx())
                drawFastingArc(c, radius.toPx(), progress.value, accent, knobPulse = pulse, stroke = 4.dp.toPx(), knob = 6.dp.toPx(), halo = halo.toPx())
            }
            Box(Modifier.align(Alignment.TopCenter).padding(top = radius + halo - ringDiameter / 2)) { ring() }
            if (inGap != null) Box(Modifier.align(Alignment.BottomCenter)) { inGap() }
        }
    }
}

/** The arc itself: a faint track, the filled part (with a soft glow under it) and the knob. */
private fun DrawScope.drawFastingArc(
    c: Offset, r: Float, p: Float, accent: Color, knobPulse: Float, stroke: Float, knob: Float, halo: Float
) {
    val tl = Offset(c.x - r, c.y - r)
    val arcSize = Size(r * 2, r * 2)
    drawArc(Color.White.copy(alpha = 0.08f), ARC_START_DEG, ARC_SWEEP_DEG, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    val sweep = ARC_SWEEP_DEG * p.coerceIn(0f, 1f)
    if (sweep > 0.5f) {
        drawArc(accent.copy(alpha = 0.18f), ARC_START_DEG, sweep, false, tl, arcSize, style = Stroke(stroke * 3f, cap = StrokeCap.Round))
        drawArc(accent, ARC_START_DEG, sweep, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    val a = Math.toRadians((ARC_START_DEG + sweep).toDouble())
    val k = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f * knobPulse), Color.Transparent), k, halo), halo, k)
    drawCircle(accent, knob, k)
    drawCircle(Cream, knob * 0.38f, k)
}

// ======================== INTENSE: the Home reminder ========================

/**
 * [com.fitpal.app.domain.model.FastingIntensity.INTENSE]'s reminder on Home: a card that drops in from
 * the top like a notification each time Home is opened during a fast. It leaves on its own after a
 * few seconds; tap it or flick it up to dismiss sooner.
 */
@Composable
fun FastingBanner(schedule: FastingSchedule, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberFastingState(schedule)
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        delay(6_500L)
        dismiss()
    }
    var drag by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, drag.roundToInt()) }
            // Fully opaque: it lands on top of the search bar and header, and their text showing
            // through the glass would muddle the reminder.
            .background(OverlaySurface.copy(alpha = 1f), shape)
            .glassOverlay(shape)
            .border(1.dp, AccentTrends.copy(alpha = 0.35f), shape)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (drag < -40.dp.toPx()) dismiss() else drag = 0f },
                    onDragCancel = { drag = 0f },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        drag = (drag + dy).coerceAtMost(0f)
                    }
                )
            }
            .clickable { dismiss() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val sw = 3.dp.toPx()
                val tl = Offset(sw / 2, sw / 2)
                val arc = Size(size.width - sw, size.height - sw)
                drawArc(Color.White.copy(alpha = 0.10f), -90f, 360f, false, tl, arc, style = Stroke(sw))
                drawArc(AccentTrends, -90f, 360f * state.progressFraction.coerceIn(0f, 1f), false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = AccentTrends, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("You're fasting", style = MaterialTheme.typography.titleSmall, color = Cream, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (state.isFasting)
                    "${fastingCountdownLabel(state.minutesLeftInPhase)} to go — you can eat from ${fastingClockLabel(state.nextChangeMin)}"
                else
                    "Your eating window is open",
                style = MaterialTheme.typography.bodySmall,
                color = CreamMuted,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = CreamFaint, modifier = Modifier.size(18.dp))
    }
}

// ======================== Settings: style thumbnails ========================

/** A tiny drawing of each [FastingStyle] for its Settings tile: a ring with the bar above, or wrapped. */
@Composable
fun FastingStyleThumbnail(style: FastingStyle, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 76.dp, height = 64.dp)) {
        val track = Color.White.copy(alpha = 0.12f)
        val ringStroke = 5.dp.toPx()
        when (style) {
            FastingStyle.BAR -> {
                val barY = 5.dp.toPx()
                val barW = size.width * 0.82f
                val x0 = (size.width - barW) / 2f
                drawLine(track, Offset(x0, barY), Offset(x0 + barW, barY), 3.dp.toPx(), StrokeCap.Round)
                drawLine(AccentTrends, Offset(x0, barY), Offset(x0 + barW * 0.62f, barY), 3.dp.toPx(), StrokeCap.Round)
                val d = size.height * 0.68f
                val c = Offset(size.width / 2f, barY + 9.dp.toPx() + d / 2f)
                drawThumbRing(c, d, ringStroke, track)
            }
            FastingStyle.RING -> {
                val d = size.height * 0.56f
                val c = Offset(size.width / 2f, size.height * 0.5f)
                drawThumbRing(c, d, ringStroke, track)
                drawFastingArc(
                    c, d / 2f + 7.dp.toPx(), 0.62f, AccentTrends, knobPulse = 1f,
                    stroke = 2.5.dp.toPx(), knob = 3.5.dp.toPx(), halo = 6.dp.toPx()
                )
            }
        }
    }
}

private fun DrawScope.drawThumbRing(c: Offset, d: Float, stroke: Float, track: Color) {
    val tl = Offset(c.x - d / 2 + stroke / 2, c.y - d / 2 + stroke / 2)
    val s = Size(d - stroke, d - stroke)
    drawArc(track, -90f, 360f, false, tl, s, style = Stroke(stroke))
    drawArc(CalorieColor, -90f, 250f, false, tl, s, style = Stroke(stroke, cap = StrokeCap.Round))
}
