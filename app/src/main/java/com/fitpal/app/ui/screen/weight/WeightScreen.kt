package com.fitpal.app.ui.screen.weight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.local.entity.WeightEntryEntity
import com.fitpal.app.domain.Trackers
import com.fitpal.app.domain.WeightTrend
import com.fitpal.app.ui.component.GlassNumberField
import com.fitpal.app.ui.component.GoldButton
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.component.StatusPill
import com.fitpal.app.ui.component.TrackerHero
import com.fitpal.app.ui.component.TrackerScaffold
import com.fitpal.app.ui.component.TrackerSection
import com.fitpal.app.ui.component.TrendChart
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.NumberFamily
import com.fitpal.app.ui.theme.MacroOver
import com.fitpal.app.ui.theme.ScoreFair
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private const val INFO_TEXT =
    "Weigh yourself at the same time each day if you can (after waking, before breakfast) — " +
        "the day-to-day wobble is mostly water, so the trend over a couple of weeks is what tells you " +
        "where you're heading.\n\nOne weigh-in a day: saving again today replaces today's. Swipe a " +
        "weigh-in away to delete it."

/**
 * The Weight page (docs/redesign/09-fasting-weight-limits-pages.md): the latest weight and how this
 * week moved, today's weigh-in right there, the trend (30 days or all of it), and every weigh-in.
 * The weigh-in notification still opens the quick weigh-in instead — it links here.
 */
