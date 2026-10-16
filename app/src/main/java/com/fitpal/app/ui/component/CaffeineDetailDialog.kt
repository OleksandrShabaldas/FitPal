package com.fitpal.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.fitpal.app.domain.CaffeineDose
import com.fitpal.app.domain.SleepVerdict
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.glassSoft
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
    // The sleep calculator's "what if" drink, drawn onto the curve while it's being tried out.
    var whatIf by remember { mutableStateOf<CaffeineDose?>(null) }
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
                CaffeineCurve(view, nowMillis, whatIf)
                Spacer(Modifier.height(18.dp))
                SleepCalculator(view, nowMillis, onChange = { whatIf = it })
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
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(com.fitpal.app.ui.theme.filmColor(0.10f))) {
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
private fun CaffeineCurve(view: CaffeineView, nowMillis: Long, whatIf: CaffeineDose? = null) {
    val hour = 3_600_000L
    val zone = ZoneId.systemDefault()
    val morning = LocalDate.now().atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
    val firstToday = view.dayDoses.minOfOrNull { it.atMillis }
    val start = minOf(firstToday?.minus(hour) ?: morning, nowMillis - 6 * hour).coerceAtLeast(nowMillis - 20 * hour)
    val clear = Caffeine.clearTime(view.doses, nowMillis, view.settings.halfLifeHours, CaffeineSettings.SLEEP_FRIENDLY_MG)
    val whatIfClear = whatIf?.let {
        Caffeine.clearTime(view.doses + it, maxOf(nowMillis, it.atMillis), view.settings.halfLifeHours, CaffeineSettings.SLEEP_FRIENDLY_MG)
    }
    val end = maxOf(nowMillis + 6 * hour, (clear ?: 0L) + hour, (whatIfClear ?: 0L) + hour).coerceAtMost(nowMillis + 24 * hour)
    val points = Caffeine.curve(view.doses, start, end, 96, view.settings.halfLifeHours)
    if (points.isEmpty()) return
    // The calculator's drink on top of what's really there — a second, dashed line.
    val whatIfPoints = whatIf?.let { Caffeine.curve(view.doses + it, start, end, 96, view.settings.halfLifeHours) }
    // The scale follows the day's real peak (with a little headroom), so a small amount is still a
    // visible curve. It never drops below 60 mg, which keeps the 50 mg line on the chart.
    val peak = (maxOf(points.maxOrNull() ?: 0f, whatIfPoints?.maxOrNull() ?: 0f) * 1.15f)
        .coerceAtLeast(60f)
    val nowFrac = ((nowMillis - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    val sleepFrac = CaffeineSettings.SLEEP_FRIENDLY_MG / peak
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = CreamFaint)
    val sleepStyle = MaterialTheme.typography.labelSmall.copy(color = ScoreFair)
    // A round number near the top for the one gridline that gives the chart its scale.
    val topMg = ((peak * 0.92f / 10f).toInt() * 10).coerceAtLeast(60)

    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = w * i / (points.size - 1)
            fun y(v: Float) = h - (v / peak).coerceIn(0f, 1f) * h * 0.92f

            // The scale: one faint gridline with its amount.
            val yTop = y(topMg.toFloat())
            drawLine(Cream.copy(alpha = 0.08f), Offset(0f, yTop), Offset(w, yTop), strokeWidth = 1.dp.toPx())
            drawText(textMeasurer, "$topMg mg", topLeft = Offset(0f, yTop + 2.dp.toPx()), style = axisStyle)

            // Sleep-friendly line, labelled on the chart itself.
            val ySleep = h - sleepFrac.coerceIn(0f, 1f) * h * 0.92f
            drawLine(
                color = ScoreFair.copy(alpha = 0.55f),
                start = Offset(0f, ySleep), end = Offset(w, ySleep),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
            val sleepLabel = textMeasurer.measure("${CaffeineSettings.SLEEP_FRIENDLY_MG} mg · easy on sleep below", sleepStyle)
            drawText(sleepLabel, topLeft = Offset(w - sleepLabel.size.width, ySleep - sleepLabel.size.height - 2.dp.toPx()))

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
            if (whatIfPoints != null) {
                val firstDiff = whatIfPoints.indices.firstOrNull { whatIfPoints[it] - points[it] > 0.5f } ?: 0
                val wi = Path().apply {
                    moveTo(x(firstDiff), y(whatIfPoints[firstDiff]))
                    for (i in firstDiff + 1 until whatIfPoints.size) lineTo(x(i), y(whatIfPoints[i]))
                }
                drawPath(
                    wi, GoldLight.copy(alpha = 0.8f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                )
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
        TimeAxis(startLabel = timeLabel(start), endLabel = timeLabel(end), nowFrac = nowFrac)
        if (whatIf != null) {
            Text("Gold dashes: with the drink below.", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
        }
    }
}

/**
 * The time labels under the curve: start and end at the edges, and "now" directly under the now
 * marker ([nowFrac] of the width) — pushed aside only as far as it takes not to overlap an edge label.
 */
@Composable
private fun TimeAxis(startLabel: String, endLabel: String, nowFrac: Float) {
    androidx.compose.ui.layout.Layout(
        content = {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = CreamFaint)
            Text("now", style = MaterialTheme.typography.labelSmall, color = Cream)
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = CreamFaint)
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0)
        val (startP, nowP, endP) = measurables.map { it.measure(loose) }
        val w = constraints.maxWidth
        val gap = 4.dp.roundToPx()
        val nowX = (w * nowFrac - nowP.width / 2f).toInt()
            .coerceIn(startP.width + gap, (w - endP.width - gap - nowP.width).coerceAtLeast(startP.width + gap))
        val height = maxOf(startP.height, nowP.height, endP.height)
        layout(w, height) {
            startP.place(0, 0)
            nowP.place(nowX, 0)
            endP.place(w - endP.width, 0)
        }
    }
}

/**
 * "Will it keep me up?" — pick a typical drink (or type the mg) and when you'd have it (now by
 * default), and see when you're back under the sleep-friendly line: with what's already in your
 * body, and for that drink on its own. Nothing is logged; it only reports [onChange] so the curve
 * above can show the drink.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepCalculator(view: CaffeineView, nowMillis: Long, onChange: (CaffeineDose?) -> Unit) {
    val context = LocalContext.current
    var presetIndex by remember { mutableIntStateOf(-1) }
    var mgText by remember { mutableStateOf("") }
    // Minute of day the drink is had; null = now.
    var atMinute by remember { mutableStateOf<Int?>(null) }

    val mg = mgText.toFloatOrNull()?.coerceIn(0f, 2000f) ?: 0f
    val atMillis = atMinute?.let {
        LocalDate.now().atTime(it / 60, it % 60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } ?: nowMillis
    val label = Caffeine.PRESETS.getOrNull(presetIndex)?.label ?: "This drink"
    val dose = if (mg >= 1f) CaffeineDose(atMillis, mg, label, estimated = false) else null
    androidx.compose.runtime.LaunchedEffect(dose) { onChange(dose) }

    Column(Modifier.fillMaxWidth().glassSoft().padding(14.dp)) {
        Text("Will it keep me up?", style = MaterialTheme.typography.titleMedium, color = Cream)
        Text(
            "Pick a drink or type the caffeine, and when you'd have it.",
            style = MaterialTheme.typography.bodySmall, color = CreamMuted
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Caffeine.PRESETS.forEachIndexed { i, p ->
                val selected = i == presetIndex
                Text(
                    "${p.label} · ${p.mg}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Cream else CreamMuted,
                    modifier = Modifier
                        .then(if (selected) Modifier.accentGlass(CaffeineColor, CircleShape) else Modifier.glassSoft(CircleShape))
                        .clickable { presetIndex = i; mgText = p.mg.toString() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Caffeine.PRESETS.getOrNull(presetIndex)?.let {
            Spacer(Modifier.height(4.dp))
            Text("Typical ${it.serving}", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundStep(Icons.Default.Remove, "Less") {
                mgText = (mg - 10f).coerceAtLeast(0f).roundToInt().toString(); presetIndex = -1
            }
            BasicTextField(
                value = mgText,
                onValueChange = { v -> mgText = v.filter { it.isDigit() }.take(4); presetIndex = -1 },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                    fontFeatureSettings = "tnum", color = Cream, textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(GoldLight),
                modifier = Modifier.width(72.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (mgText.isEmpty()) Text("0", style = MaterialTheme.typography.titleLarge, color = CreamFaint)
                        inner()
                    }
                }
            )
            RoundStep(Icons.Default.Add, "More") {
                mgText = (mg + 10f).coerceAtMost(2000f).roundToInt().toString(); presetIndex = -1
            }
            Text("mg", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .glassSoft(CircleShape)
                    .clickable {
                        val m = atMinute ?: LocalDateTime.now().let { it.hour * 60 + it.minute }
                        android.app.TimePickerDialog(context, { _, h, min -> atMinute = h * 60 + min }, m / 60, m % 60, false).show()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = CreamMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (atMinute == null) "Now" else "At ${fastingClockLabel(atMinute!!)}",
                    style = MaterialTheme.typography.labelMedium, color = Cream
                )
            }
        }
        if (atMinute != null) {
            Text(
                "Back to now",
                style = MaterialTheme.typography.labelSmall, color = GoldLight,
                modifier = Modifier.align(Alignment.End).clickable { atMinute = null }.padding(top = 4.dp)
            )
        }

        if (dose != null) {
            val s = view.settings
            val threshold = CaffeineSettings.SLEEP_FRIENDLY_MG
            val withYours = Caffeine.sleepVerdict(view.doses, dose, nowMillis, s.halfLifeHours, threshold)
            val alone = Caffeine.sleepVerdict(emptyList(), dose, nowMillis, s.halfLifeHours, threshold)
            Spacer(Modifier.height(12.dp))
            VerdictLine("With what's already in you", withYours, emphasis = true)
            if (view.doses.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                VerdictLine("That drink on its own", alone, emphasis = false)
            }
        }
    }
}

@Composable
private fun VerdictLine(title: String, verdict: SleepVerdict, emphasis: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Bedtime, contentDescription = null,
            tint = if (verdict is SleepVerdict.NotWithin36h) MacroOver else ScoreFair,
            modifier = Modifier.padding(top = 2.dp).size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = CreamMuted)
            Text(
                when (verdict) {
                    is SleepVerdict.ClearAt -> "Sleep-friendly from ${dayAwareTimeLabel(verdict.atMillis)}"
                    SleepVerdict.NeverAffects -> "Stays under ${CaffeineSettings.SLEEP_FRIENDLY_MG} mg — no problem for sleep"
                    SleepVerdict.NotWithin36h -> "Still over ${CaffeineSettings.SLEEP_FRIENDLY_MG} mg a day and a half later"
                },
                style = if (emphasis) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = Cream
            )
        }
    }
}

@Composable
private fun RoundStep(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(34.dp).glassSoft(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Cream, modifier = Modifier.size(18.dp))
    }
}

/** "11:20 PM", or "tomorrow 1:10 AM" when it's past midnight. */
private fun dayAwareTimeLabel(epochMillis: Long): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), t.toLocalDate())
    val prefix = when {
        days == 1L -> "tomorrow "
        days > 1L -> "${t.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} "
        else -> ""
    }
    return prefix + timeLabel(epochMillis)
}

private fun timeLabel(epochMillis: Long): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
    return fastingClockLabel(t.hour * 60 + t.minute)
}

private fun fmtHours(h: Float): String = if (h == h.roundToInt().toFloat()) h.roundToInt().toString() else "%.1f".format(h)
