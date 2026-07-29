@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.fitpal.app.ui.screen.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.local.dao.DailyMicros
import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.domain.DayScore
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.BarTrendChart
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.swipeNavigation
import com.fitpal.app.ui.component.MacroBar
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.component.TrendChart
import com.fitpal.app.ui.theme.AccentActivity
import com.fitpal.app.ui.theme.AccentGarden
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.CarbColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.FatColor
import com.fitpal.app.ui.theme.FiberColor
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.LoggedBest
import com.fitpal.app.ui.theme.LoggedGood
import com.fitpal.app.ui.theme.LoggedLow
import com.fitpal.app.ui.theme.LoggedMid
import com.fitpal.app.ui.theme.LoggedWorst
import com.fitpal.app.ui.theme.ProteinColor
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.ScorePoor
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.scoreColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AnalyticsScreen(
    onOpenReview: (period: String, key: String) -> Unit = { _, _ -> },
    onSwipeToHome: () -> Unit = {},
    onSwipeToCollection: () -> Unit = {},
    onOpenDay: () -> Unit = {},
    onOpenCalorieDetail: (range: String, anchor: String) -> Unit = { _, _ -> },
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val range by viewModel.range.collectAsStateWithLifecycle()
    val periodStart by viewModel.periodStart.collectAsStateWithLifecycle()
    val periodEnd by viewModel.periodEnd.collectAsStateWithLifecycle()
    val rollingStart by viewModel.rollingStart.collectAsStateWithLifecycle()
    val rollingEnd by viewModel.rollingEnd.collectAsStateWithLifecycle()
    val anchor by viewModel.anchor.collectAsStateWithLifecycle()
    val canGoPrev by viewModel.canGoPrev.collectAsStateWithLifecycle()
    val canGoNext by viewModel.canGoNext.collectAsStateWithLifecycle()
    // Jump the shared cursor to a day, then hop to Home to see it.
    val openDay: (LocalDate) -> Unit = { d -> viewModel.focusDay(d); onOpenDay() }
    val rows by viewModel.nutritionRows.collectAsStateWithLifecycle()
    val weights by viewModel.weightEntries.collectAsStateWithLifecycle()
    val targets by viewModel.dailyTargets.collectAsStateWithLifecycle()
    val latestWeight by viewModel.latestWeight.collectAsStateWithLifecycle()
    val waterSplit by viewModel.waterSplitRows.collectAsStateWithLifecycle()
    val stepRows by viewModel.stepRows.collectAsStateWithLifecycle()
    val exerciseBurn by viewModel.exerciseBurnRows.collectAsStateWithLifecycle()
    val stepTrim by viewModel.stepTrimPercent.collectAsStateWithLifecycle()
    val microsTotals by viewModel.microsTotals.collectAsStateWithLifecycle()
    val weightRate by viewModel.weightRatePerWeek.collectAsStateWithLifecycle()
    val fitnessGoal by viewModel.fitnessGoal.collectAsStateWithLifecycle()
    val impliedMaintenance by viewModel.impliedMaintenance.collectAsStateWithLifecycle()
    val spotlight by viewModel.spotlight.collectAsStateWithLifecycle()
    val analyticsViews by viewModel.analyticsViews.collectAsStateWithLifecycle()
    // Which cards are flipped to "Lifetime", plus the all-time data they draw from.
    val lifetime by viewModel.lifetime.collectAsStateWithLifecycle()
    val lifetimeRows by viewModel.lifetimeRows.collectAsStateWithLifecycle()
    val lifetimeMicros by viewModel.lifetimeMicros.collectAsStateWithLifecycle()
    val widgetLayout by viewModel.widgetLayout.collectAsStateWithLifecycle()
    var showWeightDialog by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    // The day square you last tapped stays ringed while its dialog is open and for a moment
    // after it closes, so it's obvious which day you just looked at.
    var flashedDay by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(selectedDay, flashedDay) {
        if (selectedDay == null && flashedDay != null) {
            delay(1400)
            flashedDay = null
        }
    }
    val tapDay: (LocalDate) -> Unit = { d -> selectedDay = d; flashedDay = d }

    val today = LocalDate.now()
    val iso = DateTimeFormatter.ISO_LOCAL_DATE
    val dayMonth = DateTimeFormatter.ofPattern("d MMM")
    val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")
    val dailyKey = today.format(iso)
    // AI weekly/monthly reviews follow the period you're actually viewing.
    val weekStartKey = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong()).format(iso)
    val monthKey = java.time.YearMonth.from(anchor).toString()

    // Two windows. weekDates = the calendar period (Mon–Sun week, or 30 days) — used by logged-days,
    // calorie-balance, macro-balance and the Calories widget. rollingDates = the last N days — used
    // by every other widget.
    val weekDates = generateSequence(periodStart) { it.plusDays(1) }.takeWhile { !it.isAfter(periodEnd) }.toList()
    val rollingDates = generateSequence(rollingStart) { it.plusDays(1) }.takeWhile { !it.isAfter(rollingEnd) }.toList()
    val weekKeys = weekDates.map { it.format(iso) }
    val rollingKeys = rollingDates.map { it.format(iso) }
    // All days we have data for (the union), for building lookup maps + per-day scores.
    val allDates = (weekDates + rollingDates).distinct().sorted()

    val startLbl = rollingStart.format(dayMonth)
    val endLbl = if (rollingEnd == today) "now" else rollingEnd.format(dayMonth)
    val periodLabel = "${periodStart.format(dayMonth)} – ${periodEnd.format(dayMonth)}"
    // Calendar-week start/end labels — only the Calories widget uses these (it tracks the current week).
    val periodStartLbl = periodStart.format(dayMonth)
    val periodEndLbl = if (periodEnd == today) "now" else periodEnd.format(dayMonth)
    fun seriesOf(keys: List<String>, map: Map<String, Float>): List<Float?> = keys.map { map[it] }

    val calMap = rows.associate { it.date to it.calories }
    val stepBurnMap = stepRows.associate { it.date to it.caloriesBurned * (100 - stepTrim) / 100f }
    val exMap = exerciseBurn.associate { it.date to it.burned }
    val totalBurnMap = (stepBurnMap.keys + exMap.keys).associateWith { (stepBurnMap[it] ?: 0f) + (exMap[it] ?: 0f) }

    // Rolling-window series (the default for most widgets).
    val calSeries = seriesOf(rollingKeys, calMap)
    val stepSeries = seriesOf(rollingKeys, stepRows.associate { it.date to it.steps.toFloat() })
    val burnSeries = seriesOf(rollingKeys, totalBurnMap)
    // Net calories that actually count toward the budget = eaten − burned (steps + workouts).
    val netCalSeries: List<Float?> = rollingKeys.map { key ->
        rows.find { it.date == key }?.let { (it.calories - (totalBurnMap[key] ?: 0f)).coerceAtLeast(0f) }
    }
    // Same net-calorie figure but over the calendar week (Mon–Sun) — the Calories widget alone uses
    // this so it shows the *current week*, not a rolling last-7-days window.
    val netCalWeekSeries: List<Float?> = weekKeys.map { key ->
        rows.find { it.date == key }?.let { (it.calories - (totalBurnMap[key] ?: 0f)).coerceAtLeast(0f) }
    }
    val waterSeries = seriesOf(rollingKeys, waterSplit.associate { it.date to (it.drinkWater + it.foodWater) })
    val rollingWaterSplit = waterSplit.filter { it.date in rollingKeys }
    val avgDrink = (rollingWaterSplit.sumOf { it.drinkWater.toDouble() } / range.days).toInt()
    val avgFood = (rollingWaterSplit.sumOf { it.foodWater.toDouble() } / range.days).toInt()

    val calGoal = targets?.calories ?: 0
    val pT = targets?.proteinG?.toFloat() ?: 0f
    val fT = targets?.fatG?.toFloat() ?: 0f
    val cT = targets?.carbsG?.toFloat() ?: 0f
    val fibT = targets?.fiberG?.toFloat() ?: 0f

    // Per-day balance score across every day we have, so both windows can look it up.
    val scoreByDate: Map<String, Int?> = allDates.associate { d ->
        val key = d.format(iso)
        val row = rows.find { it.date == key }
        key to if (row == null || (row.calories <= 0f && row.protein <= 0f)) null
        else DayScore.compute(
            row.calories, calGoal, row.protein, pT, row.fat, fT, row.carbs, cT, row.fiber, fibT, -1f
        ).score
    }
    val scoreSeries: List<Float?> = rollingKeys.map { scoreByDate[it]?.toFloat() }

    // Row subsets per window (for averages / balance computed in-screen).
    val rollingRows = rows.filter { it.date in rollingKeys }
    val weekRows = rows.filter { it.date in weekKeys }

    // Every day ever logged, oldest → newest (the DAO already sorts) — the x-axis for Lifetime views.
    val lifetimeDates = lifetimeRows.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }

    // Tapping a bar only navigates for days that actually have data — future/empty days do nothing (#2).
    // Lifetime views reach outside the viewed window, so the all-time rows count as logged too.
    val loggedKeys = (rows + lifetimeRows).filter { it.calories > 0f || it.protein > 0f }.map { it.date }.toSet()
    val openDayIfLogged: (LocalDate) -> Unit = { d -> if (d.format(iso) in loggedKeys) openDay(d) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var highlight by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(highlight) { if (highlight != null) { delay(1300); highlight = null } }
    // The positional order of rendered LazyColumn items — header + pills are fixed, then the
    // widgets in the user's chosen order (skipping hidden ones and data-gated cards). Drives the
    // jump-to-card scrolling from the summary tiles.
    val itemKeys = buildList {
        add("header"); add("pills")
        widgetLayout.forEach forEachKey@{ w ->
            if (!w.enabled) return@forEachKey
            when (w.key) {
                "balance" -> if (weekRows.isNotEmpty() && calGoal > 0) add("balance")
                "maintenance" -> if (impliedMaintenance != null) add("maintenance")
                "averages" -> if (rollingRows.isNotEmpty()) add("averages")
                "ai" -> { add("aihdr"); add("aiToday"); add("aiWeek"); add("aiMonth") }
                else -> add(w.key)
            }
        }
    }
    fun jumpTo(key: String) {
        scope.launch { listState.animateScrollToItem(itemKeys.indexOf(key).coerceAtLeast(0)) }
        highlight = key
    }

    GradientBackdrop(theme = BackdropTheme.TRENDS) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().swipeNavigation(
                onSwipeLeft = onSwipeToCollection,
                onSwipeRight = onSwipeToHome
            ),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Analytics", style = MaterialTheme.typography.headlineLarge, color = Cream)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Previous ${if (range == AnalyticsRange.WEEK) "week" else "period"}",
                                tint = if (canGoPrev) Cream else CreamFaint,
                                modifier = Modifier.size(22.dp).clickable(enabled = canGoPrev) { viewModel.goPrev() }
                            )
                            Text(
                                periodLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CreamMuted,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Next ${if (range == AnalyticsRange.WEEK) "week" else "period"}",
                                tint = if (canGoNext) Cream else CreamFaint,
                                modifier = Modifier.size(22.dp).clickable(enabled = canGoNext) { viewModel.goNext() }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .glass(RoundedCornerShape(50))
                            .clickable { jumpTo("aihdr") }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI reviews", tint = GoldLight, modifier = Modifier.size(16.dp))
                        Text("AI", style = MaterialTheme.typography.labelLarge, color = Cream)
                    }
                }
            }

            item {
                SegmentedPills(
                    labels = AnalyticsRange.entries.map { it.label },
                    selectedIndex = AnalyticsRange.entries.indexOf(range),
                    accent = AccentTrends,
                    onSelect = { viewModel.setRange(AnalyticsRange.entries[it]) }
                )
            }

            // Cards render in the user's chosen order, skipping hidden ones (Settings → Customize
            // screens → Analytics). The header + range pills above stay fixed. Default = original.
            widgetLayout.forEach forEachWidget@{ widget ->
                if (!widget.enabled) return@forEachWidget
                when (widget.key) {
                    // ---- Summary tiles ----
                    "tiles" -> item {
                        val avgCal = calSeries.filterNotNull().takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
                        val avgSteps = stepSeries.filterNotNull().takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
                        // Avg score + logged days are merged into one compact tile, both over the week.
                        val weekScores = weekDates.mapNotNull { scoreByDate[it.format(iso)] }
                        val avgScore = weekScores.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
                        val ww = weights.filter { it.date in rollingKeys }.sortedBy { it.date }
                        val wChange = if (ww.size >= 2) ww.last().weightKg - ww.first().weightKg else null
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(Modifier.weight(1f), "Avg calories", avgCal?.let { "${it.toInt()}" } ?: "—", "kcal/day", GoldLight, "tile_cal", spotlight, viewModel::toggleSpotlight) { jumpTo("calories") }
                                StatTile(Modifier.weight(1f), "Avg steps", avgSteps?.let { "${it.toInt()}" } ?: "—", "per day", AccentActivity, "tile_steps", spotlight, viewModel::toggleSpotlight) { jumpTo("activity") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(
                                    Modifier.weight(1f), "Weight change",
                                    wChange?.let { (if (it >= 0) "+" else "") + "%.1f".format(it) } ?: "—", "kg", GoldLight,
                                    "tile_weight", spotlight, viewModel::toggleSpotlight
                                ) { jumpTo("weight") }
                                LoggedScoreTile(
                                    Modifier.weight(1f),
                                    dates = weekDates,
                                    colorFor = { d -> scoreByDate[d.format(iso)]?.let { loggedDayColor(it) } },
                                    avgScore = avgScore,
                                    spotlight = spotlight,
                                    onToggleSpotlight = viewModel::toggleSpotlight,
                                    selected = flashedDay,
                                    onDayClick = tapDay
                                )
                            }
                        }
                    }

                    // ---- Calorie balance (net vs goal, after calories burned) ----
                    "balance" -> if (weekRows.isNotEmpty() && calGoal > 0) item {
                        // Account for steps + exercise: your effective budget for a day is
                        // goal + what you burned, so net = eaten − goal − burned (matches Home).
                        val net = weekRows.sumOf { (it.calories - calGoal - (totalBurnMap[it.date] ?: 0f)).toDouble() }.toInt()
                        val over = net > 0
                        val periodWord = if (range.days <= 7) "this week" else "this month"
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .cardSurface("balance" in spotlight)
                                .combinedClickable(
                                    onClick = { onOpenCalorieDetail(range.name, anchor.format(iso)) },
                                    onLongClick = { viewModel.toggleSpotlight("balance") }
                                )
                                .padding(16.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Calorie balance", style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.weight(1f))
                                Text("›", style = MaterialTheme.typography.titleLarge, color = CreamMuted)
                            }
                            Text("Net vs your goal after steps & exercise, $periodWord · tap for the full breakdown", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = (if (over) "+" else "") + "$net kcal",
                                style = MaterialTheme.typography.headlineMedium,
                                color = when {
                                    kotlin.math.abs(net) < 100 -> GoldLight
                                    over -> ScorePoor
                                    else -> ScoreFair
                                }
                            )
                            Text(
                                text = when {
                                    kotlin.math.abs(net) < 100 -> "Right around your goal across ${weekRows.size} logged days"
                                    over -> "Over by $net across ${weekRows.size} logged days"
                                    else -> "Under by ${-net} across ${weekRows.size} logged days"
                                },
                                style = MaterialTheme.typography.bodySmall, color = CreamMuted
                            )
                        }
                    }

                    // ---- Logged-days heatmap ----
                    "heatmap" -> item {
                        SwitchableChartCard("Logged days", listOf("Calendar", "Legend"), viewState = analyticsViews, onSetView = viewModel::setView, spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight) { v ->
                            if (v == 0) {
                                Text("${weekRows.size} of ${weekDates.size} logged · tap a day for details", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                                Spacer(Modifier.height(10.dp))
                                LoggedHeatmap(
                                    dates = weekDates,
                                    colorFor = { d -> scoreByDate[d.format(iso)]?.let { loggedDayColor(it) } },
                                    selected = flashedDay,
                                    onDayClick = tapDay
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Each square is a day, coloured by its balance score:", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                                    LegendRow(LoggedBest, "Best (80–100)")
                                    LegendRow(LoggedGood, "Good (65–79)")
                                    LegendRow(LoggedMid, "Fair (50–64)")
                                    LegendRow(LoggedLow, "Low (35–49)")
                                    LegendRow(LoggedWorst, "Poor (under 35)")
                                    LegendRow(Color.White.copy(alpha = 0.08f), "Not logged")
                                }
                            }
                        }
                    }

                    // ---- Balance score ----
                    "score" -> item {
                        SwitchableChartCard("Balance score", listOf("Trend", "Breakdown"), viewState = analyticsViews, onSetView = viewModel::setView, highlight = highlight == "score", spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight) { v ->
                            val present = scoreSeries.filterNotNull().map { it.toInt() }
                            when {
                                present.size < 2 -> NotEnough()
                                v == 0 -> TrendChart(
                                    values = scoreSeries, accent = GoldLight,
                                    startLabel = startLbl, endLabel = endLbl,
                                    minOverride = 0f, maxOverride = 100f
                                )
                                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TierRow(scoreColor(DayScore.Tier.GOOD), "Good (80+)", present.count { it >= 80 })
                                    TierRow(scoreColor(DayScore.Tier.FAIR), "Fair (60–79)", present.count { it in 60..79 })
                                    TierRow(scoreColor(DayScore.Tier.LOW), "Low (40–59)", present.count { it in 40..59 })
                                    TierRow(scoreColor(DayScore.Tier.POOR), "Poor (<40)", present.count { it < 40 })
                                    Spacer(Modifier.height(2.dp))
                                    Text("Best ${present.maxOrNull() ?: 0} · worst ${present.minOrNull() ?: 0}", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                                }
                            }
                        }
                    }

                    // ---- Calories (net of what you burned from steps + workouts) ----
                    // Unlike the other trend widgets, Calories tracks the *current calendar week*
                    // (Mon–Sun), so it uses weekDates/netCalWeekSeries instead of the rolling window.
                    "calories" -> item {
                        SwitchableChartCard("Calories", listOf("Trend", "Vs goal"), viewState = analyticsViews, onSetView = viewModel::setView, highlight = highlight == "calories", spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight) { v ->
                            when {
                                !enough(netCalWeekSeries) -> NotEnough()
                                v == 0 -> TrendChart(
                                    values = netCalWeekSeries, accent = Gold,
                                    target = calGoal.takeIf { it > 0 }?.toFloat(),
                                    startLabel = periodStartLbl, endLabel = periodEndLbl
                                )
                                else -> BarTrendChart(
                                    values = netCalWeekSeries.map { it ?: 0f },
                                    accent = ScoreFair,
                                    target = calGoal.takeIf { it > 0 }?.toFloat(),
                                    overColor = ScorePoor,
                                    startLabel = periodStartLbl, endLabel = periodEndLbl,
                                    labels = weekDates.map { it.dayOfMonth.toString() },
                                    highlightIndex = weekDates.indexOfFirst { it == today }.takeIf { it >= 0 },
                                    onBarClick = { i -> weekDates.getOrNull(i)?.let(openDayIfLogged) }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Net of calories burned (steps + workouts) · ${if (range == AnalyticsRange.WEEK) "this week" else "this period"} · tap a bar to open that day", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
                        }
                    }

                    // ---- Macro balance ----
                    "macros" -> item {
                        // "Lifetime" swaps the viewed week/month for every day ever logged.
                        val allTime = "macros" in lifetime
                        val macroRows = if (allTime) lifetimeRows else weekRows
                        val macroDates = if (allTime) lifetimeDates else weekDates
                        SwitchableChartCard(
                            "Macro balance", listOf("Average", "Over time"),
                            viewState = analyticsViews, onSetView = viewModel::setView,
                            spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight,
                            trailing = { LifetimePill(allTime) { viewModel.toggleLifetime("macros") } }
                        ) { v ->
                            when {
                                macroRows.isEmpty() -> NotEnough()
                                v == 0 -> {
                                    val n = macroRows.size
                                    MacroBar(
                                        protein = (macroRows.sumOf { it.protein.toDouble() } / n).toFloat(),
                                        fat = (macroRows.sumOf { it.fat.toDouble() } / n).toFloat(),
                                        carbs = (macroRows.sumOf { it.carbs.toDouble() } / n).toFloat(),
                                        fiber = (macroRows.sumOf { it.fiber.toDouble() } / n).toFloat()
                                    )
                                }
                                else -> {
                                    MacroCompositionChart(
                                        macroRows, macroDates,
                                        labels = macroDates.map { it.dayOfMonth.toString() },
                                        highlightIndex = macroDates.indexOfFirst { it == today }.takeIf { it >= 0 },
                                        onBarClick = { i -> macroDates.getOrNull(i)?.let(openDayIfLogged) }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        LegendDot(ProteinColor, "Protein")
                                        LegendDot(FatColor, "Fat")
                                        LegendDot(CarbColor, "Carbs")
                                        LegendDot(FiberColor, "Fiber")
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (allTime) "Averaged over all ${macroRows.size} days you've ever logged"
                                else "Averaged over ${macroRows.size} logged days in this period",
                                style = MaterialTheme.typography.labelSmall, color = CreamFaint
                            )
                        }
                    }

                    // ---- Vitamins & minerals (deficiency / proficiency) ----
                    "micros" -> item {
                        val allTime = "micros" in lifetime
                        ChartCard(
                            "Vitamins & minerals",
                            if (allTime) "All-time daily average" else "Daily average vs target",
                            spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight,
                            trailing = { LifetimePill(allTime) { viewModel.toggleLifetime("micros") } }
                        ) {
                            if (allTime) MicronutrientAnalysis(lifetimeMicros, lifetimeRows.size)
                            else MicronutrientAnalysis(microsTotals, rollingRows.size)
                        }
                    }

                    // ---- Activity (steps + total burned) ----
                    "activity" -> item {
                        ChartCard("Activity", "Steps and total calories burned", highlight = highlight == "activity", spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight) {
                            Text("Steps", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                            Spacer(Modifier.height(4.dp))
                            if (enough(stepSeries)) {
                                TrendChart(values = stepSeries, accent = AccentActivity, startLabel = startLbl, endLabel = endLbl, height = 120.dp)
                            } else NotEnough()
                            Spacer(Modifier.height(14.dp))
                            Text("Total burned (steps + workouts)", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                            Spacer(Modifier.height(4.dp))
                            if (enough(burnSeries)) {
                                TrendChart(values = burnSeries, accent = GoldLight, startLabel = startLbl, endLabel = endLbl, height = 120.dp)
                            } else NotEnough()
                        }
                    }

                    // ---- Water ----
                    "water" -> item {
                        SwitchableChartCard("Water", listOf("Total", "By source"), viewState = analyticsViews, onSetView = viewModel::setView, spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight) { v ->
                            if (v == 0) {
                                if (enough(waterSeries)) {
                                    TrendChart(values = waterSeries, accent = AccentTrends, startLabel = startLbl, endLabel = endLbl)
                                } else NotEnough()
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    WaterChip(AccentTrends, "Drinks", "$avgDrink ml/day")
                                    WaterChip(AccentGarden, "From food", "$avgFood ml/day")
                                }
                            } else {
                                val drinkSeries = seriesOf(rollingKeys, waterSplit.associate { it.date to it.drinkWater })
                                val foodSeries = seriesOf(rollingKeys, waterSplit.associate { it.date to it.foodWater })
                                Text("Clear drinks", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                                Spacer(Modifier.height(4.dp))
                                if (enough(drinkSeries)) {
                                    TrendChart(values = drinkSeries, accent = AccentTrends, startLabel = startLbl, endLabel = endLbl, height = 120.dp)
                                } else NotEnough()
                                Spacer(Modifier.height(14.dp))
                                Text("From food", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                                Spacer(Modifier.height(4.dp))
                                if (enough(foodSeries)) {
                                    TrendChart(values = foodSeries, accent = AccentGarden, startLabel = startLbl, endLabel = endLbl, height = 120.dp)
                                } else NotEnough()
                            }
                        }
                    }

                    // ---- Weight (current + trend grouped) ----
                    "weight" -> item {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .cardSurface("Weight" in spotlight)
                                .then(highlightBorder(highlight == "weight"))
                                .combinedClickable(
                                    onClick = { showWeightDialog = true },
                                    onLongClick = { viewModel.toggleSpotlight("Weight") }
                                )
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Weight", style = MaterialTheme.typography.titleMedium, color = Cream)
                                    Text(
                                        text = if (latestWeight != null) "Last logged ${latestWeight!!.date}" else "Tap to log your weight",
                                        style = MaterialTheme.typography.bodySmall, color = CreamMuted
                                    )
                                }
                                Text(
                                    text = if (latestWeight != null) "${"%.1f".format(latestWeight!!.weightKg)} kg" else "— kg",
                                    style = MaterialTheme.typography.titleLarge, color = GoldLight
                                )
                            }
                            weightRate?.let { rate ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = com.fitpal.app.domain.WeightTrend.rateLabel(rate, fitnessGoal),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (rate < 0f) ScoreFair else GoldLight
                                )
                            }

                            // "Lifetime" redraws the trend from the very first weight you logged.
                            val allTime = "weight" in lifetime
                            val byDate = weights.associate { it.date to it.weightKg }
                            val firstWeighIn = weights.minByOrNull { it.date }
                                ?.let { runCatching { LocalDate.parse(it.date) }.getOrNull() }
                            val wSeries: List<Float?> = if (allTime && firstWeighIn != null) {
                                generateSequence(firstWeighIn) { it.plusDays(1) }
                                    .takeWhile { !it.isAfter(today) }
                                    .map { byDate[it.format(iso)] }
                                    .toList()
                            } else {
                                seriesOf(rollingKeys, byDate)
                            }

                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (allTime && firstWeighIn != null) {
                                        "Since ${firstWeighIn.format(dayMonthYear)} · ${weights.size} weigh-ins"
                                    } else "$startLbl – $endLbl",
                                    style = MaterialTheme.typography.labelSmall, color = CreamFaint
                                )
                                LifetimePill(allTime) { viewModel.toggleLifetime("weight") }
                            }
                            if (enough(wSeries)) {
                                Spacer(Modifier.height(8.dp))
                                TrendChart(
                                    values = wSeries, accent = GoldLight,
                                    startLabel = if (allTime && firstWeighIn != null) firstWeighIn.format(dayMonth) else startLbl,
                                    endLabel = if (allTime && firstWeighIn != null) "now" else endLbl,
                                    valueFormatter = { "%.1f".format(it) }
                                )
                            } else {
                                Spacer(Modifier.height(8.dp))
                                NotEnough()
                            }
                        }
                    }

                    // ---- Implied maintenance (insight only — does not change the budget) ----
                    "maintenance" -> impliedMaintenance?.let { maint -> item {
                        ChartCard(
                            "Maintenance estimate", "from your data",
                            highlight = highlight == "maintenance",
                            spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight
                        ) {
                            Text(
                                "≈ ${"%,d".format(maint)} kcal/day",
                                style = MaterialTheme.typography.headlineSmall, color = GoldLight
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                buildString {
                                    append("What your average intake and weight change suggest you actually burn. ")
                                    if (calGoal > 0) append("Your target is ${"%,d".format(calGoal)} kcal. ")
                                    append("A rough estimate — it sharpens with more logged days and regular weigh-ins.")
                                },
                                style = MaterialTheme.typography.bodySmall, color = CreamMuted
                            )
                        }
                    } }

                    // ---- Averages ----
                    "averages" -> if (rollingRows.isNotEmpty()) item {
                        ChartCard("Averages", "", spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight) {
                            val n = rollingRows.size.coerceAtLeast(1)
                            listOf(
                                "Calories" to "${(rollingRows.sumOf { it.calories.toDouble() } / n).toInt()} kcal",
                                "Protein" to "${(rollingRows.sumOf { it.protein.toDouble() } / n).toInt()}g",
                                "Fat" to "${(rollingRows.sumOf { it.fat.toDouble() } / n).toInt()}g",
                                "Carbs" to "${(rollingRows.sumOf { it.carbs.toDouble() } / n).toInt()}g",
                                "Fiber" to "${(rollingRows.sumOf { it.fiber.toDouble() } / n).toInt()}g"
                            ).forEach { (label, value) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), Arrangement.SpaceBetween) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                                    Text(value, style = MaterialTheme.typography.bodyMedium, color = Cream)
                                }
                            }
                        }
                    }

                    // ---- AI reviews ----
                    "ai" -> {
                        item {
                            Text("AI reviews", style = MaterialTheme.typography.headlineSmall, color = Cream, modifier = Modifier.padding(top = 6.dp))
                        }
                        item { AiReviewCard("Today", "Tap for a review of today", "ai_today", spotlight, viewModel::toggleSpotlight, highlight = highlight == "aihdr") { onOpenReview("daily", dailyKey) } }
                        item { AiReviewCard("This week", "Tap for your 7-day overview", "ai_week", spotlight, viewModel::toggleSpotlight, highlight = highlight == "aihdr") { onOpenReview("weekly", weekStartKey) } }
                        item { AiReviewCard("This month", "Tap for your monthly overview", "ai_month", spotlight, viewModel::toggleSpotlight, highlight = highlight == "aihdr") { onOpenReview("monthly", monthKey) } }
                    }
                }
            }
        }
    }

    if (showWeightDialog) {
        WeightInputDialog(
            initialKg = latestWeight?.weightKg,
            onSave = { kg -> viewModel.logWeight(kg); showWeightDialog = false },
            onDismiss = { showWeightDialog = false }
        )
    }

    selectedDay?.let { day ->
        DayScoreDialog(
            day = day,
            row = rows.find { it.date == day.format(iso) },
            score = scoreByDate[day.format(iso)],
            calGoal = calGoal,
            proteinTarget = pT, fatTarget = fT, carbTarget = cT, fiberTarget = fibT,
            onJump = { openDay(day); selectedDay = null },
            onDismiss = { selectedDay = null }
        )
    }
}

