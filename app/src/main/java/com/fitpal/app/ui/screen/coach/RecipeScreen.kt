package com.fitpal.app.ui.screen.coach

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.local.entity.PlannedMealEntity
import com.fitpal.app.ui.component.AddIngredientDialog
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.GramsField
import com.fitpal.app.ui.component.LogBar
import com.fitpal.app.ui.component.MacroRingsRow
import com.fitpal.app.ui.component.SetupPill
import com.fitpal.app.ui.component.rememberFastingGuard
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.MacroOver
import com.fitpal.app.ui.theme.OnGold
import com.fitpal.app.ui.theme.ScoreFair
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft
import kotlin.math.roundToInt

/**
 * A planned meal's recipe (docs/redesign/10): what it brings per serving and whether it fits the day,
 * the ingredients (have / buy, editable — the numbers follow), numbered steps with a cook mode that
 * keeps the screen on, and Log at the bottom, through the fasting check like every log.
 */
@Composable
fun RecipeScreen(
    onBack: () -> Unit,
    onLogged: () -> Unit,
    viewModel: RecipeViewModel = hiltViewModel()
) {
    val entity by viewModel.entity.collectAsStateWithLifecycle()
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    val left by viewModel.leftToday.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val aiAdding by viewModel.aiAdding.collectAsStateWithLifecycle()
    val logged by viewModel.logged.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val fastingGuard = rememberFastingGuard()
    var adding by remember { mutableStateOf(false) }
    var cookMode by remember { mutableStateOf(false) }

    LaunchedEffect(logged) { if (logged) onLogged() }

    // Cook mode keeps the screen awake while you follow the steps.
    val view = LocalView.current
    DisposableEffect(cookMode) {
        view.keepScreenOn = cookMode
        onDispose { view.keepScreenOn = false }
    }

    if (adding) {
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(aiAdding) { if (aiAdding) started = true else if (started) { started = false; adding = false } }
        AddIngredientDialog(
            query = query, results = results, isDrink = false, aiLoading = aiAdding,
            onQueryChange = viewModel::onSearch,
            onPick = { viewModel.addFromDatabase(it); adding = false },
            onAiAdd = viewModel::addWithAi,
            onDismiss = { viewModel.clearSearch(); adding = false }
        )
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "Recipe",
                onBack = onBack,
                actions = {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .then(if (cookMode) Modifier.background(GoldLight) else Modifier.glass(CircleShape))
                            .clickable { cookMode = !cookMode },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SoupKitchen,
                            contentDescription = if (cookMode) "Leave cook mode" else "Cook mode — bigger text, screen stays on",
                            tint = if (cookMode) OnGold else Cream, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
            val r = recipe
            if (r == null) {
                Box(Modifier.weight(1f))
            } else LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.name, style = MaterialTheme.typography.headlineSmall, color = Cream)
                        Text(
                            listOfNotNull(
                                r.cuisine.takeIf { it.isNotBlank() },
                                r.timeMinutes.takeIf { it > 0 }?.let { "$it min" },
                                if (r.servings == 1) "1 serving" else "makes ${r.servings} servings",
                                r.difficulty.takeIf { it.isNotBlank() }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                        )
                        viewModel.source?.let { Spacer(Modifier.height(4.dp)); AiSourceBadge(it) }
                    }
                }
                if (r.allergens.isNotEmpty()) item {
                    Text(
                        "Contains ${r.allergens.joinToString(", ") { it.lowercase() }} — remove it below before you schedule or eat this.",
                        style = MaterialTheme.typography.bodyMedium, color = MacroOver,
                        modifier = Modifier.fillMaxWidth().glassSoft().padding(14.dp)
                    )
                }

                // ---- Per serving, and whether it fits today ----
                item {
                    Column(Modifier.fillMaxWidth().glass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${r.kcal.roundToInt()} kcal per serving", style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.weight(1f))
                            left?.let { l ->
                                val fits = r.kcal <= l
                                Text(
                                    if (fits) "Fits what's left today" else "${(r.kcal - l).roundToInt()} kcal over what's left",
                                    style = MaterialTheme.typography.labelMedium, color = if (fits) ScoreFair else Gold
                                )
                            }
                        }
                        targets?.let { t ->
                            MacroRingsRow(
                                protein = r.protein, proteinTarget = t.proteinG.toFloat(),
                                fat = r.fat, fatTarget = t.fatG.toFloat(),
                                carbs = r.carbs, carbTarget = t.carbsG.toFloat(),
                                fiber = r.fiber, fiberTarget = t.fiberG.toFloat()
                            )
                        }
                        if (r.whyItFits.isNotBlank()) Text("“${r.whyItFits}”", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    }
                }

                // ---- Ingredients ----
                item { Text("Ingredients", style = MaterialTheme.typography.titleMedium, color = Cream) }
                itemsIndexed(r.ingredients, key = { i, ri -> "$i-${ri.ingredient.name}" }) { i, ri ->
                    Row(Modifier.fillMaxWidth().glassSoft().padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ri.ingredient.name, style = MaterialTheme.typography.bodyLarge, color = Cream, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (ri.have) "you have it" else "to buy") + " · ${ri.ingredient.calories.roundToInt()} kcal",
                                style = MaterialTheme.typography.labelSmall, color = if (ri.have) CreamFaint else GoldLight
                            )
                        }
                        GramsField(grams = ri.ingredient.grams, onGramsChanged = { viewModel.setGrams(i, it) }, modifier = Modifier.width(96.dp))
                        Icon(
                            Icons.Default.Close, contentDescription = "Remove ${ri.ingredient.name}", tint = CreamMuted,
                            modifier = Modifier.size(40.dp).clip(CircleShape).clickable { viewModel.remove(i) }.padding(10.dp)
                        )
                    }
                }
                item { SetupPill("+ Add ingredient", selected = false) { adding = true } }

                // ---- Steps (numbering matters here) ----
                if (r.steps.isNotEmpty()) {
                    item { Text("Steps", style = MaterialTheme.typography.titleMedium, color = Cream) }
                    itemsIndexed(r.steps) { i, step ->
                        Row(Modifier.fillMaxWidth().glassSoft().padding(14.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(if (cookMode) 32.dp else 26.dp).clip(CircleShape).background(GoldLight), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = OnGold, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                step,
                                style = if (cookMode) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                                color = Cream, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (r.tips.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth().glassSoft().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Tips", style = MaterialTheme.typography.labelMedium, color = GoldLight)
                        r.tips.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Cream) }
                    }
                }
            }

            val isLogged = entity?.status == PlannedMealEntity.LOGGED
            LogBar(
                totalKcal = recipe?.kcal?.roundToInt(),
                status = when {
                    isLogged -> "Logged"
                    saving -> "Saving…"
                    else -> null
                },
                actionLabel = if (saving) "Saving…" else "Log",
                actionEnabled = !isLogged && !saving && recipe?.ingredients?.isNotEmpty() == true,
                onAction = {
                    val food = viewModel.asFood() ?: return@LogBar
                    fastingGuard.attempt(
                        isForToday = viewModel.logDate == java.time.LocalDate.now(),
                        intake = com.fitpal.app.domain.model.FastingIntake.ofFoods(listOf(food))
                    ) { decision -> viewModel.log(decision) }
                }
            )
        }
    }
}
