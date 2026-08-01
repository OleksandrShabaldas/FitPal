package com.fitpal.app.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import com.fitpal.app.domain.BmrCalculator
import com.fitpal.app.domain.Macro
import com.fitpal.app.domain.MacroPlan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.domain.model.FitnessGoal
import com.fitpal.app.domain.model.ServingPreset
import com.fitpal.app.domain.model.Sex
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.component.MarkdownText
import com.fitpal.app.ui.theme.glass
import kotlin.math.roundToInt

/** The settings categories shown on the hub, in order. */
data class SettingsCategoryInfo(val id: String, val title: String, val description: String)

val SETTINGS_CATEGORIES = listOf(
    SettingsCategoryInfo("profile", "Profile & goals", "Sex, age, height, fitness goal and calorie target"),
    SettingsCategoryInfo("activity", "Activity & health", "Step calories and Samsung Health sync"),
    SettingsCategoryInfo("presets", "Quick-add & meal times", "Tap amounts for food and drinks, and meal time windows"),
    SettingsCategoryInfo("ai", "AI", "Online Gemini key, models and on-device model"),
    SettingsCategoryInfo("personalize", "Personalize", "Reorder and show or hide cards on each screen"),
    SettingsCategoryInfo("data", "Data & about", "Back up, restore, clear data and app info")
)

fun settingsCategoryTitle(id: String): String =
    SETTINGS_CATEGORIES.firstOrNull { it.id == id }?.title ?: "Settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCategoryScreen(
    category: String,
    onManageModels: () -> Unit,
    onCustomizeScreen: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    GradientBackdrop(theme = BackdropTheme.TODAY) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassTopBar(title = settingsCategoryTitle(category), onBack = onBack)
            when (category) {
                "profile" -> {
                    ProfileSection(viewModel)
                    CalorieGoalSection(viewModel)
                    MacroTargetsSection(viewModel)
                }
                "activity" -> {
                    StepCaloriesSection(viewModel)
                    SamsungHealthSection(viewModel)
                    WatchSection(viewModel)
                }
                "presets" -> {
                    PresetsSection(viewModel)
                    MealTimesSection(viewModel)
                }
                "ai" -> {
                    OnlineAiSection(viewModel)
                    PersonalContextSection(viewModel)
                    AiModelSection(onManageModels)
                }
                "personalize" -> {
                    PersonalizeSection(onCustomizeScreen)
                    RemindersSection(viewModel)
                    HomeDisplaySection(viewModel)
                }
                "data" -> {
                    UpdatesSection(viewModel)
                    DataSection(viewModel)
                    AboutSection(viewModel)
                }
            }
        }
    }
}

