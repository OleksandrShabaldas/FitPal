package com.fitpal.wear.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.fitpal.shared.StatsSnapshot
import com.fitpal.wear.data.PhoneComms
import com.fitpal.wear.data.WearStats
import kotlinx.coroutines.launch

/** Quick-add water from preset amounts (mirrored from the phone). Each tap messages the phone. */
@Composable
fun WaterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot by produceState<StatsSnapshot?>(initialValue = null, context) {
        WearStats.flow(context).collect { value = it }
    }
    val presets = snapshot?.waterPresets ?: listOf(200, 330, 500)

    var lastLogged by remember { mutableStateOf<Int?>(null) }
    var notConnected by remember { mutableStateOf(false) }

    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { ListHeader { Text("Log water") } }
            snapshot?.let {
                item {
                    Text(
                        text = "${it.waterMl} / ${it.waterGoalMl} ml today",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onBackground
                    )
                }
            }
            items(presets) { ml ->
                Chip(
                    label = { Text("+$ml ml") },
                    onClick = {
                        notConnected = false
                        scope.launch {
                            val ok = PhoneComms.logWater(context, ml)
                            if (ok) {
                                lastLogged = ml
                                PhoneComms.requestStats(context)
                            } else {
                                notConnected = true
                            }
                        }
                    },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            lastLogged?.let {
                item {
                    Text(
                        text = "Added $it ml ✓",
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
            if (notConnected) {
                item {
                    Text(
                        text = "Phone not connected",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
