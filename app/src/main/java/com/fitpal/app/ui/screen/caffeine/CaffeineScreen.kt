package com.fitpal.app.ui.screen.caffeine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.Caffeine
import com.fitpal.app.domain.CaffeineDose
import com.fitpal.app.domain.CaffeineSettings
import com.fitpal.app.domain.GoalKind
import com.fitpal.app.domain.StatusKind
import com.fitpal.app.domain.TrackerStatus
import com.fitpal.app.domain.WaterPace
import com.fitpal.app.ui.component.CaffeineView
import com.fitpal.app.ui.component.GlassButton
import com.fitpal.app.ui.component.GlassNumberField
import com.fitpal.app.ui.component.GoldButton
import com.fitpal.app.ui.component.StatusPill
import com.fitpal.app.ui.component.TrackerChart
import com.fitpal.app.ui.component.TrackerHero
import com.fitpal.app.ui.component.TrackerScaffold
import com.fitpal.app.ui.component.TrackerSection
import com.fitpal.app.ui.component.TrackerSheet
import com.fitpal.app.ui.component.WeekBars
import com.fitpal.app.ui.theme.CaffeineColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.FastingColor
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.InterFamily
import com.fitpal.app.ui.theme.MacroOver
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.glassSoft
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HOUR = 3_600_000L

/** The planner's quick picks, by their label in [Caffeine.PRESETS]; the rest sit behind "More…". */
private val QUICK_PRESETS = listOf("Espresso", "Coffee", "Latte / cappuccino", "Black tea", "Cola", "Red Bull")

private fun infoText(halfLife: Float) =
    "Caffeine is absorbed over about an hour after a drink, then your body clears half of it every " +
        "${fmtHours(halfLife)} hours (your half-life — people differ a lot; set yours in Settings → Caffeine).\n\n" +
        "Below about ${CaffeineSettings.SLEEP_FRIENDLY_MG} mg it rarely gets in the way of sleep, so the verdict " +
        "checks where you'll be at your bedtime (Settings → Profile & goals → Your day).\n\n" +
        "≈ marks a typical amount for that drink, used until a quick check fills in the real one."

/**
 * The Caffeine page (docs/redesign/04-caffeine-page.md): what's in you now, judged against your
 * bedtime; the curve through the day with each drink on it; today's drinks against the limit; and
 * a folded "planning another one?".
 */
