package com.fitpal.app.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.StatusKind
import com.fitpal.app.domain.TrackerStatus
import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.domain.model.FastingStyle
import com.fitpal.app.domain.model.ScreenWidgets
import com.fitpal.app.domain.model.StripLayout
import com.fitpal.app.domain.model.TrackerPlacement
import com.fitpal.app.ui.component.FastingStyleThumbnail
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.component.ruleColor
import com.fitpal.app.ui.component.ruleIcon
import com.fitpal.app.ui.component.ruleShortName
import com.fitpal.app.ui.screen.home.HomeTracker
import com.fitpal.app.ui.screen.home.TodayStrip
import com.fitpal.app.ui.theme.AccentActivity
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.CaffeineColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.FastingColor
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.OnGold
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

/**
 * Settings → Home & screens → Trackers on Home (docs/redesign/05): where each tracker that's in use
 * shows — main card (fasting), card, small card, the strip, or off Home — with a preview of the
 * strip on top so every choice is visible as it's made. Below: the strip's style and, when a row
 * has three, which one is bigger. The order is Home's own (arrange it from Home's slider icon).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrackersOnHomeSection(viewModel: SettingsViewModel) {
    val layout by viewModel.homeLayout.collectAsStateWithLifecycle()
    val fasting by viewModel.fastingSchedule.collectAsStateWithLifecycle()
    val caffeine by viewModel.caffeineSettings.collectAsStateWithLifecycle()
    val rules by viewModel.dietaryRules.collectAsStateWithLifecycle()
    val bigger by viewModel.homeStripBigger.collectAsStateWithLifecycle()

    val inUse: (String) -> Boolean = { key ->
        when {
            key == "fasting" -> fasting.enabled
            key == "caffeine" -> caffeine.enabled
            key.startsWith("limit_") -> rules.any { "limit_${it.kind.id}" == key && it.enabled }
            else -> true
        }
    }
    val trackers = layout.filter { it.def.isTracker && inUse(it.key) }
    val strip = layout.firstOrNull { it.key == ScreenWidgets.STRIP }
    val numbers = strip?.variant == "numbers"
    val stripKeys = trackers.filter { it.placement == TrackerPlacement.STRIP }.map { it.key }

    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Trackers on Home", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Cream)
        Text(
            "Where each one shows. The strip fills its row whatever's in it.",
            style = MaterialTheme.typography.bodySmall, color = CreamMuted
        )
        Spacer(Modifier.height(12.dp))

        // The strip as it will look (sample numbers, your trackers and order).
        Text("Strip preview", style = MaterialTheme.typography.labelMedium, color = GoldLight)
        Spacer(Modifier.height(6.dp))
        when {
            stripKeys.isEmpty() -> Text("Nothing in the strip.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
            strip?.enabled == false -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("The strip is hidden on Home.", style = MaterialTheme.typography.bodySmall, color = CreamFaint, modifier = Modifier.weight(1f))
                Pill("Show it", selected = false) { viewModel.setStripStyle(strip.variant ?: "rings") }
            }
            else -> TodayStrip(items = stripKeys.map(::sampleTracker), bigger = bigger, numbers = numbers)
        }

        Spacer(Modifier.height(14.dp))
        trackers.forEach { card ->
            val look = sampleTracker(card.key)
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(look.icon, contentDescription = null, tint = look.color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(card.def.label, style = MaterialTheme.typography.bodyLarge, color = Cream)
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (card.def.placements + TrackerPlacement.OFF_HOME).forEach { p ->
                        Pill(p.label, selected = card.placement == p) { viewModel.setTrackerPlacement(card.key, p) }
                    }
                }
                // On the main card, fasting is a ring around the calories or a bar above them.
                if (card.key == "fasting" && card.placement == TrackerPlacement.MAIN) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(FastingStyle.RING, FastingStyle.BAR).forEach { style ->
                            FastingChoiceTile(
                                selected = fasting.style == style,
                                title = style.label,
                                blurb = style.blurb,
                                onClick = { viewModel.setFastingStyle(style) },
                                modifier = Modifier.weight(1f)
                            ) { FastingStyleThumbnail(style) }
                        }
                    }
                }
            }
        }
        if (trackers.isEmpty()) {
            Text("Turn on a tracker to place it here.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
        }

        Spacer(Modifier.height(12.dp))
        Text("Strip style", style = MaterialTheme.typography.labelMedium, color = GoldLight)
        Spacer(Modifier.height(6.dp))
        SegmentedPills(
            labels = listOf("Rings", "Numbers"),
            selectedIndex = if (numbers) 1 else 0,
            accent = GoldLight,
            onSelect = { viewModel.setStripStyle(if (it == 1) "numbers" else "rings") }
        )

        // Only when it matters: a row of three has one half-width item.
        if (StripLayout.hasRowOfThree(stripKeys.size)) {
            val row = stripKeys.takeLast(3)
            val current = bigger?.takeIf { it in row } ?: row.first()
            Spacer(Modifier.height(12.dp))
            Text("Bigger in a row of three", style = MaterialTheme.typography.labelMedium, color = GoldLight)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    Pill(sampleTracker(key).label, selected = key == current) { viewModel.setHomeStripBigger(key) }
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) OnGold else Cream,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(GoldLight) else Modifier.glassSoft(RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** A tracker with typical numbers, for the preview: the right icon, colour and label. */
private fun sampleTracker(key: String): HomeTracker {
    fun t(label: String, icon: ImageVector, color: Color, value: String, short: String, fraction: Float?, status: String) =
        HomeTracker(key, label, icon, color, value, short, fraction, TrackerStatus(StatusKind.NEUTRAL, status), onClick = {})
    return when (key) {
        "water" -> t("Water", Icons.Default.WaterDrop, AccentTrends, "1,450 ml", "1.45 L", 0.6f, "On pace")
        "steps" -> t("Steps", Icons.AutoMirrored.Filled.DirectionsWalk, AccentActivity, "6,240 steps", "6,240", 0.7f, "1,760 to go")
        "caffeine" -> t("Caffeine", Icons.Default.LocalCafe, CaffeineColor, "80 mg", "80 mg", 0.2f, "Clear by 9 PM")
        "weight" -> t("Weight", Icons.Default.MonitorWeight, GoldLight, "72.4 kg", "72.4", null, "−0.3 kg this week")
        "fasting" -> t("Fasting", Icons.Default.HourglassEmpty, FastingColor, "3h 20m", "3h 20m", 0.6f, "eat at 12:00 PM")
        else -> {
            val kind = DietaryRuleKind.entries.firstOrNull { "limit_${it.id}" == key } ?: DietaryRuleKind.DESSERT
            t(ruleShortName(kind), ruleIcon(kind), ruleColor(kind), "120 of 300 kcal", "120/300", 0.4f, "180 kcal left")
        }
    }
}
