package com.fitpal.app.ui.screen.water

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassCapsule
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterDetailScreen(
    onBack: () -> Unit,
    viewModel: WaterDetailViewModel = hiltViewModel()
) {
    val total by viewModel.totalWater.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val goal by viewModel.waterGoal.collectAsStateWithLifecycle()
    var showAddPreset by remember { mutableStateOf(false) }
    var showCustomAmount by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }

    val drinkWater = items.filter { it.isDrink }.sumOf { it.waterMl.toDouble() }.toInt()
    val foodWater = items.filter { !it.isDrink }.sumOf { it.waterMl.toDouble() }.toInt()

    if (showAddPreset) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPreset = false },
            title = { Text("Add a quick-add amount") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() } },
                    label = { Text("Amount (ml)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toIntOrNull()?.let { viewModel.addPreset(it) }
                    showAddPreset = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddPreset = false }) { Text("Cancel") } }
        )
    }

    if (showCustomAmount) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCustomAmount = false },
            title = { Text("Log a one-off amount") },
            text = {
                Column {
                    Text("Add any amount just this once — it won't be saved as a quick-add button.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter { c -> c.isDigit() } },
                        label = { Text("Amount (ml)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toIntOrNull()?.takeIf { it > 0 }?.let { viewModel.addWater(it.toFloat()) }
                    showCustomAmount = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showCustomAmount = false }) { Text("Cancel") } }
        )
    }

    if (showGoalDialog) {
        var text by remember { mutableStateOf(goal.toString()) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Daily water goal") },
            text = {
                Column {
                    Text("Set your hydration target, or reset to the weight-based default (~35 ml per kg).", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter { c -> c.isDigit() } },
                        label = { Text("Goal (ml)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toIntOrNull()?.let { viewModel.setWaterGoal(it) }
                    showGoalDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setWaterGoal(0); showGoalDialog = false }) { Text("Reset to auto") }
            }
        )
    }

    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Water", onBack = onBack)

            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Total + goal + where it came from
                Column(modifier = Modifier.fillMaxWidth().glass().padding(20.dp)) {
                    Text("Today", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    Text("${total.toInt()} ml", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = GoldLight)
                    if (goal > 0) {
                        val frac = (total / goal).coerceIn(0f, 1f)
                        Text(
                            "of $goal ml · ${(frac * 100).toInt()}% · tap to change goal",
                            style = MaterialTheme.typography.labelMedium,
                            color = CreamMuted,
                            modifier = Modifier.clickable { showGoalDialog = true }.padding(vertical = 2.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
                                .background(Cream.copy(alpha = 0.10f))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(frac).height(8.dp).clip(RoundedCornerShape(50))
                                    .background(GoldLight)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SourceRow("From drinks", drinkWater)
                    Spacer(Modifier.height(6.dp))
                    SourceRow("From food", foodWater)
                }

                // Editable quick-add amounts
                Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                    Text("Quick add", style = MaterialTheme.typography.titleMedium, color = Cream)
                    Text("Tap an amount to add it, or × to remove it. “Custom” logs a one-off amount; “Add amount” saves a new button.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { ml ->
                            Row(
                                modifier = Modifier.glassSoft(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "+$ml ml",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Cream,
                                    modifier = Modifier
                                        .clickable { viewModel.addWater(ml.toFloat()) }
                                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                                )
                                IconButton(onClick = { viewModel.removePreset(ml) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove $ml ml", tint = CreamFaint, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                        GlassCapsule(onClick = { showCustomAmount = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = "Log a custom one-off amount", tint = GoldLight, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Custom", style = MaterialTheme.typography.labelLarge, color = Cream)
                            }
                        }
                        GlassCapsule(onClick = { showAddPreset = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = "Add a reusable amount", tint = GoldLight, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add amount", style = MaterialTheme.typography.labelLarge, color = Cream)
                            }
                        }
                    }
                }

                // Today's water-contributing items
                Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                    Text("Today's water", style = MaterialTheme.typography.titleMedium, color = Cream)
                    Spacer(Modifier.height(8.dp))
                    if (items.isEmpty()) {
                        Text("Nothing yet — add water above.", style = MaterialTheme.typography.bodySmall, color = CreamFaint)
                    } else {
                        items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium, color = Cream)
                                    Text(if (item.isDrink) "Drink" else "From food", style = MaterialTheme.typography.labelSmall, color = CreamFaint)
                                }
                                Text("${item.waterMl.toInt()} ml", style = MaterialTheme.typography.bodyMedium, color = GoldLight)
                                // Only plain water quick-adds are removable here; foods are edited in their own card.
                                if (item.isDrink && item.calories <= 0f) {
                                    IconButton(onClick = { viewModel.deleteItem(item.id) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = CreamMuted, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(label: String, ml: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted)
        Text("$ml ml", style = MaterialTheme.typography.bodyMedium, color = Cream)
    }
}
