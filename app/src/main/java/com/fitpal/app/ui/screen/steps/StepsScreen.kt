package com.fitpal.app.ui.screen.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.repository.StepRepository
import com.fitpal.app.domain.GoalKind
import com.fitpal.app.domain.Trackers
import com.fitpal.app.ui.component.GlassButton
import com.fitpal.app.ui.component.GlassNumberField
import com.fitpal.app.ui.component.GoldButton
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.component.StatusPill
import com.fitpal.app.ui.component.TrackerChart
import com.fitpal.app.ui.component.TrackerHero
import com.fitpal.app.ui.component.TrackerRing
import com.fitpal.app.ui.component.TrackerScaffold
import com.fitpal.app.ui.component.TrackerSection
import com.fitpal.app.ui.component.WeekBars
import com.fitpal.app.ui.theme.AccentActivity
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.accentGlass
import com.fitpal.app.ui.theme.filmColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private const val INFO_TEXT =
    "Your steps come from Health Connect (where Samsung Health and other apps keep them) and from your " +
        "Galaxy Watch, which reports its own count. Each app's count is a view of the same steps, so " +
        "FitPal never adds them up: it uses the highest one, then adds anything you set by hand.\n\n" +
        "Steps give calories back to your daily budget — Settings → Activity & devices can trim that " +
        "if your counter tends to run high.\n\n" +
        "The goal is automatic until you set one: your average over the last two weeks plus 10%."

/**
 * The Steps page (docs/redesign/03-steps-page.md): the goal ring, the day hour by hour (when Health
 * Connect has it) or the week, where the count comes from, and a folded "count looks wrong?" with the
 * sync and the hand correction that used to fill a dialog.
 */
@Composable
fun StepsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    viewModel: StepsViewModel = hiltViewModel()
) {
    val total by viewModel.total.collectAsStateWithLifecycle()
    val manual by viewModel.manual.collectAsStateWithLifecycle()
    val synced by viewModel.synced.collectAsStateWithLifecycle()
    val calories by viewModel.calories.collectAsStateWithLifecycle()
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val hourly by viewModel.hourly.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val isToday = viewModel.isToday
    val steps = total.coerceAtLeast(0)
    val snapshot = Trackers.steps(steps, goal, isToday)
    val chartView by viewModel.chartView.collectAsStateWithLifecycle()

    TrackerScaffold(
        title = "Steps",
        onBack = onBack,
        infoTitle = "How steps work",
        infoText = INFO_TEXT,
        onOpenSettings = onOpenSettings
    ) {
        TrackerHero(
            visual = {
                TrackerRing(
                    fraction = snapshot.fraction, color = AccentActivity,
                    value = "%,d".format(steps), caption = if (goal > 0) "of %,d".format(goal) else "steps",
                    reached = goal > 0 && steps >= goal
                )
            }
        ) {
            if (!isToday) Text(viewModel.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), style = MaterialTheme.typography.labelLarge, color = CreamMuted)
            Text(
                if (calories > 0f) "+${calories.roundToInt()} kcal" else "No steps yet",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AccentActivity
            )
            if (calories > 0f) Text(if (isToday) "in today's budget" else "in that day's budget", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            snapshot.status?.let { StatusPill(it) }
        }

        // ---- One chart: the day hour by hour ⇄ the week ----
        val todayIndex = if (isToday) (LocalDate.now().toEpochDay() - viewModel.weekStart.toEpochDay()).toInt().takeIf { it in 0..6 } else null
        val walked = week.filterNotNull()
        val weekTakeaway = walked.takeIf { it.isNotEmpty() }?.let {
            val hit = if (goal > 0) it.count { s -> s >= goal } else 0
            "Avg %,d".format(it.average().roundToInt()) + (if (goal > 0) " · goal on $hit of ${it.size} days" else "")
        }
        val weekChart: @Composable () -> Unit = {
            WeekBars(
                values = week, goal = goal.toFloat().takeIf { goal > 0 }, goalKind = GoalKind.REACH,
                color = AccentActivity, todayIndex = todayIndex,
                goalLabel = if (goal > 0) "goal %,d".format(goal) else null,
                onDay = { i ->
                    val day = viewModel.weekStart.plusDays(i.toLong())
                    if (!day.isAfter(LocalDate.now()) && day != viewModel.date) onOpenDay(day)
                }
            )
        }
        val hours = hourly
        if (hours != null) {
            val busiest = hours.indices.maxByOrNull { hours[it] }
            TrackerChart(
                todayTitle = if (isToday) "Today by hour" else "That day by hour",
                weekTitle = "This week",
                view = chartView,
                onView = viewModel::setChartView,
                accent = AccentActivity,
                takeaway = if (chartView == 0) busiest?.let { "Busiest hour: ${hourLabel(it)}–${hourLabel(it + 1)}" } else weekTakeaway,
                today = { HourlyBars(hours) },
                week = { weekChart() }
            )
        } else {
            // Without Health Connect intervals there's no day to draw — the week is the chart.
            TrackerSection(title = "This week") {
                Box(Modifier.fillMaxWidth().height(150.dp)) { weekChart() }
                weekTakeaway?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                }
            }
        }

        // ---- Where the count comes from ----
        StepSources(sources = sources, synced = synced, manual = manual)

        // ---- Count looks wrong? (folded) ----
        CountFix(
            manual = manual,
            canSync = viewModel.healthConnectAvailable,
            syncing = syncing,
            lastSyncedAt = lastSyncedAt,
            onSync = viewModel::sync,
            onSetManual = viewModel::setManual
        )
    }
}

