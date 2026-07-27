package com.fitpal.app.ui.screen.garden

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.data.local.entity.CollectedPlantEntity
import com.fitpal.app.domain.GardenDisplay
import com.fitpal.app.domain.GardenRules
import com.fitpal.app.domain.PlantCatalog
import com.fitpal.app.domain.PlantRarity
import com.fitpal.app.domain.PlantSpecies
import com.fitpal.app.domain.WiltState
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.theme.AccentGarden
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.glass

@Composable
fun GardenScreen(
    onBack: () -> Unit,
    viewModel: GardenViewModel = hiltViewModel()
) {
    val display by viewModel.display.collectAsStateWithLifecycle()
    val collection by viewModel.collection.collectAsStateWithLifecycle()
    val celebration by viewModel.celebration.collectAsStateWithLifecycle()
    val weekProgress by viewModel.weekProgress.collectAsStateWithLifecycle()

    GradientBackdrop(theme = BackdropTheme.GARDEN) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Garden", onBack = onBack)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                display?.let { d ->
                    PlantHeroCard(d, onWater = viewModel::waterPlant)
                    StatsRow(d)
                }
                weekProgress?.let { WeekProgressCard(it) }
                CollectionCard(collection)
                HowItWorksCard()
            }
        }
    }

    if (celebration.isNotEmpty()) {
        BloomCelebrationDialog(plants = celebration, onDismiss = viewModel::dismissCelebration)
    }
}

@Composable
private fun PlantHeroCard(d: GardenDisplay, onWater: () -> Unit) {
    val emoji = if (d.wilt == WiltState.WITHERING) "🥀" else d.stage.emoji
    Column(
        modifier = Modifier.fillMaxWidth().glass().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val infinite = rememberInfiniteTransition(label = "plant")
        val breathe by infinite.animateFloat(
            initialValue = 0.96f, targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "breathe"
        )
        val sway by infinite.animateFloat(
            initialValue = -3f, targetValue = 3f,
            animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Reverse), label = "sway"
        )
        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(Color(0x3358B85A), Color(0x110E1E10)))
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji, fontSize = 64.sp,
                modifier = Modifier.graphicsLayer {
                    scaleX = breathe; scaleY = breathe; rotationZ = sway
                    transformOrigin = TransformOrigin(0.5f, 0.9f)
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(d.stage.label, style = MaterialTheme.typography.titleLarge, color = Cream)
        Text(wiltMessage(d.wilt), style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
        Spacer(Modifier.height(16.dp))
        val animatedProgress by animateFloatAsState(
            targetValue = (d.growthPoints.toFloat() / d.bloomCost.coerceAtLeast(1)).coerceIn(0f, 1f),
            animationSpec = tween(600), label = "growth"
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            color = AccentGarden
        )
        Spacer(Modifier.height(6.dp))
        Text("${d.growthPoints} / ${d.bloomCost} to bloom", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        Spacer(Modifier.height(14.dp))
        if (d.canWaterToday) {
            Button(onClick = onWater) { Text("💧 Water  (+${GardenRules.WATER_POINTS} ⭐)") }
        } else {
            Text(
                text = if (d.water <= 0) "Out of 💧 — log a meal to earn more" else "Watered today ✓",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Cream
            )
        }
        if (d.onGoalToday) {
            Spacer(Modifier.height(8.dp))
            StatusPill(text = "🌟 On goal today", highlight = true)
        }
    }
}

@Composable
private fun StatusPill(text: String, highlight: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (highlight) AccentGarden.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            color = if (highlight) AccentGarden else CreamMuted
        )
    }
}

@Composable
private fun StatsRow(d: GardenDisplay) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(modifier = Modifier.weight(1f), big = "💧 ${d.water}", label = "Water")
        StatCard(modifier = Modifier.weight(1f), big = "⭐ ${d.points}", label = "Points")
        StatCard(modifier = Modifier.weight(1f), big = "🔥 ${d.streak}", label = "Streak")
        StatCard(modifier = Modifier.weight(1f), big = "🌸 ${d.bloomCount}", label = "Plants")
    }
}

