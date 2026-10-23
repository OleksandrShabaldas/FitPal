package com.fitpal.app.ui.screen.fasting

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.fitpal.app.data.local.dao.DayMealRow
import com.fitpal.app.domain.StatusKind
import com.fitpal.app.domain.TrackerStatus
import com.fitpal.app.domain.model.FastingDayResult
import com.fitpal.app.domain.model.FastingSchedule
import com.fitpal.app.ui.component.GoldButton
import com.fitpal.app.ui.component.StatusPill
import com.fitpal.app.ui.component.TrackerChart
import com.fitpal.app.ui.component.TrackerHero
import com.fitpal.app.ui.component.TrackerRing
import com.fitpal.app.ui.component.TrackerScaffold
import com.fitpal.app.ui.component.TrackerSection
import com.fitpal.app.ui.component.fastingAccent
import com.fitpal.app.ui.component.fastingClockLabel
import com.fitpal.app.ui.component.fastingCountdownLabel
import com.fitpal.app.ui.component.minuteOfDayNow
import com.fitpal.app.ui.component.rememberFastingState
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.FastingColor
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.NumberFamily
import com.fitpal.app.ui.theme.MacroOver
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.filmColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val INFO_TEXT =
    "Outside your eating window you're fasting. A day counts as kept when everything you ate that day " +
        "was inside the window; drinks your drink rule allows (Settings → Fasting) never break it.\n\n" +
        "If you really ate earlier than you logged, a pass (three a month) counts the meal at the time you " +
        "ate it and keeps the day. A photo's own time inside the window is proof enough, no pass needed. " +
        "\"Log anyway\" during a fast breaks the day, even if you later change the meal's time."

/** What one meal did to the day's fast, and the colour it shows in. */
private data class MealVerdict(val text: String, val color: Color, val broke: Boolean, val drink: Boolean)

private fun verdictFor(meal: DayMealRow, schedule: FastingSchedule): MealVerdict {
    val minute = minuteOf(meal.timestamp)
    return when {
        meal.loggedDuringFast -> MealVerdict("logged during the fast", MacroOver, broke = true, drink = false)
        schedule.isEatingAt(minute) -> MealVerdict("inside the window", ScoreFair, broke = false, drink = false)
        schedule.allowsDuringFast(meal.fastingIntake) -> MealVerdict("allowed drink", CreamMuted, broke = false, drink = true)
        else -> MealVerdict("during the fast", MacroOver, broke = true, drink = false)
    }
}

/**
 * The Fasting page (docs/redesign/09-fasting-weight-limits-pages.md): the phase and its countdown,
 * the day as a band with each meal on it ⇄ the week's kept days, the streak and passes, and what
 * each of the day's meals did to the fast.
 */