/** The day hour by hour: 24 teal bars, labelled at 6 AM, noon and 6 PM. */
@Composable
private fun HourlyBars(hours: List<Long>) {
    val measurer = rememberTextMeasurer()
    val axis = MaterialTheme.typography.labelSmall.copy(color = CreamFaint)
    val color = AccentActivity
    val track = filmColor(0.06f)
    Canvas(Modifier.fillMaxSize()) {
        val labelH = 16.dp.toPx()
        val plotH = size.height - labelH - 6.dp.toPx()
        val slot = size.width / 24f
        val barW = slot * 0.62f
        val max = (hours.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val r = CornerRadius(3.dp.toPx())
        for (h in 0 until 24) {
            val x = slot * h + (slot - barW) / 2f
            drawRoundRect(track, Offset(x, 6.dp.toPx()), Size(barW, plotH), r)
            val v = hours.getOrElse(h) { 0L }
            if (v > 0) {
                val bh = (v / max) * plotH
                drawRoundRect(color, Offset(x, 6.dp.toPx() + plotH - bh), Size(barW, bh), r)
            }
        }
        listOf(6 to "6 AM", 12 to "noon", 18 to "6 PM").forEach { (h, text) ->
            val l = measurer.measure(text, axis)
            drawText(l, topLeft = Offset(slot * h - l.size.width / 2f, size.height - l.size.height))
        }
    }
}

/** One line per source and which one FitPal used — plain numbers instead of the old paragraphs. */
@Composable
private fun StepSources(sources: Map<String, Int>, synced: Int, manual: Int) {
    val watch = sources[StepRepository.WATCH_SOURCE_KEY] ?: 0
    val apps = sources.filterKeys { it != StepRepository.WATCH_SOURCE_KEY }.toList().sortedByDescending { it.second }
    val healthConnect = apps.maxOfOrNull { it.second } ?: 0
    // The synced count is the higher of the two (StepRepository); say which one that was.
    val usedWatch = watch > 0 && watch >= healthConnect && synced == watch
    val usedHealthConnect = !usedWatch && healthConnect > 0 && synced == healthConnect
    TrackerSection(title = "Where the count comes from") {
        if (watch == 0 && apps.isEmpty() && manual == 0) {
            Text(
                if (synced > 0) "Synced: %,d steps.".format(synced) else "Nothing from Health Connect or your watch yet.",
                style = MaterialTheme.typography.bodySmall, color = CreamFaint
            )
            return@TrackerSection
        }
        if (watch > 0) SourceRow("Galaxy Watch", watch, used = usedWatch)
        if (apps.isNotEmpty()) {
            SourceRow("Health Connect", healthConnect, used = usedHealthConnect)
            if (apps.size > 1) apps.forEach { (pkg, n) ->
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 2.dp, bottom = 2.dp)) {
                    Text(friendlyStepSource(pkg), style = MaterialTheme.typography.labelMedium, color = CreamFaint, modifier = Modifier.weight(1f))
                    Text("%,d".format(n), style = MaterialTheme.typography.labelMedium, color = CreamFaint)
                }
            }
        }
        if (manual != 0) {
            SourceRow("Set by hand", manual, used = false, signed = true)
        }
        Spacer(Modifier.height(6.dp))
        Text("FitPal uses the higher of the watch and Health Connect, so steps never go missing.", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
    }
}

