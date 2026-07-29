package com.fitpal.app.ui.screen.caloriedetail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.screen.analytics.AnalyticsRange
import com.fitpal.app.ui.theme.AccentActivity
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.ScorePoor
import com.fitpal.app.ui.theme.glass
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val CARD = RoundedCornerShape(26.dp)
private val DAY_LABEL = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * Calories in vs. out for the week or last 30 days, opened from the Analytics
 * "Calorie balance" card. Totals up top, then an expandable day-by-day list.
 */
@Composable
fun CalorieDetailScreen(
    onBack: () -> Unit,
    onOpenDay: () -> Unit = {},
    viewModel: CalorieDetailViewModel = hiltViewModel()
) {
    val range by viewModel.range.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }

    val logged = summary.loggedDays
    val net = summary.net
    val periodWord = if (range == AnalyticsRange.WEEK) "this week" else "the last 30 days"

    GradientBackdrop(theme = BackdropTheme.TRENDS) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Calorie balance", onBack = onBack)
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SegmentedPills(
                        labels = AnalyticsRange.entries.map { it.label },
                        selectedIndex = AnalyticsRange.entries.indexOf(range),
                        accent = AccentTrends,
                        onSelect = { viewModel.setRange(AnalyticsRange.entries[it]) }
                    )
                }

                item {
                    Text(
                        summary.periodLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = CreamMuted,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                // ---- Net vs goal (the same number the Analytics card shows) ----
                item {
                    Column(modifier = Modifier.fillMaxWidth().glass(CARD).padding(16.dp)) {
                        Text("Net vs your goal", style = MaterialTheme.typography.titleMedium, color = Cream)
                        Text(
                            "After steps & exercise, $periodWord",
                            style = MaterialTheme.typography.bodySmall, color = CreamMuted
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = (if (net > 0) "+" else "") + "${"%,d".format(net)} kcal",
                            style = MaterialTheme.typography.headlineMedium,
                            color = when {
                                abs(net) < 100 -> GoldLight
                                net > 0 -> ScorePoor
                                else -> ScoreFair
                            }
                        )
                        Text(
                            text = when {
                                logged.isEmpty() -> "Nothing logged in this period yet"
                                abs(net) < 100 -> "Right around your goal across ${logged.size} logged days"
                                net > 0 -> "Over by ${"%,d".format(net)} across ${logged.size} logged days"
                                else -> "Under by ${"%,d".format(-net)} across ${logged.size} logged days"
                            },
                            style = MaterialTheme.typography.bodySmall, color = CreamMuted
                        )
                        if (summary.goal > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Daily goal ${"%,d".format(summary.goal)} kcal",
                                style = MaterialTheme.typography.labelSmall, color = CreamFaint
                            )
                        }
                    }
                }

                // ---- The two totals, side by side ----
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TotalTile(
                            modifier = Modifier.weight(1f),
                            label = "Consumed",
                            total = summary.consumed,
                            perDay = if (logged.isNotEmpty()) summary.consumed / logged.size else null,
                            perDaySuffix = "per logged day",
                            accent = GoldLight
                        )
                        TotalTile(
                            modifier = Modifier.weight(1f),
                            label = "Burned",
                            total = summary.burned,
                            perDay = if (summary.days.isNotEmpty()) summary.burned / summary.days.size else null,
                            perDaySuffix = "per day",
                            accent = AccentActivity
                        )
                    }
                }

                // ---- In vs out, drawn to scale ----
                item {
                    Column(modifier = Modifier.fillMaxWidth().glass(CARD).padding(16.dp)) {
                        Text("In vs out", style = MaterialTheme.typography.titleMedium, color = Cream)
                        Spacer(Modifier.height(12.dp))
                        val scale = maxOf(summary.consumed, summary.burned, 1f)
                        EnergyBar("Eaten", summary.consumed, summary.consumed / scale, GoldLight)
                        Spacer(Modifier.height(10.dp))
                        EnergyBar("Burned", summary.burned, summary.burned / scale, AccentActivity)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Burned = ${"%,d".format(summary.stepBurn.roundToInt())} kcal from steps · " +
                                "${"%,d".format(summary.exerciseBurn.roundToInt())} kcal from workouts",
                            style = MaterialTheme.typography.labelSmall, color = CreamFaint
                        )
                    }
                }

                // ---- Day by day ----
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().glass(CARD)
                            .padding(16.dp)
                            .animateContentSize()
                    ) {
                        // Only the header toggles — so tapping a day row below opens that day
                        // instead of folding the list shut under your finger.
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Day by day", style = MaterialTheme.typography.titleMedium, color = Cream)
                                Text(
                                    if (expanded) "Eaten and burned for each day" else "Tap to see every day",
                                    style = MaterialTheme.typography.bodySmall, color = CreamMuted
                                )
                            }
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = CreamMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (expanded) {
                            Spacer(Modifier.height(12.dp))
                            DayHeaderRow()
                            Spacer(Modifier.height(4.dp))
                            summary.days.asReversed().forEach { day ->
                                DayEnergyRow(
                                    day = day,
                                    goal = summary.goal,
                                    onClick = if (day.logged) {
                                        { viewModel.focusDay(day.date); onOpenDay() }
                                    } else null
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Newest first · tap a logged day to open it",
                                style = MaterialTheme.typography.labelSmall, color = CreamFaint
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One big number with its per-day average underneath. */
@Composable
private fun TotalTile(
    modifier: Modifier,
    label: String,
    total: Float,
    perDay: Float?,
    perDaySuffix: String,
    accent: Color
) {
    Column(modifier = modifier.glass(CARD).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = CreamMuted)
        Spacer(Modifier.height(6.dp))
        Text("${"%,d".format(total.roundToInt())}", style = MaterialTheme.typography.titleLarge, color = accent)
        Text("kcal total", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
        Spacer(Modifier.height(6.dp))
        Text(
            perDay?.let { "${"%,d".format(it.roundToInt())} $perDaySuffix" } ?: "—",
            style = MaterialTheme.typography.labelSmall, color = CreamMuted
        )
    }
}

/** A labelled bar drawn as a share of the bigger of the two totals. */
@Composable
private fun EnergyBar(label: String, value: Float, fraction: Float, accent: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
            Text("${"%,d".format(value.roundToInt())} kcal", style = MaterialTheme.typography.bodyMedium, color = accent)
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.07f))
        ) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent)
            )
        }
    }
}

