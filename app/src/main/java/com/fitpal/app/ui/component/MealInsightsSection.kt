package com.fitpal.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitpal.app.domain.HealthScorer
import com.fitpal.app.domain.model.MealInsights
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.ScoreGreen
import com.fitpal.app.ui.theme.ScoreRed
import com.fitpal.app.ui.theme.ScoreYellow
import com.fitpal.app.ui.theme.glass

private fun levelColor(level: HealthScorer.Level): Color = when (level) {
    HealthScorer.Level.GOOD -> ScoreGreen
    HealthScorer.Level.OKAY -> ScoreYellow
    HealthScorer.Level.POOR -> ScoreRed
}

/** Sort order for the breakdown: problems first (red → yellow → green) so they group together. */
private fun levelRank(level: HealthScorer.Level): Int = when (level) {
    HealthScorer.Level.POOR -> 0
    HealthScorer.Level.OKAY -> 1
    HealthScorer.Level.GOOD -> 2
}

/**
 * The AI/health insights panel: a tappable health score with a full per-dimension
 * breakdown, healthier swaps, energy/mood meters, and pairing ideas.
 * Shared by Analysis and Entry-detail. Pass [breakdown] for the rich score view.
 */
@Composable
fun MealInsightsSection(insights: MealInsights, breakdown: List<HealthScorer.Dimension> = emptyList()) {
    var scoreExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- Health score ----
        Column(
            modifier = Modifier.fillMaxWidth().glass().clickable { scoreExpanded = !scoreExpanded }.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HealthScoreBadge(score = insights.healthScore)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Health score", style = MaterialTheme.typography.titleMedium, color = Cream)
                    Text(
                        text = if (scoreExpanded) "Tap to collapse" else "Tap to see the full breakdown",
                        style = MaterialTheme.typography.bodySmall, color = CreamMuted
                    )
                }
            }
            AnimatedVisibility(visible = scoreExpanded) {
                Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (breakdown.isNotEmpty()) {
                        breakdown.sortedBy { levelRank(it.level) }.forEach { d ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(levelColor(d.level)))
                                Spacer(Modifier.width(10.dp))
                                Text(d.label, style = MaterialTheme.typography.bodyMedium, color = Cream)
                                Spacer(Modifier.weight(1f))
                                Text(d.note, style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                            }
                        }
                    } else if (insights.scoreFactors.isNotEmpty()) {
                        insights.scoreFactors.sortedBy { it.isPositive }.forEach { factor ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape)
                                        .background(if (factor.isPositive) ScoreGreen else ScoreRed)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(factor.description, style = MaterialTheme.typography.bodyMedium, color = Cream)
                            }
                        }
                    } else {
                        Text("No breakdown available for this meal.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    }
                }
            }
        }

        // ---- Healthier swaps ----
        if (insights.healthSwaps.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = GoldLight, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Healthier swaps", style = MaterialTheme.typography.titleSmall, color = Cream)
                }
                Spacer(Modifier.height(8.dp))
                insights.healthSwaps.forEach { swap ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Text(swap.original, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.width(100.dp))
                        Text("→ ", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                        Column {
                            Text(swap.suggestion, style = MaterialTheme.typography.bodyMedium, color = ScoreGreen, fontWeight = FontWeight.SemiBold)
                            if (swap.benefit.isNotEmpty()) {
                                // Models sometimes emphasise with **stars** — render it, don't show it.
                                MarkdownText(
                                    swap.benefit,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CreamMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Energy & mood meters ----
        val hasEnergy = insights.energyImpact.isNotEmpty() || insights.energyScore > 0
        val hasMood = insights.moodImpact.isNotEmpty() || insights.moodScore > 0
        if (hasEnergy || hasMood) {
            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Text("How this meal affects you", style = MaterialTheme.typography.titleSmall, color = Cream)
                if (hasEnergy) ImpactMeter("Energy", insights.energyScore, insights.energyImpact)
                if (hasMood) ImpactMeter("Mood", insights.moodScore, insights.moodImpact)
            }
        }

        // ---- Pairings ----
        if (insights.pairingRecommendations.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                Text("Goes well with", style = MaterialTheme.typography.titleSmall, color = Cream)
                Spacer(Modifier.height(8.dp))
                insights.pairingRecommendations.forEach { suggestion ->
                    MarkdownText(
                        "• $suggestion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CreamMuted,
                        bulletColor = CreamMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ImpactMeter(label: String, score: Int, text: String) {
    val color = when {
        score >= 4 -> ScoreGreen
        score == 3 -> ScoreYellow
        score > 0 -> ScoreRed
        else -> CreamMuted
    }
    val word = when {
        score >= 4 -> "High"
        score == 3 -> "Moderate"
        score > 0 -> "Low"
        else -> ""
    }
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Cream)
        if (word.isNotEmpty()) Text(word, style = MaterialTheme.typography.labelLarge, color = color)
    }
    if (score > 0) {
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(Modifier.fillMaxWidth((score / 5f).coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(6.dp)).background(color))
        }
    }
    if (text.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        MarkdownText(text, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
    }
}

@Composable
fun HealthScoreBadge(score: Int) {
    val color = when {
        score >= 8 -> ScoreGreen
        score >= 5 -> ScoreYellow
        else -> ScoreRed
    }
    Box(
        modifier = Modifier.size(48.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text("$score", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1206))
    }
}
