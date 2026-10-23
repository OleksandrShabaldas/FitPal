package com.fitpal.app.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.model.LayoutPreset
import com.fitpal.app.domain.model.NavConfig
import com.fitpal.app.domain.model.NavStyle
import com.fitpal.app.domain.model.NavTab
import com.fitpal.app.domain.model.PlusAction
import com.fitpal.app.ui.component.SegmentedPills
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

/**
 * Settings → Home & screens → Bottom bar: which tabs (Home and Settings always stay), their order,
 * labels, what the "+" does, and whether the bar floats or docks.
 */
@Composable
internal fun NavBarSection(viewModel: SettingsViewModel) {
    val config by viewModel.navConfig.collectAsStateWithLifecycle()
    val shown = config.shownTabs
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Bottom bar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Cream)
        Text(
            "Up to ${NavConfig.MAX_TABS} tabs around the +. Swiping sideways follows this order.",
            style = MaterialTheme.typography.bodySmall, color = CreamMuted
        )
        Spacer(Modifier.height(10.dp))
        val trailOn by viewModel.trailEnabled.collectAsStateWithLifecycle()
        val featuresOn by viewModel.featuresOn.collectAsStateWithLifecycle()
        val coachOn = com.fitpal.app.domain.model.Feature.COACH in featuresOn
        NavTab.entries.filter { (it != NavTab.TRAIL || trailOn) && (it != NavTab.COACH || coachOn) }.forEach { tab ->
            val on = tab in shown
            val locked = tab == NavTab.HOME || tab == NavTab.SETTINGS
            val index = shown.indexOf(tab)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tab.label, style = MaterialTheme.typography.bodyLarge, color = if (on) Cream else CreamFaint, modifier = Modifier.weight(1f))
                if (on) {
                    RoundIcon(Icons.Default.KeyboardArrowUp, "Move ${tab.label} left", enabled = index > 0) {
                        viewModel.setNavConfig(config.copy(tabs = shown.toMutableList().apply { add(index - 1, removeAt(index)) }))
                    }
                    RoundIcon(Icons.Default.KeyboardArrowDown, "Move ${tab.label} right", enabled = index < shown.lastIndex) {
                        viewModel.setNavConfig(config.copy(tabs = shown.toMutableList().apply { add(index + 1, removeAt(index)) }))
                    }
                }
                if (locked) {
                    Icon(Icons.Default.Lock, contentDescription = "Always shown", tint = CreamFaint, modifier = Modifier.padding(horizontal = 14.dp).size(18.dp))
                } else {
                    Switch(
                        checked = on,
                        enabled = on || shown.size < NavConfig.MAX_TABS,
                        onCheckedChange = { add ->
                            val tabs = if (add) shown.toMutableList().apply { add(lastIndex, tab) } else shown - tab
                            viewModel.setNavConfig(config.copy(tabs = tabs))
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Show labels", style = MaterialTheme.typography.bodyLarge, color = Cream)
                Text("Names under the icons.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
            Switch(checked = config.labels, onCheckedChange = { viewModel.setNavConfig(config.copy(labels = it)) })
        }
        Spacer(Modifier.height(12.dp))
        Text("The + button opens", style = MaterialTheme.typography.labelMedium, color = GoldLight)
        Spacer(Modifier.height(6.dp))
        SegmentedPills(
            labels = PlusAction.entries.map { it.label },
            selectedIndex = config.plus.ordinal,
            accent = GoldLight,
            onSelect = { viewModel.setNavConfig(config.copy(plus = PlusAction.entries[it])) }
        )
        Text("A long press on + always opens the full Add menu.", style = MaterialTheme.typography.labelSmall, color = CreamFaint, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Text("Bar", style = MaterialTheme.typography.labelMedium, color = GoldLight)
        Spacer(Modifier.height(6.dp))
        SegmentedPills(
            labels = NavStyle.entries.map { it.label },
            selectedIndex = config.style.ordinal,
            accent = GoldLight,
            onSelect = { viewModel.setNavConfig(config.copy(style = NavStyle.entries[it])) }
        )
    }
}

/** Settings → Home & screens → Start from a preset. Asks first — it replaces the current layout. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LayoutPresetsSection(viewModel: SettingsViewModel) {
    var confirm by remember { mutableStateOf<LayoutPreset?>(null) }
    Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Start from a preset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Cream)
        Text(
            "Sets Home, Add food and the bottom bar in one go. You can change anything after.",
            style = MaterialTheme.typography.bodySmall, color = CreamMuted
        )
        Spacer(Modifier.height(10.dp))
        LayoutPreset.entries.forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).glassSoft().clickable { confirm = p }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.label, style = MaterialTheme.typography.titleSmall, color = Cream)
                    Text(p.blurb, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                }
            }
        }
    }
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Use “${p.label}”?") },
            text = { Text("This replaces your current Home, Add food and bottom-bar layout.") },
            confirmButton = { TextButton(onClick = { viewModel.applyLayoutPreset(p); confirm = null }) { Text("Use it") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Icon(
        icon, contentDescription = description,
        tint = if (enabled) Cream else CreamFaint.copy(alpha = 0.3f),
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(6.dp)
    )
}
