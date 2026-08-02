package com.fitpal.app.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fitpal.app.data.local.dao.DailyMicros
import com.fitpal.app.data.local.entity.ExerciseEntryEntity
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.domain.DayScore
import com.fitpal.app.domain.HomeCoachTip
import com.fitpal.app.domain.model.MealTypes
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.ui.component.CalorieRing
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.GlassCapsule
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.MacroRingsRow
import com.fitpal.app.ui.component.MealTypeSelector
import com.fitpal.app.ui.component.MicronutrientBars
import com.fitpal.app.ui.theme.AccentGarden
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.InkBlack
import com.fitpal.app.ui.theme.ProteinColor
import com.fitpal.app.ui.theme.ScorePoor
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onAddFood: () -> Unit,
    onEntryClick: (Long) -> Unit = {},
    onGroupClick: (Long) -> Unit = {},
    onOpenReview: (period: String, key: String) -> Unit = { _, _ -> },
    onOpenTrail: () -> Unit = {},
    onLogExercise: () -> Unit = {},
    onExerciseClick: (Long) -> Unit = {},
    onOpenWater: (String) -> Unit = {},
    onSwipeToNextScreen: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val nutrition by viewModel.dailyNutrition.collectAsStateWithLifecycle()
    val sections by viewModel.mealSections.collectAsStateWithLifecycle()
    val water by viewModel.dailyWater.collectAsStateWithLifecycle()
    val micros by viewModel.dailyMicros.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val targets by viewModel.dailyTargets.collectAsStateWithLifecycle()
    val dailySteps by viewModel.dailySteps.collectAsStateWithLifecycle()
    val manualSteps by viewModel.manualSteps.collectAsStateWithLifecycle()
    val syncedSteps by viewModel.syncedSteps.collectAsStateWithLifecycle()
    val stepSources by viewModel.stepSources.collectAsStateWithLifecycle()
    val dailyStreak by viewModel.dailyStreak.collectAsStateWithLifecycle()
    val trailPending by viewModel.trailPending.collectAsStateWithLifecycle()
    val exerciseCalories by viewModel.exerciseCalories.collectAsStateWithLifecycle()
    val exerciseEntries by viewModel.exerciseEntries.collectAsStateWithLifecycle()
    val latestWeight by viewModel.latestWeight.collectAsStateWithLifecycle()
    val stepCalories by viewModel.stepCalories.collectAsStateWithLifecycle()
    val widgetLayout by viewModel.widgetLayout.collectAsStateWithLifecycle()
    val waterPresets by viewModel.waterPresets.collectAsStateWithLifecycle()
    val waterGoal by viewModel.waterGoal.collectAsStateWithLifecycle()
    val compactEmptyMeals by viewModel.compactEmptyMeals.collectAsStateWithLifecycle()
    val fitnessGoal by viewModel.fitnessGoal.collectAsStateWithLifecycle()
    val weeklyBalanceKcal by viewModel.weeklyBalanceKcal.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.clearPendingMealType()
        viewModel.syncHealthConnect()
    }

    var editingItem by remember { mutableStateOf<MealLogItemEntity?>(null) }
    var editingType by remember { mutableStateOf(MealTypes.BREAKFAST) }
    var showCalendar by remember { mutableStateOf(false) }
    var showStepDialog by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }

    // Calories burned are added back to the day's eating budget: 50% of (rougher) exercise burn,
    // and the step-calorie estimate in full (it's already trimmed by the user's step setting).
    val baseGoal = targets?.calories ?: 0
    // Exercise counts in full — the AI already discounts rest/breaks when it estimates the active
    // time, so no extra haircut here. (The step-calorie trim setting applies only to steps.)
    val effectiveGoal = baseGoal + exerciseCalories.toInt() + stepCalories.toInt()

    val score = DayScore.compute(
        calories = nutrition.calories,
        calorieGoal = effectiveGoal,
        protein = nutrition.protein, proteinTarget = targets?.proteinG?.toFloat() ?: 0f,
        fat = nutrition.fat, fatTarget = targets?.fatG?.toFloat() ?: 0f,
        carbs = nutrition.carbs, carbTarget = targets?.carbsG?.toFloat() ?: 0f,
        fiber = nutrition.fiber, fiberTarget = targets?.fiberG?.toFloat() ?: 0f,
        microCoverage = microCoverage(micros)
    )

    var swipeDragX by remember { mutableFloatStateOf(0f) }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = {
                if (targetState.isAfter(initialState)) {
                    slideInHorizontally { w -> w } togetherWith slideOutHorizontally { w -> -w }
                } else {
                    slideInHorizontally { w -> -w } togetherWith slideOutHorizontally { w -> w }
                }
            },
            label = "daySwipe"
        ) { _ ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeDragX > 150f) {
                                    viewModel.goPrevDay()
                                } else if (swipeDragX < -150f) {
                                    // Past today doesn't exist — swipe forward there moves to the next screen.
                                    if (selectedDate == LocalDate.now()) onSwipeToNextScreen()
                                    else viewModel.goNextDay()
                                }
                                swipeDragX = 0f
                            },
                            onDragCancel = { swipeDragX = 0f },
                            onHorizontalDrag = { _, dragAmount -> swipeDragX += dragAmount }
                        )
                    },
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    val nowHour = java.time.LocalTime.now().hour
                    HomeHeader(
                        date = selectedDate,
                        status = HomeCoachTip.line(
                            HomeCoachTip.Input(
                                isToday = selectedDate == LocalDate.now(),
                                hasGoal = effectiveGoal > 0,
                                hour = nowHour,
                                dayOfWeek = selectedDate.dayOfWeek,
                                consumed = nutrition.calories.toInt(),
                                goal = effectiveGoal,
                                leftKcal = effectiveGoal - nutrition.calories.toInt(),
                                goalType = fitnessGoal,
                                protein = nutrition.protein.toInt(),
                                proteinTarget = targets?.proteinG ?: 0,
                                waterMl = water.toInt(),
                                waterGoalMl = waterGoal,
                                tier = score.tier,
                                streak = dailyStreak,
                                weeklyBalanceKcal = weeklyBalanceKcal,
                                seed = LocalDate.now().toEpochDay() * 31 + nowHour
                            )
                        ),
                        streak = dailyStreak,
                        onPickDate = { showCalendar = true },
                        onToday = viewModel::goToday,
                        onPrevDay = viewModel::goPrevDay,
                        onNextDay = viewModel::goNextDay,
                        onOpenTrail = onOpenTrail,
                        trailPending = trailPending
                    )
                }

                // Cards below the header render in the user's chosen order, skipping hidden ones
                // (Settings → Customize screens → Home). The default order matches the original.
                widgetLayout.forEach forEachWidget@{ widget ->
                    if (!widget.enabled) return@forEachWidget
                    when (widget.key) {
                        "hero" -> item(key = "hero") {
                            HeroCard(
                                consumed = nutrition.calories.toInt(),
                                goal = effectiveGoal,
                                tier = score.tier,
                                balanceScore = score.score,
                                balanceHint = balanceHint(
                                    over = nutrition.calories.toInt() > effectiveGoal,
                                    protein = nutrition.protein, proteinTarget = targets?.proteinG?.toFloat() ?: 0f,
                                    fat = nutrition.fat, fatTarget = targets?.fatG?.toFloat() ?: 0f,
                                    carbs = nutrition.carbs, carbTarget = targets?.carbsG?.toFloat() ?: 0f,
                                    fiber = nutrition.fiber, fiberTarget = targets?.fiberG?.toFloat() ?: 0f,
                                    microCoverage = microCoverage(micros)
                                ),
                                baseGoal = baseGoal,
                                activityKcal = exerciseCalories.toInt() + stepCalories.toInt(),
                                micros = Micronutrients(
                                    vitaminAMcg = micros.vitaminA, vitaminCMg = micros.vitaminC, vitaminDMcg = micros.vitaminD,
                                    calciumMg = micros.calcium, ironMg = micros.iron, potassiumMg = micros.potassium, sodiumMg = micros.sodium,
                                    vitaminB12Mcg = micros.vitaminB12, folateMcg = micros.folate, vitaminB6Mg = micros.vitaminB6,
                                    magnesiumMg = micros.magnesium, zincMg = micros.zinc, vitaminEMg = micros.vitaminE
                                ),
                                protein = nutrition.protein, proteinTarget = targets?.proteinG?.toFloat() ?: 0f,
                                fat = nutrition.fat, fatTarget = targets?.fatG?.toFloat() ?: 0f,
                                carbs = nutrition.carbs, carbTarget = targets?.carbsG?.toFloat() ?: 0f,
                                fiber = nutrition.fiber, fiberTarget = targets?.fiberG?.toFloat() ?: 0f
                            )
                        }

                        "ai_review" -> item(key = "ai_review") {
                            AiDailyReviewCard(
                                selectedDate = selectedDate,
                                onOpen = {
                                    onOpenReview("daily", selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                                }
                            )
                        }

                        "meals" -> sections.forEach { section ->
                            item(key = "section_${section.type}") {
                                MealCategorySection(
                                    section = section,
                                    compact = compactEmptyMeals,
                                    onAdd = { viewModel.startAddTo(section.type); onAddFood() },
                                    onItemClick = { onEntryClick(it) },
                                    onItemLongClick = { logged -> editingItem = logged; editingType = section.type },
                                    onGroupClick = { onGroupClick(it) }
                                )
                            }
                        }

                        "water" -> item(key = "water") {
                            WaterCard(
                                totalMl = water,
                                goalMl = waterGoal,
                                presets = waterPresets,
                                onAddWater = viewModel::addWater,
                                onOpen = { onOpenWater(selectedDate.toString()) }
                            )
                        }

                        "steps" -> item(key = "steps") {
                            StepsCard(
                                totalSteps = dailySteps,
                                caloriesBurned = stepCalories,
                                onLogSteps = { showStepDialog = true; viewModel.loadStepSources() }
                            )
                        }

                        "exercise" -> item(key = "exercise") {
                            ExerciseCard(
                                totalBurned = exerciseCalories,
                                entries = exerciseEntries,
                                onAdd = { viewModel.prepareLogToViewedDate(); onLogExercise() },
                                onDelete = viewModel::deleteExercise,
                                onEntryClick = onExerciseClick
                            )
                        }

                        "weight" -> item(key = "weight") {
                            WeightCard(latestWeight = latestWeight?.weightKg, onLog = { showWeightDialog = true })
                        }
                    }
                }
            }
        }
    }

    editingItem?.let { item ->
        EditEntryDialog(
            item = item,
            currentMealType = editingType,
            sourceDate = selectedDate,
            onSave = { grams -> viewModel.updateItemGrams(item, grams); editingItem = null },
            onDelete = { viewModel.deleteItem(item.id); editingItem = null },
            onCopyToDate = { date, meal, copies -> viewModel.copyItemToDate(item, date, meal, copies); editingItem = null },
            onChangeMealType = { type -> viewModel.setItemMealType(item, type); editingItem = null },
            onDismiss = { editingItem = null }
        )
    }

    if (showCalendar) {
        val calendarCalories by viewModel.calendarCalories.collectAsStateWithLifecycle()
        CalorieCalendarDialog(
            initialDate = selectedDate,
            calorieGoal = baseGoal,
            caloriesByDate = calendarCalories,
            onMonthChanged = { viewModel.loadCalendarMonth(it) },
            onDateSelected = { date -> viewModel.selectDate(date); showCalendar = false },
            onDismiss = { showCalendar = false }
        )
    }

    if (showStepDialog) {
        StepInputDialog(
            initialManual = manualSteps,
            initialSynced = syncedSteps,
            sources = stepSources,
            canSync = viewModel.healthConnectAvailable,
            onSync = { viewModel.syncHealthConnect() },
            onSave = { manual, synced ->
                viewModel.setManualSteps(manual)
                viewModel.setSyncedSteps(synced)
                showStepDialog = false
            },
            onDismiss = { showStepDialog = false }
        )
    }

    if (showWeightDialog) {
        WeightLogDialog(
            initialKg = latestWeight?.weightKg,
            onSave = { kg -> viewModel.logWeight(kg); showWeightDialog = false },
            onDismiss = { showWeightDialog = false }
        )
    }
}