// ======================== PIECES ========================

/** A fading accent border used to flag a widget after the user jumps to it. */
@Composable
private fun highlightBorder(active: Boolean): Modifier {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            alpha.snapTo(1f)
            alpha.animateTo(0f, animationSpec = tween(1200))
        }
    }
    return if (alpha.value > 0f) Modifier.border(2.dp, AccentTrends.copy(alpha = alpha.value), RoundedCornerShape(26.dp)) else Modifier
}

/**
 * The card's base surface. When [spotlit] (the user long-pressed it) it switches to the
 * warm gold palette from the home screen — a gold gradient fill and bright gold ring —
 * so the widgets you care about clearly stand out against the blue analytics backdrop.
 */
@Composable
private fun Modifier.cardSurface(spotlit: Boolean, shape: RoundedCornerShape = RoundedCornerShape(26.dp)): Modifier =
    if (spotlit) this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Gold.copy(alpha = 0.30f), Gold.copy(alpha = 0.10f))))
        .border(1.5.dp, GoldLight.copy(alpha = 0.9f), shape)
    else this.glass(shape)

@Composable
private fun ChartCard(
    title: String,
    subtitle: String,
    highlight: Boolean = false,
    spotlight: Set<String> = emptySet(),
    onToggleSpotlight: (String) -> Unit = {},
    /** Optional control parked at the end of the header row (e.g. the Lifetime pill). */
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .cardSurface(title in spotlight)
            .then(highlightBorder(highlight))
            .combinedClickable(onClick = {}, onLongClick = { onToggleSpotlight(title) })
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.weight(1f, fill = false))
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(start = 12.dp))
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/**
 * The "Lifetime" switch on the weight / macro / micronutrient cards: off = the period you're
 * viewing, on = everything since your very first entry. It swallows the tap so it doesn't also
 * flip the card's view or trigger the card's own click.
 */
