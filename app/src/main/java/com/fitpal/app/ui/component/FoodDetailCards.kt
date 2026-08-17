package com.fitpal.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.Ingredient
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.domain.model.Micronutrients
import com.fitpal.app.ml.AiSource
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

/**
 * The cards a "food detail" screen is made of, shared by the logged-item detail
 * ([com.fitpal.app.ui.screen.entrydetail.EntryDetailScreen]) and the saved-food detail
 * ([com.fitpal.app.ui.screen.gallery.GalleryFoodDetailScreen]). The two screens differ (a logged
 * item's edits hit your log; a saved food is a template), but the cards don't — so they live here
 * once. Optional slots ([IngredientsCard]'s total-weight scaler and edit-with-AI) appear only for
 * the callers that pass them.
 */

/** The editable ingredient list. Tap a name to swap it; edit grams inline; add/remove. */
@Composable
fun IngredientsCard(
    ingredients: List<Ingredient>,
    isDrink: Boolean,
    onGramsChanged: (index: Int, newGrams: Float) -> Unit,
    onRemove: (index: Int) -> Unit,
    onReplace: (index: Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set (with [onScaleTo] and >1 ingredient), shows a whole-dish weight that scales all. */
    totalGrams: Float? = null,
    onScaleTo: ((Float) -> Unit)? = null,
    /** When set, shows an "Edit with AI" button beside "Add ingredient". */
    onEditWithAi: (() -> Unit)? = null
) {
    val unit = if (isDrink) "ml" else "g"
    Column(modifier = modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Ingredients", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(bottom = 8.dp))
        // Whole-dish size: edit the total to scale every ingredient together.
        if (ingredients.size > 1 && totalGrams != null && onScaleTo != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total weight", style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f))
                GramsField(grams = totalGrams, onGramsChanged = onScaleTo, modifier = Modifier.width(100.dp), unit = unit)
            }
        }
        ingredients.forEachIndexed { index, ingredient ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Tap the name to swap this ingredient for a different one (keeps the amount).
                Row(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onReplace(index) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(ingredient.name, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Change ${ingredient.name}", tint = CreamMuted, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(6.dp))
                GramsField(
                    grams = ingredient.grams,
                    onGramsChanged = { onGramsChanged(index, it) },
                    modifier = Modifier.width(82.dp),
                    unit = unit
                )
                Spacer(Modifier.width(8.dp))
                Text("${ingredient.calories.toInt()} kcal", style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.width(56.dp))
                IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${ingredient.name}", tint = CreamMuted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.glassSoft(CircleShape).clickable(onClick = onAdd).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add ingredient", style = MaterialTheme.typography.labelLarge, color = Cream)
            }
            // The same whole-dish correction the pre-log screen offers — a mistake shouldn't become
            // permanent just because the meal was already saved.
            if (onEditWithAi != null) {
                Row(
                    modifier = Modifier.glassSoft(CircleShape).clickable(onClick = onEditWithAi).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit with AI", style = MaterialTheme.typography.labelLarge, color = Cream)
                }
            }
        }
    }
}

/** Vitamins & minerals for this food — collapsed by default, only the ones actually present. */
@Composable
fun MicronutrientCard(micros: Micronutrients) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().glass().clickable { expanded = !expanded }.padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vitamins & minerals", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Show vitamins & minerals",
                tint = CreamMuted
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(14.dp))
                MicronutrientBars(micros, showAll = false)
            }
        }
    }
}

/**
 * The AI-analysis block: a spinner while generating, the full [MealInsightsSection] once ready (with
 * a refresh action), or a "generate" prompt. [subject] is the word used in the empty state ("meal"
 * for a logged item, "food" for a saved one).
 */
@Composable
fun AiInsightsArea(
    isLoadingAi: Boolean,
    insights: MealInsights?,
    insightsSource: AiSource?,
    modelReady: Boolean,
    breakdown: List<HealthScorer.Dimension>,
    onGenerate: () -> Unit,
    onRegenerate: () -> Unit,
    subject: String = "meal"
) {
    when {
        isLoadingAi -> {
            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("AI analysis", style = MaterialTheme.typography.titleSmall, color = Cream)
                }
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("Analysing...", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
        }
        insights != null -> {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MealInsightsSection(insights, breakdown = breakdown)
                insightsSource?.let { AiSourceBadge(it) }
                if (modelReady) {
                    Row(
                        modifier = Modifier.glassSoft(CircleShape).clickable(onClick = onRegenerate).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh analysis", style = MaterialTheme.typography.labelLarge, color = Cream)
                    }
                }
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("AI analysis", style = MaterialTheme.typography.titleSmall, color = Cream)
                }
                Spacer(Modifier.height(10.dp))
                if (modelReady) {
                    Row(
                        modifier = Modifier.glassSoft(CircleShape).clickable(onClick = onGenerate).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generate full AI analysis", style = MaterialTheme.typography.labelLarge, color = Cream)
                    }
                } else {
                    Text("Set up the AI model to analyse this $subject.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                }
            }
        }
    }
}