// ======================== HEADER ========================

@Composable
private fun HomeHeader(
    date: LocalDate,
    status: String,
    streak: Int,
    onPickDate: () -> Unit,
    onToday: () -> Unit,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenTrail: () -> Unit,
    trailPending: Boolean = false
) {
    val isToday = date == LocalDate.now()
    val dateLabel = if (isToday) "Today" else date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = "Previous day",
                    tint = CreamMuted,
                    modifier = Modifier.size(22.dp).clickable(onClick = onPrevDay)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = dateLabel.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = CreamMuted,
                    modifier = Modifier.clickable(onClick = onPickDate)
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = "Pick date",
                    tint = CreamFaint,
                    modifier = Modifier.size(15.dp).clickable(onClick = onPickDate)
                )
                if (!isToday) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Next day",
                        tint = CreamMuted,
                        modifier = Modifier.size(22.dp).clickable(onClick = onNextDay)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldLight,
                        modifier = Modifier.clickable(onClick = onToday)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            HeaderGreeting(status)
        }

        // The trail has growth waiting — this chip is its only entry point, so the badge
        // rides on the corner where a notification dot belongs, not tucked inside the pill.
        Box {
            GlassCapsule(onClick = onOpenTrail) {
                Icon(
                    Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (streak > 0) GoldLight else CreamMuted,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "$streak",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (streak > 0) Cream else CreamMuted
                )
            }
            if (trailPending) {
                PendingBadge(
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                )
            }
        }
    }
}

