package com.fitpal.wear.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.fitpal.shared.StatsSnapshot
import com.fitpal.wear.StepsSync
import com.fitpal.wear.data.PhoneComms
import com.fitpal.wear.data.WearStats

/**
 * The watch home screen: today's calories (as a ring), macros, water, exercise + steps, and the
 * action chips that open the log flows. Reads the phone's synced snapshot and asks for a fresh one
 * on open.
 */
@Composable
fun StatsScreen(
    onLogWater: () -> Unit,
    onDescribeMeal: () -> Unit,
    onLogExercise: () -> Unit
) {
    val context = LocalContext.current
    val snapshot by produceState<StatsSnapshot?>(initialValue = null, context) {
        WearStats.flow(context).collect { value = it }
    }

    // Connection state: null = still checking, true/false = phone app reachable. Re-checked on
    // every open and on demand via the Reconnect chip.
    var connected by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(false) }
    var reconnectTick by remember { mutableStateOf(0) }
    LaunchedEffect(reconnectTick) {
        checking = true
        val ok = PhoneComms.isPhoneAppReachable(context)
        connected = ok
        // Asking for stats also wakes the phone's listener service, so this doubles as the "pull
        // fresh numbers" step — no need for the phone app to be open.
        if (ok) PhoneComms.requestStats(context)
        checking = false
    }

    // Watch-steps sync needs activity recognition once; after that it runs passively forever.
    var hasStepsPermission by remember { mutableStateOf(StepsSync.hasPermission(context)) }
    val stepsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasStepsPermission = granted
        if (granted) StepsSync.register(context)
    }

    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { CalorieRing(snapshot) }
            item { MacrosRow(snapshot) }
            item { WaterRow(snapshot) }
            item { BurnRow(snapshot) }
            item { ConnectionRow(connected = connected, checking = checking) }
            if (snapshot == null && connected == false) {
                item {
                    Text(
                        text = "Can't reach your phone. Check Bluetooth and that FitPal is installed on it.",
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onBackground,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    )
                }
            }
            if (connected != true || snapshot == null) {
                item {
                    Chip(
                        label = { Text(if (checking) "Connecting…" else "Reconnect") },
                        onClick = { if (!checking) reconnectTick++ },
                        colors = ChipDefaults.secondaryChipColors(),
                        enabled = !checking,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Chip(
                    label = { Text("Log water") },
                    onClick = onLogWater,
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    label = { Text("Describe meal") },
                    onClick = onDescribeMeal,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    label = { Text("Log exercise") },
                    onClick = onLogExercise,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!hasStepsPermission) {
                item {
                    Chip(
                        label = { Text("Sync watch steps") },
                        secondaryLabel = { Text("Allow step access") },
                        onClick = {
                            stepsPermissionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun CalorieRing(snapshot: StatsSnapshot?) {
    val consumed = snapshot?.caloriesConsumed ?: 0
    val target = snapshot?.caloriesTarget ?: 0
    val left = snapshot?.caloriesLeft ?: 0
    val fraction = if (target > 0) (consumed.toFloat() / target).coerceIn(0f, 1f) else 0f
    val over = target > 0 && consumed > target

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
            CircularProgressIndicator(
                progress = fraction,
                modifier = Modifier.size(96.dp),
                strokeWidth = 6.dp,
                indicatorColor = if (over) MaterialTheme.colors.error else MaterialTheme.colors.primary,
                trackColor = MaterialTheme.colors.surface
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (target > 0) "$left" else "$consumed",
                    style = MaterialTheme.typography.title1,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.onBackground
                )
                Text(
                    text = if (target > 0) "kcal left" else "kcal",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onBackground
                )
            }
        }
        if (target > 0) {
            Text(
                text = "eaten $consumed / $target",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onBackground
            )
        }
    }
}

@Composable
private fun MacrosRow(snapshot: StatsSnapshot?) {
    val s = snapshot ?: StatsSnapshot()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        MacroCell("P", s.proteinG, s.proteinTargetG)
        MacroCell("F", s.fatG, s.fatTargetG)
        MacroCell("C", s.carbsG, s.carbsTargetG)
    }
}

@Composable
private fun MacroCell(label: String, value: Int, target: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.caption2, color = MaterialTheme.colors.primary)
        Text(
            text = if (target > 0) "$value/$target" else "$value",
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onBackground
        )
    }
}

@Composable
private fun WaterRow(snapshot: StatsSnapshot?) {
    val s = snapshot ?: return
    Text(
        text = "Water ${s.waterMl} / ${s.waterGoalMl} ml",
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.onBackground
    )
}

/** A small dot + label showing whether the FitPal phone app is reachable right now. */
@Composable
private fun ConnectionRow(connected: Boolean?, checking: Boolean) {
    val (color, label) = when {
        checking || connected == null -> MaterialTheme.colors.onBackground to "Checking phone…"
        connected -> MaterialTheme.colors.primary to "Phone connected"
        else -> MaterialTheme.colors.error to "Phone not reachable"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onBackground
        )
    }
}

@Composable
private fun BurnRow(snapshot: StatsSnapshot?) {
    val s = snapshot ?: return
    Text(
        text = "Burned ${s.totalBurned} kcal · ${s.steps} steps",
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onBackground
    )
}
