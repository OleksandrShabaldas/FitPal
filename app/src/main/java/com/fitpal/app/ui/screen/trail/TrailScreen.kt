package com.fitpal.app.ui.screen.trail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.CaseCatalog
import com.fitpal.app.domain.CasePack
import com.fitpal.app.domain.CaseReward
import com.fitpal.app.domain.ChallengeView
import com.fitpal.app.domain.Curio
import com.fitpal.app.domain.CurioCatalog
import com.fitpal.app.domain.CurioRarity
import com.fitpal.app.domain.ProjectView
import com.fitpal.app.domain.SceneTheme
import com.fitpal.app.domain.ShopState
import com.fitpal.app.domain.ThemeCatalog
import com.fitpal.app.domain.TrailDisplay
import com.fitpal.app.domain.TrailFeature
import com.fitpal.app.domain.TrailRules
import com.fitpal.app.domain.TutorialText
import com.fitpal.app.domain.nextTutorialStep
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.component.SpotlightOverlay
import com.fitpal.app.ui.component.rememberSpotlightState
import com.fitpal.app.ui.component.spotlightTarget
import com.fitpal.app.ui.theme.AccentGarden
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

@Composable
fun TrailScreen(
    onBack: () -> Unit,
    viewModel: TrailViewModel = hiltViewModel()
) {
    val display by viewModel.display.collectAsStateWithLifecycle()
    val challenges by viewModel.challenges.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val shop by viewModel.shop.collectAsStateWithLifecycle()
    val reward by viewModel.lastReward.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val spotlight = rememberSpotlightState()
    val step = display?.let { nextTutorialStep(it) }

    GradientBackdrop(theme = BackdropTheme.GARDEN) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = display?.site?.name ?: "The trail", onBack = onBack)
            val d = display
            if (d == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Getting the trail ready…", style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val p = d.progression
                    val claimable = challenges.count { it.canClaim }

                    // Tabs appear one at a time as the game opens up.
                    val tabs = buildList {
                        add("Site")
                        if (p.has(TrailFeature.TASKS)) {
                            add(if (claimable > 0) "Tasks ($claimable)" else "Tasks")
                        }
                        if (p.has(TrailFeature.SHOP)) add("Shop")
                    }
                    val safeTab = tab.coerceAtMost(tabs.lastIndex)
                    if (tabs.size > 1) {
                        SegmentedPills(
                            labels = tabs,
                            selectedIndex = safeTab,
                            accent = AccentGarden,
                            onSelect = { tab = it },
                            modifier = Modifier.spotlightTarget(spotlight, TutorialText.TARGET_TABS)
                        )
                    }

                    when (tabs.getOrNull(safeTab)) {
                        "Site" -> {
                            if (p.has(TrailFeature.MAP)) {
                                TrailMapCard(
                                    d,
                                    modifier = Modifier.spotlightTarget(spotlight, TutorialText.TARGET_MAP)
                                )
                            }
                            SiteCard(d)
                            CollectCard(
                                d,
                                onCollect = viewModel::collect,
                                modifier = Modifier.spotlightTarget(spotlight, TutorialText.TARGET_COLLECT)
                            )
                            ResourceRow(
                                d,
                                modifier = Modifier.spotlightTarget(spotlight, TutorialText.TARGET_VITALITY)
                            )
                            ProjectsCard(
                                d,
                                onBuild = { viewModel.build(it) },
                                onAdvance = viewModel::advanceSite,
                                modifier = Modifier.spotlightTarget(spotlight, TutorialText.TARGET_PROJECTS)
                            )
                            NextUnlockHint(p)
                        }
                        else -> if (tabs.getOrNull(safeTab) == "Shop") {
                            ResourceRow(d)
                            shop?.let {
                                ShopSection(
                                    shop = it,
                                    onOpenCase = viewModel::openCase,
                                    onBuyTheme = viewModel::buyTheme,
                                    onEquipTheme = viewModel::equipTheme
                                )
                            }
                        } else {
                            ResourceRow(d)
                            ChallengesSection(
                                challenges = challenges,
                                checkingSlot = checking,
                                onClaim = viewModel::claim,
                                onCheck = viewModel::checkCreative
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    // Teach one thing at a time, only once each, and only when it becomes relevant.
    // Held back while a reward dialog is up so two overlays never stack.
    if (step != null && reward == null) {
        val copy = TutorialText.of(step)
        SpotlightOverlay(
            state = spotlight,
            targetId = copy.targetId,
            title = copy.title,
            body = copy.body,
            onDismiss = { viewModel.dismissTutorial(step) }
        )
    }

    reward?.let { RewardDialog(it, onDismiss = viewModel::dismissReward) }
}

/** A quiet nudge toward whatever opens up next. */
@Composable
private fun NextUnlockHint(progression: com.fitpal.app.domain.TrailProgression) {
    val label = progression.nextUnlockLabel() ?: return
    val remaining = progression.projectsUntilNextUnlock()
    val text = if (remaining > 0) {
        "Restore $remaining more ${if (remaining == 1) "thing" else "things"} to open $label."
    } else {
        "Claim a challenge to open $label."
    }
    Row(
        modifier = Modifier.fillMaxWidth().glassSoft().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔒", fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
    }
}

// ======================== SITE + SCENE ========================

/**
 * Phase-A scene: a simple lit strip — a horizon, and one slot per project that fills
 * in as you build. Phase B replaces this with the full diorama.
 */
@Composable
private fun SiteCard(d: TrailDisplay) {
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SITE ${d.siteNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = CreamFaint
            )
            Text(
                text = TrailRules.vitalityLabel(d.vitality),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (d.vitality >= 1f) AccentGarden else GoldLight
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(d.site.name, style = MaterialTheme.typography.headlineSmall, color = Cream)
        Text(d.site.blurb, style = MaterialTheme.typography.bodySmall, color = CreamMuted)

        Spacer(Modifier.height(14.dp))
        TrailScene(d)
        Spacer(Modifier.height(10.dp))

        Text(
            text = "${d.builtCount} of ${d.totalCount} restored" +
                if (d.requiredRemaining > 0) " · ${d.requiredRemaining} to move on" else " · ready to move on",
            style = MaterialTheme.typography.bodySmall,
            color = CreamMuted
        )
    }
}

// ======================== COLLECT ========================

@Composable
private fun CollectCard(d: TrailDisplay, onCollect: () -> Unit, modifier: Modifier = Modifier) {
    val banked by animateFloatAsState(
        targetValue = d.bankedGrowth.toFloat(),
        animationSpec = tween(600),
        label = "banked"
    )
    val canCollect = d.canCollect

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass()
            .then(if (canCollect) Modifier.clickable(onClick = onCollect) else Modifier)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (canCollect) "Waiting for you" else "Nothing to collect",
            style = MaterialTheme.typography.labelMedium,
            color = CreamFaint
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = banked.toLong().toString(),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 46.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            color = if (canCollect) AccentGarden else CreamMuted
        )
        Text("growth", style = MaterialTheme.typography.bodySmall, color = CreamMuted)

        Spacer(Modifier.height(12.dp))
        if (canCollect) {
            Box(
                modifier = Modifier
                    .glassSoft(RoundedCornerShape(50))
                    .clickable(onClick = onCollect)
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    "Collect  ·  +${TrailRules.COLLECT_POINTS} ⭐",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Cream
                )
            }
        } else {
            Text(
                text = when {
                    !d.loggedToday -> "Log a meal today to run the next day of growth"
                    d.water <= 0 -> "Out of 💧 — log to refill the reserve"
                    else -> "Today's growth is already collected. Back tomorrow."
                },
                style = MaterialTheme.typography.bodySmall,
                color = CreamMuted
            )
        }
    }
}