/** A pulsing dot that says "there's something waiting for you in there". */
@Composable
private fun PendingBadge(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "pending").animateFloat(
        initialValue = 0.75f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "pendingPulse"
    )
    Canvas(modifier = modifier.size(18.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        // Halo, then a dark ring so the dot reads against whatever is behind the header.
        drawCircle(Gold.copy(alpha = 0.22f), radius = size.minDimension * 0.5f * pulse, center = c)
        drawCircle(InkBlack, radius = size.minDimension * 0.30f, center = c)
        drawCircle(Gold, radius = size.minDimension * 0.24f, center = c)
    }
}

/** Serif greeting with the first word emphasised in gold italic, Stoic-style. */
@Composable
private fun HeaderGreeting(text: String) {
    val space = text.indexOf(' ')
    val ann = buildAnnotatedString {
        if (space > 0) {
            withStyle(SpanStyle(color = GoldLight, fontStyle = FontStyle.Italic)) {
                append(text.substring(0, space))
            }
            append(text.substring(space))
        } else {
            withStyle(SpanStyle(color = GoldLight, fontStyle = FontStyle.Italic)) { append(text) }
        }
    }
    // The coaching lines vary a lot in length, so auto-fit instead of a fixed size: start at the
    // premium serif size and step down only until the text fits two rows. Short tips stay large;
    // long ones shrink rather than getting cut off. maxLines = 2 + ellipsis is the final guard if
    // even the smallest size still overflows. (autoSize on Text isn't in Compose 1.7, hence manual.)
    val maxSize = 24.sp
    val minSize = 15.sp
    var fontSize by remember(text) { mutableStateOf(maxSize) }
    var settled by remember(text) { mutableStateOf(false) }
    Text(
        ann,
        style = MaterialTheme.typography.displaySmall.copy(
            fontSize = fontSize,
            lineHeight = fontSize * 1.16f
        ),
        color = Cream,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        // Stay invisible while we're still shrinking, so the reader never sees it jump sizes.
        modifier = Modifier.drawWithContent { if (settled) drawContent() },
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize.value > minSize.value) {
                fontSize = (fontSize.value - 1f).sp
            } else {
                settled = true
            }
        }
    )
}