@Composable
private fun LifetimePill(active: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) AccentTrends.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Default.AllInclusive,
            contentDescription = if (active) "Show the viewed period" else "Show all time",
            tint = if (active) AccentTrends else CreamMuted,
            modifier = Modifier.size(13.dp)
        )
        Text(
            "Lifetime",
            style = MaterialTheme.typography.labelSmall,
            color = if (active) AccentTrends else CreamMuted
        )
    }
}

/** A chart card whose content switches between perspectives when tapped. */
@Composable
private fun SwitchableChartCard(
    title: String,
    views: List<String>,
    viewState: Map<String, Int> = emptyMap(),
    onSetView: (String, Int) -> Unit = { _, _ -> },
    highlight: Boolean = false,
    spotlight: Set<String> = emptySet(),
    onToggleSpotlight: (String) -> Unit = {},
    /** Optional control parked before the view switcher (e.g. the Lifetime pill). */
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable (Int) -> Unit
) {
    val view = (viewState[title] ?: 0).coerceIn(0, views.size - 1)
    Column(
        modifier = Modifier.fillMaxWidth()
            .cardSurface(title in spotlight)
            .then(highlightBorder(highlight))
            .combinedClickable(
                onClick = { onSetView(title, (view + 1) % views.size) },
                onLongClick = { onToggleSpotlight(title) }
            )
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.weight(1f, fill = false))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                trailing?.invoke()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(views[view], style = MaterialTheme.typography.labelMedium, color = AccentTrends)
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Switch view", tint = AccentTrends, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        content(view)
    }
}

