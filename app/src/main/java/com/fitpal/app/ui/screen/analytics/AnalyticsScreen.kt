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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.local.dao.DailyMicros
import com.fitpal.app.data.local.dao.DailyNutritionRow
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.domain.DayScore
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.BarTrendChart
import com.fitpal.app.ui.component.fastingClockLabel
import com.fitpal.app.ui.component.fastingCountdownLabel
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.swipeNavigation
import com.fitpal.app.ui.component.MacroBar
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.component.TrendChart
import com.fitpal.app.ui.theme.AccentActivity
import com.fitpal.app.ui.theme.AccentGarden
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.FastingColor
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
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AnalyticsScreen(
    onOpenReview: (period: String, key: String) -> Unit = { _, _ -> },
    onSwipeToHome: () -> Unit = {},
    onSwipeToCollection: () -> Unit = {},
    onOpenDay: () -> Unit = {},
    onOpenCalorieDetail: (range: String, anchor: String) -> Unit = { _, _ -> },
    /** Open the Weight page. */
    onOpenWeight: () -> Unit = {},
    /** Open the Fasting page for a day. */
    onOpenFasting: (String) -> Unit = {},
    /** Open the Caffeine page for a day. */
    onOpenCaffeine: (String) -> Unit = {},
    /** Open the Food limits page for a day (and a limit's id, or null for the first). */
    onOpenLimits: (String, String?) -> Unit = { _, _ -> },
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val range by viewModel.range.collectAsStateWithLifecycle()
    val periodStart by viewModel.periodStart.collectAsStateWithLifecycle()
    val periodEnd by viewModel.periodEnd.collectAsStateWithLifecycle()
    val elapsedStart by viewModel.elapsedStart.collectAsStateWithLifecycle()
    val elapsedEnd by viewModel.elapsedEnd.collectAsStateWithLifecycle()
    val previousStart by viewModel.previousStart.collectAsStateWithLifecycle()
    val previousEnd by viewModel.previousEnd.collectAsStateWithLifecycle()
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
    val lifetimeMaintenance by viewModel.lifetimeMaintenance.collectAsStateWithLifecycle()
    val maintenanceBreakdown by viewModel.maintenanceBreakdown.collectAsStateWithLifecycle()
    val intakeDays by viewModel.intakeDays.collectAsStateWithLifecycle()
    val theoreticalBurnPerDay by viewModel.theoreticalBurnPerDay.collectAsStateWithLifecycle()
    val spotlight by viewModel.spotlight.collectAsStateWithLifecycle()
    val analyticsViews by viewModel.analyticsViews.collectAsStateWithLifecycle()
    // Which cards are flipped to "Lifetime", plus the all-time data they draw from.
    val lifetime by viewModel.lifetime.collectAsStateWithLifecycle()
    val lifetimeRows by viewModel.lifetimeRows.collectAsStateWithLifecycle()
    val lifetimeMicros by viewModel.lifetimeMicros.collectAsStateWithLifecycle()
    val widgetLayout by viewModel.widgetLayout.collectAsStateWithLifecycle()
    val fastingSchedule by viewModel.fastingSchedule.collectAsStateWithLifecycle()
    val fastingAdherence by viewModel.fastingAdherence.collectAsStateWithLifecycle()
    val fastingStreak by viewModel.fastingStreak.collectAsStateWithLifecycle()
    val fastingMealMinutes by viewModel.fastingMealMinutesByDate.collectAsStateWithLifecycle()
    val fastingGrace by viewModel.fastingGrace.collectAsStateWithLifecycle()
    val fastingLoggedDuringFastDays by viewModel.fastingLoggedDuringFastDays.collectAsStateWithLifecycle()
    val collapsed by viewModel.collapsed.collectAsStateWithLifecycle()
    val caffeineSettings by viewModel.caffeineSettings.collectAsStateWithLifecycle()
    val caffeineByDay by viewModel.caffeineByDay.collectAsStateWithLifecycle()
    val enabledLimits by viewModel.enabledLimits.collectAsStateWithLifecycle()
    val limitKcalByDay by viewModel.limitKcalByDay.collectAsStateWithLifecycle()
    val waterGoal by viewModel.waterGoal.collectAsStateWithLifecycle()
    val stepGoal by viewModel.stepGoal.collectAsStateWithLifecycle()
    // The maintenance-estimate card taps open a "how is this calculated?" explainer; its two rows
    // drill further into the day-by-day intake list and the full weigh-in list ("intake"/"weight").
    var showMaintenanceInfo by remember { mutableStateOf(false) }
    var maintenanceDrill by remember { mutableStateOf<String?>(null) }
    // A fasting square opens its own explainer (not the score popup); this holds which day.
    var selectedFastingDay by remember { mutableStateOf<LocalDate?>(null) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    // The day square you last tapped stays ringed while its dialog is open and for a moment
    // after it closes, so it's obvious which day you just looked at.
    var flashedDay by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(selectedDay, selectedFastingDay, flashedDay) {
        if (selectedDay == null && selectedFastingDay == null && flashedDay != null) {
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
    // AI weekly/monthly reviews follow the period you're viewing — except right after one ends, when
    // "this month" / "this week" means the one just finished (see ReviewPeriods).
    val weekReview = com.fitpal.app.domain.ReviewPeriods.week(anchor, today)
    val monthReview = com.fitpal.app.domain.ReviewPeriods.month(anchor, today)

    // One window for every card: the viewed period (the Mon–Sun week, or the 30 days ending at the
    // anchor). Charts span the whole period, with days still to come as gaps; averages use the days so
    // far (elapsedDates); prevDates is the period before, for the "vs last week" changes.
    val weekDates = generateSequence(periodStart) { it.plusDays(1) }.takeWhile { !it.isAfter(periodEnd) }.toList()
    val elapsedDates = generateSequence(elapsedStart) { it.plusDays(1) }.takeWhile { !it.isAfter(elapsedEnd) }.toList()
    val prevDates = generateSequence(previousStart) { it.plusDays(1) }.takeWhile { !it.isAfter(previousEnd) }.toList()
    val weekKeys = weekDates.map { it.format(iso) }
    val elapsedKeys = elapsedDates.map { it.format(iso) }
    val prevKeys = prevDates.map { it.format(iso) }
    // Every day we fetched data for (previous + viewed period), for lookup maps + per-day scores.
    val allDates = (prevDates + weekDates).distinct().sorted()

    val startLbl = periodStart.format(dayMonth)
    val endLbl = if (periodEnd == today) "now" else periodEnd.format(dayMonth)
    val periodLabel = "${periodStart.format(dayMonth)} – ${periodEnd.format(dayMonth)}"
    val previousLabel = if (range == AnalyticsRange.WEEK) "last week" else "the 30 days before"
    fun seriesOf(keys: List<String>, map: Map<String, Float>): List<Float?> = keys.map { map[it] }
    fun averageOf(keys: List<String>, map: Map<String, Float>): Float? =
        keys.mapNotNull { map[it] }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    val calMap = rows.associate { it.date to it.calories }
    val stepMap = stepRows.associate { it.date to it.steps.toFloat() }
    val stepBurnMap = stepRows.associate { it.date to it.caloriesBurned * (100 - stepTrim) / 100f }
    val exMap = exerciseBurn.associate { it.date to it.burned }
    val totalBurnMap = (stepBurnMap.keys + exMap.keys).associateWith { (stepBurnMap[it] ?: 0f) + (exMap[it] ?: 0f) }

    val calSeries = seriesOf(weekKeys, calMap)
    val stepSeries = seriesOf(weekKeys, stepMap)
    val burnSeries = seriesOf(weekKeys, totalBurnMap)
    // Net calories that actually count toward the budget = eaten − burned (steps + workouts).
    val netCalSeries: List<Float?> = weekKeys.map { key ->
        rows.find { it.date == key }?.let { (it.calories - (totalBurnMap[key] ?: 0f)).coerceAtLeast(0f) }
    }
    val waterMap = waterSplit.associate { it.date to (it.drinkWater + it.foodWater) }
    val waterSeries = seriesOf(weekKeys, waterMap)
    val elapsedWaterSplit = waterSplit.filter { it.date in elapsedKeys }
    val elapsedDays = elapsedDates.size.coerceAtLeast(1)
    val avgDrink = (elapsedWaterSplit.sumOf { it.drinkWater.toDouble() } / elapsedDays).toInt()
    val avgFood = (elapsedWaterSplit.sumOf { it.foodWater.toDouble() } / elapsedDays).toInt()
    val prevAvgWater = waterSplit.filter { it.date in prevKeys }
        .takeIf { it.isNotEmpty() }
        ?.let { (it.sumOf { r -> (r.drinkWater + r.foodWater).toDouble() } / prevDates.size.coerceAtLeast(1)).toFloat() }

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
    val scoreSeries: List<Float?> = weekKeys.map { scoreByDate[it]?.toFloat() }

    // The viewed period's rows (only days so far can have any) and the previous period's.
    val weekRows = rows.filter { it.date in weekKeys }
    val prevRows = rows.filter { it.date in prevKeys }

    // Every day ever logged, oldest → newest (the DAO already sorts) — the x-axis for Lifetime views.
    val lifetimeDates = lifetimeRows.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }

    // Tapping a bar only navigates for days that actually have data — future/empty days do nothing (#2).
    // Lifetime views reach outside the viewed window, so the all-time rows count as logged too.
    val loggedKeys = (rows + lifetimeRows).filter { it.calories > 0f || it.protein > 0f }.map { it.date }.toSet()
    val openDayIfLogged: (LocalDate) -> Unit = { d -> if (d.format(iso) in loggedKeys) openDay(d) }

    // ---- Takeaways and section summaries (docs/redesign/07): rules over the period, no AI ----
    val isCurrent = !periodEnd.isBefore(today)
    // Early in a period (every Monday) the card speaks about the one before, so it isn't empty.
    val fallback = isCurrent &&
        rows.count { it.date in elapsedKeys && it.calories > 0f } <= 1 &&
        rows.any { it.date in prevKeys && it.calories > 0f }
    fun takeawayDay(d: LocalDate): com.fitpal.app.domain.TakeawayDay {
        val k = d.format(iso)
        val r = rows.find { it.date == k }
        return com.fitpal.app.domain.TakeawayDay(
            date = d,
            kcal = r?.calories ?: 0f, protein = r?.protein ?: 0f, fat = r?.fat ?: 0f, carbs = r?.carbs ?: 0f, fiber = r?.fiber ?: 0f,
            burned = totalBurnMap[k] ?: 0f,
            waterMl = waterMap[k],
            steps = stepMap[k]?.toInt(),
            fastKept = if (!fastingSchedule.enabled) null else when (fastingAdherence[k]) {
                com.fitpal.app.domain.model.FastingDayResult.HELD -> true
                com.fitpal.app.domain.model.FastingDayResult.BROKE -> false
                null -> null
            },
            caffeineMg = if (!caffeineSettings.enabled) null else caffeineByDay[k] ?: if ((r?.calories ?: 0f) > 0f) 0f else null,
            limitKcal = enabledLimits.associate { com.fitpal.app.ui.component.ruleShortName(it.kind) to (limitKcalByDay[it.kind]?.get(k) ?: 0f) }
        )
    }
    val weekWord = range == AnalyticsRange.WEEK
    val viewedChange = com.fitpal.app.domain.WeightTrend.changeOver(weights, elapsedStart, elapsedEnd)
    val viewedInput = com.fitpal.app.domain.TakeawayInput(
        days = elapsedDates.map(::takeawayDay),
        previous = prevDates.map(::takeawayDay),
        periodPhrase = when {
            isCurrent -> if (weekWord) "this week" else "these 30 days"
            else -> if (weekWord) "that week" else "those 30 days"
        },
        previousLabel = previousLabel,
        calorieGoal = calGoal, proteinTarget = pT, fatTarget = fT, carbTarget = cT, fiberTarget = fibT,
        goal = fitnessGoal,
        waterGoalMl = waterGoal,
        stepGoal = stepGoal,
        caffeineLimitMg = if (caffeineSettings.enabled) caffeineSettings.dailyLimitMg else null,
        limits = enabledLimits.associate { com.fitpal.app.ui.component.ruleShortName(it.kind) to it.dailyLimitKcal },
        fastingOn = fastingSchedule.enabled,
        fastingStreak = fastingStreak,
        weightChangeKg = viewedChange,
        weightOnTrack = viewedChange?.let { com.fitpal.app.domain.WeightTrend.changeIsOnTrack(it, fitnessGoal) },
        lowNutrients = lowMicros(microsTotals, weekRows.count { it.calories > 0f })
    )
    val takeawayInput = if (!fallback) viewedInput else {
        val prevChange = com.fitpal.app.domain.WeightTrend.changeOver(weights, previousStart, previousEnd)
        viewedInput.copy(
            days = prevDates.map(::takeawayDay), previous = emptyList(),
            periodPhrase = if (weekWord) "last week" else "the 30 days before",
            weightChangeKg = prevChange,
            weightOnTrack = prevChange?.let { com.fitpal.app.domain.WeightTrend.changeIsOnTrack(it, fitnessGoal) },
            lowNutrients = emptyList()
        )
    }
    val highlights = remember(takeawayInput) { com.fitpal.app.domain.Takeaways.compute(takeawayInput) }
    val highlightsTitle = when {
        fallback -> if (weekWord) "Last week" else "The 30 days before"
        isCurrent -> if (weekWord) "This week" else "These 30 days"
        else -> if (weekWord) "Week of ${periodStart.format(dayMonth)}" else "30 days to ${periodEnd.format(dayMonth)}"
    }
    val summaries = remember(viewedInput) { com.fitpal.app.domain.Takeaways.sections(viewedInput) }
    fun sectionSummary(id: String): String = when (id) {
        com.fitpal.app.domain.model.ScreenWidgets.SECTION_ENERGY -> summaries.energy
        com.fitpal.app.domain.model.ScreenWidgets.SECTION_NUTRITION -> summaries.nutrition
        com.fitpal.app.domain.model.ScreenWidgets.SECTION_BODY -> summaries.body
        else -> summaries.habits
    }

    // Cards that only show with data, or with their feature on (off = invisible).
    fun cardShown(key: String): Boolean = when (key) {
        "balance" -> weekRows.isNotEmpty() && calGoal > 0
        "maintenance" -> lifetimeMaintenance != null
        "averages" -> weekRows.isNotEmpty()
        "fasting" -> fastingSchedule.enabled
        "caffeine" -> caffeineSettings.enabled
        "limits" -> enabledLimits.isNotEmpty()
        else -> true
    }
    val sectionCards: List<Pair<Pair<String, String>, List<com.fitpal.app.domain.model.ResolvedCard>>> =
        com.fitpal.app.domain.model.ScreenWidgets.analyticsSections.map { sec ->
            sec to widgetLayout.filter { it.def.section == sec.first && it.enabled && cardShown(it.key) }
        }.filter { it.second.isNotEmpty() }
    val tilesCard = widgetLayout.firstOrNull { it.key == "tiles" && it.enabled }
    val tailCards = widgetLayout.filter { it.def.section == null && it.key != "tiles" && it.enabled }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var highlight by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(highlight) { if (highlight != null) { delay(1300); highlight = null } }
    // The positional order of rendered LazyColumn items — header, pills and takeaways, then the
    // tiles, each section's header and (when open) its cards, then the AI reviews. Must mirror the
    // LazyColumn below; drives the jump-to-card scrolling from the takeaways and tiles.
    val itemKeys = buildList {
        add("header"); add("pills")
        if (highlights.isNotEmpty()) add("highlights")
        if (tilesCard != null) add("tiles")
        sectionCards.forEach { (sec, cards) ->
            add("sec_${sec.first}")
            if (sec.first !in collapsed) cards.forEach { add(it.key) }
        }
        tailCards.forEach { w ->
            if (w.key == "ai") { add("aihdr"); add("aiToday"); add("aiWeek"); add("aiMonth") } else add(w.key)
        }
    }
    val latestItemKeys by androidx.compose.runtime.rememberUpdatedState(itemKeys)
    // Opened at a section (Home's streak with the Trail off → Habits): unfold it and scroll there.
    val pendingSection by viewModel.pendingSection.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSection) {
        val id = pendingSection ?: return@LaunchedEffect
        viewModel.consumeFocus()
        viewModel.expandSection(id)
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        val i = latestItemKeys.indexOf("sec_$id")
        if (i >= 0) listState.animateScrollToItem(i)
    }
    fun jumpTo(key: String) {
        // A card in a folded section opens its section first, then scrolls once that's drawn.
        widgetLayout.firstOrNull { it.key == key }?.def?.section?.let { viewModel.expandSection(it) }
        scope.launch {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            val i = latestItemKeys.indexOf(key)
            if (i >= 0) listState.animateScrollToItem(i)
        }
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
            verticalArrangement = Arrangement.spacedBy(com.fitpal.app.ui.theme.CardGap + 2.dp)
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

            if (highlights.isNotEmpty()) item {
                HighlightsCard(highlightsTitle, highlights, onOpen = ::jumpTo)
            }

            // One card, if it's on and has something to show. Where it goes is decided below.
            val renderCard: androidx.compose.foundation.lazy.LazyListScope.(com.fitpal.app.domain.model.ResolvedCard) -> Unit = renderCard@{ widget ->
                if (!widget.enabled || !cardShown(widget.key)) return@renderCard
                when (widget.key) {
                    // ---- Summary tiles ----
                    "tiles" -> item {
                        val avgCal = averageOf(weekKeys, calMap)
                        val avgSteps = averageOf(weekKeys, stepMap)
                        // Avg score + logged days are merged into one compact tile, both over the week.
                        val weekScores = weekDates.mapNotNull { scoreByDate[it.format(iso)] }
                        val avgScore = weekScores.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }
                        // Same calculation as the weight card's change chip, so the two always agree.
                        val wChange = com.fitpal.app.domain.WeightTrend.changeOver(weights, elapsedStart, elapsedEnd)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Tiles in a row share a height, whatever their text.
                            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(
                                    Modifier.weight(1f).fillMaxHeight(), "Avg calories", avgCal?.let { "%,d".format(it.roundToInt()) } ?: "—", "kcal/day", GoldLight,
                                    "tile_cal", spotlight, viewModel::toggleSpotlight,
                                    change = percentChange(avgCal, averageOf(prevKeys, calMap), previousLabel, higherIsBetter = null)
                                ) { jumpTo("calories") }
                                StatTile(
                                    Modifier.weight(1f).fillMaxHeight(), "Avg steps", avgSteps?.let { "%,d".format(it.roundToInt()) } ?: "—", "per day", AccentActivity,
                                    "tile_steps", spotlight, viewModel::toggleSpotlight,
                                    change = percentChange(avgSteps, averageOf(prevKeys, stepMap), previousLabel, higherIsBetter = true)
                                ) { jumpTo("activity") }
                            }
                            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(
                                    Modifier.weight(1f).fillMaxHeight(), "Weight change",
                                    wChange?.let { (if (it >= 0) "+" else "") + "%.1f".format(it) } ?: "—", "kg", GoldLight,
                                    "tile_weight", spotlight, viewModel::toggleSpotlight
                                ) { jumpTo("weight") }
                                LoggedScoreTile(
                                    Modifier.weight(1f).fillMaxHeight(),
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
                        // Per-day net, this period vs the one before: sums over different numbers of
                        // logged days can't be compared, daily averages can.
                        val prevNet = prevRows.takeIf { it.isNotEmpty() }
                            ?.let { rs -> rs.sumOf { (it.calories - calGoal - (totalBurnMap[it.date] ?: 0f)).toDouble() } / rs.size }
                        val dailyChange = prevNet?.let { (net.toDouble() / weekRows.size - it).roundToInt() }
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .cardSurface("balance" in spotlight)
                                .then(highlightBorder(highlight == "balance"))
                                .combinedClickable(
                                    onClick = { onOpenCalorieDetail(range.name, anchor.format(iso)) },
                                    onLongClick = { viewModel.toggleSpotlight("balance") }
                                )
                                .padding(16.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Calorie balance", style = MaterialTheme.typography.titleMedium, color = Cream)
                                com.fitpal.app.ui.component.InfoButton(
                                    title = "Calorie balance",
                                    text = "What you ate minus your goal, across the days you logged. Steps and workouts add to " +
                                        "each day's budget, the same as on Home."
                                )
                                Spacer(Modifier.weight(1f))
                                Text("›", style = MaterialTheme.typography.titleLarge, color = CreamMuted)
                            }
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
                            dailyChange?.let { d ->
                                Text(
                                    text = when {
                                        kotlin.math.abs(d) < 25 -> "About the same each day as $previousLabel"
                                        else -> "${if (d > 0) "+" else "−"}${"%,d".format(kotlin.math.abs(d))} kcal a day vs $previousLabel"
                                    },
                                    style = MaterialTheme.typography.labelSmall, color = CreamFaint
                                )
                            }
                        }
                    }

                    // ---- Logged-days heatmap ----
                    "heatmap" -> item {
                        SwitchableChartCard("Logged days", listOf("Calendar", "Legend"), viewState = analyticsViews, onSetView = viewModel::setView, highlight = highlight == "heatmap", spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight) { v ->
                            if (v == 0) {
                                Text("${weekRows.size} of ${weekDates.size} logged", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
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
                                    LegendRow(com.fitpal.app.ui.theme.filmColor(0.08f), "Not logged")
                                }
                            }
                        }
                    }

                    // ---- Fasting: which days the eating window was kept, + a streak ----
                    "fasting" -> item {
                        val judged = weekDates.mapNotNull { fastingAdherence[it.format(iso)] }
                        val kept = judged.count { it == com.fitpal.app.domain.model.FastingDayResult.HELD }
                        ChartCard(
                            "Fasting",
                            if (judged.isEmpty()) "No days to judge yet"
                            else "$kept of ${judged.size} days kept" + (if (fastingStreak > 0) " · $fastingStreak-day streak" else ""),
                            highlight = highlight == "fasting",
                            info = "A day is kept when everything you ate fell inside your eating window. Drinks your " +
                                "drink rule allows don't break it, and an \u201cI ate earlier\u201d pass keeps the day.",
                            spotlight = spotlight,
                            onToggleSpotlight = viewModel::toggleSpotlight,
                            onClick = { if (fastingSchedule.enabled) onOpenFasting(today.toString()) }
                        ) {
                            if (!fastingSchedule.enabled) {
                                Text(
                                    "Turn on fasting in Settings to see which days you kept your fast.",
                                    style = MaterialTheme.typography.bodySmall, color = CreamMuted
                                )
                            } else {
                                val held = weekDates.count { fastingAdherence[it.format(iso)] == com.fitpal.app.domain.model.FastingDayResult.HELD }
                                val broke = weekDates.count { fastingAdherence[it.format(iso)] == com.fitpal.app.domain.model.FastingDayResult.BROKE }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = if (fastingStreak > 0) "$fastingStreak" else "—",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = FastingColor
                                    )
                                    Column {
                                        Text("day streak", style = MaterialTheme.typography.labelMedium, color = Cream)
                                        Text(
                                            "$held kept · $broke broken this ${range.label.lowercase()}",
                                            style = MaterialTheme.typography.labelSmall, color = CreamMuted
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                LoggedHeatmap(
                                    dates = weekDates,
                                    colorFor = { d ->
                                        when (fastingAdherence[d.format(iso)]) {
                                            com.fitpal.app.domain.model.FastingDayResult.HELD -> AccentGarden
                                            com.fitpal.app.domain.model.FastingDayResult.BROKE -> ScorePoor
                                            else -> null
                                        }
                                    },
                                    selected = flashedDay,
                                    onDayClick = { d -> selectedFastingDay = d; flashedDay = d }
                                )
                            }
                        }
                    }

                    // ---- Balance score ----
                    "score" -> item {
                        androidx.compose.runtime.CompositionLocalProvider(com.fitpal.app.ui.component.LocalChartScale provides widget.chartScale) {
                        val scored = weekDates.mapNotNull { d -> scoreByDate[d.format(iso)]?.let { d to it } }
                        val scoreLine = if (scored.isEmpty()) "" else
                            "Avg ${scored.sumOf { it.second } / scored.size} · best ${scored.maxBy { it.second }.first.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())}"
                        SwitchableChartCard(
                            "Balance score", listOf("Trend", "Breakdown"), viewState = analyticsViews, onSetView = viewModel::setView,
                            highlight = highlight == "score", spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight,
                            subtitle = scoreLine,
                            info = "Each day scored out of 100 on how close calories, protein, fat, carbs and fibre came to your targets."
                        ) { v ->
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
                    }

                    // ---- Calories (net of what you burned from steps + workouts) ----
                    "calories" -> item {
                        androidx.compose.runtime.CompositionLocalProvider(com.fitpal.app.ui.component.LocalChartScale provides widget.chartScale) {
                        val netDays = netCalSeries.filterNotNull()
                        val calLine = if (netDays.isEmpty()) "" else buildString {
                            append("Avg ${"%,d".format((netDays.sum() / netDays.size).roundToInt())} kcal")
                            if (calGoal > 0) {
                                val over = netDays.count { it > calGoal }
                                append(" · ${if (over == 0) "no days" else if (over == 1) "1 day" else "$over days"} over goal")
                            }
                        }
                        SwitchableChartCard(
                            "Calories", listOf("Trend", "Vs goal"), viewState = analyticsViews, onSetView = viewModel::setView,
                            highlight = highlight == "calories", spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight,
                            subtitle = calLine,
                            info = "What you ate each day, net of the calories your steps and workouts burned, against your goal."
                        ) { v ->
                            when {
                                !enough(netCalSeries) -> NotEnough()
                                v == 0 -> TrendChart(
                                    values = netCalSeries, accent = Gold,
                                    target = calGoal.takeIf { it > 0 }?.toFloat(),
                                    startLabel = startLbl, endLabel = endLbl
                                )
                                else -> BarTrendChart(
                                    values = netCalSeries.map { it ?: 0f },
                                    accent = ScoreFair,
                                    target = calGoal.takeIf { it > 0 }?.toFloat(),
                                    overColor = ScorePoor,
                                    startLabel = startLbl, endLabel = endLbl,
                                    labels = weekDates.map { it.dayOfMonth.toString() },
                                    highlightIndex = weekDates.indexOfFirst { it == today }.takeIf { it >= 0 },
                                    onBarClick = { i -> weekDates.getOrNull(i)?.let(openDayIfLogged) }
                                )
                            }
                        }
                    }
                    }

                    // ---- Macro balance ----
                    "macros" -> item {
                        // "Lifetime" swaps the viewed week/month for every day ever logged.
                        val allTime = "macros" in lifetime
                        val macroRows = if (allTime) lifetimeRows else weekRows
                        val macroDates = if (allTime) lifetimeDates else weekDates
                        val macroLine = run {
                            val p = macroRows.sumOf { it.protein.toDouble() } * 4
                            val f = macroRows.sumOf { it.fat.toDouble() } * 9
                            val c = macroRows.sumOf { it.carbs.toDouble() } * 4
                            val total = p + f + c
                            if (total <= 0.0) "" else
                                "Protein ${(p / total * 100).roundToInt()}% · fat ${(f / total * 100).roundToInt()}% · carbs ${(c / total * 100).roundToInt()}% of calories"
                        }
                        SwitchableChartCard(
                            "Macro balance", listOf("Average", "Over time"),
                            viewState = analyticsViews, onSetView = viewModel::setView,
                            highlight = highlight == "macros",
                            subtitle = macroLine,
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
                        val low = if (allTime) lowMicros(lifetimeMicros, lifetimeRows.size) else lowMicros(microsTotals, weekRows.size)
                        val hasMicros = if (allTime) lifetimeMicros != null && lifetimeRows.isNotEmpty() else microsTotals != null && weekRows.isNotEmpty()
                        ChartCard(
                            "Vitamins & minerals",
                            when {
                                !hasMicros -> ""
                                low.isEmpty() -> "Most essentials covered"
                                else -> "Low on " + low.take(2).joinToString(" and ") { it.replaceFirstChar { c -> c.lowercase() }.replace(Regex("^vitamin (.*)")) { m -> "vitamin " + m.groupValues[1].uppercase() } }
                            },
                            highlight = highlight == "micros",
                            info = if (allTime) "Your all-time daily average of each vitamin and mineral, against an adult's daily target."
                            else "Your daily average of each vitamin and mineral over the days you logged, against an adult's daily target.",
                            spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight,
                            trailing = { LifetimePill(allTime) { viewModel.toggleLifetime("micros") } }
                        ) {
                            if (allTime) MicronutrientAnalysis(lifetimeMicros, lifetimeRows.size)
                            else MicronutrientAnalysis(microsTotals, weekRows.size)
                        }
                    }

                    // ---- Activity (steps + total burned) ----
                    "activity" -> item {
                        androidx.compose.runtime.CompositionLocalProvider(com.fitpal.app.ui.component.LocalChartScale provides widget.chartScale) {
                        val walked = weekDates.mapNotNull { d -> stepMap[d.format(iso)]?.takeIf { it > 0f }?.let { d to it } }
                        val activityLine = if (walked.isEmpty()) "" else
                            "Avg ${"%,d".format((walked.sumOf { it.second.toDouble() } / walked.size).roundToInt())} steps · most on " +
                                walked.maxBy { it.second }.first.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                        ChartCard(
                            "Activity", activityLine, highlight = highlight == "activity", spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight,
                            info = "Your steps each day, and everything you burned on top of resting: steps plus logged workouts."
                        ) {
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
                    }

                    // ---- Water ----
                    "water" -> item {
                        androidx.compose.runtime.CompositionLocalProvider(com.fitpal.app.ui.component.LocalChartScale provides widget.chartScale) {
                        val waterDays = elapsedKeys.mapNotNull { waterMap[it]?.takeIf { ml -> ml > 0f } }
                        val waterLine = if (waterDays.isEmpty() || waterGoal <= 0) "" else {
                            val pct = (waterDays.average() / waterGoal * 100).roundToInt()
                            val short = waterDays.count { it < waterGoal }
                            "$pct% of your goal on average · ${if (short == 0) "no days" else if (short == 1) "1 day" else "$short days"} short"
                        }
                        SwitchableChartCard(
                            "Water", listOf("Total", "By source"), viewState = analyticsViews, onSetView = viewModel::setView,
                            highlight = highlight == "water", spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight,
                            subtitle = waterLine,
                            info = "Water from drinks and from the food you ate, against your daily water goal."
                        ) { v ->
                            if (v == 0) {
                                if (enough(waterSeries)) {
                                    TrendChart(values = waterSeries, accent = AccentTrends, startLabel = startLbl, endLabel = endLbl)
                                } else NotEnough()
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    WaterChip(AccentTrends, "Drinks", "$avgDrink ml/day")
                                    WaterChip(WaterFromFood, "From food", "$avgFood ml/day")
                                }
                                if (elapsedWaterSplit.isNotEmpty()) {
                                    percentChange((avgDrink + avgFood).toFloat(), prevAvgWater, previousLabel, higherIsBetter = true)?.let {
                                        Spacer(Modifier.height(6.dp))
                                        PeriodChangeText(it)
                                    }
                                }
                            } else {
                                val drinkSeries = seriesOf(weekKeys, waterSplit.associate { it.date to it.drinkWater })
                                val foodSeries = seriesOf(weekKeys, waterSplit.associate { it.date to it.foodWater })
                                Text("Clear drinks", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                                Spacer(Modifier.height(4.dp))
                                if (enough(drinkSeries)) {
                                    TrendChart(values = drinkSeries, accent = AccentTrends, startLabel = startLbl, endLabel = endLbl, height = 120.dp)
                                } else NotEnough()
                                Spacer(Modifier.height(14.dp))
                                Text("From food", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                                Spacer(Modifier.height(4.dp))
                                if (enough(foodSeries)) {
                                    TrendChart(values = foodSeries, accent = WaterFromFood, startLabel = startLbl, endLabel = endLbl, height = 120.dp)
                                } else NotEnough()
                            }
                        }
                    }
                    }

                    // ---- Weight (current + trend grouped) ----
                    "weight" -> item {
                        androidx.compose.runtime.CompositionLocalProvider(com.fitpal.app.ui.component.LocalChartScale provides widget.chartScale) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .cardSurface("Weight" in spotlight)
                                .then(highlightBorder(highlight == "weight"))
                                .combinedClickable(
                                    onClick = onOpenWeight,
                                    onLongClick = { viewModel.toggleSpotlight("Weight") }
                                )
                                .padding(16.dp)
                        ) {
                            // How much it moved over what you're viewing: this week / 30 days, or
                            // since your very first weigh-in when the card is on Lifetime.
                            val weightDelta = if ("weight" in lifetime) {
                                com.fitpal.app.domain.WeightTrend.lifetimeChange(weights)
                            } else {
                                com.fitpal.app.domain.WeightTrend.changeOver(weights, elapsedStart, elapsedEnd)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Weight", style = MaterialTheme.typography.titleMedium, color = Cream)
                                    Text(
                                        text = when {
                                            latestWeight == null -> "No weigh-ins yet"
                                            weightDelta != null && "weight" !in lifetime -> {
                                                val track = com.fitpal.app.domain.WeightTrend.changeIsOnTrack(weightDelta, fitnessGoal)
                                                (if (weightDelta > 0) "+" else if (weightDelta < 0) "−" else "") +
                                                    "%.1f kg this ${if (weekWord) "week" else "30 days"}".format(kotlin.math.abs(weightDelta)) +
                                                    when (track) { true -> " · on pace for your goal"; false -> " · off pace for your goal"; null -> "" }
                                            }
                                            else -> "Last logged ${runCatching { LocalDate.parse(latestWeight!!.date).format(dayMonth) }.getOrDefault(latestWeight!!.date)}"
                                        },
                                        style = MaterialTheme.typography.bodySmall, color = CreamMuted
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (latestWeight != null) "${"%.1f".format(latestWeight!!.weightKg)} kg" else "— kg",
                                        style = MaterialTheme.typography.titleLarge, color = GoldLight
                                    )
                                    weightDelta?.let { delta ->
                                        Spacer(Modifier.height(4.dp))
                                        WeightChangeChip(delta, fitnessGoal)
                                    }
                                }
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
                                seriesOf(weekKeys, byDate)
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
                    }

                    // ---- Implied maintenance (insight only — does not change the budget) ----
                    // Always from the whole history, so it's the same number in every range.
                    "maintenance" -> lifetimeMaintenance?.let { maint -> item {
                        ChartCard(
                            "Maintenance estimate", "",
                            highlight = highlight == "maintenance",
                            spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight,
                            onClick = { showMaintenanceInfo = true },
                            trailing = {
                                com.fitpal.app.ui.component.InfoButton(
                                    title = "Maintenance estimate",
                                    text = "What all your logged days and weigh-ins suggest you actually burn. " +
                                        "It's a rough estimate: it can still read high while you're losing weight quickly, " +
                                        "and settles as your weight steadies. It never changes your calorie goal."
                                )
                            }
                        ) {
                            Text(
                                "≈ ${"%,d".format(maint)} kcal/day",
                                style = MaterialTheme.typography.headlineSmall, color = GoldLight
                            )
                            Text(
                                if (calGoal > 0) "What you really burn, from your history · your goal is ${"%,d".format(calGoal)}"
                                else "What you really burn, from your history",
                                style = MaterialTheme.typography.bodySmall, color = CreamMuted
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "How it's worked out ›",
                                style = MaterialTheme.typography.labelMedium, color = GoldLight
                            )
                        }
                    } }

                    // ---- Averages ----
                    "averages" -> if (weekRows.isNotEmpty()) item {
                        ChartCard("Averages", "", spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight) {
                            // Average over EATEN days only — a water-only day logs as a 0-kcal row and
                            // would otherwise drag every average down (same rule as the maintenance estimate).
                            val eaten = weekRows.filter { it.calories > 0f }
                            val n = eaten.size.coerceAtLeast(1)
                            listOf(
                                "Calories" to "${(eaten.sumOf { it.calories.toDouble() } / n).toInt()} kcal",
                                "Protein" to "${(eaten.sumOf { it.protein.toDouble() } / n).toInt()}g",
                                "Fat" to "${(eaten.sumOf { it.fat.toDouble() } / n).toInt()}g",
                                "Carbs" to "${(eaten.sumOf { it.carbs.toDouble() } / n).toInt()}g",
                                "Fiber" to "${(eaten.sumOf { it.fiber.toDouble() } / n).toInt()}g"
                            ).forEach { (label, value) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), Arrangement.SpaceBetween) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                                    Text(value, style = MaterialTheme.typography.bodyMedium, color = Cream)
                                }
                            }
                        }
                    }

                    // ---- Caffeine (Habits): each day against the daily limit ----
                    "caffeine" -> item {
                        val limit = caffeineSettings.dailyLimitMg.takeIf { it > 0 }
                        val values = weekDates.map { d -> if (d.isAfter(today)) null else caffeineByDay[d.format(iso)] ?: 0f }
                        val had = elapsedKeys.mapNotNull { caffeineByDay[it] }
                        val over = if (limit == null) 0 else had.count { it > limit }
                        ChartCard(
                            "Caffeine",
                            if (had.isEmpty()) "None logged" else buildString {
                                append("Avg ${(had.sum() / elapsedDates.size.coerceAtLeast(1)).roundToInt()} mg a day")
                                if (limit != null) append(" · ${if (over == 0) "never" else if (over == 1) "1 day" else "$over days"} over your limit")
                            },
                            highlight = highlight == "caffeine",
                            info = "Caffeine from everything you logged each day, against your daily limit (Settings → Caffeine).",
                            spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight,
                            onClick = { onOpenCaffeine((if (periodEnd.isAfter(today)) today else periodEnd).toString()) }
                        ) {
                            LimitBars(
                                dates = weekDates, values = values, limit = limit?.toFloat(),
                                color = com.fitpal.app.ui.theme.CaffeineColor, unit = "mg", height = 130.dp,
                                onDay = { d -> onOpenCaffeine(d.toString()) }
                            )
                        }
                    }

                    // ---- Food limits (Habits): each limit that's on, its days against its cap ----
                    "limits" -> item {
                        val overall = enabledLimits.sumOf { rule ->
                            elapsedKeys.count { (limitKcalByDay[rule.kind]?.get(it) ?: 0f) > rule.dailyLimitKcal }
                        }
                        ChartCard(
                            "Food limits",
                            if (overall == 0) "Within every cap" else "Over a cap ${if (overall == 1) "once" else "$overall times"}",
                            highlight = highlight == "limits",
                            info = "The kcal from each food limit's foods per day — only the part of a dish that counts — against its cap.",
                            spotlight = spotlight, onToggleSpotlight = viewModel::toggleSpotlight,
                            onClick = { onOpenLimits((if (periodEnd.isAfter(today)) today else periodEnd).toString(), null) }
                        ) {
                            enabledLimits.forEach { rule ->
                                val byDay = limitKcalByDay[rule.kind].orEmpty()
                                LimitRow(
                                    icon = com.fitpal.app.ui.component.ruleIcon(rule.kind),
                                    name = com.fitpal.app.ui.component.ruleShortName(rule.kind),
                                    color = com.fitpal.app.ui.component.ruleColor(rule.kind),
                                    overDays = elapsedKeys.count { (byDay[it] ?: 0f) > rule.dailyLimitKcal },
                                    onOpen = { onOpenLimits((if (periodEnd.isAfter(today)) today else periodEnd).toString(), rule.kind.id) }
                                ) {
                                    LimitBars(
                                        dates = weekDates,
                                        values = weekDates.map { d -> if (d.isAfter(today)) null else byDay[d.format(iso)] ?: 0f },
                                        limit = rule.dailyLimitKcal.toFloat().takeIf { it > 0f },
                                        color = com.fitpal.app.ui.component.ruleColor(rule.kind), unit = "kcal", height = 90.dp,
                                        onDay = { d -> onOpenLimits(d.toString(), rule.kind.id) }
                                    )
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
                        item { AiReviewCard(weekReview.title, weekReview.subtitle, "ai_week", spotlight, viewModel::toggleSpotlight, highlight = highlight == "aihdr") { onOpenReview("weekly", weekReview.key) } }
                        item { AiReviewCard(monthReview.title, monthReview.subtitle, "ai_month", spotlight, viewModel::toggleSpotlight, highlight = highlight == "aihdr") { onOpenReview("monthly", monthReview.key) } }
                    }
                }
            }

            // The tiles (off unless picked), then the four sections in their fixed order — each with
            // its cards in the user's order, folded away when collapsed — then the AI reviews.
            tilesCard?.let { renderCard(it) }
            sectionCards.forEach { (sec, cards) ->
                val (id, title) = sec
                item {
                    AnalyticsSectionHeader(title, sectionSummary(id), collapsed = id in collapsed) { viewModel.toggleSection(id) }
                }
                if (id !in collapsed) cards.forEach { renderCard(it) }
            }
            tailCards.forEach { renderCard(it) }
        }
    }

    if (showMaintenanceInfo) {
        maintenanceBreakdown?.let { est ->
            MaintenanceDialog(
                estimate = est,
                calGoal = calGoal,
                theoreticalBurnPerDay = theoreticalBurnPerDay,
                onShowIntake = { showMaintenanceInfo = false; maintenanceDrill = "intake" },
                onShowWeight = { showMaintenanceInfo = false; maintenanceDrill = "weight" },
                onDismiss = { showMaintenanceInfo = false }
            )
        }
    }

    when (maintenanceDrill) {
        "intake" -> maintenanceBreakdown?.let { est ->
            IntakeDetailDialog(
                days = intakeDays,
                average = est.avgIntakeKcal,
                onBack = { maintenanceDrill = null; showMaintenanceInfo = true },
                onDismiss = { maintenanceDrill = null }
            )
        }
        "weight" -> maintenanceBreakdown?.let { est ->
            WeightTrendDetailDialog(
                weights = weights,
                ratePerWeekKg = est.ratePerWeekKg,
                onBack = { maintenanceDrill = null; showMaintenanceInfo = true },
                onDismiss = { maintenanceDrill = null }
            )
        }
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

    selectedFastingDay?.let { day ->
        FastingDayDialog(
            day = day,
            schedule = fastingSchedule,
            mealMinutes = fastingMealMinutes[day.format(iso)],
            graceMinute = fastingGrace[day.format(iso)],
            loggedDuringFast = day.format(iso) in fastingLoggedDuringFastDays,
            onJump = { openDay(day); selectedFastingDay = null },
            onDismiss = { selectedFastingDay = null }
        )
    }
}