// ======================== HERO CARD ========================

@Composable
private fun HeroCard(
    consumed: Int,
    goal: Int,
    tier: DayScore.Tier,
    balanceScore: Int,
    balanceHint: String,
    baseGoal: Int,
    activityKcal: Int,
    micros: Micronutrients,
    protein: Float, proteinTarget: Float,
    fat: Float, fatTarget: Float,
    carbs: Float, carbTarget: Float,
    fiber: Float, fiberTarget: Float
) {
    val pager = rememberPagerState(pageCount = { 2 })
    Column(modifier = Modifier.fillMaxWidth().glass().padding(vertical = 14.dp, horizontal = 16.dp)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(280.dp)) { page ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (page == 0) {
                    CalorieRing(
                        consumed = consumed, goal = goal, tier = tier, diameter = 150.dp,
                        balanceScore = balanceScore, balanceHint = balanceHint
                    )
                    if (activityKcal > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${"%,d".format(baseGoal)} base + ${"%,d".format(activityKcal)} activity",
                            style = MaterialTheme.typography.labelMedium,
                            color = CreamFaint
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    MacroRingsRow(
                        protein = protein, proteinTarget = proteinTarget,
                        fat = fat, fatTarget = fatTarget,
                        carbs = carbs, carbTarget = carbTarget,
                        fiber = fiber, fiberTarget = fiberTarget
                    )
                } else {
                    Text("Vitamins & minerals", style = MaterialTheme.typography.titleMedium, color = Cream)
                    Text("vs adult daily target", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    Spacer(Modifier.height(14.dp))
                    if (micros.isEmpty) {
                        Text(
                            "Log some food to see your vitamins & minerals",
                            style = MaterialTheme.typography.bodySmall, color = CreamFaint,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        MicronutrientBars(micros, compact = true)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(2) { i ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (i == pager.currentPage) 18.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) Cream else CreamFaint)
                )
            }
        }
    }
}

// ======================== MEAL CATEGORY SECTION ========================

/** All meal categories share one warm accent — the label tells them apart, the panel just groups. */
private val MealAccent = GoldLight

/**
 * One meal category (Breakfast / Lunch / …) as a distinct panel: a colour-coded accent dot +
 * header, a faint tinted border grouping its items, and the logged dishes inside. Empty categories
 * still show their panel so the four meals are easy to tell apart at a glance.
 */
@Composable
private fun MealCategorySection(
    section: MealSection,
    onAdd: () -> Unit,
    onItemClick: (Long) -> Unit,
    onItemLongClick: (MealLogItemEntity) -> Unit,
    onGroupClick: (Long) -> Unit,
    compact: Boolean = false
) {
    val accent = MealAccent
    // Compact mode: an empty meal shrinks to a one-line "add" row instead of a full panel.
    if (compact && section.items.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassSoft()
                .clickable(onClick = onAdd)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
            Text(section.label, style = MaterialTheme.typography.titleSmall, color = CreamMuted, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Add, contentDescription = "Add to ${section.label}", tint = GoldLight, modifier = Modifier.size(20.dp))
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(accent.copy(alpha = 0.05f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(9.dp))
                Text(section.label, style = MaterialTheme.typography.headlineSmall, color = Cream)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (section.items.isNotEmpty()) {
                    Text(
                        text = "${section.calories.toInt()} kcal",
                        style = MaterialTheme.typography.titleMedium,
                        color = CalorieColor
                    )
                }
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "Add to ${section.label}", tint = GoldLight)
                }
            }
        }
        if (section.items.isEmpty()) {
            Text(
                text = "Nothing yet — tap + to add",
                style = MaterialTheme.typography.bodySmall,
                color = CreamFaint,
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
            )
        } else {
            // Group by the meal-log they were logged together in (one photo / describe = one
            // generation = one card); items added later get their own card.
            val groups = section.items.groupBy { it.mealLogId }.values.toList()
            groups.forEach { groupItems ->
                if (groupItems.size <= 1) {
                    val single = groupItems.first()
                    MealItemRow(
                        item = single,
                        onClick = { onItemClick(single.id) },
                        onLongClick = { onItemLongClick(single) }
                    )
                } else {
                    MealGroupCard(
                        items = groupItems,
                        onGroupClick = { onGroupClick(groupItems.first().mealLogId) },
                        onItemClick = { onItemClick(it.id) },
                        onItemLongClick = { onItemLongClick(it) }
                    )
                }
            }
        }
    }
}