/** Per-day stacked macro composition (protein/fat/net-carbs/fiber by share of grams). */
@Composable
private fun MacroCompositionChart(
    rows: List<DailyNutritionRow>,
    dates: List<LocalDate>,
    labels: List<String> = emptyList(),
    highlightIndex: Int? = null,
    onBarClick: ((Int) -> Unit)? = null
) {
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val byDate = rows.associateBy { it.date }
    val emptyC = Color.White.copy(alpha = 0.06f)
    val labelArgb = CreamFaint.toArgb()
    val highlightArgb = Cream.toArgb()
    val tapModifier = if (onBarClick != null && dates.isNotEmpty()) {
        Modifier.pointerInput(dates.size) {
            detectTapGestures { offset ->
                val idx = (offset.x / (size.width.toFloat() / dates.size)).toInt().coerceIn(0, dates.size - 1)
                onBarClick(idx)
            }
        }
    } else Modifier
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp).then(tapModifier)) {
        val chartH = size.height * 0.82f
        val slot = size.width / dates.size
        val barW = slot * 0.6f
        val radius = androidx.compose.ui.geometry.CornerRadius(barW / 2.5f, barW / 2.5f)
        dates.forEachIndexed { i, d ->
            val row = byDate[d.format(fmt)]
            val protein = row?.protein ?: 0f
            val fat = row?.fat ?: 0f
            val netCarbs = ((row?.carbs ?: 0f) - (row?.fiber ?: 0f)).coerceAtLeast(0f)
            val fiber = row?.fiber ?: 0f
            val total = protein + fat + netCarbs + fiber
            val x = i * slot + (slot - barW) / 2
            if (total <= 0f) {
                drawRoundRect(emptyC, Offset(x, chartH * 0.6f), Size(barW, chartH * 0.4f), cornerRadius = radius)
            } else {
                // Clip each day's column to a rounded rect so the whole stacked bar reads as one rounded bar.
                val path = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(x, 0f, x + barW, chartH, radius))
                }
                clipPath(path) {
                    var y = chartH
                    listOf(protein to ProteinColor, fat to FatColor, netCarbs to CarbColor, fiber to FiberColor).forEach { (g, c) ->
                        val segH = (g / total) * chartH
                        drawRect(c, Offset(x, y - segH), Size(barW, segH))
                        y -= segH
                    }
                }
            }
            if (i == highlightIndex) {
                drawCircle(Cream, radius = barW * 0.16f, center = Offset(x + barW / 2f, chartH + 8f))
            }
        }
        // Per-bar day labels when there's room.
        if (labels.size == dates.size && dates.size <= 16) {
            val paint = android.graphics.Paint().apply {
                color = labelArgb; textSize = 22f; isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            labels.forEachIndexed { i, lbl ->
                paint.color = if (i == highlightIndex) highlightArgb else labelArgb
                drawContext.canvas.nativeCanvas.drawText(lbl, i * slot + slot / 2f, size.height - 2f, paint)
            }
        }
    }
}