@Composable
fun CaffeineScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenEntry: (Long) -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    viewModel: CaffeineViewModel = hiltViewModel()
) {
    val day by viewModel.day.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val bedMin by viewModel.bedMinutes.collectAsStateWithLifecycle()
    val chartView by viewModel.chartView.collectAsStateWithLifecycle()
    val isToday = viewModel.isToday

    // A once-a-minute clock, so "in you now" keeps draining while the page is open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isToday) {
        while (isToday) {
            delay(60_000L)
            now = System.currentTimeMillis()
        }
    }
    var whatIf by remember { mutableStateOf<CaffeineDose?>(null) }
    var selectedDose by remember { mutableStateOf<CaffeineDose?>(null) }

    TrackerScaffold(
        title = "Caffeine",
        onBack = onBack,
        infoTitle = "How caffeine works",
        infoText = infoText(settings.halfLifeHours),
        onOpenSettings = onOpenSettings
    ) {
        val d = day
        if (d == null) {
            TrackerSection(title = "The caffeine tracker is off") {
                Text("Turn it on to see what's in you now and whether it'll get in the way of sleep.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                Spacer(Modifier.height(12.dp))
                GoldButton("Open caffeine settings", onClick = onOpenSettings)
            }
            return@TrackerScaffold
        }
        val view = d.view
        val dayStart = viewModel.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Judge the day against its bedtime: the next one for today, that evening's for a past day.
        val bedMillis = if (isToday) Caffeine.nextBedtime(now, bedMin) else Caffeine.nextBedtime(dayStart + 12 * HOUR, bedMin)

        // ---- Hero: the amount now and the verdict for tonight ----
        TrackerHero(visual = null) {
            if (isToday) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        view.amountAt(now).roundToInt().toString(),
                        style = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, fontFeatureSettings = "tnum"),
                        color = Cream
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("mg in you now", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.padding(bottom = 8.dp))
                }
                StatusPill(Caffeine.bedtimeStatus(view.doses, now, bedMillis, settings.halfLifeHours))
            } else {
                Text(viewModel.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), style = MaterialTheme.typography.labelLarge, color = CreamMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        view.dayTotalMg.roundToInt().toString(),
                        style = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, fontFeatureSettings = "tnum"),
                        color = Cream
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("mg that day", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (view.overLimit) StatusPill(TrackerStatus(StatusKind.OVER, "Over your ${settings.dailyLimitMg} mg limit"))
            }
        }

        // ---- One chart: the day's curve ⇄ the week ----
        val todayIndex = if (isToday) (LocalDate.now().toEpochDay() - viewModel.weekStart.toEpochDay()).toInt().takeIf { it in 0..6 } else null
        val days = week.filterNotNull()
        TrackerChart(
            todayTitle = if (isToday) "Today" else "That day",
            weekTitle = "This week",
            view = chartView,
            onView = viewModel::setChartView,
            accent = CaffeineColor,
            takeaway = if (chartView == 0) {
                selectedDose?.let { "${it.name} · ${if (it.estimated) "≈" else ""}${it.mg.roundToInt()} mg · ${clock(it.atMillis)}" }
                    ?: if (view.dayDoses.isNotEmpty()) "Tap a dot to see the drink" else null
            } else if (days.isNotEmpty()) {
                val over = if (settings.dailyLimitMg > 0) days.count { it > settings.dailyLimitMg } else 0
                "Avg ${days.average().roundToInt()} mg a day" + (if (over > 0) " · over the limit on $over day${if (over == 1) "" else "s"}" else "")
            } else null,
            today = {
                CaffeineCurve(
                    view = view, dayStart = dayStart, nowMillis = if (isToday) now else null,
                    bedMillis = bedMillis, whatIf = whatIf, selected = selectedDose,
                    onSelect = { selectedDose = if (selectedDose == it) null else it }
                )
            },
            week = {
                WeekBars(
                    values = week,
                    goal = settings.dailyLimitMg.toFloat().takeIf { settings.dailyLimitMg > 0 },
                    goalKind = GoalKind.LIMIT,
                    color = CaffeineColor,
                    todayIndex = todayIndex,
                    goalLabel = if (settings.dailyLimitMg > 0) "limit ${settings.dailyLimitMg}" else null,
                    onDay = { i ->
                        val dd = viewModel.weekStart.plusDays(i.toLong())
                        if (!dd.isAfter(LocalDate.now()) && dd != viewModel.date) onOpenDay(dd)
                    }
                )
            }
        )

        // ---- The day's drinks against the limit ----
        TrackerSection(
            title = if (isToday) "Today" else "That day",
            action = {
                Text(
                    "${view.dayTotalMg.roundToInt()} of ${settings.dailyLimitMg} mg",
                    style = MaterialTheme.typography.labelLarge, color = if (view.overLimit) MacroOver else Cream
                )
            }
        ) {
            val limit = settings.dailyLimitMg.coerceAtLeast(1)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(filmColor(0.10f))) {
                Box(
                    Modifier.fillMaxWidth((view.dayTotalMg / limit).coerceIn(0f, 1f)).height(6.dp)
                        .clip(RoundedCornerShape(50)).background(if (view.overLimit) MacroOver else CaffeineColor)
                )
            }
            Spacer(Modifier.height(8.dp))
            if (d.items.isEmpty()) {
                Text("Nothing with caffeine logged.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
            } else {
                d.items.sortedByDescending { it.second.atMillis }.forEach { (itemId, dose) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpenEntry(itemId) }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(clock(dose.atMillis), style = MaterialTheme.typography.labelMedium, color = CreamFaint, modifier = Modifier.width(72.dp))
                        Text(dose.name, style = MaterialTheme.typography.bodyMedium, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text((if (dose.estimated) "≈" else "") + "${dose.mg.roundToInt()} mg", style = MaterialTheme.typography.labelLarge, color = CaffeineColor)
                    }
                }
            }
        }

        // ---- Planning another one? (today only, folded) ----
        if (isToday) {
            Planner(view = view, nowMillis = now, bedMinute = bedMin, onChange = { whatIf = it })
        }
    }
}

/**
 * The body curve from the morning to the next morning: the band where caffeine is easy on sleep,
 * the bedtime line, each drink as a dot where it was had (tap one for its name), "now", and a gold
 * dashed line for a drink being planned.
 */
