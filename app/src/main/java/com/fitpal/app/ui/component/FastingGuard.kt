package com.fitpal.app.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fitpal.app.domain.model.FastingSchedule
import com.fitpal.app.domain.model.FastingState
import java.time.LocalTime

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
 * screens can wrap every log call unconditionally.
 */
@Composable
fun rememberFastingGuard(): FastingGuard {
    val schedule = LocalFastingSchedule.current
    val guard = remember(schedule) { FastingGuard(schedule) }
    guard.stateForDialog?.let { state ->
        FastingWarningDialog(
            state = state,
            onConfirm = guard::confirm,
            onDismiss = guard::dismiss
        )
    }
    return guard
}

@Stable
class FastingGuard(private val schedule: FastingSchedule) {
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

    private fun nowMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
}

@Composable
private fun FastingWarningDialog(state: FastingState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("You're fasting") },
        text = {
            Text(
                "Your eating window opens at ${fastingClockLabel(state.nextChangeMin)} — " +
                    "${fastingCountdownLabel(state.minutesLeftInPhase)} to go. Log this anyway?"
            )
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
