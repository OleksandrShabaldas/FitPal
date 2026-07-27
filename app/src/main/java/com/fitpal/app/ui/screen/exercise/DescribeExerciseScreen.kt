package com.fitpal.app.ui.screen.exercise

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fitpal.app.ui.component.AiSourceBadge
import com.fitpal.app.ui.component.BackdropTheme
import com.fitpal.app.ui.component.GlassTopBar
import com.fitpal.app.ui.component.GradientBackdrop
import com.fitpal.app.ui.theme.AccentGarden
import com.fitpal.app.ui.theme.AccentTrends
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.Gold
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DescribeExerciseScreen(
    onLogged: () -> Unit,
    onSetupModel: () -> Unit,
    onBack: () -> Unit,
    viewModel: ExerciseViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedWorkouts by viewModel.savedWorkouts.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onLogged() }

    GradientBackdrop(theme = BackdropTheme.ACTIVITY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(title = "Log exercise", onBack = onBack)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick log — instant, offline, no AI needed (same MET math as the AI path).
                Text("Quick log — tap an activity", style = MaterialTheme.typography.titleSmall, color = Cream)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QUICK_ACTIVITIES.forEach { activity ->
                        Box(
                            modifier = Modifier
                                .glassSoft(RoundedCornerShape(50))
                                .clickable { viewModel.quickPick(activity) }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(activity.label, style = MaterialTheme.typography.labelLarge, color = Cream)
                        }
                    }
                }

                // From the collection — the user's saved workouts, one tap to load & log.
                if (savedWorkouts.isNotEmpty()) {
                    Text("From your collection", style = MaterialTheme.typography.titleSmall, color = Cream)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        savedWorkouts.forEach { workout ->
                            Box(
                                modifier = Modifier
                                    .glassSoft(RoundedCornerShape(50))
                                    .clickable { viewModel.pickSaved(workout) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    "${workout.name.replaceFirstChar { it.uppercase() }} · ${workout.minutes} min",
                                    style = MaterialTheme.typography.labelLarge, color = Cream
                                )
                            }
                        }
                    }
                }

                // AI free-text path (for anything not in the quick list, or with a stated distance/time).
                if (state.modelReady) {
                    Text(
                        "Or describe it — the AI estimates the burn from the activity, time and your weight.",
                        style = MaterialTheme.typography.bodyMedium, color = CreamMuted
                    )
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("e.g. ran 5 km in 30 min, 1h weights") },
                        minLines = 2
                    )
                    Button(
                        onClick = viewModel::estimate,
                        enabled = state.query.isNotBlank() && !state.isEstimating,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isEstimating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Estimating…")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Estimate")
                        }
                    }
                } else {
                    Text(
                        "Set up the AI model to estimate free-text workouts — or just use a quick-log chip above.",
                        style = MaterialTheme.typography.bodySmall, color = CreamMuted
                    )
                    Button(onClick = onSetupModel) { Text("Set up AI model") }
                }

                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                // Result — same card for a quick-pick or an AI estimate.
                state.estimate?.let { est ->
                    Column(modifier = Modifier.fillMaxWidth().glass().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                est.activity.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleLarge, color = Cream,
                                modifier = Modifier.weight(1f)
                            )
                            IntensityChip(est.intensity)
                        }
                        state.aiSource?.let {
                            Spacer(Modifier.height(8.dp))
                            AiSourceBadge(it)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Active duration", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.weight(1f))
                            FilledTonalIconButton(onClick = { viewModel.setMinutes(est.minutes - 5) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Less")
                            }
                            Text("${est.minutes} min", style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.padding(horizontal = 12.dp))
                            FilledTonalIconButton(onClick = { viewModel.setMinutes(est.minutes + 5) }) {
                                Icon(Icons.Default.Add, contentDescription = "More")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${est.calories.toInt()}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = CalorieColor)
                            Spacer(Modifier.width(6.dp))
                            Text("kcal burned", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        // Where the number came from — so it isn't a black box.
                        Text(
                            "≈ ${est.minutes} active min × MET ${"%.1f".format(est.met)} (${est.intensity}) · scales with your body weight. Counts in full toward today's budget.",
                            style = MaterialTheme.typography.bodySmall, color = CreamMuted
                        )
                        if (est.suggestions.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text("Tips", style = MaterialTheme.typography.titleSmall, color = Cream)
                            Spacer(Modifier.height(4.dp))
                            est.suggestions.forEach { tip ->
                                Text("• $tip", style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                    Button(onClick = viewModel::logExercise, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isSaving) "Saving…" else "Log exercise")
                    }
                }
            }
        }
    }
}

/** A small pill showing the workout intensity, colour-coded by effort. */
@Composable
internal fun IntensityChip(intensity: String) {
    val color = when (intensity) {
        "Light" -> AccentTrends
        "Moderate" -> Gold
        else -> AccentGarden // Vigorous
    }
    Box(modifier = Modifier.glassSoft(RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp)) {
        Text(intensity, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