/** RDA reference + averaged daily intake for one micronutrient. */
private data class MicroStat(val label: String, val avg: Float, val rda: Float, val unit: String)

/**
 * Daily-average intake of each goal micronutrient versus its target, surfaced as the
 * three you're lowest on (to supplement) and the three you're best on. Sodium is a
 * limit, not a goal, so it's called out separately only when it runs high.
 */
@Composable
private fun MicronutrientAnalysis(totals: DailyMicros?, loggedDays: Int) {
    if (totals == null || loggedDays <= 0) {
        NotEnough()
        return
    }
    val d = loggedDays.toFloat()
    val goals = listOf(
        MicroStat("Vitamin A", totals.vitaminA / d, 700f, "mcg"),
        MicroStat("Vitamin C", totals.vitaminC / d, 75f, "mg"),
        MicroStat("Vitamin D", totals.vitaminD / d, 15f, "mcg"),
        MicroStat("Vitamin E", totals.vitaminE / d, 15f, "mg"),
        MicroStat("Vitamin B6", totals.vitaminB6 / d, 1.3f, "mg"),
        MicroStat("Vitamin B12", totals.vitaminB12 / d, 2.4f, "mcg"),
        MicroStat("Folate", totals.folate / d, 400f, "mcg"),
        MicroStat("Calcium", totals.calcium / d, 1000f, "mg"),
        MicroStat("Iron", totals.iron / d, 12f, "mg"),
        MicroStat("Magnesium", totals.magnesium / d, 400f, "mg"),
        MicroStat("Zinc", totals.zinc / d, 11f, "mg"),
        MicroStat("Potassium", totals.potassium / d, 3400f, "mg")
    )
    val ranked = goals.sortedBy { it.avg / it.rda }
    val deficient = ranked.take(3)
    val proficient = ranked.reversed().take(3)

    Text("Lowest — consider topping up", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
    Spacer(Modifier.height(8.dp))
    deficient.forEach { MicroRow(it) }

    Spacer(Modifier.height(14.dp))
    Text("Best covered", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
    Spacer(Modifier.height(8.dp))
    proficient.forEach { MicroRow(it) }

    val sodiumAvg = totals.sodium / d
    if (sodiumAvg > 2300f) {
        Spacer(Modifier.height(14.dp))
        Text(
            "Sodium averaging ${sodiumAvg.toInt()} mg/day — over the 2300 mg limit. Worth cutting back.",
            style = MaterialTheme.typography.bodySmall,
            color = ScorePoor
        )
    }
}

@Composable
private fun MicroRow(stat: MicroStat) {
    val coverage = if (stat.rda > 0f) stat.avg / stat.rda else 0f
    val pct = (coverage * 100f).toInt()
    val color = when {
        coverage < 0.5f -> ScorePoor
        coverage < 0.8f -> Gold
        else -> ScoreFair
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stat.label, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.width(86.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(coverage.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("$pct%", style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.width(46.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
    }
}

@Composable
private fun TierRow(color: Color, label: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = Cream)
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Cream)
    }
}

@Composable
private fun StatTile(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    accent: Color,
    spotKey: String,
    spotlight: Set<String>,
    onToggleSpotlight: (String) -> Unit,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .cardSurface(spotKey in spotlight)
            .combinedClickable(onClick = onClick, onLongClick = { onToggleSpotlight(spotKey) })
            .padding(14.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = CreamMuted)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = CreamFaint)
    }
}

/**
 * A compact tile merging the average balance score with a mini logged-days heatmap — sits in the
 * summary grid where "Avg score" used to. Each tiny square is a day, tappable to open its details.
 */
@Composable
private fun LoggedScoreTile(
    modifier: Modifier,
    dates: List<LocalDate>,
    colorFor: (LocalDate) -> Color?,
    avgScore: Int?,
    spotlight: Set<String>,
    onToggleSpotlight: (String) -> Unit,
    selected: LocalDate? = null,
    onDayClick: (LocalDate) -> Unit
) {
    Column(
        modifier = modifier
            .cardSurface("tile_score" in spotlight)
            .combinedClickable(onClick = {}, onLongClick = { onToggleSpotlight("tile_score") })
            .padding(14.dp)
    ) {
        // Header row: title on the left, the average score parked in the free space on the right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Logged days", style = MaterialTheme.typography.labelMedium, color = CreamMuted, modifier = Modifier.weight(1f))
            Text(avgScore?.toString() ?: "—", style = MaterialTheme.typography.titleMedium, color = AccentTrends)
            Spacer(Modifier.width(3.dp))
            Text("avg", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
        }
        Spacer(Modifier.height(8.dp))
        // Fixed-height mini heatmap that auto-packs however many days into the same box, so the tile
        // stays the same size as the other summary tiles whether it's a 7-day week or a 30-day month.
        MiniHeatmap(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            dates = dates,
            colorFor = colorFor,
            selected = selected,
            onDayClick = onDayClick
        )
    }
}

/** The packed grid: columns, square cell size (px) and the centring offset within the [w]×[h] box. */
private data class HeatGrid(val cols: Int, val cell: Float, val offsetX: Float, val offsetY: Float)

/** Finds the largest square cell (with [gap]) that packs [n] cells into the box, then centres it. */
private fun heatGrid(w: Float, h: Float, n: Int, gap: Float): HeatGrid {
    if (n <= 0 || w <= 0f || h <= 0f) return HeatGrid(1, 0f, 0f, 0f)
    var bestCols = 1
    var bestCell = 0f
    for (cols in 1..n) {
        val rows = (n + cols - 1) / cols
        val cell = minOf((w - (cols - 1) * gap) / cols, (h - (rows - 1) * gap) / rows)
        if (cell > bestCell) { bestCell = cell; bestCols = cols }
    }
    val rows = (n + bestCols - 1) / bestCols
    val gridW = bestCols * bestCell + (bestCols - 1) * gap
    val gridH = rows * bestCell + (rows - 1) * gap
    return HeatGrid(bestCols, bestCell, ((w - gridW) / 2f).coerceAtLeast(0f), ((h - gridH) / 2f).coerceAtLeast(0f))
}

/** A Canvas mini-heatmap that fills a fixed box, auto-sizing + centring the day squares; tappable. */
@Composable
private fun MiniHeatmap(
    modifier: Modifier,
    dates: List<LocalDate>,
    colorFor: (LocalDate) -> Color?,
    selected: LocalDate? = null,
    onDayClick: (LocalDate) -> Unit
) {
    val empty = Color.White.copy(alpha = 0.06f)
    val gap = 4f
    // The tapped square gets a ring that springs open, matching the big heatmap's pop.
    val selectedIndex = dates.indexOfFirst { it == selected }.takeIf { it >= 0 }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null) {
            ring.snapTo(0f)
            ring.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        } else {
            ring.animateTo(0f, tween(200))
        }
    }
    Canvas(
        modifier = modifier.pointerInput(dates.size) {
            detectTapGestures { offset ->
                val g = heatGrid(size.width.toFloat(), size.height.toFloat(), dates.size, gap)
                if (g.cell <= 0f) return@detectTapGestures
                val col = ((offset.x - g.offsetX) / (g.cell + gap)).toInt()
                val row = ((offset.y - g.offsetY) / (g.cell + gap)).toInt()
                val idx = row * g.cols + col
                if (col in 0 until g.cols && idx in dates.indices) onDayClick(dates[idx])
            }
        }
    ) {
        val g = heatGrid(size.width, size.height, dates.size, gap)
        if (g.cell <= 0f) return@Canvas
        val r = androidx.compose.ui.geometry.CornerRadius(g.cell / 4f, g.cell / 4f)
        dates.forEachIndexed { i, d ->
            val x = g.offsetX + (i % g.cols) * (g.cell + gap)
            val y = g.offsetY + (i / g.cols) * (g.cell + gap)
            drawRoundRect(colorFor(d) ?: empty, topLeft = Offset(x, y), size = Size(g.cell, g.cell), cornerRadius = r)
        }
        val lift = ring.value
        if (selectedIndex != null && lift > 0.01f) {
            val x = g.offsetX + (selectedIndex % g.cols) * (g.cell + gap)
            val y = g.offsetY + (selectedIndex / g.cols) * (g.cell + gap)
            val grow = g.cell * 0.35f * lift
            val side = g.cell + grow
            drawRoundRect(
                color = Cream.copy(alpha = lift.coerceIn(0f, 1f)),
                topLeft = Offset(x - grow / 2f, y - grow / 2f),
                size = Size(side, side),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(side / 4f, side / 4f),
                style = Stroke(width = 2.5f)
            )
        }
    }
}

