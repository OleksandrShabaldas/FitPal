package com.fitpal.app.ui.screen.coach

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.PlanGoal
import com.fitpal.app.domain.model.PlanRequest
import com.fitpal.app.domain.model.PlanScope
import com.fitpal.app.ui.component.DatePickerDialog
import com.fitpal.app.ui.component.GlassButton
import com.fitpal.app.ui.component.GoldButton
import com.fitpal.app.ui.component.SetupPill
import com.fitpal.app.ui.component.TrackerSheet
import com.fitpal.app.ui.component.logDateLabel
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.glassSoft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The form's steps; Budget only shows when they can buy more. */
private enum class PlanStep(val title: String) {
    WHAT("What to plan"), HAVE("What you have"), BUDGET("Budget"), FEEL("What do you feel like?"),
    GOAL("What's the meal for?"), CUISINE("Cuisine"), TIME("Cooking time"), SERVINGS("Servings")
}

/**
 * The short form before each plan (docs/redesign/10): one question per step, a progress bar, Back
 * and Next — and "Generate" from the very first step, since every later step has a default. Starts
 * from the last request.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanRequestSheet(
    initial: PlanRequest,
    favouriteCuisines: Set<String>,
    recentIngredients: List<String>,
    leftToday: Pair<Int, Int>?,
    onlineAvailable: Boolean,
    onGenerate: (PlanRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var req by remember { mutableStateOf(initial) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var pickingDay by remember { mutableStateOf(false) }
    val steps = PlanStep.entries.filter { it != PlanStep.BUDGET || !req.onlyHave }
    val step = steps[stepIndex.coerceIn(0, steps.size - 1)]

    if (pickingDay) {
        DatePickerDialog(
            initialDate = req.date,
            title = if (req.scope == PlanScope.WEEK) "Start the week on" else "Plan for which day?",
            confirmLabel = "Use this day",
            onConfirm = { d -> req = req.copy(date = d); pickingDay = false },
            onDismiss = { pickingDay = false }
        )
    }

    TrackerSheet(title = step.title, onDismiss = onDismiss) {
        // Progress: one segment per step.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEachIndexed { i, _ ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50))
                        .background(if (i <= stepIndex) GoldLight else filmColor(0.12f))
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (step) {
                PlanStep.WHAT -> {
                    Pills(PlanScope.entries.map { it.label }, req.scope.label) { label ->
                        val scope = PlanScope.entries.first { it.label == label }
                        req = req.copy(scope = scope, date = if (scope == PlanScope.WEEK) LocalDate.now() else req.date)
                    }
                    Label(if (req.scope == PlanScope.WEEK) "Starting" else "For")
                    val today = LocalDate.now()
                    val options = if (req.scope == PlanScope.WEEK) {
                        listOf("Today" to today, "Next Monday" to today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)))
                    } else listOf("Today" to today, "Tomorrow" to today.plusDays(1))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { (label, d) -> SetupPill(label, selected = req.date == d) { req = req.copy(date = d) } }
                        SetupPill(
                            if (options.none { it.second == req.date }) logDateLabel(req.date) else "Pick a day",
                            selected = options.none { it.second == req.date }
                        ) { pickingDay = true }
                    }
                    if (req.scope == PlanScope.WEEK && !onlineAvailable) {
                        Text(
                            "Online AI isn't available, so your phone will plan the week — that takes a while.",
                            style = MaterialTheme.typography.bodySmall, color = CreamFaint
                        )
                    }
                }

                PlanStep.HAVE -> {
                    ChipInput(
                        chips = req.have,
                        placeholder = "e.g. chicken, rice, spinach",
                        onAdd = { req = req.copy(have = (req.have + it).distinct()) },
                        onRemove = { req = req.copy(have = req.have - it) }
                    )
                    val offer = recentIngredients.filter { it !in req.have }.take(8)
                    if (offer.isNotEmpty()) {
                        Label("From what you've logged")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            offer.forEach { name -> SetupPill("+ $name", selected = false) { req = req.copy(have = req.have + name) } }
                        }
                    }
                    Label("Shopping")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SetupPill("Only use what I have", selected = req.onlyHave) { req = req.copy(onlyHave = true) }
                        SetupPill("I can buy more", selected = !req.onlyHave) { req = req.copy(onlyHave = false) }
                    }
                }

                PlanStep.BUDGET -> {
                    NumberLine(
                        value = req.budget?.let { if (it % 1f == 0f) it.toInt().toString() else "%.2f".format(it) }.orEmpty(),
                        placeholder = "No budget",
                        onValue = { req = req.copy(budget = it.replace(',', '.').toFloatOrNull()?.takeIf { v -> v > 0f }) }
                    )
                    Pills(listOf("meal", "day", "week"), req.budgetPer, label = { "per $it" }) { req = req.copy(budgetPer = it) }
                }

                PlanStep.FEEL -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanRequest.FEEL_PILLS.forEach { p ->
                            SetupPill(p, selected = p in req.feelPills) {
                                req = req.copy(feelPills = if (p in req.feelPills) req.feelPills - p else req.feelPills + p)
                            }
                        }
                    }
                    TextLine(req.feel, "Anything else? e.g. something with eggs") { req = req.copy(feel = it) }
                }

                PlanStep.GOAL -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanGoal.entries.forEach { g -> SetupPill(g.label, selected = req.goal == g) { req = req.copy(goal = g) } }
                    }
                    if (req.goal == PlanGoal.FIT_LEFT) {
                        Text(
                            leftToday?.let { (kcal, protein) -> "Left today: ${"%,d".format(kcal)} kcal and $protein g protein." }
                                ?: "Log a weight to see what's left today.",
                            style = MaterialTheme.typography.bodySmall, color = CreamMuted
                        )
                    }
                }

                PlanStep.CUISINE -> {
                    val all = favouriteCuisines.toList() + com.fitpal.app.domain.model.FoodProfile.CUISINES.filter { it !in favouriteCuisines }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SetupPill("Any", selected = req.cuisine == null) { req = req.copy(cuisine = null) }
                        all.forEach { c -> SetupPill(c, selected = req.cuisine == c) { req = req.copy(cuisine = c) } }
                    }
                }

                PlanStep.TIME -> Pills(PlanRequest.TIMES.map { it.toString() }, req.timeMinutes.toString(), label = { if (it == "60") "60+ min" else "$it min" }) {
                    req = req.copy(timeMinutes = it.toInt())
                }

                PlanStep.SERVINGS -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundStep(Icons.Default.Remove, "Fewer") { req = req.copy(servings = (req.servings - 1).coerceAtLeast(1)) }
                        Text(
                            "${req.servings}", style = MaterialTheme.typography.headlineSmall, color = Cream,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        RoundStep(Icons.Default.Add, "More") { req = req.copy(servings = (req.servings + 1).coerceAtMost(8)) }
                    }
                    Text("Nutrition is always for the portion you eat.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (stepIndex > 0) GlassButton("Back", onClick = { stepIndex-- }, modifier = Modifier.weight(1f))
            if (stepIndex < steps.size - 1) GlassButton("Next", onClick = { stepIndex++ }, modifier = Modifier.weight(1f))
            GoldButton("Generate", onClick = { onGenerate(req) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = GoldLight)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pills(options: List<String>, selected: String, label: (String) -> String = { it }, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> SetupPill(label(o), selected = o == selected) { onPick(o) } }
    }
}

/** Type a word, press enter or +: it becomes a chip; tap a chip to take it off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipInput(chips: List<String>, placeholder: String, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun commit() {
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach(onAdd)
        text = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).glassSoft(RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = CreamFaint)
            BasicTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Cream),
                cursorBrush = SolidColor(GoldLight),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(8.dp))
        RoundStep(Icons.Default.Add, "Add") { commit() }
    }
    if (chips.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { c -> SetupPill("$c  ×", selected = true) { onRemove(c) } }
        }
    }
}

@Composable
private fun TextLine(value: String, placeholder: String, onValue: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().glassSoft(RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = CreamFaint)
        BasicTextField(
            value = value, onValueChange = onValue,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Cream),
            cursorBrush = SolidColor(GoldLight), modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NumberLine(value: String, placeholder: String, onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    Box(Modifier.fillMaxWidth().glassSoft(RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = CreamFaint)
        BasicTextField(
            value = text,
            onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' }.take(8); onValue(text) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = Cream, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(GoldLight),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RoundStep(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).glassSoft(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = Cream, modifier = Modifier.size(18.dp))
    }
}