@Composable
private fun SourceRow(label: String, steps: Int, used: Boolean, signed: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f))
        Text(
            (if (signed && steps > 0) "+" else if (steps < 0) "−" else "") + "%,d".format(abs(steps)),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = if (used) Cream else CreamMuted
        )
        if (used) {
            Spacer(Modifier.width(8.dp))
            Text(
                "used",
                style = MaterialTheme.typography.labelSmall, color = Cream,
                modifier = Modifier.accentGlass(ScoreFair, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * "Count looks wrong?", folded: sync now, open Samsung Health, or correct the count by hand. The
 * hand value is a SET (not an add) and may subtract, to trim an over-counting sync.
 */
@Composable
private fun CountFix(
    manual: Int,
    canSync: Boolean,
    syncing: Boolean,
    lastSyncedAt: Long,
    onSync: () -> Unit,
    onSetManual: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var subtract by remember(manual) { mutableStateOf(manual < 0) }
    var text by remember(manual) { mutableStateOf(if (manual != 0) abs(manual).toString() else "") }
    val context = LocalContext.current
    TrackerSection(
        title = "Count looks wrong?",
        action = {
            Icon(
                if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (open) "Fold" else "Show the fixes",
                tint = CreamMuted,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50)).clickable { open = !open }
            )
        }
    ) {
        if (!open) {
            Text("Sync, open Samsung Health, or fix it by hand", style = MaterialTheme.typography.bodySmall, color = CreamFaint, modifier = Modifier.clickable { open = true })
        }
        AnimatedVisibility(visible = open) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canSync) {
                    Text(
                        "Samsung Health updates Health Connect on its own schedule, so it can trail what Samsung Health shows. Open it to push its latest count, then sync.",
                        style = MaterialTheme.typography.bodySmall, color = CreamMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoldButton(if (syncing) "Syncing…" else "Sync now", onClick = onSync, enabled = !syncing, modifier = Modifier.weight(1f))
                        GlassButton("Open Samsung Health", onClick = {
                            val intent = context.packageManager.getLaunchIntentForPackage("com.sec.android.app.shealth")
                            if (intent != null) context.startActivity(intent)
                            else android.widget.Toast.makeText(context, "Samsung Health isn't installed", android.widget.Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.weight(1f))
                    }
                    Text(
                        if (lastSyncedAt > 0L) "Checked " + android.text.format.DateUtils.getRelativeTimeSpanString(
                            lastSyncedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
                        ) + "." else "Not synced yet.",
                        style = MaterialTheme.typography.labelSmall, color = CreamFaint
                    )
                }
                Text("Set by hand", style = MaterialTheme.typography.titleSmall, color = Cream)
                Text(
                    "Sets the steps you add (or take away) on top of the synced count. It replaces what you set before.",
                    style = MaterialTheme.typography.bodySmall, color = CreamMuted
                )
                SegmentedPills(
                    labels = listOf("Add", "Take away"),
                    selectedIndex = if (subtract) 1 else 0,
                    accent = AccentActivity,
                    onSelect = { subtract = it == 1 }
                )
                GlassNumberField(
                    text = text, onText = { text = it },
                    step = 100f, min = 0f, max = 50_000f, fallback = 0f, maxChars = 5, fontSize = androidx.compose.ui.unit.TextUnit(40f, androidx.compose.ui.unit.TextUnitType.Sp),
                    modifier = Modifier.fillMaxWidth()
                )
                GoldButton("Save", onClick = {
                    val n = text.toIntOrNull() ?: 0
                    onSetManual(if (subtract) -n else n)
                    open = false
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** "5 PM". */
private fun hourLabel(hour: Int): String {
    val h = hour % 24
    val h12 = ((h + 11) % 12) + 1
    return "$h12 ${if (h < 12) "AM" else "PM"}"
}

/** A Health Connect writer's package as a name people recognise. */
internal fun friendlyStepSource(pkg: String): String = when {
    pkg == StepRepository.WATCH_SOURCE_KEY -> "Your watch (FitPal)"
    pkg == "com.fitpal.app" -> "FitPal"
    "shealth" in pkg -> "Samsung Health"
    "fitness" in pkg -> "Google Fit"
    "gms" in pkg -> "Google"
    else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}