/** Calendar heatmap of the range, each day coloured by its balance-score tier (faint if not logged). */
@Composable
private fun LoggedHeatmap(
    dates: List<LocalDate>,
    colorFor: (LocalDate) -> Color?,
    selected: LocalDate? = null,
    onDayClick: (LocalDate) -> Unit
) {
    if (dates.isEmpty()) return
    if (dates.size <= 7) {
        // Short range: one straight row, oldest → newest, newest on the right.
        val cell = 26.dp
        val scroll = rememberScrollState()
        LaunchedEffect(dates.size) { scroll.scrollTo(scroll.maxValue) }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            dates.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HeatCell(cell, colorFor(d), selected = d == selected) { onDayClick(d) }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (d == selected) Cream else CreamFaint
                    )
                }
            }
        }
    } else {
        // Longer range: a compact month-style grid, weekday-aligned (Mon first), small squares.
        val cell = 20.dp
        val startOffset = dates.first().dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(startOffset) { null } + dates
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = CreamFaint, textAlign = TextAlign.Center, modifier = Modifier.width(cell))
                }
            }
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    week.forEach { d ->
                        if (d == null) Box(Modifier.size(cell))
                        else HeatCell(cell, colorFor(d), selected = d == selected) { onDayClick(d) }
                    }
                }
            }
        }
    }
}