@Composable
private fun CaffeineCurve(
    view: CaffeineView,
    dayStart: Long,
    nowMillis: Long?,
    bedMillis: Long,
    whatIf: CaffeineDose?,
    selected: CaffeineDose?,
    onSelect: (CaffeineDose) -> Unit
) {
    val hl = view.settings.halfLifeHours
    val firstDose = view.dayDoses.minOfOrNull { it.atMillis }
    var start = minOf(dayStart + 6 * HOUR, (firstDose ?: Long.MAX_VALUE) - HOUR)
    if (nowMillis != null) start = minOf(start, nowMillis - 2 * HOUR)
    val end = dayStart + 30 * HOUR // the next morning, 6 AM
    val all = view.doses + listOfNotNull(whatIf)
    val points = Caffeine.curve(view.doses, start, end, 121, hl)
    val whatIfPoints = whatIf?.let { Caffeine.curve(all, start, end, 121, hl) }
    if (points.isEmpty()) return
    val peak = maxOf(points.maxOrNull() ?: 0f, whatIfPoints?.maxOrNull() ?: 0f)
    val top = (peak * 1.15f).coerceAtLeast(60f)
    val line = CaffeineSettings.SLEEP_FRIENDLY_MG.toFloat()
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.typography.labelSmall.copy(color = CreamFaint)
    val bandStyle = MaterialTheme.typography.labelSmall.copy(color = ScoreFair)
    val bedStyle = MaterialTheme.typography.labelSmall.copy(color = FastingColor)
    val nowStyle = MaterialTheme.typography.labelSmall.copy(color = Cream, fontWeight = FontWeight.Bold)
    val visibleDoses = view.doses.filter { it.atMillis in start..end }
    val dotAt: (CaffeineDose) -> Long = { it.atMillis + 36 * 60_000L } // where absorption makes it visible
    val color = CaffeineColor
    val fillTop = CaffeineColor.copy(alpha = 0.30f)
    val grid = Cream.copy(alpha = 0.08f)
    Canvas(
        Modifier.fillMaxSize().pointerInput(visibleDoses, start, end) {
            detectTapGestures { p ->
                val left = 24.dp.toPx()
                val w = size.width - left
                val hit = visibleDoses.minByOrNull { abs(left + w * (dotAt(it) - start).toFloat() / (end - start) - p.x) }
                if (hit != null && abs(left + w * (dotAt(hit) - start).toFloat() / (end - start) - p.x) < 28.dp.toPx()) onSelect(hit)
            }
        }
    ) {
        val left = 24.dp.toPx()
        val topPad = 14.dp.toPx()
        val labelH = 16.dp.toPx()
        val w = size.width - left
        val h = size.height - topPad - labelH
        fun x(t: Long) = left + w * (t - start).toFloat() / (end - start)
        fun xi(i: Int) = left + w * i / (points.size - 1)
        fun y(v: Float) = topPad + h - (v / top).coerceIn(0f, 1f) * h
        val nowIdx = nowMillis?.let { (((it - start).toFloat() / (end - start)) * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1) }

        // The band where caffeine is easy on sleep, labelled inside it.
        drawRect(ScoreFair.copy(alpha = 0.07f), Offset(left, y(line)), androidx.compose.ui.geometry.Size(w, y(0f) - y(line)))
        drawLine(ScoreFair.copy(alpha = 0.55f), Offset(left, y(line)), Offset(size.width, y(line)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        val band = measurer.measure("easy on sleep", bandStyle)
        drawText(band, topLeft = Offset(size.width - band.size.width - 4.dp.toPx(), y(0f) - band.size.height - 2.dp.toPx()))
        // The scale: 0, 50 and one round gridline near the top.
        val topMg = ((top * 0.9f / 10f).toInt() * 10).coerceAtLeast(60)
        drawLine(grid, Offset(left, y(topMg.toFloat())), Offset(size.width, y(topMg.toFloat())), 1.dp.toPx())
        listOf(0 to y(0f), line.toInt() to y(line), topMg to y(topMg.toFloat())).forEach { (mg, yy) ->
            val l = measurer.measure(mg.toString(), axis)
            drawText(l, topLeft = Offset(left - l.size.width - 4.dp.toPx(), yy - l.size.height / 2f))
        }

        // Bedtime.
        if (bedMillis in start..end) {
            val bx = x(bedMillis)
            drawLine(FastingColor.copy(alpha = 0.8f), Offset(bx, topPad), Offset(bx, y(0f)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f)))
            val b = measurer.measure("bed ${clock(bedMillis)}", bedStyle)
            drawText(b, topLeft = Offset((bx + 3.dp.toPx()).coerceAtMost(size.width - b.size.width), 0f))
        }

        // The curve: what's happened solid, the projection dashed.
        val pastEnd = nowIdx ?: (points.size - 1)
        val past = Path().apply { moveTo(xi(0), y(points[0])); for (i in 1..pastEnd) lineTo(xi(i), y(points[i])) }
        val area = Path().apply { addPath(past); lineTo(xi(pastEnd), y(0f)); lineTo(xi(0), y(0f)); close() }
        drawPath(area, Brush.verticalGradient(listOf(fillTop, Color.Transparent), startY = topPad, endY = y(0f)))
        drawPath(past, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        if (nowIdx != null && nowIdx < points.size - 1) {
            val future = Path().apply { moveTo(xi(nowIdx), y(points[nowIdx])); for (i in nowIdx + 1 until points.size) lineTo(xi(i), y(points[i])) }
            drawPath(future, color.copy(alpha = 0.55f), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))))
        }
        // A drink being planned, on top of what's really there.
        if (whatIfPoints != null) {
            val first = whatIfPoints.indices.firstOrNull { whatIfPoints[it] - points[it] > 0.5f } ?: 0
            val wi = Path().apply { moveTo(xi(first), y(whatIfPoints[first])); for (i in first + 1 until whatIfPoints.size) lineTo(xi(i), y(whatIfPoints[i])) }
            drawPath(wi, GoldLight.copy(alpha = 0.85f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        }

        // Each drink, where it was had.
        visibleDoses.forEach { dose ->
            val t = dotAt(dose).coerceIn(start, end)
            val c = Offset(x(t), y(Caffeine.amountAt(view.doses, t, hl)))
            drawCircle(com.fitpal.app.ui.theme.InkBlack, 4.dp.toPx(), c)
            drawCircle(color, 4.dp.toPx(), c, style = Stroke(1.6.dp.toPx()))
            if (dose == selected) drawCircle(color, 2.6.dp.toPx(), c)
        }

        // Now.
        if (nowMillis != null && nowIdx != null) {
            val nx = x(nowMillis)
            val ny = y(points[nowIdx])
            drawLine(Cream.copy(alpha = 0.25f), Offset(nx, topPad), Offset(nx, y(0f)), 1.dp.toPx())
            drawCircle(color.copy(alpha = 0.3f), 7.dp.toPx(), Offset(nx, ny))
            drawCircle(Cream, 3.dp.toPx(), Offset(nx, ny))
            val n = measurer.measure("now", nowStyle)
            drawText(n, topLeft = Offset((nx - n.size.width / 2f).coerceIn(left, size.width - n.size.width), (ny - n.size.height - 9.dp.toPx()).coerceAtLeast(0f)))
        }

        // Time labels at their true positions: 6 AM, noon, 6 PM, midnight, 6 AM.
        val labelY = size.height - labelH + 2.dp.toPx()
        var lastRight = -1f
        listOf(6L to "6 AM", 12L to "noon", 18L to "6 PM", 24L to "midnight", 30L to "6 AM").forEach { (hours, text) ->
            val t = dayStart + hours * HOUR
            if (t < start || t > end) return@forEach
            val l = measurer.measure(text, axis)
            val lx = (x(t) - l.size.width / 2f).coerceIn(left, size.width - l.size.width)
            if (lx > lastRight + 6.dp.toPx()) {
                drawText(l, topLeft = Offset(lx, labelY))
                lastRight = lx + l.size.width
            }
        }
    }
}

/**
 * "Planning another one?" — folded until opened. A few common drinks (the rest behind "More…") and
 * a time; the answer is one sentence, and the drink is drawn on the curve above. Nothing is logged.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Planner(view: CaffeineView, nowMillis: Long, bedMinute: Int, onChange: (CaffeineDose?) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf<String?>(null) }
    var mg by remember { mutableIntStateOf(0) }
    var atMinute by remember { mutableStateOf<Int?>(null) }
    var showMore by remember { mutableStateOf(false) }
    val atMillis = atMinute?.let {
        LocalDate.now().atTime(it / 60, it % 60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } ?: nowMillis
    val dose = if (open && mg >= 1) CaffeineDose(atMillis, mg.toFloat(), label ?: "This drink", estimated = false) else null
    LaunchedEffect(dose) { onChange(dose) }

    TrackerSection(
        title = "Planning another one?",
        action = {
            Icon(
                if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (open) "Fold" else "Open the planner",
                tint = CreamMuted,
                modifier = Modifier.size(28.dp).clip(CircleShape).clickable { open = !open }
            )
        }
    ) {
        if (!open) {
            Text(
                "See what a coffee now would do to tonight",
                style = MaterialTheme.typography.bodySmall, color = CreamFaint,
                modifier = Modifier.clickable { open = true }
            )
        }
        AnimatedVisibility(visible = open) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QUICK_PRESETS.mapNotNull { name -> Caffeine.PRESETS.firstOrNull { it.label == name } }.forEach { p ->
                        PresetPill("${p.label} · ${p.mg}", selected = label == p.label) { label = p.label; mg = p.mg }
                    }
                    PresetPill("More…", selected = label != null && label !in QUICK_PRESETS) { showMore = true }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.glassSoft(CircleShape).clickable {
                            val m = atMinute ?: LocalDateTime.now().let { it.hour * 60 + it.minute }
                            android.app.TimePickerDialog(context, { _, h, min -> atMinute = h * 60 + min }, m / 60, m % 60, false).show()
                        }.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = CreamMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (atMinute == null) "Now" else "At ${WaterPace.clockLabel(atMinute!!)}", style = MaterialTheme.typography.labelLarge, color = Cream)
                    }
                    if (atMinute != null) {
                        Spacer(Modifier.width(10.dp))
                        Text("Back to now", style = MaterialTheme.typography.labelMedium, color = GoldLight, modifier = Modifier.clickable { atMinute = null })
                    }
                }
                if (dose != null) {
                    val what = "${articleFor(label)} ${if (atMinute == null) "now" else "at ${WaterPace.clockLabel(atMinute!!)}"}"
                    Text(
                        Caffeine.planSentence(view.doses, dose, what, nowMillis, bedMinute, view.settings.halfLifeHours),
                        style = MaterialTheme.typography.bodyLarge, color = Cream
                    )
                } else {
                    Text("Pick a drink to see the answer.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
                }
            }
        }
    }

    if (showMore) {
        MoreDrinksSheet(
            initialMg = mg.takeIf { it > 0 } ?: 95,
            onPick = { name, value -> label = name; mg = value; showMore = false },
            onDismiss = { showMore = false }
        )
    }
}

@Composable
private fun PresetPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Cream else CreamMuted,
        maxLines = 1,
        modifier = Modifier
            .then(if (selected) Modifier.accentGlass(CaffeineColor, CircleShape) else Modifier.glassSoft(CircleShape))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/** The rest of the typical drinks, and any amount of your own. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoreDrinksSheet(initialMg: Int, onPick: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialMg.toString()) }
    TrackerSheet(title = "More drinks", onDismiss = onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Caffeine.PRESETS.filter { it.label !in QUICK_PRESETS }.forEach { p ->
                PresetPill("${p.label} · ${p.mg}", selected = false) { onPick(p.label, p.mg) }
            }
        }
        Text("Or an amount of your own", style = MaterialTheme.typography.titleSmall, color = Cream)
        GlassNumberField(
            text = text, onText = { text = it },
            step = 10f, min = 0f, max = 1000f, fallback = initialMg.toFloat(), maxChars = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Text("mg", style = MaterialTheme.typography.titleMedium, color = CreamMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            GoldButton("Use this", onClick = { text.toIntOrNull()?.takeIf { it > 0 }?.let { onPick("This drink", it) } }, enabled = (text.toIntOrNull() ?: 0) > 0, modifier = Modifier.weight(1f))
        }
    }
}

/** "A coffee", "An espresso", "This drink". */
private fun articleFor(label: String?): String {
    if (label == null || label == "This drink") return "This drink"
    val name = label.substringBefore(" /").lowercase().let { if (it == "red bull") "Red Bull" else it }
    val vowel = name.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')
    return (if (vowel) "An " else "A ") + name
}

private fun clock(epochMillis: Long): String {
    val t = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return WaterPace.clockLabel(t.hour * 60 + t.minute)
}

private fun fmtHours(h: Float): String = if (h == h.roundToInt().toFloat()) h.roundToInt().toString() else "%.1f".format(h)