@Composable
fun FastingScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenMeal: (Long) -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    viewModel: FastingViewModel = hiltViewModel()
) {
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val meals by viewModel.meals.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val isToday = viewModel.isToday
    val chartView by viewModel.chartView.collectAsStateWithLifecycle()

    TrackerScaffold(
        title = "Fasting",
        onBack = onBack,
        infoTitle = "How fasting works",
        infoText = INFO_TEXT,
        onOpenSettings = onOpenSettings
    ) {
        if (!schedule.enabled) {
            TrackerSection(title = "Fasting is off") {
                Text("Set an eating window to see your fast, your streak and which days you kept.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                Spacer(Modifier.height(12.dp))
                GoldButton("Set an eating window", onClick = onOpenSettings)
            }
            return@TrackerScaffold
        }
        val result = history.adherence[viewModel.date.toString()]
        val firstBreak = meals.firstOrNull { verdictFor(it, schedule).broke }

        // ---- Hero: the phase now (today) or how the day went ----
        if (isToday) {
            val state = rememberFastingState(schedule)
            val accent = fastingAccent(state)
            TrackerHero(
                visual = {
                    TrackerRing(
                        fraction = state.progressFraction, color = accent,
                        value = fastingCountdownLabel(state.minutesLeftInPhase), caption = "left"
                    )
                }
            ) {
                Text(if (state.isFasting) "Fasting" else "Eating window", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
                Text(
                    if (state.isFasting) "until ${fastingClockLabel(state.nextChangeMin)}" else "closes ${fastingClockLabel(state.nextChangeMin)}",
                    style = MaterialTheme.typography.bodySmall, color = CreamMuted
                )
                StatusPill(
                    when {
                        firstBreak != null -> TrackerStatus(StatusKind.CLOSE, "Today's fast was broken at ${clock(firstBreak.timestamp)}")
                        state.isFasting -> TrackerStatus(StatusKind.NEUTRAL, "Window opens at ${fastingClockLabel(state.nextChangeMin)}")
                        else -> TrackerStatus(StatusKind.OK, "Window closes at ${fastingClockLabel(state.nextChangeMin)}")
                    }
                )
            }
        } else {
            TrackerHero(visual = null) {
                Text(viewModel.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), style = MaterialTheme.typography.labelLarge, color = CreamMuted)
                Text(
                    when (result) {
                        FastingDayResult.HELD -> "Kept"
                        FastingDayResult.BROKE -> "Broken"
                        null -> "Nothing logged"
                    },
                    style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp),
                    color = when (result) { FastingDayResult.HELD -> ScoreFair; FastingDayResult.BROKE -> MacroOver; null -> CreamMuted }
                )
                firstBreak?.let { StatusPill(TrackerStatus(StatusKind.CLOSE, "Broken at ${clock(it.timestamp)}")) }
            }
        }

        // ---- One chart: the day's band ⇄ the week's kept days ----
        val weekResults = (0L until 7L).map { i ->
            val d = viewModel.weekStart.plusDays(i)
            if (d.isAfter(LocalDate.now())) null else history.adherence[d.toString()]
        }
        val judged = weekResults.filterNotNull()
        TrackerChart(
            todayTitle = if (isToday) "Today" else "That day",
            weekTitle = "This week",
            view = chartView,
            onView = viewModel::setChartView,
            accent = FastingColor,
            takeaway = if (chartView == 0) {
                "${hours(schedule.fastingLengthMin())} fast · ${hours(schedule.eatingLengthMin())} eating window"
            } else if (judged.isNotEmpty()) {
                "${judged.count { it == FastingDayResult.HELD }} of ${judged.size} days kept"
            } else "No days judged yet this week",
            today = {
                DayBand(
                    schedule = schedule,
                    meals = meals.map { it to verdictFor(it, schedule) },
                    nowMin = if (isToday) minuteOfDayNow() else null
                )
            },
            week = {
                WeekCells(
                    results = weekResults,
                    todayIndex = if (isToday) (LocalDate.now().toEpochDay() - viewModel.weekStart.toEpochDay()).toInt().takeIf { it in 0..6 } else null,
                    onDay = { i ->
                        val d = viewModel.weekStart.plusDays(i.toLong())
                        if (!d.isAfter(LocalDate.now()) && d != viewModel.date) onOpenDay(d)
                    }
                )
            }
        )

        // ---- Streak and passes ----
        TrackerSection(title = "Streak") {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (history.streak > 0) "${history.streak}" else "—",
                    style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, fontFeatureSettings = "tnum"),
                    color = FastingColor
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(if (history.streak == 1) "day in a row" else "days in a row", style = MaterialTheme.typography.bodyMedium, color = Cream)
                    if (history.best > 0) Text("best ${history.best}", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Passes left this month: ${history.passesLeft} of ${com.fitpal.app.data.repository.SettingsRepository.FASTING_BACKDATE_LIMIT}",
                style = MaterialTheme.typography.bodySmall, color = CreamMuted
            )
        }

        // ---- What each meal did ----
        TrackerSection(title = if (isToday) "Today's meals" else "That day's meals") {
            if (meals.isEmpty()) {
                Text("Nothing logged yet.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
            } else {
                meals.forEach { meal ->
                    val v = verdictFor(meal, schedule)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpenMeal(meal.mealLogId) }.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(clock(meal.timestamp), style = MaterialTheme.typography.labelMedium, color = CreamFaint, modifier = Modifier.width(72.dp))
                        Text(
                            meal.mealName ?: meal.itemNames ?: "Meal",
                            style = MaterialTheme.typography.bodyMedium, color = Cream,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(v.text, style = MaterialTheme.typography.labelMedium, color = v.color, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * The day from midnight to midnight as one band: the fasting hours in the fasting colour, the eating
 * window in gold, each meal a dot at the time it was eaten (red if it broke the fast, hollow for an
 * allowed drink), and "now".
 */
@Composable
private fun DayBand(schedule: FastingSchedule, meals: List<Pair<DayMealRow, MealVerdict>>, nowMin: Int?) {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.typography.labelSmall.copy(color = CreamFaint)
    val nowStyle = MaterialTheme.typography.labelSmall.copy(color = Cream, fontWeight = FontWeight.Bold)
    val fast = FastingColor.copy(alpha = 0.35f)
    val eat = GoldLight.copy(alpha = 0.35f)
    val dotOk = GoldLight
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val bandH = 30.dp.toPx()
        val bandTop = size.height / 2f - bandH / 2f
        fun x(min: Int) = w * min / (24f * 60f)
        // The band, 15 minutes at a time so a window that wraps past midnight just works.
        var m = 0
        while (m < 24 * 60) {
            drawRect(if (schedule.isEatingAt(m)) eat else fast, Offset(x(m), bandTop), Size(x(m + 15) - x(m) + 0.5f, bandH))
            m += 15
        }
        meals.forEach { (meal, v) ->
            val c = Offset(x(minuteOf(meal.timestamp)), bandTop + bandH / 2f)
            when {
                v.drink -> drawCircle(Cream, 5.dp.toPx(), c, style = Stroke(1.6.dp.toPx()))
                v.broke -> drawCircle(MacroOver, 5.5.dp.toPx(), c)
                else -> drawCircle(dotOk, 5.5.dp.toPx(), c)
            }
            drawCircle(Color.Black.copy(alpha = 0.25f), 5.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        }
        if (nowMin != null) {
            val nx = x(nowMin)
            drawLine(Cream, Offset(nx, bandTop - 8.dp.toPx()), Offset(nx, bandTop + bandH + 8.dp.toPx()), 2.dp.toPx())
            val n = measurer.measure("now", nowStyle)
            drawText(n, topLeft = Offset((nx - n.size.width / 2f).coerceIn(0f, w - n.size.width), bandTop - 10.dp.toPx() - n.size.height))
        }
        val labelY = bandTop + bandH + 14.dp.toPx()
        listOf(0 to "12 AM", 6 * 60 to "6 AM", 12 * 60 to "noon", 18 * 60 to "6 PM", 24 * 60 to "12 AM").forEach { (min, text) ->
            val l = measurer.measure(text, axis)
            drawText(l, topLeft = Offset((x(min) - l.size.width / 2f).coerceIn(0f, w - l.size.width), labelY))
        }
    }
}

/** Seven squares, Monday to Sunday: kept in green, broken in red, empty when there's nothing to judge. */
@Composable
private fun WeekCells(results: List<FastingDayResult?>, todayIndex: Int?, onDay: (Int) -> Unit) {
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        results.forEachIndexed { i, r ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onDay(i) }.padding(4.dp)) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(
                        when (r) {
                            FastingDayResult.HELD -> ScoreFair
                            FastingDayResult.BROKE -> MacroOver
                            null -> filmColor(0.10f)
                        }
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    letters[i], style = MaterialTheme.typography.labelSmall,
                    color = if (i == todayIndex) Cream else CreamFaint,
                    fontWeight = if (i == todayIndex) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

private fun minuteOf(epochMillis: Long): Int =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).let { it.hour * 60 + it.minute }

private fun clock(epochMillis: Long): String = fastingClockLabel(minuteOf(epochMillis))

private fun hours(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}