/**
 * One day square. When it's the day you just tapped it springs out and takes a bright ring —
 * so the details that pop up are visibly tied to the square you actually touched.
 */
@Composable
private fun HeatCell(
    size: androidx.compose.ui.unit.Dp,
    color: Color?,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        } else {
            pop.animateTo(0f, tween(200))
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val lift = pop.value
    Box(
        Modifier
            .size(size)
            .scale(1f + 0.18f * lift)
            .clip(shape)
            .background(color ?: Color.White.copy(alpha = 0.05f))
            .then(if (lift > 0.01f) Modifier.border(2.dp, Cream.copy(alpha = lift.coerceIn(0f, 1f)), shape) else Modifier)
            .clickable(onClick = onClick)
    )
}

/** What shaped a single day's score: its macros/calories vs targets, opened by tapping a day. */
@Composable
private fun DayScoreDialog(
    day: LocalDate,
    row: DailyNutritionRow?,
    score: Int?,
    calGoal: Int,
    proteinTarget: Float,
    fatTarget: Float,
    carbTarget: Float,
    fiberTarget: Float,
    onJump: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateLabel = day.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onJump) { Text("Jump to this day") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(dateLabel) },
        text = {
            if (row == null || (row.calories <= 0f && row.protein <= 0f)) {
                Text("Nothing logged this day.", color = CreamMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (score != null) {
                        val tier = DayScore.tierOf(score)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$score", style = MaterialTheme.typography.headlineMedium, color = scoreColor(tier))
                            Spacer(Modifier.width(8.dp))
                            Text("/ 100 · ${tier.label}", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                        }
                    }
                    Text("What shaped the score:", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                    DayFactorRow("Calories", row.calories, calGoal.toFloat(), "kcal", overIsBad = true)
                    DayFactorRow("Protein", row.protein, proteinTarget, "g", overIsBad = false)
                    DayFactorRow("Fat", row.fat, fatTarget, "g", overIsBad = true)
                    DayFactorRow("Carbs", row.carbs, carbTarget, "g", overIsBad = true)
                    DayFactorRow("Fiber", row.fiber, fiberTarget, "g", overIsBad = false)
                }
            }
        }
    )
}

