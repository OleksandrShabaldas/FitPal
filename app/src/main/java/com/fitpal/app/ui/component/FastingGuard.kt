package com.fitpal.app.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitpal.app.domain.model.FastingSchedule
import com.fitpal.app.domain.model.FastingState
import java.time.LocalDate
import java.time.LocalTime

/**
 * The monthly "I ate earlier" allowance, published app-wide by `MainActivity` from settings so the
 * log-time warning can offer it. [onUse] records a grace day (dateIso -> the eating time the user
 * attested), which forces that day's fast to count as kept.
 */
data class FastingBackdate(
    val remainingThisMonth: Int = 0,
    val onUse: (dateIso: String, minute: Int) -> Unit = { _, _ -> }
)

val LocalFastingBackdate = compositionLocalOf { FastingBackdate() }

/**
 * A reusable "you're fasting — log anyway?" gate. Call [rememberFastingGuard] once near the top of a
 * screen (it also emits the confirm dialog when needed), then wrap the log action:
 *
 * ```
 * val guard = rememberFastingGuard()
 * Button(onClick = { guard.attempt { viewModel.logMeal() } }) { … }
 * ```
 *
 * The dialog only interrupts when fasting is on, the warning is on, it's currently the fasting phase,
 * and the log is for today. Otherwise [FastingGuard.attempt] just runs the action immediately, so
 * screens can wrap every log call unconditionally. The dialog also offers an "I ate this earlier"
 * correction (up to a few times a month) that keeps the day's fasting streak intact.
 */
@Composable
fun rememberFastingGuard(): FastingGuard {
    val schedule = LocalFastingSchedule.current
    val backdate = LocalFastingBackdate.current
    val context = LocalContext.current
    val guard = remember(schedule, backdate) { FastingGuard(schedule, backdate) }
    guard.stateForDialog?.let { state ->
        FastingWarningDialog(
            state = state,
            remainingBackdates = backdate.remainingThisMonth,
            onConfirm = guard::confirm,
            onDismiss = guard::dismiss,
            onEatenEarlier = {
                // Default the picker to the middle of the eating window — always a valid "in window" time.
                val mid = (schedule.eatStartMin + schedule.eatingLengthMin() / 2).mod(24 * 60)
                android.app.TimePickerDialog(
                    context,
                    { _, h, m ->
                        val minute = h * 60 + m
                        if (guard.applyBackdate(minute)) {
                            Toast.makeText(
                                context,
                                "Logged as eaten at ${fastingClockLabel(minute)} — your streak stays intact.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "That time is still inside your fasting window — pick a time you were allowed to eat.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    mid / 60, mid % 60, false
                ).show()
            }
        )
    }
    return guard
}

@Stable
class FastingGuard(private val schedule: FastingSchedule, private val backdate: FastingBackdate) {
    internal var stateForDialog by mutableStateOf<FastingState?>(null)
        private set
    private var pending: (() -> Unit)? = null

    /**
     * Run [action] now, unless the user is mid-fast (with the warning on and [isForToday]) — in which
     * case stash it and raise the confirm dialog. Pass `isForToday = false` when logging to a past day
     * so back-filling history never nags.
     */
    fun attempt(isForToday: Boolean = true, action: () -> Unit) {
        val state = if (schedule.enabled) schedule.stateAt(nowMinutes()) else null
        if (schedule.warnOnLog && isForToday && state != null && state.isFasting) {
            pending = action
            stateForDialog = state
        } else {
            action()
        }
    }

    internal fun confirm() {
        val action = pending
        pending = null
        stateForDialog = null
        action?.invoke()
    }

    internal fun dismiss() {
        pending = null
        stateForDialog = null
    }

    /**
     * Apply an "I ate earlier" correction for today at [minute]. Only valid when that minute is inside
     * the eating window: it records a grace day (so the streak/heatmap count today as kept) and then
     * logs. Returns false if the chosen time is still in the fasting window — the dialog stays open.
     */
    internal fun applyBackdate(minute: Int): Boolean {
        if (!schedule.isEatingAt(minute)) return false
        backdate.onUse(LocalDate.now().toString(), minute)
        confirm()
        return true
    }

    private fun nowMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
}

@Composable
private fun FastingWarningDialog(
    state: FastingState,
    remainingBackdates: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onEatenEarlier: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("You're fasting") },
        text = {
            Column {
                Text(
                    "Your eating window opens at ${fastingClockLabel(state.nextChangeMin)} — " +
                        "${fastingCountdownLabel(state.minutesLeftInPhase)} to go. Log this anyway?"
                )
                Spacer(Modifier.height(12.dp))
                if (remainingBackdates > 0) {
                    TextButton(onClick = onEatenEarlier) {
                        Text("I actually ate this earlier · $remainingBackdates left this month")
                    }
                } else {
                    Text(
                        "No \"ate earlier\" corrections left this month.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Log anyway") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not yet") } }
    )
}

/** "3h 12m" / "45m" / "2h" — a compact human countdown. Shared by the guard, strip, and Settings. */
fun fastingCountdownLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

/** 12-hour clock label for a minute-of-day, matching Settings' own time formatting. */
fun fastingClockLabel(minutes: Int): String {
    val mm = minutes.mod(24 * 60)
    val h = mm / 60
    val hr12 = ((h + 11) % 12) + 1
    return "$hr12:${"%02d".format(mm % 60)} ${if (h < 12) "AM" else "PM"}"
}