@Composable
fun WeightScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: WeightViewModel = hiltViewModel()
) {
    val weights by viewModel.weights.collectAsStateWithLifecycle()
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    val trendView by viewModel.trendView.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val latest = weights.lastOrNull()
    val weekChange = WeightTrend.changeOver(weights, today.minusDays(6), today)
    val snapshot = Trackers.weight(latest?.weightKg, weekChange, weekChange?.let { WeightTrend.changeIsOnTrack(it, goal) })

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    TrackerScaffold(
        title = "Weight",
        onBack = onBack,
        infoTitle = "About your weight",
        infoText = INFO_TEXT,
        onOpenSettings = onOpenSettings,
        snackbarHost = snackbar
    ) {
        // ---- Hero ----
        TrackerHero(visual = null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    latest?.let { format1(it.weightKg) } ?: "—",
                    style = TextStyle(fontFamily = NumberFamily, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, fontFeatureSettings = "tnum"),
                    color = Cream
                )
                Spacer(Modifier.width(6.dp))
                Text("kg", style = MaterialTheme.typography.titleMedium, color = CreamMuted, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                when {
                    latest == null -> "No weigh-ins yet"
                    latest.date == today.toString() -> "Logged today"
                    else -> "Last logged ${dayLabel(latest.date)}"
                },
                style = MaterialTheme.typography.bodySmall, color = CreamMuted
            )
            snapshot.status?.let { StatusPill(it) }
        }

        // ---- Log today ----
        LogToday(latest = latest, weights = weights, goal = goal, onSave = viewModel::save)

        // ---- Trend: 30 days | All ----
        TrackerSection(
            title = "Trend",
            action = {
                SegmentedPills(
                    labels = listOf("30 days", "All"),
                    selectedIndex = trendView,
                    accent = GoldLight,
                    onSelect = viewModel::setTrendView,
                    modifier = Modifier.width(150.dp)
                )
            }
        ) {
            val byDate = weights.associate { it.date to it.weightKg }
            val first = weights.firstOrNull()?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val from = if (trendView == 1 && first != null) first else today.minusDays(29)
            val series = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.map { byDate[it.toString()] }.toList()
            if (series.count { it != null } >= 2) {
                TrendChart(
                    values = series, accent = GoldLight,
                    startLabel = from.format(DateTimeFormatter.ofPattern("d MMM")), endLabel = "now",
                    valueFormatter = { "%.1f".format(it) }
                )
                val change = WeightTrend.changeOver(weights, from, today)
                val rate = WeightTrend.ratePerWeek(weights, from, today)
                val line = listOfNotNull(
                    change?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + "%.1f kg".format(abs(it)) + if (trendView == 1) " since you started" else " in 30 days" },
                    rate?.let { WeightTrend.rateLabel(it, goal) }
                ).joinToString(" · ")
                if (line.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(line, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                }
            } else {
                Text("Two weigh-ins draw the trend.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
            }
        }

        // ---- Every weigh-in ----
        TrackerSection(title = "Weigh-ins") {
            if (weights.isEmpty()) {
                Text("Nothing yet — log one above.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
            } else {
                val newestFirst = weights.reversed()
                newestFirst.forEachIndexed { i, entry ->
                    key(entry.id) {
                        WeighInRow(
                            entry = entry,
                            previous = newestFirst.getOrNull(i + 1),
                            goal = goal,
                            onDelete = {
                                viewModel.delete(entry)
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    if (snackbar.showSnackbar("Deleted ${format1(entry.weightKg)} kg", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                                        viewModel.restore(entry)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Today's weigh-in, right on the page: the same −/+ number as the quick weigh-in. */
@Composable
private fun LogToday(latest: WeightEntryEntity?, weights: List<WeightEntryEntity>, goal: com.fitpal.app.domain.model.FitnessGoal, onSave: (Float) -> Unit) {
    val todayIso = LocalDate.now().toString()
    val loggedToday = latest?.date == todayIso
    // Re-weighing today replaces today's entry, so compare against the one before it.
    val compareWith = if (loggedToday) weights.getOrNull(weights.size - 2) else latest
    var text by remember(latest?.date, latest?.weightKg) { mutableStateOf(latest?.weightKg?.let(::format1) ?: "") }
    val value = text.replace(',', '.').toFloatOrNull()?.takeIf { it in 20f..400f }
    TrackerSection(title = if (loggedToday) "Today's weigh-in" else "Log today") {
        GlassNumberField(
            text = text, onText = { text = it },
            step = 0.1f, min = 20f, max = 400f, decimals = 1,
            fallback = compareWith?.weightKg ?: 70f,
            modifier = Modifier.fillMaxWidth(),
            onDone = { value?.let(onSave) }
        )
        val delta = if (value != null && compareWith != null) value - compareWith.weightKg else null
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                delta == null -> "kg"
                abs(delta) < 0.05f -> "kg · same as last time"
                else -> "kg · " + (if (delta > 0) "+" else "−") + "${format1(abs(delta))} since ${dayLabel(compareWith!!.date)}"
            },
            style = MaterialTheme.typography.labelLarge,
            color = when (delta?.let { WeightTrend.changeIsOnTrack(it, goal) }) { true -> ScoreFair; false -> Gold; null -> CreamMuted },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(10.dp))
        GoldButton(
            if (loggedToday) "Update today's weigh-in" else "Save",
            onClick = { value?.let(onSave) }, enabled = value != null, modifier = Modifier.fillMaxWidth()
        )
    }
}

/** One weigh-in: the day, the weight, the change since the one before. Swipe to delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeighInRow(entry: WeightEntryEntity, previous: WeightEntryEntity?, goal: com.fitpal.app.domain.model.FitnessGoal, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v -> if (v != SwipeToDismissBoxValue.Settled) { onDelete(); true } else false })
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(MacroOver.copy(alpha = 0.18f)).padding(horizontal = 14.dp),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) { Text("Delete", style = MaterialTheme.typography.labelLarge, color = MacroOver) }
        }
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(dayLabel(entry.date), style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f))
            Text("${format1(entry.weightKg)} kg", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Cream)
            val d = previous?.let { entry.weightKg - it.weightKg }
            Text(
                when {
                    d == null -> ""
                    abs(d) < 0.05f -> "±0.0"
                    else -> (if (d > 0) "+" else "−") + format1(abs(d))
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (d?.takeIf { abs(it) >= 0.05f }?.let { WeightTrend.changeIsOnTrack(it, goal) }) { true -> ScoreFair; false -> Gold; null -> CreamFaint },
                modifier = Modifier.width(56.dp).padding(start = 10.dp)
            )
        }
    }
}

private fun format1(kg: Float): String = String.format(Locale.US, "%.1f", kg)

private fun dayLabel(iso: String): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
}
