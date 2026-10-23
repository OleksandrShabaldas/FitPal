package com.fitpal.app.ui.screen.water

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.local.dao.WaterTimelineRow
import com.fitpal.app.domain.GoalKind
import com.fitpal.app.domain.WaterPace
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
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.NumberFamily
import com.fitpal.app.ui.theme.MacroOver
import com.fitpal.app.ui.theme.OnGold
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.glassSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Water that came from food: the water colour, lighter — still water, not a different feature. */
private val WaterFromFood: Color get() = AccentTrends.copy(alpha = 0.45f)

private const val INFO_TEXT =
    "Your water counts what you drink and the water in what you eat — soup, fruit, yoghurt and the like.\n\n" +
        "The pace line spreads your goal evenly between when you wake up and when you go to bed. Set " +
        "those in Settings → Profile & goals → Your day (7:00 AM and 11:00 PM until you do).\n\n" +
        "Quick-add buttons log plain water; tap Edit to change them. Swipe a plain-water entry to remove " +
        "it. Drinks and food are edited from their own entry."

/**
 * The Water page (docs/redesign/02-water-page.md), built on the tracker template: the day's ring
 * (drinks and food), a pace pill, quick add, one chart that switches between today and the week,
 * and the day's entries.
 */
@Composable
fun WaterDetailScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenEntry: (Long) -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    viewModel: WaterDetailViewModel = hiltViewModel()
) {
    val total by viewModel.totalWater.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val goal by viewModel.waterGoal.collectAsStateWithLifecycle()
    val goalOverride by viewModel.goalOverride.collectAsStateWithLifecycle()
    val autoGoal by viewModel.autoGoal.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val wake by viewModel.wakeMinutes.collectAsStateWithLifecycle()
    val bed by viewModel.bedMinutes.collectAsStateWithLifecycle()
    val chartView by viewModel.chartView.collectAsStateWithLifecycle()
    val isToday = viewModel.isToday

    // A once-a-minute clock, so the pace and the "now" marker move while the page is open.
    var nowMin by remember { mutableIntStateOf(minuteOfDayNow()) }
    LaunchedEffect(isToday) {
        while (isToday) {
            delay(60_000L)
            nowMin = minuteOfDayNow()
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun announce(message: String, onUndo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
    fun add(ml: Int) = viewModel.addWater(ml.toFloat()) { id -> announce("Added $ml ml") { viewModel.undoAdd(id) } }

    var editingButtons by remember { mutableStateOf(false) }
    var amountSheet by remember { mutableStateOf<AmountMode?>(null) }
    var showGoalSheet by remember { mutableStateOf(false) }

    val drinkMl = timeline.filter { it.isDrink }.sumOf { it.waterMl.toDouble() }.toFloat()
    val foodMl = timeline.filterNot { it.isDrink }.sumOf { it.waterMl.toDouble() }.toFloat()
    val glassMl = presets.firstOrNull() ?: 250
    val status = if (isToday) WaterPace.statusToday(total, goal, nowMin, wake, bed, glassMl)
        else WaterPace.statusPast(total, goal)

    TrackerScaffold(
        title = "Water",
        onBack = onBack,
        infoTitle = "How water works",
        infoText = INFO_TEXT,
        onOpenSettings = onOpenSettings,
        snackbarHost = snackbar
    ) {
        // ---- Hero: the ring, where it came from, and the pace ----
        TrackerHero(
            visual = {
                WaterRing(
                    drinkMl = drinkMl, foodMl = foodMl, totalMl = total, goalMl = goal,
                    onClick = { showGoalSheet = true }
                )
            }
        ) {
            if (!isToday) {
                Text(viewModel.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), style = MaterialTheme.typography.labelLarge, color = CreamMuted)
            }
            LegendRow(AccentTrends, "Drinks", drinkMl)
            LegendRow(WaterFromFood, "From food", foodMl)
            status?.let { StatusPill(it) }
        }

        // ---- Quick add ----
        TrackerSection(
            title = "Quick add",
            action = {
                Text(
                    if (editingButtons) "Done" else "Edit",
                    style = MaterialTheme.typography.labelLarge, color = GoldLight,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { editingButtons = !editingButtons }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        ) {
            QuickAddGrid(
                presets = presets,
                editing = editingButtons,
                onAdd = { add(it) },
                onRemove = viewModel::removePreset,
                onOther = { amountSheet = AmountMode.OTHER },
                onNewButton = { amountSheet = AmountMode.NEW_BUTTON }
            )
        }

        // ---- One chart: today so far ⇄ this week ----
        val todayIndex = if (isToday) (LocalDate.now().toEpochDay() - viewModel.weekStart.toEpochDay()).toInt().takeIf { it in 0..6 } else null
        val elapsed = week.filterNotNull()
        TrackerChart(
            todayTitle = if (isToday) "Today so far" else "That day",
            weekTitle = "This week",
            view = chartView,
            onView = viewModel::setChartView,
            accent = AccentTrends,
            takeaway = if (chartView == 0) {
                if (isToday && goal > 0 && total < goal) {
                    "About ${"%,d".format(roundTo50(WaterPace.expectedBy(goal, nowMin, wake, bed)))} ml by now keeps you on pace"
                } else null
            } else if (elapsed.isNotEmpty()) {
                val avg = elapsed.average().roundToInt()
                val hit = if (goal > 0) elapsed.count { it >= goal } else 0
                "Avg ${"%,d".format(avg)} ml" + (if (goal > 0) " · goal on $hit of ${elapsed.size} days" else "")
            } else null,
            today = {
                WaterDayChart(
                    rows = timeline, goalMl = goal, wakeMin = wake, bedMin = bed,
                    nowMin = if (isToday) nowMin else null
                )
            },
            week = {
                WeekBars(
                    values = week,
                    goal = goal.toFloat().takeIf { goal > 0 },
                    goalKind = GoalKind.REACH,
                    color = AccentTrends,
                    todayIndex = todayIndex,
                    goalLabel = if (goal > 0) "goal ${"%,d".format(goal)}" else null,
                    onDay = { i ->
                        val day = viewModel.weekStart.plusDays(i.toLong())
                        if (!day.isAfter(LocalDate.now()) && day != viewModel.date) onOpenDay(day)
                    }
                )
            }
        )

        // ---- The day's entries ----
        TrackerSection(title = if (isToday) "Today" else "That day") {
            if (timeline.isEmpty()) {
                Text(if (isToday) "Nothing yet today." else "Nothing logged that day.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
            } else {
                timeline.forEach { row ->
                    key(row.itemId) {
                        WaterEntry(
                            row = row,
                            onOpen = { onOpenEntry(row.itemId) },
                            onRemove = {
                                viewModel.remove(row)
                                announce("Removed ${row.waterMl.roundToInt()} ml") { viewModel.restore(row) }
                            }
                        )
                    }
                }
            }
        }
    }

    amountSheet?.let { mode ->
        WaterAmountSheet(
            mode = mode,
            initialMl = glassMl,
            onAdd = { ml -> amountSheet = null; add(ml) },
            onSaveButton = { ml, alsoLog ->
                amountSheet = null
                viewModel.addPreset(ml)
                if (alsoLog) add(ml)
            },
            onDismiss = { amountSheet = null }
        )
    }

    if (showGoalSheet) {
        WaterGoalSheet(
            goalMl = goal,
            isAutomatic = goalOverride <= 0,
            autoGoalMl = autoGoal,
            onSave = { viewModel.setWaterGoal(it); showGoalSheet = false },
            onDismiss = { showGoalSheet = false }
        )
    }
}

@Composable
private fun LegendRow(color: Color, label: String, ml: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.weight(1f))
        Text("%,d ml".format(ml.roundToInt()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Cream)
    }
}

/** The day's water as a ring: drinks, then food, against the goal. Tap to change the goal. */
@Composable
private fun WaterRing(drinkMl: Float, foodMl: Float, totalMl: Float, goalMl: Int, onClick: () -> Unit) {
    val goal = goalMl.coerceAtLeast(1).toFloat()
    val drinkTarget = (drinkMl / goal).coerceIn(0f, 1f)
    val drinkFrac by animateFloatAsState(drinkTarget, tween(650), label = "waterDrinks")
    val foodFrac by animateFloatAsState((foodMl / goal).coerceIn(0f, 1f - drinkTarget), tween(650), label = "waterFood")
    val track = filmColor(0.10f)
    val drinkColor = AccentTrends
    val foodColor = WaterFromFood
    Box(
        modifier = Modifier.size(112.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (goalMl > 0) {
                if (foodFrac > 0f) {
                    drawArc(foodColor, -90f + 360f * drinkFrac, 360f * foodFrac, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                if (drinkFrac > 0f) {
                    drawArc(drinkColor, -90f, 360f * drinkFrac, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "%,d".format(totalMl.roundToInt()),
                style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, fontFeatureSettings = "tnum"),
                color = Cream
            )
            Text(
                if (goalMl > 0) "of %,d ml".format(goalMl) else "ml",
                style = MaterialTheme.typography.labelSmall, color = CreamMuted
            )
        }
    }
}

/**
 * Quick-add buttons in rows of four, the same width. In edit mode each gets an × and the last cell
 * becomes "+ Add"; otherwise it's "Other" for a one-off amount. No × outside edit mode, so a button
 * can't be removed by accident.
 */
@Composable
private fun QuickAddGrid(
    presets: List<Int>,
    editing: Boolean,
    onAdd: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onOther: () -> Unit,
    onNewButton: () -> Unit
) {
    val cells: List<Int?> = presets + listOf(null) // null = the Other / + Add cell
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ml ->
                    Box(Modifier.weight(1f)) {
                        if (ml == null) {
                            QuickAddCell(
                                label = if (editing) "+ Add" else "Other",
                                filled = false,
                                onClick = if (editing) onNewButton else onOther
                            )
                        } else {
                            QuickAddCell(label = "+$ml", filled = true, onClick = { if (!editing) onAdd(ml) })
                            if (editing) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 6.dp, y = (-6).dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MacroOver)
                                        .clickable { onRemove(ml) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove the $ml ml button", tint = OnGold, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QuickAddCell(label: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(if (filled) Modifier.accentGlass(AccentTrends, shape) else Modifier.glassSoft(shape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Cream, maxLines = 1)
    }
}

/**
 * The day so far: a step line of water through the day (each entry a step at the time it was had)
 * against a dashed pace line from waking up (0) to bedtime (the goal). [nowMin] is null on a past day.
 */
@Composable
private fun WaterDayChart(rows: List<WaterTimelineRow>, goalMl: Int, wakeMin: Int, bedMin: Int, nowMin: Int?) {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.typography.labelSmall.copy(color = CreamFaint)
    val nowStyle = MaterialTheme.typography.labelSmall.copy(color = Cream, fontWeight = FontWeight.Bold)
    val goalStyle = MaterialTheme.typography.labelSmall.copy(color = AccentTrends)
    val zone = ZoneId.systemDefault()
    val wake = wakeMin
    val bed = if (bedMin <= wakeMin) bedMin + 24 * 60 else bedMin
    fun onTimeline(min: Int): Int = if (min < wake && min + 24 * 60 <= bed) min + 24 * 60 else min
    // Oldest first, each placed on the waking-day timeline; anything before waking sits at the start.
    val points = rows.sortedBy { it.timestamp }.map { row ->
        val t = Instant.ofEpochMilli(row.timestamp).atZone(zone).toLocalTime()
        onTimeline(t.hour * 60 + t.minute).coerceIn(wake, bed) to row.waterMl
    }
    val total = points.sumOf { it.second.toDouble() }.toFloat()
    val lineColor = AccentTrends
    val fill = AccentTrends.copy(alpha = 0.18f)
    val paceColor = Cream.copy(alpha = 0.45f)
    val baseColor = Cream.copy(alpha = 0.15f)
    Canvas(Modifier.fillMaxSize()) {
        val labelH = 16.dp.toPx()
        val top = 14.dp.toPx()
        val plotH = size.height - labelH - top
        val w = size.width
        val max = maxOf(goalMl.toFloat(), total).coerceAtLeast(1f) * 1.08f
        fun x(min: Int) = w * (min - wake).toFloat() / (bed - wake)
        fun y(ml: Float) = top + plotH - (ml / max).coerceIn(0f, 1f) * plotH
        drawLine(baseColor, Offset(0f, top + plotH), Offset(w, top + plotH), 1.dp.toPx())

        // The goal, dotted, labelled at its right end — and the pace line rising to it.
        if (goalMl > 0) {
            val gy = y(goalMl.toFloat())
            drawLine(lineColor.copy(alpha = 0.4f), Offset(0f, gy), Offset(w, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f)))
            val g = measurer.measure("goal %,d".format(goalMl), goalStyle)
            drawText(g, topLeft = Offset(w - g.size.width, (gy - g.size.height - 1.dp.toPx()).coerceAtLeast(0f)))
            drawLine(paceColor, Offset(0f, y(0f)), Offset(w, gy), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)))
        }

        // The step line of what you've had, up to now (today) or to bedtime (a past day).
        val endMin = (nowMin?.let { onTimeline(it) } ?: bed).coerceIn(wake, bed)
        val line = Path().apply {
            moveTo(0f, y(0f))
            var sum = 0f
            for ((min, ml) in points) {
                lineTo(x(min), y(sum))
                sum += ml
                lineTo(x(min), y(sum))
            }
            lineTo(x(maxOf(endMin, points.lastOrNull()?.first ?: wake)), y(sum))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(maxOf(endMin, points.lastOrNull()?.first ?: wake)), y(0f))
            lineTo(0f, y(0f))
            close()
        }
        drawPath(area, fill)
        drawPath(line, lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

        if (nowMin != null) {
            val nx = x(endMin)
            val ny = y(total)
            drawCircle(lineColor.copy(alpha = 0.3f), 7.dp.toPx(), Offset(nx, ny))
            drawCircle(Cream, 3.dp.toPx(), Offset(nx, ny))
            val n = measurer.measure("now", nowStyle)
            drawText(n, topLeft = Offset((nx - n.size.width / 2f).coerceIn(0f, w - n.size.width), (ny - n.size.height - 8.dp.toPx()).coerceAtLeast(0f)))
        }

        // Time labels at their true positions: waking up, noon and 6 PM when they fit, bedtime.
        val wakeLbl = measurer.measure(WaterPace.clockLabel(wake), axis)
        val bedLbl = measurer.measure(WaterPace.clockLabel(bed), axis)
        val labelY = size.height - wakeLbl.size.height
        drawText(wakeLbl, topLeft = Offset(0f, labelY))
        drawText(bedLbl, topLeft = Offset(w - bedLbl.size.width, labelY))
        listOf(12 * 60 to "noon", 18 * 60 to "6 PM").forEach { (min, text) ->
            val m = onTimeline(min)
            if (m in (wake + 1) until bed) {
                val l = measurer.measure(text, axis)
                val lx = x(m) - l.size.width / 2f
                if (lx > wakeLbl.size.width + 8.dp.toPx() && lx + l.size.width < w - bedLbl.size.width - 8.dp.toPx()) {
                    drawText(l, topLeft = Offset(lx, labelY))
                }
            }
        }
    }
}

/** One entry: time, what it was, its water. Plain water can be swiped away; the rest open their entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaterEntry(row: WaterTimelineRow, onOpen: () -> Unit, onRemove: () -> Unit) {
    val plainWater = row.isDrink && row.calories <= 0f
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .then(if (plainWater) Modifier else Modifier.clickable(onClick = onOpen))
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                timeLabel(row.timestamp),
                style = MaterialTheme.typography.labelMedium, color = CreamFaint,
                modifier = Modifier.width(72.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.bodyMedium, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!row.isDrink) Text("from food", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
            }
            Text(
                "%,d ml".format(row.waterMl.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.isDrink) AccentTrends else Cream
            )
        }
    }
    if (!plainWater) {
        content()
        return
    }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onRemove()
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(MacroOver.copy(alpha = 0.18f)).padding(horizontal = 14.dp),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Text("Remove", style = MaterialTheme.typography.labelLarge, color = MacroOver)
            }
        }
    ) { content() }
}

private enum class AmountMode { OTHER, NEW_BUTTON }

/** "Other" (a one-off amount, or save it as a button too) and "+ Add" (a new quick-add button). */
@Composable
private fun WaterAmountSheet(
    mode: AmountMode,
    initialMl: Int,
    onAdd: (Int) -> Unit,
    onSaveButton: (ml: Int, alsoLog: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialMl.toString()) }
    val ml = text.toIntOrNull()?.takeIf { it in 1..3000 }
    TrackerSheet(title = if (mode == AmountMode.OTHER) "Add water" else "New quick-add button", onDismiss = onDismiss) {
        GlassNumberField(
            text = text, onText = { text = it },
            step = 50f, min = 0f, max = 3000f, fallback = initialMl.toFloat(), maxChars = 4,
            modifier = Modifier.fillMaxWidth(),
            onDone = { ml?.let { if (mode == AmountMode.OTHER) onAdd(it) else onSaveButton(it, false) } }
        )
        Text("ml", style = MaterialTheme.typography.titleMedium, color = CreamMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (mode == AmountMode.OTHER) {
                GlassButton("Save as a button", onClick = { ml?.let { onSaveButton(it, true) } }, modifier = Modifier.weight(1f))
                GoldButton("Add", onClick = { ml?.let(onAdd) }, enabled = ml != null, modifier = Modifier.weight(1f))
            } else {
                GlassButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                GoldButton("Save button", onClick = { ml?.let { onSaveButton(it, false) } }, enabled = ml != null, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * The daily water goal: a number of your own, or the automatic one (~35 ml per kg of body weight).
 * [onSave] gets the goal in ml, or 0 for automatic. Shared by the Water page and Settings.
 */
@Composable
internal fun WaterGoalSheet(goalMl: Int, isAutomatic: Boolean, autoGoalMl: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(goalMl.toString()) }
    val ml = text.toIntOrNull()?.takeIf { it in 500..10000 }
    TrackerSheet(title = "Daily water goal", onDismiss = onDismiss) {
        GlassNumberField(
            text = text, onText = { text = it },
            step = 100f, min = 500f, max = 10000f, fallback = goalMl.toFloat(), maxChars = 5,
            modifier = Modifier.fillMaxWidth(),
            onDone = { ml?.let(onSave) }
        )
        Text("ml a day", style = MaterialTheme.typography.titleMedium, color = CreamMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            if (isAutomatic) "Automatic right now: %,d ml, about 35 ml per kg you weigh.".format(autoGoalMl)
            else "Automatic would be %,d ml, about 35 ml per kg you weigh.".format(autoGoalMl),
            style = MaterialTheme.typography.bodySmall, color = CreamMuted
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Use automatic", onClick = { onSave(0) }, modifier = Modifier.weight(1f))
            GoldButton("Save", onClick = { ml?.let(onSave) }, enabled = ml != null, modifier = Modifier.weight(1f))
        }
    }
}

private fun minuteOfDayNow(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

private fun timeLabel(epochMillis: Long): String {
    val t = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    return WaterPace.clockLabel(t.hour * 60 + t.minute)
}

private fun roundTo50(ml: Float): Int = (ml / 50f).roundToInt() * 50