// ======================== MEAL ITEM ROW ========================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealItemRow(
    item: MealLogItemEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSoft()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!item.photoPath.isNullOrBlank()) {
            AsyncImage(
                model = item.photoPath,
                contentDescription = item.name,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                color = Cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.grams.toInt()} ${if (item.isDrink) "ml" else "g"}",
                style = MaterialTheme.typography.bodySmall,
                color = CreamMuted
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${item.calories.toInt()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CalorieColor
            )
            Text("kcal", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
        }
    }
}

/**
 * One meal logged in a single generation (a photo or describe with several dishes). Shown as one
 * card — recognisable as ONE meal — with each dish listed and individually tappable to its detail.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealGroupCard(
    items: List<MealLogItemEntity>,
    onGroupClick: () -> Unit,
    onItemClick: (MealLogItemEntity) -> Unit,
    onItemLongClick: (MealLogItemEntity) -> Unit
) {
    val totalKcal = items.sumOf { it.calories.toDouble() }.toInt()
    val photo = items.firstOrNull { !it.photoPath.isNullOrBlank() }?.photoPath
    Column(modifier = Modifier.fillMaxWidth().glassSoft().padding(11.dp)) {
        // Tapping the header (photo + summary) opens the whole meal on one screen.
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onGroupClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!photo.isNullOrBlank()) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Meal · ${items.size} dishes",
                    style = MaterialTheme.typography.titleSmall,
                    color = Cream
                )
                Text(
                    text = items.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = CreamMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$totalKcal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CalorieColor
                )
                Text("kcal", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
            }
        }
        Spacer(Modifier.height(6.dp))
        items.forEach { dish ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onItemClick(dish) }, onLongClick = { onItemLongClick(dish) })
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(CreamFaint))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = dish.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${dish.calories.toInt()} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = CreamMuted
                )
            }
        }
    }
}

// ======================== AI REVIEW ========================

@Composable
private fun AiDailyReviewCard(selectedDate: LocalDate, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().glass().clickable(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (selectedDate == LocalDate.now()) "AI daily overview"
                    else "AI overview — ${selectedDate.format(DateTimeFormatter.ofPattern("d MMM"))}",
                style = MaterialTheme.typography.titleSmall,
                color = Cream
            )
            Text(
                text = "Tap for your personal review of this day",
                style = MaterialTheme.typography.bodySmall,
                color = CreamMuted
            )
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = CreamMuted)
    }
}

// ======================== WATER / STEPS / EXERCISE ========================

@Composable
private fun WaterCard(totalMl: Float, goalMl: Int, presets: List<Int>, onAddWater: (Float) -> Unit, onOpen: () -> Unit) {
    val frac = if (goalMl > 0) (totalMl / goalMl).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth().accentGlass(AccentTrends).padding(16.dp)) {
        // Tapping the header opens the water detail (sources + preset editing).
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Water", style = MaterialTheme.typography.titleMedium, color = Cream)
                Text("Drinks + water from food · tap for details", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${totalMl.toInt()} ml", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AccentTrends)
                if (goalMl > 0) {
                    Text("of $goalMl ml", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
                }
            }
        }
        if (goalMl > 0) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Box(
                    Modifier.fillMaxWidth(frac).height(6.dp).clip(RoundedCornerShape(50))
                        .background(AccentTrends)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.take(3).forEach { ml ->
                GlassCapsule(modifier = Modifier.weight(1f), onClick = { onAddWater(ml.toFloat()) }) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("+$ml", style = MaterialTheme.typography.labelLarge, color = Cream)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepsCard(totalSteps: Int, caloriesBurned: Float, onLogSteps: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().accentGlass(AccentGarden).clickable(onClick = onLogSteps).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Steps", style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(
                text = if (caloriesBurned > 0) "${caloriesBurned.toInt()} kcal burned" else "Tap to log steps",
                style = MaterialTheme.typography.bodySmall,
                color = CreamMuted
            )
        }
        Text("%,d".format(totalSteps.coerceAtLeast(0)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AccentGarden)
    }
}

@Composable
private fun WeightCard(latestWeight: Float?, onLog: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().accentGlass(GoldLight).clickable(onClick = onLog).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Weight", style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(
                text = if (latestWeight != null) "Tap to log today's weight" else "Tap to log your weight",
                style = MaterialTheme.typography.bodySmall,
                color = CreamMuted
            )
        }
        Text(
            text = if (latestWeight != null) "${"%.1f".format(latestWeight)} kg" else "— kg",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = GoldLight
        )
    }
}

@Composable
private fun WeightLogDialog(initialKg: Float?, onSave: (Float) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialKg?.let { "%.1f".format(it) } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log weight") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { text.toFloatOrNull()?.let(onSave) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ExerciseCard(
    totalBurned: Float,
    entries: List<ExerciseEntryEntity>,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
    onEntryClick: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().accentGlass(AccentGarden).padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Exercise", style = MaterialTheme.typography.titleMedium, color = Cream)
                Text(
                    text = if (totalBurned > 0) "+${totalBurned.toInt()} kcal back to your budget"
                        else "Log a workout to earn back calories",
                    style = MaterialTheme.typography.bodySmall,
                    color = CreamMuted
                )
            }
            Text(
                text = if (totalBurned > 0) "${totalBurned.toInt()} kcal" else "—",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CalorieColor
            )
        }
        entries.forEach { e ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onEntryClick(e.id) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(e.name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = Cream)
                    Text("${e.minutes} min", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                }
                Text("${e.caloriesBurned.toInt()} kcal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Cream)
                IconButton(onClick = { onDelete(e.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Delete ${e.name}", tint = CreamMuted)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassCapsule(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
            Text("Add exercise", style = MaterialTheme.typography.labelLarge, color = Cream)
        }
    }
}

// ======================== DIALOGS ========================

@Composable
private fun EditEntryDialog(
    item: MealLogItemEntity,
    currentMealType: String,
    sourceDate: LocalDate,
    onSave: (Float) -> Unit,
    onDelete: () -> Unit,
    onCopyToDate: (LocalDate, String, Int) -> Unit,
    onChangeMealType: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val unit = if (item.isDrink) "ml" else "g"
    var grams by remember { mutableStateOf(item.grams.toInt().toString()) }
    var showCopyPicker by remember { mutableStateOf(false) }

    if (showCopyPicker) {
        DatePickerDialog(
            initialDate = sourceDate,
            title = "Copy to which day?",
            confirmLabel = "Copy here",
            mealTypeChooser = true,
            initialMealType = currentMealType,
            copiesChooser = true,
            onConfirmMeal = { date, meal, copies -> showCopyPicker = false; onCopyToDate(date, meal, copies) },
            onConfirm = {},
            onDismiss = { showCopyPicker = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it },
                    label = { Text("Amount ($unit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("Meal", style = MaterialTheme.typography.labelLarge, color = Cream)
                Spacer(Modifier.height(4.dp))
                MealTypeSelector(selected = currentMealType, onSelected = onChangeMealType)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showCopyPicker = true }) {
                    Text("Copy to another date")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete this entry", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(grams.toFloatOrNull() ?: item.grams) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A human-readable name for a step source (Health Connect package or the watch's own report). */