/** Column captions for the day-by-day table. */
@Composable
private fun DayHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Text("Day", style = MaterialTheme.typography.labelSmall, color = CreamFaint, modifier = Modifier.width(84.dp))
        listOf("In", "Out", "Net").forEach { caption ->
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = CreamFaint,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** One row of the day-by-day list: what went in, what went out, and the day's net vs goal. */
@Composable
private fun DayEnergyRow(day: DayEnergy, goal: Int, onClick: (() -> Unit)?) {
    val net = (day.consumed - goal - day.burned).roundToInt()
    val netColor = when {
        !day.logged || goal <= 0 -> CreamFaint
        abs(net) < 100 -> GoldLight
        net > 0 -> ScorePoor
        else -> ScoreFair
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            day.date.format(DAY_LABEL),
            style = MaterialTheme.typography.bodySmall,
            color = if (day.logged) Cream else CreamFaint,
            modifier = Modifier.width(84.dp)
        )
        Text(
            if (day.logged) "%,d".format(day.consumed.roundToInt()) else "—",
            style = MaterialTheme.typography.bodySmall,
            color = if (day.logged) GoldLight else CreamFaint,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (day.burned > 0f) "%,d".format(day.burned.roundToInt()) else "—",
            style = MaterialTheme.typography.bodySmall,
            color = if (day.burned > 0f) AccentActivity else CreamFaint,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (day.logged && goal > 0) (if (net > 0) "+" else "") + "%,d".format(net) else "—",
            style = MaterialTheme.typography.bodySmall,
            color = netColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