// ======================== RESOURCES ========================

@Composable
private fun ResourceRow(d: TrailDisplay, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat(Modifier.weight(1f), "🌿", d.growth.toString(), "growth")
        Stat(Modifier.weight(1f), "💧", d.water.toString(), "water")
        Stat(Modifier.weight(1f), "⭐", d.points.toString(), "points")
        Stat(Modifier.weight(1f), "✦", "${"%.1f".format(d.vitality)}×", "vitality")
    }
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun Stat(modifier: Modifier, icon: String, value: String, label: String) {
    Column(
        modifier = modifier.glassSoft().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 13.sp)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Cream
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
    }
}

// ======================== SHOP ========================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShopSection(
    shop: ShopState,
    onOpenCase: (CasePack) -> Unit,
    onBuyTheme: (SceneTheme) -> Unit,
    onEquipTheme: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ---- Cases ----
        Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
            Text("Cases", style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(
                "Something found along the way — a curio, a new look, or a pile of growth.",
                style = MaterialTheme.typography.bodySmall, color = CreamMuted
            )
            Spacer(Modifier.height(12.dp))
            CaseCatalog.ALL.forEach { pack ->
                val affordable = shop.points >= pack.cost
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSoft()
                        .then(if (affordable) Modifier.clickable { onOpenCase(pack) } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pack.name, style = MaterialTheme.typography.bodyMedium, color = Cream)
                        Text(pack.blurb, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
                    }
                    Text(
                        "${pack.cost} ⭐",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (affordable) Gold else CreamFaint
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ---- Curio collection ----
        Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
            Text("Curios", style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(
                "${shop.curiosFound} of ${shop.curiosTotal} found · ${shop.completionPercent}% complete",
                style = MaterialTheme.typography.bodySmall, color = CreamMuted
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CurioCatalog.ALL.forEach { curio ->
                    val count = shop.curios[curio.id] ?: 0
                    CurioChip(curio, count)
                }
            }
        }

        // ---- Themes ----
        Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
            Text("Looks", style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(
                "Change how the whole place is lit.",
                style = MaterialTheme.typography.bodySmall, color = CreamMuted
            )
            Spacer(Modifier.height(12.dp))
            ThemeCatalog.ALL.forEach { theme ->
                val owned = shop.ownedThemes.contains(theme.id)
                val equipped = shop.activeTheme == theme.id
                val affordable = shop.points >= theme.cost
                val palette = themePalette(theme.id)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSoft()
                        .then(
                            when {
                                equipped -> Modifier
                                owned -> Modifier.clickable { onEquipTheme(theme.id) }
                                affordable -> Modifier.clickable { onBuyTheme(theme) }
                                else -> Modifier
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // A tiny swatch so you can see what you're buying.
                    Canvas(Modifier.size(22.dp)) {
                        drawCircle(palette.leaf, radius = size.minDimension / 2.6f)
                        drawCircle(
                            palette.glow,
                            radius = size.minDimension / 5f,
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.32f)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(theme.name, style = MaterialTheme.typography.bodyMedium, color = Cream)
                        Text(theme.blurb, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
                    }
                    Text(
                        text = when {
                            equipped -> "Equipped"
                            owned -> "Wear"
                            else -> "${theme.cost} ⭐"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            equipped -> AccentGarden
                            owned -> Cream
                            affordable -> Gold
                            else -> CreamFaint
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CurioChip(curio: Curio, count: Int) {
    val found = count > 0
    val color = curioColor(curio.rarity)
    Column(
        modifier = Modifier
            .size(width = 74.dp, height = 86.dp)
            .glassSoft(RoundedCornerShape(16.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (found) curio.glyph else "❓", fontSize = 26.sp)
        Text(
            text = if (found) curio.name else "Unfound",
            style = MaterialTheme.typography.labelSmall,
            color = if (found) color else CreamFaint,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = if (found && count > 1) "×$count" else curio.rarity.label,
            style = MaterialTheme.typography.labelSmall,
            color = CreamMuted
        )
    }
}

@Composable
private fun RewardDialog(reward: CaseReward, onDismiss: () -> Unit) {
    val (glyph, title, body) = when (reward) {
        is CaseReward.GotCurio -> Triple(
            reward.curio.glyph,
            if (reward.duplicate) "Another ${reward.curio.name.lowercase()}" else reward.curio.name,
            if (reward.duplicate) "You already had one — traded it in for ${reward.growthBonus} 🌿."
            else "${reward.curio.rarity.label} · added to your curios."
        )
        is CaseReward.GotTheme -> Triple("🎨", reward.theme.name, "A new look, and it's on you already.")
        is CaseReward.GotGrowth -> Triple("🌿", "${reward.amount} growth", "Straight into your pocket.")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Nice") } },
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(glyph, fontSize = 44.sp)
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    )
}

private fun curioColor(rarity: CurioRarity): Color = when (rarity) {
    CurioRarity.COMMON -> Color(0xFF9AA38F)
    CurioRarity.UNCOMMON -> Color(0xFF6FD08A)
    CurioRarity.RARE -> Color(0xFF6FA8FF)
    CurioRarity.LEGENDARY -> Color(0xFFC79BF0)
}

// ======================== CHALLENGES ========================

@Composable
private fun ChallengesSection(
    challenges: List<ChallengeView>,
    checkingSlot: String?,
    onClaim: (ChallengeView) -> Unit,
    onCheck: (ChallengeView) -> Unit
) {
    if (challenges.isEmpty()) {
        Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
            Text("No challenges yet", style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(
                "They'll appear once you've logged something — come back in a moment.",
                style = MaterialTheme.typography.bodySmall, color = CreamMuted
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        challenges.forEach { c ->
            ChallengeCard(c, checkingSlot == c.slot, onClaim, onCheck)
        }
        Column(modifier = Modifier.fillMaxWidth().glassSoft().padding(14.dp)) {
            Text(
                "⭐ points only land when you tap claim — that's the whole idea. " +
                    "Spend them on keystone projects along the trail.",
                style = MaterialTheme.typography.bodySmall, color = CreamMuted
            )
        }
    }
}

@Composable
private fun ChallengeCard(
    c: ChallengeView,
    isChecking: Boolean,
    onClaim: (ChallengeView) -> Unit,
    onCheck: (ChallengeView) -> Unit
) {
    val accent = if (c.canClaim) Gold else AccentGarden

    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = c.period.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = CreamFaint
            )
            Text(
                text = "+${c.reward} ⭐",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (c.claimed) CreamFaint else Gold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = c.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (c.claimed) CreamMuted else Cream
        )

        // Concrete challenges show live progress; creative ones have nothing to count.
        c.progress?.let { p ->
            Spacer(Modifier.height(10.dp))
            val fraction by animateFloatAsState(
                targetValue = p.fraction, animationSpec = tween(500), label = "chal"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(p.label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
        }

        c.verdictNote?.takeIf { !c.completed }?.let {
            Spacer(Modifier.height(8.dp))
            Text("Not yet — $it", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }

        Spacer(Modifier.height(12.dp))
        when {
            c.claimed -> Text(
                "Claimed ✓",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold, color = AccentGarden
            )
            c.canClaim -> Box(
                modifier = Modifier
                    .glassSoft(RoundedCornerShape(50))
                    .clickable { onClaim(c) }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    "Claim +${c.reward} ⭐",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold, color = Gold
                )
            }
            c.needsCheck -> Box(
                modifier = Modifier
                    .glassSoft(RoundedCornerShape(50))
                    .then(if (isChecking) Modifier else Modifier.clickable { onCheck(c) })
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    if (isChecking) "Checking…" else "Check with AI",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isChecking) CreamMuted else Cream
                )
            }
            else -> Text(
                "In progress",
                style = MaterialTheme.typography.labelMedium, color = CreamFaint
            )
        }
    }
}

// ======================== PROJECTS ========================

@Composable
private fun ProjectsCard(
    d: TrailDisplay,
    onBuild: (com.fitpal.app.domain.TrailProject) -> Unit,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Keystones cost ⭐, so they stay out of sight until challenges can actually supply it.
    val showKeystones = d.progression.has(TrailFeature.KEYSTONES)
    val visible = d.projects.filter { showKeystones || !it.project.isKeystone }

    Column(modifier = modifier.fillMaxWidth().glass().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Restore", style = MaterialTheme.typography.titleMedium, color = Cream)
            Text(
                "+${d.perTick} per logged day",
                style = MaterialTheme.typography.labelMedium,
                color = AccentGarden
            )
        }
        Spacer(Modifier.height(10.dp))

        visible.forEach { pv ->
            ProjectRow(pv, onBuild)
            Spacer(Modifier.height(8.dp))
        }

        if (d.canAdvance) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSoft(RoundedCornerShape(50))
                    .clickable(onClick = onAdvance)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Follow the trail onward →",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = GoldLight
                )
            }
        }
    }
}

@Composable
private fun ProjectRow(pv: ProjectView, onBuild: (com.fitpal.app.domain.TrailProject) -> Unit) {
    val p = pv.project
    val keystone = p.isKeystone
    val accent = if (keystone) Gold else AccentGarden

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSoft()
            .then(if (!pv.built && pv.affordable) Modifier.clickable { onBuild(p) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(8.dp)) {
                if (pv.built) drawCircle(accent)
                else drawCircle(Color.White.copy(alpha = 0.22f), style = Stroke(width = 1.5f))
            }
        }
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = p.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (pv.built) CreamMuted else Cream
            )
            if (keystone && !pv.built) {
                Text("Keystone", style = MaterialTheme.typography.labelSmall, color = Gold)
            }
        }

        Spacer(Modifier.width(10.dp))

        if (pv.built) {
            Text("Restored", style = MaterialTheme.typography.labelMedium, color = accent)
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${p.cost} 🌿",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (pv.affordable) Cream else CreamFaint
                )
                if (keystone) {
                    Text(
                        text = "${p.pointCost} ⭐",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pv.affordable) Gold else CreamFaint
                    )
                }
            }
        }
    }
}