private fun friendlyStepSource(pkg: String): String = when {
    pkg == com.fitpal.app.data.repository.StepRepository.WATCH_SOURCE_KEY -> "Your watch (FitPal)"
    pkg == "com.fitpal.app" -> "FitPal"
    "shealth" in pkg -> "Samsung Health"
    "fitness" in pkg -> "Google Fit"
    "gms" in pkg -> "Google"
    else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

@Composable
private fun StepInputDialog(
    initialManual: Int,
    initialSynced: Int,
    sources: Map<String, Int>,
    canSync: Boolean,
    onSync: () -> Unit,
    onSave: (manual: Int, synced: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Manual is a SET value and may be negative — the +/− toggle holds the sign so a plain
    // number keyboard is enough. Synced mirrors the Samsung Health count and can be corrected.
    var subtract by remember(initialManual) { mutableStateOf(initialManual < 0) }
    var manualMag by remember(initialManual) {
        mutableStateOf(if (initialManual != 0) initialManual.toString().removePrefix("-") else "")
    }
    var syncedText by remember(initialSynced) {
        mutableStateOf(if (initialSynced != 0) initialSynced.toString() else "")
    }
    val manual = (manualMag.toIntOrNull() ?: 0) * (if (subtract) -1 else 1)
    val synced = syncedText.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Steps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Manual steps", style = MaterialTheme.typography.labelLarge, color = Cream)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { subtract = !subtract }, modifier = Modifier.width(56.dp)) {
                            Text(if (subtract) "−" else "+", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedTextField(
                            value = manualMag,
                            onValueChange = { manualMag = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "Sets your manual count (doesn't add). Tap − to trim the synced steps.",
                        style = MaterialTheme.typography.labelSmall, color = CreamFaint
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Samsung Health (synced)", style = MaterialTheme.typography.labelLarge, color = Cream)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = syncedText,
                            onValueChange = { syncedText = it.filter { c -> c.isDigit() } },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (canSync) {
                            TextButton(onClick = onSync) { Text("Sync") }
                        }
                    }
                    Text(
                        "The higher of Health Connect and what your watch reports directly. Open FitPal " +
                            "on your watch once and tap \"Sync watch steps\" so the watch can report its own " +
                            "count — then watch steps can't go missing even if Samsung Health doesn't share them.",
                        style = MaterialTheme.typography.labelSmall, color = CreamFaint
                    )
                }
                if (sources.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Health Connect has, today:", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                        sources.entries.sortedByDescending { it.value }.forEach { (pkg, count) ->
                            Text("• ${friendlyStepSource(pkg)}: %,d".format(count), style = MaterialTheme.typography.labelSmall, color = CreamFaint)
                        }
                    }
                }
                Text(
                    "Total: %,d steps".format((manual + synced).coerceAtLeast(0)),
                    style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(manual, synced) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CalorieCalendarDialog(
    initialDate: LocalDate,
    calorieGoal: Int,
    caloriesByDate: Map<String, Float>,
    onMonthChanged: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var viewMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    val isoFormat = DateTimeFormatter.ISO_LOCAL_DATE
    val today = LocalDate.now()

    androidx.compose.runtime.LaunchedEffect(viewMonth) { onMonthChanged(viewMonth) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Select a date") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewMonth = viewMonth.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous month")
                    }
                    Text(
                        text = "${viewMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${viewMonth.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { viewMonth = viewMonth.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next month")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { name ->
                        Text(
                            text = name,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val firstOfMonth = viewMonth.atDay(1)
                val startOffset = (firstOfMonth.dayOfWeek.value - 1)
                val daysInMonth = viewMonth.lengthOfMonth()
                val cells = mutableListOf<LocalDate?>()
                repeat(startOffset) { cells.add(null) }
                for (d in 1..daysInMonth) cells.add(viewMonth.atDay(d))
                while (cells.size < 42) cells.add(null)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    userScrollEnabled = false
                ) {
                    items(cells) { date ->
                        if (date == null) {
                            Spacer(Modifier.aspectRatio(1f))
                        } else {
                            CalendarDayCell(
                                day = date.dayOfMonth,
                                caloriesEaten = caloriesByDate[date.format(isoFormat)],
                                calorieGoal = calorieGoal,
                                isSelected = date == initialDate,
                                isToday = date == today,
                                onClick = { onDateSelected(date) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendCircle(Gold, "Under goal")
                    Spacer(Modifier.width(16.dp))
                    LegendCircle(ScorePoor, "Over goal")
                }
            }
        }
    )
}

@Composable
private fun CalendarDayCell(
    day: Int,
    caloriesEaten: Float?,
    calorieGoal: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (caloriesEaten != null && caloriesEaten > 0f && calorieGoal > 0) {
            Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                val strokeWidth = 4.dp.toPx()
                val diameter = size.minDimension
                val topLeft = Offset(
                    (size.width - diameter) / 2 + strokeWidth / 2,
                    (size.height - diameter) / 2 + strokeWidth / 2
                )
                val arcSize = androidx.compose.ui.geometry.Size(diameter - strokeWidth, diameter - strokeWidth)
                drawArc(trackColor, -90f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidth))
                val pct = caloriesEaten / calorieGoal
                if (pct <= 1f) {
                    drawArc(Gold, -90f, pct * 360f, false, topLeft, arcSize, style = Stroke(strokeWidth))
                } else {
                    drawArc(Gold, -90f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidth))
                    val overPct = ((pct - 1f) / 1f).coerceAtMost(1f)
                    drawArc(ScorePoor, -90f, overPct * 360f, false, topLeft, arcSize, style = Stroke(strokeWidth))
                }
            }
        }
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
            ),
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun LegendCircle(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color, style = Stroke(width = 3f)) }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ======================== HELPERS ========================

