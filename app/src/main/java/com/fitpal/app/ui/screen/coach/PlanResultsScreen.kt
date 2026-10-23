package com.fitpal.app.ui.screen.coach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.local.entity.PlannedMealEntity
import com.fitpal.app.domain.model.MealTypes
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ml.PlanStatus
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.AnalysingStrip
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GoldButton
import com.fitpal.app.ui.component.GoldPill
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.InlineMessageCard
import com.fitpal.app.ui.component.MealTypeSelector
import com.fitpal.app.ui.component.SetupPill
import com.fitpal.app.ui.component.SkeletonCard
import com.fitpal.app.ui.component.TrackerSheet
import com.fitpal.app.ui.component.logDateLabel
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.MacroOver
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.glass
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * A plan's recipes (docs/redesign/10), grouped by day, each with Schedule · Log now · ⋯ (Save idea ·
 * Regenerate · Dismiss), the day's totals against the targets, and the shopping list. "Generated meals
 * stay on that screen" — until scheduled, logged or dismissed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanResultsScreen(
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    viewModel: PlanResultsViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val job by viewModel.job.collectAsStateWithLifecycle()
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    val shopping by viewModel.shopping.collectAsStateWithLifecycle()
    var scheduling by remember { mutableStateOf<PlannedItem?>(null) }
    var showShopping by remember { mutableStateOf(false) }

    val visible = items.filter { it.entity.status != PlannedMealEntity.DISMISSED }
    val byDay = visible.groupBy { it.entity.date ?: "" }.toSortedMap()
    val oneMealPlan = !viewModel.isSaved && visible.size == 1
    val ideasLeft = visible.count { it.entity.status == PlannedMealEntity.IDEA && it.recipe.allergens.isEmpty() }

    GradientBackdrop(theme = BackdropTheme.TRENDS) {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(title = if (viewModel.isSaved) "Saved ideas" else "Your plan", onBack = onBack)
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // The job, while this plan is still being made (or one meal regenerated).
                when (val s = job?.status) {
                    is PlanStatus.Running -> {
                        item { AnalysingStrip(message = s.message, source = s.source) }
                        if (visible.isEmpty()) items(2) { SkeletonCard() }
                    }
                    is PlanStatus.OnlineFailed -> item {
                        InlineMessageCard(
                            title = "Online AI isn't available", reason = s.reason,
                            primary = "Try online again" to { viewModel.retryOnline() },
                            secondary = "Use on-device" to { viewModel.useOnDevice() },
                            tertiary = "Cancel" to { viewModel.cancel() }
                        )
                    }
                    is PlanStatus.Failed -> if (visible.isEmpty()) item {
                        InlineMessageCard(title = "Couldn't make the plan", reason = s.message, primary = "Back" to onBack)
                    }
                    is PlanStatus.Done -> s.note?.let { note -> item { Text(note, style = MaterialTheme.typography.bodySmall, color = CreamMuted) } }
                    null -> Unit
                }

                visible.firstOrNull()?.entity?.let { e ->
                    AiSource.fromName(e.aiSource, e.aiModel)?.let { src -> item { AiSourceBadge(src) } }
                }

                if (visible.isEmpty() && job == null) item {
                    Text(
                        if (viewModel.isSaved) "Nothing saved yet. Save an idea from a plan with ⋯ → Save idea."
                        else "Nothing left in this plan.",
                        style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                    )
                }

                byDay.forEach { (day, meals) ->
                    if (byDay.size > 1 || meals.size > 1) item(key = "day_$day") {
                        val kcal = meals.sumOf { it.recipe.kcal.toDouble() }.roundToInt()
                        val protein = meals.sumOf { it.recipe.protein.toDouble() }.roundToInt()
                        Column(Modifier.padding(top = 6.dp)) {
                            Text(dayLabel(day), style = MaterialTheme.typography.headlineSmall, color = Cream)
                            Text(
                                "Day total: ${"%,d".format(kcal)} kcal · $protein g protein" +
                                    (targets?.let { " (targets ${"%,d".format(it.calories)} · ${it.proteinG})" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = CreamMuted
                            )
                        }
                    }
                    items(meals.sortedBy { MealTypes.ALL.indexOf(it.entity.mealType) }, key = { it.entity.id }) { item ->
                        PlanMealCard(
                            item = item,
                            onOpen = { onOpenRecipe(item.entity.id) },
                            onSchedule = {
                                // A one-meal plan (or a saved idea) asks which day; a day/week plan uses its own.
                                if (oneMealPlan || viewModel.isSaved) scheduling = item
                                else viewModel.schedule(
                                    item.entity.id,
                                    item.entity.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
                                    item.entity.mealType ?: item.recipe.mealType
                                )
                            },
                            onLogNow = { onOpenRecipe(item.entity.id) },
                            onSave = { viewModel.saveIdea(item.entity.id) },
                            onRegenerate = { viewModel.regenerate(item) },
                            onDismiss = { viewModel.dismiss(item.entity.id) },
                            showSave = !viewModel.isSaved
                        )
                    }
                }

                if (!viewModel.isSaved && ideasLeft > 1) item {
                    GoldButton("Schedule all", onClick = { viewModel.scheduleAll() }, modifier = Modifier.fillMaxWidth())
                }
                if (shopping.isNotEmpty()) item {
                    Row(
                        Modifier.fillMaxWidth().glass().clickable { showShopping = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Shopping list (${shopping.count { !it.checked }})", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CreamFaint)
                    }
                }
            }
        }
    }

    scheduling?.let { item -> ScheduleSheet(item, onDismiss = { scheduling = null }) { date, type -> viewModel.schedule(item.entity.id, date, type); scheduling = null } }

    if (showShopping) {
        TrackerSheet(title = "Shopping list", onDismiss = { showShopping = false }) {
            shopping.forEach { line ->
                Row(
                    Modifier.fillMaxWidth().clickable { viewModel.toggleShopping(line.name) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (line.checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = if (line.checked) "Got it" else "Still to buy",
                        tint = if (line.checked) GoldLight else CreamMuted, modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        line.name, style = MaterialTheme.typography.bodyLarge,
                        color = if (line.checked) CreamFaint else Cream,
                        textDecoration = if (line.checked) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${line.grams.roundToInt()} g", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                }
            }
        }
    }
}

private fun dayLabel(iso: String): String {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return "Saved"
    return when (d) {
        LocalDate.now() -> "Today"
        LocalDate.now().plusDays(1) -> "Tomorrow"
        else -> logDateLabel(d)
    }
}

/** One planned meal: type · name, kcal and protein per serving, time and difficulty, why it fits, actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanMealCard(
    item: PlannedItem,
    onOpen: () -> Unit,
    onSchedule: () -> Unit,
    onLogNow: () -> Unit,
    onSave: () -> Unit,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
    showSave: Boolean
) {
    val r = item.recipe
    val e = item.entity
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().glass().clickable(onClick = onOpen).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(MealTypes.label(e.mealType ?: r.mealType), style = MaterialTheme.typography.labelMedium, color = GoldLight)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, style = MaterialTheme.typography.titleMedium, color = Cream, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${r.kcal.roundToInt()} kcal · ${r.protein.roundToInt()} g", style = MaterialTheme.typography.labelLarge, color = CreamMuted)
        }
        Text(
            listOfNotNull(
                r.timeMinutes.takeIf { it > 0 }?.let { "$it min" },
                r.difficulty.takeIf { it.isNotBlank() },
                r.cuisine.takeIf { it.isNotBlank() }
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = CreamMuted
        )
        if (r.whyItFits.isNotBlank()) Text("“${r.whyItFits}”", style = MaterialTheme.typography.bodySmall, color = Cream)
        if (r.allergens.isNotEmpty()) {
            Text(
                "Contains ${r.allergens.joinToString(", ") { it.lowercase() }} — change it in the recipe before scheduling.",
                style = MaterialTheme.typography.bodySmall, color = MacroOver
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (e.status) {
                PlannedMealEntity.SCHEDULED -> Text("Scheduled · ${dayLabel(e.date ?: "")}", style = MaterialTheme.typography.labelLarge, color = ScoreFair)
                PlannedMealEntity.LOGGED -> Text("Logged", style = MaterialTheme.typography.labelLarge, color = ScoreFair)
                else -> {
                    GoldPill("Schedule", onSchedule, enabled = r.allergens.isEmpty())
                    SetupPill("Log now", selected = false, onClick = onLogNow)
                }
            }
            Spacer(Modifier.weight(1f))
            Box {
                Icon(
                    Icons.Default.MoreHoriz, contentDescription = "More", tint = Cream,
                    modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { menu = true }.padding(6.dp)
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (showSave && e.status == PlannedMealEntity.IDEA) DropdownMenuItem(text = { Text("Save idea") }, onClick = { menu = false; onSave() })
                    if (e.status != PlannedMealEntity.LOGGED) DropdownMenuItem(text = { Text("Regenerate") }, onClick = { menu = false; onRegenerate() })
                    DropdownMenuItem(text = { Text("Dismiss") }, onClick = { menu = false; onDismiss() })
                }
            }
        }
    }
}

/** Which day and meal to schedule on — prefilled from the plan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleSheet(item: PlannedItem, onDismiss: () -> Unit, onConfirm: (LocalDate, String) -> Unit) {
    val planned = item.entity.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { !it.isBefore(LocalDate.now()) } ?: LocalDate.now()
    var date by remember { mutableStateOf(planned) }
    var type by remember { mutableStateOf(item.entity.mealType ?: item.recipe.mealType) }
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        DatePickerDialog(initialDate = date, title = "Schedule for which day?", confirmLabel = "Use this day",
            onConfirm = { date = it; picking = false }, onDismiss = { picking = false })
    }
    TrackerSheet(title = "Schedule ${item.recipe.name}", onDismiss = onDismiss) {
        Text("Day", style = MaterialTheme.typography.labelMedium, color = GoldLight)
        Spacer(Modifier.height(6.dp))
        val today = LocalDate.now()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SetupPill("Today", selected = date == today) { date = today }
            SetupPill("Tomorrow", selected = date == today.plusDays(1)) { date = today.plusDays(1) }
            val other = date != today && date != today.plusDays(1)
            SetupPill(if (other) logDateLabel(date) else "Pick a day", selected = other) { picking = true }
        }
        Spacer(Modifier.height(12.dp))
        Text("Meal", style = MaterialTheme.typography.labelMedium, color = GoldLight)
        Spacer(Modifier.height(6.dp))
        MealTypeSelector(selected = type, onSelected = { type = it })
        Spacer(Modifier.height(16.dp))
        GoldButton("Schedule", onClick = { onConfirm(date, type) }, modifier = Modifier.fillMaxWidth())
    }
}
