package com.fitpal.app.ui.screen.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.fitpal.app.ui.theme.CalorieColor
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.glass
import com.fitpal.app.ui.theme.glassSoft
import com.fitpal.app.ml.AiSource
import com.fitpal.app.sync.CalistappWorkoutDetails
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    val context = androidx.compose.ui.platform.LocalContext.current
    var showCopyPicker by remember { mutableStateOf(false) }
    LaunchedEffect(state.copyConfirmation) {
        state.copyConfirmation?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearCopyConfirmation()
        }
    }

    if (showCopyPicker) {
        com.fitpal.app.ui.component.DatePickerDialog(
            initialDate = state.entry?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
            title = "Copy to which day?",
            confirmLabel = "Copy here",
            copiesChooser = true,
            onConfirmCopies = { date, copies -> showCopyPicker = false; viewModel.copyToDate(date, copies) },
            onConfirm = {},
            onDismiss = { showCopyPicker = false }
        )
    }

    GradientBackdrop(theme = BackdropTheme.ACTIVITY) {
        Column(modifier = Modifier.fillMaxSize()) {
            GlassTopBar(
                title = "Exercise",
                onBack = onBack,
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).glass(CircleShape).clickable { showCopyPicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy to another date", tint = CreamMuted, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.size(40.dp).glass(CircleShape).clickable(onClick = viewModel::saveToCollection),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (state.savedToCollection) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Save to collection",
                                tint = if (state.savedToCollection) GoldLight else CreamMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.size(40.dp).glass(CircleShape).clickable(onClick = viewModel::delete),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CreamMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )

            val entry = state.entry
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (entry == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("This exercise is no longer available.", color = CreamMuted, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Headline card — activity, intensity, burn, breakdown.
                    Column(modifier = Modifier.fillMaxWidth().glass().padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                entry.name.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleLarge, color = Cream,
                                modifier = Modifier.weight(1f)
                            )
                            IntensityChip(state.intensity)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(prettyDate(entry.date), style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                        AiSource.fromName(entry.aiSource, entry.aiModel)?.let { src ->
                            Spacer(Modifier.height(10.dp))
                            AiSourceBadge(src)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${entry.caloriesBurned.toInt()}", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = CalorieColor)
                            Spacer(Modifier.width(6.dp))
                            Text("kcal burned", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.padding(bottom = 9.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (state.isFromCalistapp)
                                "≈ ${entry.minutes} active min at ${state.intensity.lowercase()} intensity · imported from Calistapp with its heart-rate–based calories. Counts in full toward that day's budget."
                            else
                                "≈ ${entry.minutes} active min × MET ${"%.1f".format(entry.met)} (${state.intensity}) · scales with your body weight. Counts in full toward that day's budget.",
                            style = MaterialTheme.typography.bodySmall, color = CreamMuted
                        )
                    }

                    // Calistapp-only: the real workout breakdown (intensity, HR, reps, exercises).
                    state.calistappDetails?.let { CalistappBreakdownCard(it) }

                    // Editable duration — calories recompute from the same MET.
                    Column(modifier = Modifier.fillMaxWidth().glass().padding(18.dp)) {
                        Text("Duration", style = MaterialTheme.typography.titleMedium, color = Cream)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Active minutes", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.weight(1f))
                            FilledTonalIconButton(onClick = { viewModel.setMinutes(entry.minutes - 5) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Less")
                            }
                            Text("${entry.minutes} min", style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.padding(horizontal = 12.dp))
                            FilledTonalIconButton(onClick = { viewModel.setMinutes(entry.minutes + 5) }) {
                                Icon(Icons.Default.Add, contentDescription = "More")
                            }
                        }
                    }

                    // Quick stat row.
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Intensity", state.intensity, Modifier.weight(1f))
                        StatTile("MET", "%.1f".format(entry.met), Modifier.weight(1f))
                    }

                    // Coaching tips.
                    if (state.suggestions.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().glass().padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = AccentGarden, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Tips", style = MaterialTheme.typography.titleMedium, color = Cream)
                            }
                            Spacer(Modifier.height(8.dp))
                            state.suggestions.forEach { tip ->
                                Text("• $tip", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.padding(vertical = 3.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.glassSoft().padding(vertical = 14.dp, horizontal = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Cream)
    }
}

/**
 * The workout breakdown Calistapp shipped with an imported exercise — heart rate, reps, RPE and the
 * per-exercise split. Shown only for Calistapp-sourced rows; FitPal's own exercises never reach here.
 */
@Composable
private fun CalistappBreakdownCard(details: CalistappWorkoutDetails) {
    Column(modifier = Modifier.fillMaxWidth().glass().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Workout breakdown", style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.weight(1f))
            Text("from Calistapp", style = MaterialTheme.typography.labelSmall, color = CreamMuted)
        }
        Spacer(Modifier.height(12.dp))

        // Heart rate + reps tiles (only those Calistapp actually measured).
        val tiles = buildList<Pair<String, String>> {
            details.effortHr?.let { add("Avg HR" to "$it bpm") }
            details.peakHr?.takeIf { it > 0 }?.let { add("Peak HR" to "$it bpm") }
            details.totalReps?.takeIf { it > 0 }?.let { add("Total reps" to "$it") }
        }
        if (tiles.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
            }
        }

        val extras = buildList {
            details.rpe?.let { add("RPE $it/10") }
            details.activeMinutes?.takeIf { it > 0 }?.let { add("$it active min") }
        }
        if (extras.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        }

        if (details.exercises.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Exercises", style = MaterialTheme.typography.titleSmall, color = Cream)
            Spacer(Modifier.height(6.dp))
            details.exercises.forEach { e ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.name.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.weight(1f))
                    Text(exerciseDetailLine(e), style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                }
            }
        }
    }
}

/** "3 sets · 30 reps · 42 kcal", dropping any piece Calistapp didn't record. */
private fun exerciseDetailLine(e: CalistappWorkoutDetails.Exercise): String = buildList {
    if (e.sets > 0) add("${e.sets} set${if (e.sets == 1) "" else "s"}")
    if (e.reps > 0) add("${e.reps} reps")
    if (e.kcal >= 1.0) add("${e.kcal.toInt()} kcal")
}.joinToString(" · ").ifBlank { "—" }

private val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM")

private fun prettyDate(iso: String): String = try {
    LocalDate.parse(iso).format(dateFmt)
} catch (e: Exception) {
    iso
}
