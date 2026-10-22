package com.fitpal.app.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.model.Feature
import com.fitpal.app.ui.component.FeatureIconCircle
import com.fitpal.app.ui.component.FeatureSetupSheet
import com.fitpal.app.ui.component.FeatureVisual
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.filmColor
import com.fitpal.app.ui.theme.glass

/**
 * Settings → Features (docs/redesign/06): one row per optional feature with its switch. Tapping a
 * row shows what it adds and a link to its settings; switching one on opens its basic setup (one
 * sheet, defaults filled in); switching off asks nothing and keeps everything for next time.
 */
@Composable
internal fun FeaturesSection(viewModel: SettingsViewModel, onOpenCategory: (String, String?) -> Unit) {
    val on by viewModel.featuresOn.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var settingUp by remember { mutableStateOf<Feature?>(null) }

    Column(Modifier.fillMaxWidth().glass().padding(vertical = 6.dp)) {
        Text(
            "Turn on only what you'll use. Everything else stays out of the way.",
            style = MaterialTheme.typography.bodySmall, color = CreamMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Feature.entries.forEachIndexed { i, feature ->
            if (i > 0) HorizontalDivider(color = filmColor(0.08f), modifier = Modifier.padding(horizontal = 16.dp))
            val isOn = feature in on
            val open = expanded == feature.id
            Column(Modifier.fillMaxWidth().clickable { expanded = if (open) null else feature.id }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FeatureIconCircle(feature)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(feature.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Cream)
                        Text(feature.line, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = isOn,
                        onCheckedChange = { turnOn ->
                            // The Trail has nothing to set up; the rest open their setup first.
                            if (!turnOn) viewModel.turnFeatureOff(feature)
                            else if (feature == Feature.TRAIL) viewModel.turnFeatureOn(feature, viewModel.featureSetup())
                            else settingUp = feature
                        }
                    )
                }
                AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        FeatureVisual(feature)
                        feature.settingsCategory?.let { category ->
                            Text(
                                "Settings ›",
                                style = MaterialTheme.typography.labelLarge, color = GoldLight,
                                modifier = Modifier.clickable { onOpenCategory(category, null) }.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    settingUp?.let { feature ->
        FeatureSetupSheet(
            feature = feature,
            initial = viewModel.featureSetup(),
            onTurnOn = { setup -> viewModel.turnFeatureOn(feature, setup); settingUp = null },
            onDismiss = { settingUp = null }
        )
    }
}
