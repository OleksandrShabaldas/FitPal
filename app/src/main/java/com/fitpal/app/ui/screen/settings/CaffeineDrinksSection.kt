package com.fitpal.app.ui.screen.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.Caffeine
import com.fitpal.app.domain.CaffeinePreset
import com.fitpal.app.ui.component.GlassButton
import com.fitpal.app.ui.component.GlassNumberField
import com.fitpal.app.ui.component.GoldButton
import com.fitpal.app.ui.component.SetupPill
import com.fitpal.app.ui.component.TrackerSheet
import com.fitpal.app.ui.theme.CaffeineColor
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass

/**
 * Settings → Caffeine → Your drinks (docs/redesign/12, #3): the drinks the caffeine planner offers,
 * least caffeine first. Add your own, remove any (never the last), bring back the ones that used to
 * ship. The three starred in the planner's "More…" sit on its card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CaffeineDrinksSection(viewModel: SettingsViewModel) {
    val settings by viewModel.caffeineSettings.collectAsStateWithLifecycle()
    if (!settings.enabled) return
    val drinks by viewModel.caffeineDrinks.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
        Text("Your drinks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "What the planner on the Caffeine page offers. Star three in its \"More…\" to keep them on the card.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        drinks.forEach { d ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (d.serving.isNotBlank()) Text(d.serving, style = MaterialTheme.typography.labelSmall, color = CreamFaint)
                }
                Text("${d.mg} mg", style = MaterialTheme.typography.labelLarge, color = CaffeineColor)
                if (drinks.size > 1) {
                    Icon(
                        Icons.Default.Close, contentDescription = "Remove ${d.label}", tint = CreamMuted,
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable { viewModel.removeCaffeineDrink(d.label) }.padding(11.dp)
                    )
                } else {
                    Spacer(Modifier.width(40.dp))
                }
            }
        }
        val missing = Caffeine.SUGGESTED_DRINKS.filter { s -> drinks.none { it.label.equals(s.label, ignoreCase = true) } }
        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Add back", style = MaterialTheme.typography.labelLarge, color = CreamMuted)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                missing.forEach { s -> SetupPill("+ ${s.label} · ${s.mg}", selected = false) { viewModel.addCaffeineDrink(s) } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassButton("+ Add a drink", onClick = { adding = true })
            Spacer(Modifier.weight(1f))
            if (confirmReset) {
                Text(
                    "Reset", style = MaterialTheme.typography.labelLarge, color = GoldLight,
                    modifier = Modifier.clip(CircleShape).clickable { viewModel.resetCaffeineDrinks(); confirmReset = false }.padding(8.dp)
                )
                Text(
                    "Cancel", style = MaterialTheme.typography.labelLarge, color = CreamMuted,
                    modifier = Modifier.clip(CircleShape).clickable { confirmReset = false }.padding(8.dp)
                )
            } else {
                Text(
                    "Back to the default seven", style = MaterialTheme.typography.labelMedium, color = GoldLight,
                    modifier = Modifier.clip(CircleShape).clickable { confirmReset = true }.padding(8.dp)
                )
            }
        }
    }
    if (adding) {
        AddDrinkSheet(onAdd = { viewModel.addCaffeineDrink(it); adding = false }, onDismiss = { adding = false })
    }
}

/** A drink of your own: its name, an optional serving, and its caffeine. */
@Composable
private fun AddDrinkSheet(onAdd: (CaffeinePreset) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var serving by remember { mutableStateOf("") }
    var mgText by remember { mutableStateOf("80") }
    val mg = mgText.toIntOrNull()?.takeIf { it in 1..1000 }
    TrackerSheet(title = "Add a drink", onDismiss = onDismiss) {
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) },
            label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = serving, onValueChange = { serving = it.take(30) },
            label = { Text("Serving (optional)") }, placeholder = { Text("e.g. 330 ml can") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Text("Caffeine", style = MaterialTheme.typography.titleSmall)
        GlassNumberField(
            text = mgText, onText = { mgText = it },
            step = 5f, min = 0f, max = 1000f, fallback = 80f, maxChars = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Text("mg", style = MaterialTheme.typography.titleMedium, color = CreamMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            GoldButton(
                "Add",
                onClick = { if (mg != null && name.isNotBlank()) onAdd(CaffeinePreset(name.trim(), serving.trim(), mg)) },
                enabled = mg != null && name.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