@Composable
private fun WeekProgressCard(wp: com.fitpal.app.domain.WeekProgress) {
    val met = wp.loggedDays >= wp.neededDays
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(
                text = when {
                    met && wp.onTrackSoFar -> "🏆 Bonus locked in"
                    wp.onTrackSoFar -> "On track"
                    else -> "Off track"
                },
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = if (wp.onTrackSoFar) AccentGarden else MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(8.dp))
        val progress by animateFloatAsState(
            targetValue = (wp.loggedDays.toFloat() / wp.neededDays.coerceAtLeast(1)).coerceIn(0f, 1f),
            animationSpec = tween(500), label = "weekprog"
        )
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = AccentGarden)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${wp.loggedDays} of ${wp.neededDays} days logged" +
                if (met && wp.onTrackSoFar) " — keep the week's average where it is!"
                else if (!met) " — log ${wp.neededDays - wp.loggedDays} more to qualify" else "",
            style = MaterialTheme.typography.bodySmall, color = CreamMuted
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier, big: String, label: String) {
    Column(
        modifier = modifier.glass().padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(big, style = MaterialTheme.typography.titleMedium, color = Cream, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionCard(collection: List<CollectedPlantEntity>) {
    val counts = collection.groupingBy { it.speciesId }.eachCount()
    val discovered = counts.keys.count { PlantCatalog.byId(it) != null }
    val total = PlantCatalog.ALL.size
    val pct = if (total > 0) discovered * 100 / total else 0

    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Collection", style = MaterialTheme.typography.titleMedium, color = Cream)
        Text("$discovered of $total species · $pct% complete", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PlantCatalog.ALL.forEach { species ->
                CatalogChip(species = species, count = counts[species.id] ?: 0)
            }
        }
    }
}

@Composable
private fun CatalogChip(species: PlantSpecies, count: Int) {
    val found = count > 0
    val color = rarityColor(species.rarity)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (found) color.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f)
    ) {
        Column(
            modifier = Modifier.size(width = 74.dp, height = 88.dp).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(if (found) species.emoji else "❓", fontSize = 30.sp)
            Text(
                text = if (found) species.name else "Locked",
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1,
                color = if (found) color else CreamMuted
            )
            Text(
                text = if (found && count > 1) "×$count" else species.rarity.label,
                style = MaterialTheme.typography.labelSmall, color = CreamMuted
            )
        }
    }
}

@Composable
private fun BloomCelebrationDialog(plants: List<CollectedPlantEntity>, onDismiss: () -> Unit) {
    val species = plants.mapNotNull { PlantCatalog.byId(it.speciesId) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(
        targetValue = if (shown) 1f else 0.3f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "pop"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Lovely!") } },
        title = { Text(if (species.size > 1) "New blooms! 🎉" else "A new bloom! 🎉") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = species.joinToString(" ") { it.emoji }.ifEmpty { "🌸" }, fontSize = 44.sp, modifier = Modifier.scale(pop))
                Spacer(Modifier.height(8.dp))
                species.forEach { s ->
                    Text("${s.name} — ${s.rarity.label}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = rarityColor(s.rarity))
                }
                Spacer(Modifier.height(8.dp))
                Text("Pressed into your collection. A fresh seed is already growing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
private fun HowItWorksCard() {
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("How your garden grows", style = MaterialTheme.typography.titleSmall, color = Cream)
        Spacer(Modifier.height(6.dp))
        Text(
            "• Logging food earns 💧 water — on-goal days earn extra.\n" +
                "• Tap 💧 Water once a day to grow your plant and earn ⭐ points.\n" +
                "• Forget, and it auto-waters from your reserve — but no points. Run out of 💧 and it wilts.\n" +
                "• Every bloom joins your collection and a fresh seed starts.",
            style = MaterialTheme.typography.bodySmall, color = CreamMuted
        )
    }
}

private fun wiltMessage(wilt: WiltState): String = when (wilt) {
    WiltState.HEALTHY -> "Thriving 🌞"
    WiltState.DROOPING -> "A little droopy — log today to perk it up"
    WiltState.WILTING -> "Wilting — it misses you"
    WiltState.WITHERING -> "Withering — log today to save it"
}

private fun rarityColor(rarity: PlantRarity): Color = when (rarity) {
    PlantRarity.COMMON -> Color(0xFF9AA38F)
    PlantRarity.UNCOMMON -> Color(0xFF6FD08A)
    PlantRarity.RARE -> Color(0xFF6FA8FF)
    PlantRarity.LEGENDARY -> Color(0xFFC79BF0)
}