// ======================== SECTIONS ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSection(viewModel: SettingsViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Your profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Used to calculate your daily calorie and macro targets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            var sexExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = sexExpanded, onExpandedChange = { sexExpanded = it }) {
                OutlinedTextField(
                    value = profile.sex.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sex") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sexExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = sexExpanded, onDismissRequest = { sexExpanded = false }) {
                    Sex.entries.forEach { sex ->
                        DropdownMenuItem(
                            text = { Text(sex.label) },
                            onClick = { viewModel.setSex(sex); sexExpanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            var ageText by remember(profile.ageYears) { mutableStateOf(profile.ageYears.toString()) }
            var heightText by remember(profile.heightCm) { mutableStateOf(profile.heightCm.toInt().toString()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { newText -> ageText = newText.filter { it.isDigit() }; ageText.toIntOrNull()?.let(viewModel::setAge) },
                    label = { Text("Age") },
                    suffix = { Text("years") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { newText -> heightText = newText.filter { it.isDigit() }; heightText.toFloatOrNull()?.let(viewModel::setHeight) },
                    label = { Text("Height") },
                    suffix = { Text("cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            val latestWeight by viewModel.latestWeight.collectAsStateWithLifecycle()
            var weightText by remember(latestWeight?.weightKg) {
                mutableStateOf(latestWeight?.weightKg?.let { it.toInt().toString() } ?: "")
            }
            OutlinedTextField(
                value = weightText,
                onValueChange = { t ->
                    weightText = t.filter { it.isDigit() || it == '.' }
                    weightText.toFloatOrNull()?.let(viewModel::logWeight)
                },
                label = { Text("Current weight") },
                suffix = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            var goalExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = goalExpanded, onExpandedChange = { goalExpanded = it }) {
                OutlinedTextField(
                    value = profile.fitnessGoal.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fitness goal") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = goalExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = goalExpanded, onDismissRequest = { goalExpanded = false }) {
                    FitnessGoal.entries.forEach { goal ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(goal.label, fontWeight = FontWeight.SemiBold)
                                    Text(goal.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = { viewModel.setFitnessGoal(goal); goalExpanded = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalorieGoalSection(viewModel: SettingsViewModel) {
    val calorieGoal by viewModel.dailyCalorieGoal.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Daily calorie goal (override)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Leave at 0 to use your auto-calculated target from profile + weight",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = if (calorieGoal == 0) "" else calorieGoal.toString(),
                onValueChange = { text -> viewModel.setDailyCalorieGoal(text.toIntOrNull() ?: 0) },
                modifier = Modifier.width(140.dp),
                suffix = { Text("kcal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    }
}

@Composable
private fun MacroTargetsSection(viewModel: SettingsViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val selection by viewModel.macroSelection.collectAsStateWithLifecycle()
    val weight by viewModel.latestWeight.collectAsStateWithLifecycle()
    val calorieGoal by viewModel.dailyCalorieGoal.collectAsStateWithLifecycle()
    val kg = weight?.weightKg
    val targets = kg?.let { BmrCalculator.dailyTargets(profile, it, calorieGoal, selection) }
    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Macro targets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Set each macro, or leave it on Auto. ★ marks the pick we recommend for your goal (${profile.fitnessGoal.label}).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (kg == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Log your weight first — protein, fat and carb targets scale with it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                MacroTargetRow(Macro.PROTEIN, "Protein", selection.protein, targets?.proteinG, profile.fitnessGoal, viewModel)
                MacroTargetRow(Macro.FAT, "Fat", selection.fat, targets?.fatG, profile.fitnessGoal, viewModel)
                MacroTargetRow(Macro.CARBS, "Carbs", selection.carbs, targets?.carbsG, profile.fitnessGoal, viewModel)
                MacroTargetRow(Macro.FIBER, "Fiber", selection.fiber, targets?.fiberG, profile.fitnessGoal, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MacroTargetRow(
    macro: Macro,
    label: String,
    selectedKey: String,
    grams: Int?,
    goal: com.fitpal.app.domain.model.FitnessGoal,
    viewModel: SettingsViewModel
) {
    val recommended = MacroPlan.recommendedKey(macro, goal)
    Spacer(Modifier.height(14.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text("${grams ?: "—"} g / day", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MacroPlan.optionsFor(macro).forEach { opt ->
            val isRecommended = opt.key == recommended
            FilterChip(
                selected = selectedKey == opt.key,
                onClick = { viewModel.setMacroTarget(macro, opt.key) },
                label = { Text(if (isRecommended) "${opt.label} ★" else opt.label) }
            )
        }
    }
}

@Composable
private fun StepCaloriesSection(viewModel: SettingsViewModel) {
    val stepTrimPercent by viewModel.stepCalorieReductionPercent.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Step calories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (stepTrimPercent == 0) "No trim" else "−$stepTrimPercent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Fitness trackers tend to over-count steps (arm movements, bumps). " +
                    "Trim the calories burned from steps to compensate. Applies to every day, including past ones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = stepTrimPercent.toFloat(),
                onValueChange = { viewModel.setStepCalorieReductionPercent(it.roundToInt()) },
                valueRange = 0f..50f,
                steps = 9
            )
            Text(
                text = when {
                    stepTrimPercent == 0 -> "Showing the full estimate."
                    stepTrimPercent <= 10 -> "A light trim."
                    stepTrimPercent <= 20 -> "A balanced trim — good for most wrist trackers."
                    else -> "A heavy trim — for trackers that over-count a lot."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SamsungHealthSection(viewModel: SettingsViewModel) {
    val healthConnected by viewModel.healthConnected.collectAsStateWithLifecycle()
    val healthSyncMessage by viewModel.healthSyncMessage.collectAsStateWithLifecycle()
    val hcLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { _ -> viewModel.onHealthPermissionResult() }

    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Samsung Health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Auto-sync your steps from Samsung Health via Health Connect — stays on your phone, no account needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            when {
                !viewModel.healthConnectAvailable -> {
                    Text(
                        "Health Connect isn't available on this device. Install or update it from the Play Store, " +
                            "then enable steps sharing in Samsung Health.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                healthConnected -> {
                    Text("Connected ✓", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.syncSteps() }) { Text("Sync now") }
                }
                else -> {
                    Button(onClick = { hcLauncher.launch(viewModel.healthConnectPermissions) }) { Text("Connect") }
                }
            }
            healthSyncMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PresetsSection(viewModel: SettingsViewModel) {
    val mealPresets by viewModel.mealPresets.collectAsStateWithLifecycle()
    val drinkPresets by viewModel.drinkPresets.collectAsStateWithLifecycle()
    PresetEditorCard(
        title = "Meal presets",
        description = "Quick-tap amounts shown when logging food",
        unit = "g",
        presets = mealPresets,
        onAdd = viewModel::addMealPreset,
        onUpdate = viewModel::updateMealPreset,
        onRemove = viewModel::removeMealPreset
    )
    Spacer(Modifier.height(16.dp))
    PresetEditorCard(
        title = "Drink presets",
        description = "Quick-tap amounts shown when logging drinks",
        unit = "ml",
        presets = drinkPresets,
        onAdd = viewModel::addDrinkPreset,
        onUpdate = viewModel::updateDrinkPreset,
        onRemove = viewModel::removeDrinkPreset
    )
}

@Composable
private fun OnlineAiSection(viewModel: SettingsViewModel) {
    val onlineAiEnabled by viewModel.onlineAiEnabled.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val geminiModel by viewModel.geminiModel.collectAsStateWithLifecycle()
    val geminiModel2 by viewModel.geminiModel2.collectAsStateWithLifecycle()
    val geminiModel3 by viewModel.geminiModel3.collectAsStateWithLifecycle()
    val onlineTesting by viewModel.onlineTesting.collectAsStateWithLifecycle()
    val onlineTestStatus by viewModel.onlineTestStatus.collectAsStateWithLifecycle()
    val modelTesting by viewModel.modelTesting.collectAsStateWithLifecycle()
    val modelTestStatus by viewModel.modelTestStatus.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Online AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Use Google Gemini for sharper photo and meal analysis. Falls back to the on-device model " +
                            "automatically when you're offline or out of free quota.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = onlineAiEnabled, onCheckedChange = { viewModel.setOnlineAiEnabled(it) })
            }

            if (onlineAiEnabled) {
                Spacer(Modifier.height(12.dp))
                var keyText by remember { mutableStateOf(geminiApiKey.orEmpty()) }
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it; viewModel.setGeminiApiKey(it) },
                    label = { Text("Gemini API key") },
                    placeholder = { Text("Paste your key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (geminiApiKey.isNullOrBlank())
                        "Get a free key at aistudio.google.com → \"API keys\", then paste it here. " +
                            "Heads-up: on Google's free tier, your prompts (and meal photos) may be reviewed to improve their AI."
                    else
                        "Key saved ✓ — online analysis runs when you have internet, with on-device fallback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (geminiApiKey.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(12.dp))
                var modelText by remember { mutableStateOf(geminiModel) }
                ModelFieldWithTest(
                    label = "Model",
                    value = modelText,
                    onValueChange = { modelText = it; viewModel.setGeminiModel(it) },
                    onTest = { viewModel.testModel(modelText) },
                    testing = modelTesting == modelText.trim(),
                    status = modelTestStatus[modelText.trim()],
                    enabled = !geminiApiKey.isNullOrBlank()
                )
                Text(
                    "Default: gemini-3-flash-preview. If a test fails with \"model not found\", try gemini-3-flash or gemini-flash-latest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(12.dp))
                var model2Text by remember { mutableStateOf(geminiModel2) }
                ModelFieldWithTest(
                    label = "Fallback model 2",
                    value = model2Text,
                    onValueChange = { model2Text = it; viewModel.setGeminiModel2(it) },
                    onTest = { viewModel.testModel(model2Text) },
                    testing = modelTesting == model2Text.trim(),
                    status = modelTestStatus[model2Text.trim()],
                    enabled = !geminiApiKey.isNullOrBlank()
                )
                Spacer(Modifier.height(8.dp))
                var model3Text by remember { mutableStateOf(geminiModel3) }
                ModelFieldWithTest(
                    label = "Fallback model 3",
                    value = model3Text,
                    onValueChange = { model3Text = it; viewModel.setGeminiModel3(it) },
                    onTest = { viewModel.testModel(model3Text) },
                    testing = modelTesting == model3Text.trim(),
                    status = modelTestStatus[model3Text.trim()],
                    enabled = !geminiApiKey.isNullOrBlank()
                )
                Text(
                    "When the primary model runs out of today's free quota, FitPal tries model 2, then model 3, then your " +
                        "on-device AI. Leave a field blank to skip it. Use each Test button to check that model on its own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { viewModel.testOnlineConnection() }, enabled = !onlineTesting && !geminiApiKey.isNullOrBlank()) {
                        Text(if (onlineTesting) "Testing…" else "Test all models")
                    }
                    if (onlineTesting) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                onlineTestStatus?.let { status ->
                    Spacer(Modifier.height(8.dp))
                    val allOk = status.contains("✓") && !status.contains("✗")
                    val noneOk = status.contains("✗") && !status.contains("✓")
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            allOk -> MaterialTheme.colorScheme.primary
                            noneOk -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/**
 * Wear OS link status + a manual reconnect. The watch works without the phone app open (Play
 * Services wakes FitPal's listener for it) — this is here to confirm it's connected and to force a
 * refresh if the numbers ever look stale.
 */
@Composable
private fun WatchSection(viewModel: SettingsViewModel) {
    val status by viewModel.watchStatus.collectAsStateWithLifecycle()
    val checking by viewModel.watchChecking.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshWatchStatus() }

    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Wear OS watch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(10.dp).clip(CircleShape).background(
                        when {
                            checking -> MaterialTheme.colorScheme.onSurfaceVariant
                            status.connected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        checking -> "Checking…"
                        status.connected -> "Connected: ${status.names.joinToString(", ").ifBlank { "watch" }}"
                        else -> "No watch connected"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (status.connected)
                    "Your watch can log water, meals and exercise even when this app is closed. Tap " +
                        "Sync to push today's numbers to it right now."
                else
                    "Pair your watch in the Galaxy Wearable app and make sure FitPal is installed on " +
                        "it. Bluetooth must be on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            status.lastPushOk?.let { ok ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (ok) "Sent today's numbers to your watch ✓" else "Couldn't reach the watch — try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { viewModel.reconnectWatch() }, enabled = !checking) {
                Text(if (checking) "Checking…" else "Reconnect & sync")
            }
        }
    }
}

@Composable
private fun PersonalContextSection(viewModel: SettingsViewModel) {
    val personalContext by viewModel.personalContext.collectAsStateWithLifecycle()
    var text by remember(personalContext) { mutableStateOf(personalContext) }
    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("About your situation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Anything the AI overview should keep in mind — living with parents, school or work " +
                    "lunches, a tight budget, allergies, vegetarian/halal, a medical condition… It'll " +
                    "tailor advice to fit and won't suggest things this rules out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 600) { text = it; viewModel.setPersonalContext(it) } },
                label = { Text("Your context & limitations") },
                placeholder = { Text("e.g. I live with my parents and eat school lunches on weekdays; vegetarian; on a budget") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${text.length}/600",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun AiModelSection(onManageModels: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().glass().clickable(onClick = onManageModels)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("On-device AI model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Download or update the offline AI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PersonalizeSection(onCustomizeScreen: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Customize screens", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Reorder and show or hide the cards on each screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            com.fitpal.app.domain.model.ScreenWidgets.screens.forEach { screenId ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onCustomizeScreen(screenId) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        com.fitpal.app.domain.model.ScreenWidgets.titles[screenId] ?: screenId,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RemindersSection(viewModel: SettingsViewModel) {
    val enabled by viewModel.reminderEnabled.collectAsStateWithLifecycle()
    val minutes by viewModel.reminderMinutes.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Legacy "log your day" reminder.
        Box(modifier = Modifier.fillMaxWidth().glass()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Daily reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("A gentle once-a-day nudge to log your meals.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = enabled, onCheckedChange = { viewModel.setReminderEnabled(it) })
                }
                if (enabled) {
                    Spacer(Modifier.height(4.dp))
                    TimePickRow("Reminder time", minutes) { viewModel.setReminderTime(it) }
                }
            }
        }

        // Meal & weigh-in reminders.
        Box(modifier = Modifier.fillMaxWidth().glass()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Meal & weigh-in reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Nudges to log each meal and your weight. Turn on the ones you want.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    com.fitpal.app.domain.model.ReminderKind.BREAKFAST,
                    com.fitpal.app.domain.model.ReminderKind.LUNCH,
                    com.fitpal.app.domain.model.ReminderKind.DINNER,
                    com.fitpal.app.domain.model.ReminderKind.WEIGHT
                ).forEach { kind ->
                    ReminderRow(kind, reminders[kind.key], viewModel)
                }
            }
        }

        // AI overviews (auto-generate + notify).
        Box(modifier = Modifier.fillMaxWidth().glass()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AI overviews", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Automatically write an AI overview and notify you — daily, and a weekly one on Sundays. Tapping the notification opens it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    com.fitpal.app.domain.model.ReminderKind.DAILY_OVERVIEW,
                    com.fitpal.app.domain.model.ReminderKind.WEEKLY_OVERVIEW
                ).forEach { kind ->
                    ReminderRow(kind, reminders[kind.key], viewModel)
                }
            }
        }

        BackgroundReliabilitySection(viewModel)
    }
}

/**
 * Grants the permissions scheduled reminders/overviews need to fire on time: exact alarms and a
 * battery-optimisation exemption. Without these, Android's Doze mode drops the alarms after a day or
 * two — which is why "it worked the first couple of times, then stopped".
 */
@Composable
private fun BackgroundReliabilitySection(viewModel: SettingsViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Read once per visit to this screen (re-read on next navigation in — good enough for a status hint).
    val exactOk = remember { viewModel.canScheduleExactAlarms() }
    val batteryOk = remember {
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Background reliability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "For the automatic overviews and reminders to fire on time, Android needs to let FitPal " +
                    "run on schedule. If they stop after a day or two, grant these:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            ReliabilityRow(
                label = "Alarms & reminders",
                granted = exactOk,
                onFix = {
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                            )
                        }.onFailure {
                            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
                        }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            ReliabilityRow(
                label = "Ignore battery optimisation",
                granted = batteryOk,
                onFix = {
                    runCatching {
                        @android.annotation.SuppressLint("BatteryLife")
                        val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(android.net.Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }.onFailure {
                        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    }
                }
            )
        }
    }
}

@Composable
private fun ReliabilityRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (granted) "Allowed ✓" else "Not allowed — tap to fix",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        if (!granted) {
            OutlinedButton(onClick = onFix) { Text("Allow") }
        }
    }
}

/** One toggle + time row for a [com.fitpal.app.domain.model.ReminderKind]. */
@Composable
private fun ReminderRow(
    kind: com.fitpal.app.domain.model.ReminderKind,
    state: com.fitpal.app.domain.model.ReminderState?,
    viewModel: SettingsViewModel
) {
    val enabled = state?.enabled ?: kind.defaultEnabled
    val minutes = state?.minutes ?: kind.defaultMinutes
    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(kind.settingTitle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(kind.settingDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = { viewModel.setReminder(kind, it, minutes) })
    }
    if (enabled) {
        TimePickRow(if (kind.weekly) "Sunday time" else "Time", minutes) { viewModel.setReminder(kind, true, it) }
    }
}

/** Tap-to-pick-a-time row, showing the current time formatted. */
@Composable
private fun TimePickRow(label: String, minutes: Int, onPick: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            android.app.TimePickerDialog(
                context, { _, hh, mm -> onPick(hh * 60 + mm) }, minutes / 60, minutes % 60, false
            ).show()
        }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(minutesToLabel(minutes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

private fun minutesToLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val hr12 = ((h + 11) % 12) + 1
    return "$hr12:${"%02d".format(m)} ${if (h < 12) "AM" else "PM"}"
}

/** #3 — editable breakfast/lunch/dinner time windows; outside them a logged food is a snack. */
@Composable
private fun MealTimesSection(viewModel: SettingsViewModel) {
    val windows by viewModel.mealWindows.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Meal times", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "When you log or copy a food, its meal is set from the time of day. Anything outside every window — including gaps between meals — becomes a snack.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text("Breakfast", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            TimePickRow("From", windows.breakfastStart) { viewModel.setMealWindows(windows.copy(breakfastStart = it)) }
            TimePickRow("Until", windows.breakfastEnd) { viewModel.setMealWindows(windows.copy(breakfastEnd = it)) }
            Spacer(Modifier.height(6.dp))
            Text("Lunch", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            TimePickRow("From", windows.lunchStart) { viewModel.setMealWindows(windows.copy(lunchStart = it)) }
            TimePickRow("Until", windows.lunchEnd) { viewModel.setMealWindows(windows.copy(lunchEnd = it)) }
            Spacer(Modifier.height(6.dp))
            Text("Dinner", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            TimePickRow("From", windows.dinnerStart) { viewModel.setMealWindows(windows.copy(dinnerStart = it)) }
            TimePickRow("Until", windows.dinnerEnd) { viewModel.setMealWindows(windows.copy(dinnerEnd = it)) }
        }
    }
}

@Composable
private fun HomeDisplaySection(viewModel: SettingsViewModel) {
    val compactEmptyMeals by viewModel.compactEmptyMeals.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Compact empty meals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Shrink empty Breakfast/Lunch/Dinner/Snack panels to a slim add row on Home.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = compactEmptyMeals, onCheckedChange = { viewModel.setCompactEmptyMeals(it) })
            }
        }
    }
}

/**
 * Update check for this sideloaded build. FitPal isn't on the Play Store, so it watches its own
 * GitHub releases. Android never allows a silent install, so even "auto update" ends at the
 * system's own confirm screen — the toggle just removes the in-app tap before it.
 */
@Composable
private fun UpdatesSection(viewModel: SettingsViewModel) {
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    val autoCheck by viewModel.autoCheckUpdates.collectAsStateWithLifecycle()
    val autoInstall by viewModel.autoInstallUpdates.collectAsStateWithLifecycle()
    val busy = state.phase == com.fitpal.app.update.UpdatePhase.CHECKING ||
        state.phase == com.fitpal.app.update.UpdatePhase.DOWNLOADING_PHONE ||
        state.phase == com.fitpal.app.update.UpdatePhase.DOWNLOADING_WATCH ||
        state.phase == com.fitpal.app.update.UpdatePhase.SENDING_WATCH

    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("App updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "You're on ${state.currentVersion}" +
                    (state.watchVersion?.let { " · watch on $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            when (state.phase) {
                com.fitpal.app.update.UpdatePhase.CHECKING ->
                    UpdateProgressRow("Checking GitHub…", null)

                com.fitpal.app.update.UpdatePhase.DOWNLOADING_PHONE ->
                    UpdateProgressRow("Downloading update…", state.progress)

                com.fitpal.app.update.UpdatePhase.DOWNLOADING_WATCH ->
                    UpdateProgressRow("Downloading watch update…", state.progress)

                com.fitpal.app.update.UpdatePhase.SENDING_WATCH ->
                    UpdateProgressRow("Sending to your watch…", state.progress)

                com.fitpal.app.update.UpdatePhase.UP_TO_DATE -> Text(
                    "You're on the latest version ✓",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                com.fitpal.app.update.UpdatePhase.WATCH_SENT -> Text(
                    "Sent to your watch — tap the notification on the watch to install it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                com.fitpal.app.update.UpdatePhase.READY_PHONE -> Text(
                    "Downloaded — finish in the installer Android opened. If you dismissed it, tap Install again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                com.fitpal.app.update.UpdatePhase.FAILED -> Text(
                    state.message ?: "Something went wrong.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )

                else -> state.available?.let { u ->
                    Text(
                        "FitPal ${u.version} is available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            state.available?.let { u ->
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (u.phoneApkUrl != null) {
                        OutlinedButton(
                            onClick = {
                                if (state.phase == com.fitpal.app.update.UpdatePhase.READY_PHONE) {
                                    viewModel.installDownloadedUpdate()
                                } else viewModel.downloadAndInstallUpdate()
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Install ${u.version}") }
                    }
                    if (state.watchNeedsUpdate) {
                        OutlinedButton(
                            onClick = { viewModel.updateWatchApp() },
                            enabled = !busy && state.watchConnected,
                            modifier = Modifier.weight(1f)
                        ) { Text("Update watch") }
                    }
                }
                if (u.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    MarkdownText(
                        markdown = u.releaseNotes.lineSequence().take(10).joinToString("\n").trim(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { viewModel.checkForUpdates() }, enabled = !busy) {
                Text(if (state.phase == com.fitpal.app.update.UpdatePhase.CHECKING) "Checking…" else "Check for updates")
            }

            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Check automatically", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Look for a new version once a day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoCheck, onCheckedChange = { viewModel.setAutoCheckUpdates(it) })
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download automatically", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Fetch the update as soon as it's found and jump straight to the installer. " +
                            "Android still asks you to confirm — nothing installs on its own.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoInstall,
                    enabled = autoCheck,
                    onCheckedChange = { viewModel.setAutoInstallUpdates(it) }
                )
            }
        }
    }
}

@Composable
private fun UpdateProgressRow(label: String, progress: Float?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DataSection(viewModel: SettingsViewModel) {
    val dataMessage by viewModel.dataMessage.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportData) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importData) }

    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Your data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Back up your meals, weights, steps, exercises and trail progress, or restore from a backup. The downloaded food database isn't included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("fitpal-backup.json") }, modifier = Modifier.weight(1f)) { Text("Export") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f)) { Text("Import") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showClearConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Clear all data", color = MaterialTheme.colorScheme.error)
            }
            dataMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all data?") },
            text = { Text("This permanently deletes all your logged meals, weights, steps, exercises, trail progress and saved foods. Your downloaded food database stays. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showClearConfirm = false; viewModel.clearData() }) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AboutSection(viewModel: SettingsViewModel) {
    val onlineAiEnabled by viewModel.onlineAiEnabled.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            SettingRow(label = "App", value = "FitPal")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingRow(label = "Version", value = com.fitpal.app.BuildConfig.VERSION_NAME)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingRow(
                label = "Mode",
                value = if (onlineAiEnabled && !geminiApiKey.isNullOrBlank()) "Online + offline fallback" else "Fully offline"
            )
        }
    }
}

// ======================== SHARED HELPERS ========================

/** A model id field with its own inline "Test" button + result, for testing each model alone. */
@Composable
private fun ModelFieldWithTest(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onTest: () -> Unit,
    testing: Boolean,
    status: String?,
    enabled: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onTest, enabled = enabled && value.isNotBlank() && !testing) {
            if (testing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Test")
        }
    }
    status?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp)
        )
    }
}

/**
 * A self-contained preset list editor: lists each preset with edit/remove, plus a
 * row to add a new one (or save the one being edited). Reused for meals (g) & drinks (ml).
 */
@Composable
private fun PresetEditorCard(
    title: String,
    description: String,
    unit: String,
    presets: List<ServingPreset>,
    onAdd: (String, Float) -> Unit,
    onUpdate: (Int, String, Float) -> Unit,
    onRemove: (ServingPreset) -> Unit
) {
    var newLabel by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxWidth().glass()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            presets.forEachIndexed { index, preset ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${preset.grams.toInt()} $unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { editingIndex = index; newLabel = preset.label; newAmount = preset.grams.toInt().toString() }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${preset.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemove(preset) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove ${preset.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = newLabel, onValueChange = { newLabel = it }, label = { Text("Name") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = newAmount,
                    onValueChange = { newAmount = it },
                    label = { Text(unit) },
                    modifier = Modifier.width(90.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                IconButton(onClick = {
                    val amount = newAmount.toFloatOrNull() ?: 0f
                    val idx = editingIndex
                    if (idx == null) onAdd(newLabel, amount) else onUpdate(idx, newLabel, amount)
                    newLabel = ""; newAmount = ""; editingIndex = null
                }) {
                    Icon(
                        if (editingIndex == null) Icons.Default.Add else Icons.Default.Check,
                        contentDescription = if (editingIndex == null) "Add preset" else "Save preset",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