@Composable
private fun DayFactorRow(label: String, actual: Float, target: Float, unit: String, overIsBad: Boolean) {
    val over = target > 0f && actual > target
    val color = when {
        target <= 0f -> Cream
        overIsBad && over -> ScorePoor
        !overIsBad && actual >= target -> ScoreFair
        else -> Cream
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
        Text(
            text = "${actual.toInt()}${if (target > 0f) " / ${target.toInt()}" else ""} $unit",
            style = MaterialTheme.typography.bodyMedium, color = color
        )
    }
}

@Composable
private fun WaterChip(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
            Text(value, style = MaterialTheme.typography.bodySmall, color = Cream)
        }
    }
}

@Composable
private fun NotEnough() {
    Text(
        "Not enough data yet",
        style = MaterialTheme.typography.bodySmall,
        color = CreamFaint,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun AiReviewCard(
    title: String,
    subtitle: String,
    spotKey: String,
    spotlight: Set<String>,
    onToggleSpotlight: (String) -> Unit,
    highlight: Boolean = false,
    onOpen: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .cardSurface(spotKey in spotlight)
            .then(highlightBorder(highlight))
            .combinedClickable(onClick = onOpen, onLongClick = { onToggleSpotlight(spotKey) })
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = CreamMuted)
    }
}

@Composable
private fun WeightInputDialog(initialKg: Float?, onSave: (Float) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialKg?.let { "%.1f".format(it) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log weight") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { text.toFloatOrNull()?.let(onSave) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun enough(s: List<Float?>): Boolean = s.count { it != null } >= 2

/**
 * The 5-band best→worst colour used *only* by the logged-days widgets (heatmap card + summary tile):
 * blue (best) → green → yellow → orange → red (worst). Kept local so the Home ring and the other
 * score-tier colours keep their gold-based scheme.
 */
private fun loggedDayColor(score: Int): Color = when {
    score >= 80 -> LoggedBest
    score >= 65 -> LoggedGood
    score >= 50 -> LoggedMid
    score >= 35 -> LoggedLow
    else -> LoggedWorst
}

private fun avgLabel(series: List<Float?>, unit: String): String {
    val present = series.filterNotNull()
    if (present.isEmpty()) return ""
    val avg = present.sum() / present.size
    return "avg ${avg.toInt()}${if (unit.isEmpty()) "" else " $unit"}/day"
}
