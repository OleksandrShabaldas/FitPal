package com.fitpal.app.ui.screen.limits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.local.entity.MealLogItemEntity
import com.fitpal.app.domain.DietaryTally
import com.fitpal.app.domain.GoalKind
import com.fitpal.app.domain.StatusKind
import com.fitpal.app.domain.TrackerStatus
import com.fitpal.app.domain.Trackers
import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.ui.component.GlassButton
import com.fitpal.app.ui.component.GoldButton
import com.fitpal.app.ui.component.StatusPill
import com.fitpal.app.ui.component.TrackerChip
import com.fitpal.app.ui.component.TrackerHero
import com.fitpal.app.ui.component.TrackerScaffold
import com.fitpal.app.ui.component.TrackerSection
import com.fitpal.app.ui.component.TrackerSheet
import com.fitpal.app.ui.component.WeekBars
import com.fitpal.app.ui.component.ruleColor
import com.fitpal.app.ui.component.ruleShortName
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.accentGlass
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val INFO_TEXT =
    "Each limit is a daily calorie cap for one kind of food. When you log something, FitPal works out " +
        "which limits it counts toward — the AI when you're online, a careful keyword list when you're " +
        "not — and adds its calories there.\n\nIf something was counted that shouldn't be, tap its tag " +
        "here to take it out; the totals follow. To change what counts in general, add a note to the " +
        "limit in Settings → Food limits."

private fun iconFor(kind: DietaryRuleKind): ImageVector = com.fitpal.app.ui.component.ruleIcon(kind)

/**
 * The Food limits page (docs/redesign/09-fasting-weight-limits-pages.md): one chip per limit that's
 * on, the closest one's verdict, the chosen limit's week, and what counted today — where a wrong tag
 * can be taken off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LimitsScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenEntry: (Long) -> Unit = {},
    onOpenDay: (LocalDate) -> Unit = {},
    viewModel: LimitsViewModel = hiltViewModel()
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val consumed by viewModel.consumed.collectAsStateWithLifecycle()
    val items by viewModel.taggedItems.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val isToday = viewModel.isToday
    var untag by remember { mutableStateOf<Pair<MealLogItemEntity, DietaryRuleKind>?>(null) }

    TrackerScaffold(
        title = "Food limits",
        onBack = onBack,
        infoTitle = "How food limits work",
        infoText = INFO_TEXT,
        onOpenSettings = onOpenSettings
    ) {
        if (rules.isEmpty()) {
            TrackerSection(title = "No limits are on") {
                Text("Set a daily cap for desserts, fried food or sugary drinks to see them here.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                Spacer(Modifier.height(12.dp))
                GoldButton("Open food limits", onClick = onOpenSettings)
            }
            return@TrackerScaffold
        }
        val snapshots = rules.associate { rule ->
            rule.kind to Trackers.limit(ruleShortName(rule.kind), consumed[rule.kind] ?: 0, rule.dailyLimitKcal)
        }

        // ---- One chip per limit, and the verdict of the closest one ----
        TrackerHero(visual = null) {
            if (!isToday) Text(viewModel.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), style = MaterialTheme.typography.labelLarge, color = CreamMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rules.forEach { rule ->
                    val s = snapshots.getValue(rule.kind)
                    TrackerChip(
                        icon = iconFor(rule.kind), color = ruleColor(rule.kind),
                        value = s.shortDisplay, label = ruleShortName(rule.kind),
                        fraction = s.fraction, status = s.status?.kind,
                        selected = rule.kind == selected,
                        onClick = { viewModel.select(rule.kind) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            val closest = snapshots.values.maxByOrNull { it.fraction ?: 0f }
            val status = closest?.status?.let { st ->
                if (st.kind == StatusKind.OK) TrackerStatus(StatusKind.OK, if (isToday) "Every limit has room today" else "Every limit had room") else st
            }
            status?.let { StatusPill(it) }
        }

        // ---- The selected limit's week ----
        selected?.let { kind ->
            val rule = rules.firstOrNull { it.kind == kind } ?: return@let
            val days = week.filterNotNull()
            val over = days.count { it > rule.dailyLimitKcal }
            TrackerSection(title = "This week · ${ruleShortName(kind)}") {
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    WeekBars(
                        values = week,
                        goal = rule.dailyLimitKcal.toFloat(),
                        goalKind = GoalKind.LIMIT,
                        color = ruleColor(kind),
                        todayIndex = if (isToday) (LocalDate.now().toEpochDay() - viewModel.weekStart.toEpochDay()).toInt().takeIf { it in 0..6 } else null,
                        goalLabel = "cap ${rule.dailyLimitKcal}",
                        onDay = { i ->
                            val d = viewModel.weekStart.plusDays(i.toLong())
                            if (!d.isAfter(LocalDate.now()) && d != viewModel.date) onOpenDay(d)
                        }
                    )
                }
                if (days.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (over == 0) "Under the cap every day so far" else "Over the cap on $over of ${days.size} days",
                        style = MaterialTheme.typography.bodySmall, color = CreamMuted
                    )
                }
            }
        }

        // ---- What counted ----
        TrackerSection(title = if (isToday) "What counted today" else "What counted that day") {
            val enabledKinds = rules.map { it.kind }.toSet()
            val shown = items.filter { DietaryRuleKind.tagsFromDb(it.ruleTags).any { k -> k in enabledKinds } }
            if (shown.isEmpty()) {
                Text("Nothing counted toward a limit.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
            } else {
                shown.forEach { item ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { onOpenEntry(item.id) }, verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            // A dish may count only in part (the fried bit of a bigger plate) — say how much.
                            val counted = DietaryRuleKind.tagsFromDb(item.ruleTags).filter { it in enabledKinds }
                                .maxOfOrNull { DietaryTally.countedKcal(item, it) } ?: item.calories
                            Text(
                                if (counted < item.calories - 0.5f) "${counted.roundToInt()} of ${item.calories.roundToInt()} kcal"
                                else "${item.calories.roundToInt()} kcal",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Cream
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DietaryRuleKind.tagsFromDb(item.ruleTags).filter { it in enabledKinds }.forEach { kind ->
                                Text(
                                    ruleShortName(kind) + " ×",
                                    style = MaterialTheme.typography.labelMedium, color = Cream,
                                    modifier = Modifier
                                        .accentGlass(ruleColor(kind), RoundedCornerShape(50))
                                        .clickable { untag = item to kind }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    untag?.let { (item, kind) ->
        TrackerSheet(title = "Doesn't count as ${ruleShortName(kind).lowercase()}?", onDismiss = { untag = null }) {
            Text(
                "${item.name} will stop counting toward your ${ruleShortName(kind).lowercase()} limit. Its calories still count for the day.",
                style = MaterialTheme.typography.bodyMedium, color = CreamMuted
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Keep it", onClick = { untag = null }, modifier = Modifier.weight(1f))
                GoldButton("Take it out", onClick = { viewModel.removeTag(item, kind); untag = null }, modifier = Modifier.weight(1f))
            }
        }
    }
}
