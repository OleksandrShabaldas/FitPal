package com.fitpal.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fitpal.app.domain.Caffeine
import com.fitpal.app.domain.CaffeineSettings
import com.fitpal.app.ui.theme.CaffeineColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.InterFamily
import com.fitpal.app.ui.theme.MacroOver
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.glassOverlay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The full caffeine picture, opened from the caffeine ring: what's in your body now, when it'll be
 * low enough not to get in the way of sleep, today's total against your limit, the rise-and-fall
 * curve, and what it came from. All worked out on the phone from what you logged.
 */
@Composable
fun CaffeineDetailDialog(view: CaffeineView, nowMillis: Long, onDismiss: () -> Unit) {
    val s = view.settings
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .glassOverlay()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalCafe, contentDescription = null, tint = CaffeineColor, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Caffeine", style = MaterialTheme.typography.headlineMedium, color = Cream)
            }
            Text(
                if (view.isToday) "An estimate from what you've logged" else "What you logged that day",
                style = MaterialTheme.typography.bodySmall, color = CreamMuted
            )
            Spacer(Modifier.height(16.dp))

            if (view.isToday) {
                val now = view.amountAt(nowMillis)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        now.roundToInt().toString(),
                        style = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, fontFeatureSettings = "tnum"),
                        color = Cream
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("mg in your body now", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.padding(bottom = 8.dp))
                }
                val clear = Caffeine.clearTime(view.doses, nowMillis, s.halfLifeHours, CaffeineSettings.SLEEP_FRIENDLY_MG)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bedtime, contentDescription = null, tint = ScoreFair, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            clear != null -> "Below ${CaffeineSettings.SLEEP_FRIENDLY_MG} mg around ${timeLabel(clear)} — easier on sleep from then"
                            now <= CaffeineSettings.SLEEP_FRIENDLY_MG -> "Low enough not to get in the way of sleep"
                            else -> "Won't drop below ${CaffeineSettings.SLEEP_FRIENDLY_MG} mg in the next day and a half"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = Cream
                    )
                }
                Spacer(Modifier.height(18.dp))
                CaffeineCurve(view, nowMillis)
                Spacer(Modifier.height(18.dp))
            }

            // Today's (or that day's) total against the limit.
            val limit = s.dailyLimitMg.coerceAtLeast(1)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (view.isToday) "Today" else "That day", style = MaterialTheme.typography.labelLarge, color = CreamMuted)
                Text(
                    "${view.dayTotalMg.roundToInt()} of ${s.dailyLimitMg} mg",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (view.overLimit) MacroOver else Cream
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f))) {
                Box(
                    Modifier.fillMaxWidth((view.dayTotalMg / limit).coerceIn(0f, 1f)).height(6.dp)
                        .clip(RoundedCornerShape(50)).background(if (view.overLimit) MacroOver else CaffeineColor)
                )
            }

            if (view.dayDoses.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                view.dayDoses.forEach { d ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(timeLabel(d.atMillis), style = MaterialTheme.typography.labelMedium, color = CreamFaint, modifier = Modifier.width(72.dp))
                        Text(
                            d.name, style = MaterialTheme.typography.bodyMedium, color = Cream,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        Text(
                            (if (d.estimated) "≈" else "") + "${d.mg.roundToInt()} mg",
                            style = MaterialTheme.typography.labelLarge, color = CaffeineColor
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text("Nothing with caffeine logged.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                buildString {
                    append("Worked out with a ${fmtHours(s.halfLifeHours)}-hour half-life — how long your body takes to clear half of it. ")
                    append("People differ a lot; set yours in Settings → Caffeine.")
                    if (view.dayDoses.any { it.estimated }) {
                        append(" ≈ marks a typical amount, used until a quick check fills in the real one.")
                    }
                },
                style = MaterialTheme.typography.labelSmall, color = CreamFaint
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
        }
    }
}

/**
 * The rise-and-fall line: from the morning (or the first drink) to several hours ahead, with the
 * sleep-friendly line dashed across and "now" marked. Hand-drawn on a Canvas like the app's other charts.
 */
@Composable
private fun CaffeineCurve(view: CaffeineView, nowMillis: Long) {
    val hour = 3_600_000L
    val zone = ZoneId.systemDefault()
    val morning = LocalDate.now().atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
    val firstToday = view.dayDoses.minOfOrNull { it.atMillis }
    val start = minOf(firstToday?.minus(hour) ?: morning, nowMillis - 6 * hour).coerceAtLeast(nowMillis - 20 * hour)
    val clear = Caffeine.clearTime(view.doses, nowMillis, view.settings.halfLifeHours, CaffeineSettings.SLEEP_FRIENDLY_MG)
    val end = maxOf(nowMillis + 6 * hour, (clear ?: 0L) + hour).coerceAtMost(nowMillis + 24 * hour)
    val points = Caffeine.curve(view.doses, start, end, 96, view.settings.halfLifeHours)
    if (points.isEmpty()) return
    val peak = (points.maxOrNull() ?: 0f).coerceAtLeast(CaffeineSettings.SLEEP_FRIENDLY_MG * 2f)
    val nowFrac = ((nowMillis - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    val sleepFrac = CaffeineSettings.SLEEP_FRIENDLY_MG / peak

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = w * i / (points.size - 1)
            fun y(v: Float) = h - (v / peak).coerceIn(0f, 1f) * h * 0.92f

            // Sleep-friendly line.
            val ySleep = h - sleepFrac.coerceIn(0f, 1f) * h * 0.92f
            drawLine(
                color = ScoreFair.copy(alpha = 0.55f),
                start = Offset(0f, ySleep), end = Offset(w, ySleep),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )

            val line = Path().apply {
                moveTo(x(0), y(points[0]))
                for (i in 1 until points.size) lineTo(x(i), y(points[i]))
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(CaffeineColor.copy(alpha = 0.35f), Color.Transparent)))
            // Past solid, the future dashed and fainter — the projection is only a projection.
            val nowX = w * nowFrac
            val nowIdx = (nowFrac * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
            drawLine(Cream.copy(alpha = 0.25f), Offset(nowX, 0f), Offset(nowX, h), strokeWidth = 1.dp.toPx())
            val past = Path().apply {
                moveTo(x(0), y(points[0]))
                for (i in 1..nowIdx) lineTo(x(i), y(points[i]))
            }
            val future = Path().apply {
                moveTo(x(nowIdx), y(points[nowIdx]))
                for (i in nowIdx + 1 until points.size) lineTo(x(i), y(points[i]))
            }
            drawPath(past, CaffeineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            drawPath(
                future, CaffeineColor.copy(alpha = 0.55f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
            )
            val nowY = y(points[nowIdx])
            drawCircle(CaffeineColor.copy(alpha = 0.3f), radius = 8.dp.toPx(), center = Offset(nowX, nowY))
            drawCircle(Cream, radius = 3.5.dp.toPx(), center = Offset(nowX, nowY))
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(timeLabel(start), style = MaterialTheme.typography.labelSmall, color = CreamFaint)
            Text("now", style = MaterialTheme.typography.labelSmall, color = CreamMuted)
            Text(timeLabel(end), style = MaterialTheme.typography.labelSmall, color = CreamFaint)
        }
        Text(
            "Dashed line: ${CaffeineSettings.SLEEP_FRIENDLY_MG} mg, where it stops getting in the way of sleep for most people.",
            style = MaterialTheme.typography.labelSmall, color = CreamFaint
        )
    }
}

private fun timeLabel(epochMillis: Long): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
    return fastingClockLabel(t.hour * 60 + t.minute)
}

private fun fmtHours(h: Float): String = if (h == h.roundToInt().toFloat()) h.roundToInt().toString() else "%.1f".format(h)