/**
 * A short "what's pulling today's balance down" line for the flipped ring. Picks the single biggest
 * detractor (over-budget first, then a macro over its cap, then a low reach goal, then thin micros).
 */
private fun balanceHint(
    over: Boolean,
    protein: Float, proteinTarget: Float,
    fat: Float, fatTarget: Float,
    carbs: Float, carbTarget: Float,
    fiber: Float, fiberTarget: Float,
    microCoverage: Float
): String = when {
    over -> "calories over budget"
    fatTarget > 0f && fat > fatTarget -> "fat over target"
    carbTarget > 0f && carbs > carbTarget -> "carbs over target"
    proteinTarget > 0f && protein < proteinTarget * 0.7f -> "low on protein"
    fiberTarget > 0f && fiber < fiberTarget * 0.7f -> "low on fiber"
    microCoverage in 0f..0.5f -> "low on vitamins & minerals"
    else -> "nicely balanced"
}

/** Fraction (0..1) of key vitamins/minerals reached vs. rough daily targets, or -1 if no data. */
private fun microCoverage(m: DailyMicros): Float {
    val ratios = listOf(
        m.vitaminA / 700f,
        m.vitaminC / 75f,
        m.vitaminD / 15f,
        m.vitaminE / 15f,
        m.vitaminB6 / 1.3f,
        m.vitaminB12 / 2.4f,
        m.folate / 400f,
        m.calcium / 1000f,
        m.iron / 12f,
        m.magnesium / 400f,
        m.zinc / 11f,
        m.potassium / 3400f
    )
    if (ratios.all { it <= 0f }) return -1f
    return (ratios.sumOf { it.coerceIn(0f, 1f).toDouble() } / ratios.size).toFloat()
}
