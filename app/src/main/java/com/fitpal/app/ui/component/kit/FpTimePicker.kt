package com.fitpal.app.ui.component.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitpal.app.domain.ClockFormat
import com.fitpal.app.ui.component.GoldButton
import com.fitpal.app.ui.component.SetupPill
import com.fitpal.app.ui.component.TrackerSheet
import com.fitpal.app.ui.theme.Cream
import com.fitpal.app.ui.theme.CreamFaint
import com.fitpal.app.ui.theme.CreamMuted
import com.fitpal.app.ui.theme.GoldLight
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import com.fitpal.app.ui.theme.NumberFamily
import java.time.LocalTime
import kotlin.math.abs

/*
 * FitPal's own time picker (docs/redesign/16), in place of Android's clock dialog everywhere a time is
 * picked: hour and minute wheels (plus AM/PM on the 12-hour clock) in a sheet, a gold band on the
 * picked row, "… ago" chips where they make sense, and a gold Done. It follows the 12/24-hour setting
 * ([ClockFormat]). In Material You it's Material 3's own clock in its dialog, as in Pixel apps.
 */

/** Opens the picker from a click handler; the composable that remembered it draws it. */
class TimePickerHost internal constructor() {
    internal class Request(val title: String, val minutes: Int, val quickAgo: Boolean, val onPick: (Int) -> Unit)

    internal var request by mutableStateOf<Request?>(null)

    /**
     * Pick a time, starting at [minutes] (minute of the day). [quickAgo] adds "Now · 15 min ago · …"
     * chips (for when something was eaten); [onPick] gets the minute of the day.
     */
    fun open(minutes: Int, title: String = "Pick a time", quickAgo: Boolean = false, onPick: (Int) -> Unit) {
        request = Request(title, minutes.mod(DAY), quickAgo, onPick)
    }
}

@Composable
fun rememberTimePicker(): TimePickerHost {
    val host = remember { TimePickerHost() }
    host.request?.let { r ->
        FpTimePicker(
            title = r.title,
            initialMinutes = r.minutes,
            quickAgo = r.quickAgo,
            onPick = { m -> host.request = null; r.onPick(m) },
            onDismiss = { host.request = null }
        )
    }
    return host
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FpTimePicker(
    title: String,
    initialMinutes: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    quickAgo: Boolean = false
) {
    val h24 = ClockFormat.use24h
    if (LookState.style == LookStyle.MATERIAL) {
        MaterialClock(title, initialMinutes, h24, onPick, onDismiss)
        return
    }
    val startHour = initialMinutes / 60
    var hour by remember { mutableIntStateOf(startHour) }          // 0–23 on the 24-hour clock, 1–12 otherwise
    var minute by remember { mutableIntStateOf(initialMinutes % 60) }
    var pm by remember { mutableStateOf(startHour >= 12) }
    fun result(): Int {
        val h = if (h24) hour else (hour % 12) + if (pm) 12 else 0
        return h * 60 + minute
    }

    TrackerSheet(title = title, onDismiss = onDismiss) {
        if (quickAgo) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_AGO.forEach { (label, ago) ->
                    SetupPill(label, selected = false) {
                        val now = LocalTime.now()
                        onPick((now.hour * 60 + now.minute - ago).mod(DAY))
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The gold band behind the picked row.
            val band = RoundedCornerShape(14.dp)
            Box(
                Modifier.fillMaxWidth().height(ROW_HEIGHT).background(GoldLight.copy(alpha = 0.10f), band)
                    .border(1.dp, GoldLight.copy(alpha = 0.32f), band)
            )
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (h24) Wheel(24, startHour, { "%02d".format(it) }, { hour = it })
                else Wheel(12, (startHour + 11) % 12, { "${it + 1}" }, { hour = it + 1 })
                Text(":", style = wheelStyle(true), color = Cream, modifier = Modifier.width(18.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Wheel(60, initialMinutes % 60, { "%02d".format(it) }, { minute = it })
                if (!h24) Wheel(2, if (startHour >= 12) 1 else 0, { if (it == 0) "AM" else "PM" }, { pm = it == 1 }, cyclic = false)
            }
        }
        GoldButton("Done", onClick = { onPick(result()) }, modifier = Modifier.fillMaxWidth())
    }
}

/** One wheel: flick it, it snaps to a row; the picked one is big and bright, the rest fade out. */
@Composable
private fun Wheel(
    count: Int,
    initial: Int,
    label: (Int) -> String,
    onSelected: (Int) -> Unit,
    cyclic: Boolean = true
) {
    // A cyclic wheel is a long strip of repeats, started in the middle, so it turns past 59 → 00.
    val total = if (cyclic) count * CYCLES else count
    val start = if (cyclic) count * (CYCLES / 2) + initial else initial
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val haptics = LocalHapticFeedback.current
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: start
        }
    }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(centered) {
        onSelected(centered % count)
        if (first) first = false else haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = ROW_HEIGHT * (VISIBLE / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(if (count == 2) 64.dp else 72.dp).height(ROW_HEIGHT * VISIBLE)
    ) {
        items(total) { i ->
            val away = abs(i - centered)
            Box(Modifier.height(ROW_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    label(i % count),
                    style = wheelStyle(away == 0),
                    color = when (away) { 0 -> Cream; 1 -> CreamMuted; else -> CreamFaint }
                )
            }
        }
    }
}

@Composable
private fun wheelStyle(picked: Boolean) = MaterialTheme.typography.titleLarge.copy(
    fontFamily = NumberFamily,
    fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
    fontSize = if (picked) 26.sp else 19.sp,
    fontFeatureSettings = "tnum"
)

/** Material You: Material 3's own clock, in its dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialClock(title: String, initialMinutes: Int, h24: Boolean, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = h24)
    FpAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private val ROW_HEIGHT = 44.dp
private const val VISIBLE = 5
private const val CYCLES = 200
private const val DAY = 24 * 60
private val QUICK_AGO = listOf("Now" to 0, "15 min ago" to 15, "30 min ago" to 30, "1 h ago" to 60)
