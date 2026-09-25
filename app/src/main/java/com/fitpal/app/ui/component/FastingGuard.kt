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
import com.fitpal.app.domain.model.LogDecision
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
 * screen (it also emits the prompt when needed), then wrap the log action:
 *
 * ```
 * val guard = rememberFastingGuard()
 * Button(onClick = { guard.attempt(eatenAt = time, photoTime = fromPhoto) { decision -> viewModel.logMeal(decision) } }) { … }
 * ```
 *
 * The rules (a pass = one of the few monthly "I ate this earlier" corrections):
 *  - Only a log **for today, while it's fasting time right now** can be stopped — back-filling another
 *    day never nags, and with the eating window open nothing is in the way.
 *  - A **photo's own capture time** inside the eating window is proof: no prompt, no pass spent.
 *  - Otherwise the prompt offers a pass (the day's fast stays kept; the meal is logged at the eaten
 *    time) or "log anyway", which marks the meal as logged during the fast — that day stays broken
 *    even if the meal's time is edited later, so a free time edit never stands in for a pass.
 *
 * The action receives a [LogDecision] describing what was chosen, to be saved with the meal.
 */
@Composable
fun rememberFastingGuard(): FastingGuard {
    val schedule = LocalFastingSchedule.current
    val backdate = LocalFastingBackdate.current
    val context = LocalContext.current
    val guard = remember(schedule, backdate) { FastingGuard(schedule, backdate) }
    guard.promptForDialog?.let { prompt ->
        FastingWarningDialog(
            prompt = prompt,
            remainingPasses = backdate.remainingThisMonth,
            onLogAnyway = guard::logAnyway,
            onDismiss = guard::dismiss,
            onUsePassAtChosenTime = guard::usePassAtChosenTime,
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
                                "Logged as eaten at ${fastingClockLabel(minute)} — your fast stays kept.",
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

/** Why the prompt is up, and what the meal's time looks like against the window. */
data class FastingPrompt(
    /** Where "now" sits in the fasting cycle (for "your window opens at …"). */
    val now: FastingState,
    /** The meal's eaten time (minute of day). */
    val mealMinute: Int,
    /** A time was chosen by hand and it's inside the eating window — spending a pass keeps it. */
    val chosenTimeInWindow: Boolean,
    /** The meal's time came from the photo (and it's inside the fast, or there'd be no prompt). */
    val fromPhoto: Boolean
)

@Stable
class FastingGuard(private val schedule: FastingSchedule, private val backdate: FastingBackdate) {
    internal var promptForDialog by mutableStateOf<FastingPrompt?>(null)
        private set
    private var pending: ((LogDecision) -> Unit)? = null

    /**
     * Run [action] now, unless this log needs the fasting prompt — in which case stash it and raise
     * the prompt. [eatenAt] is the meal's chosen time (null = now); [photoTime] = it came from the
     * photo's own metadata. Pass `isForToday = false` when logging to another day.
     */
    fun attempt(
        isForToday: Boolean = true,
        eatenAt: LocalTime? = null,
        photoTime: Boolean = false,
        action: (LogDecision) -> Unit
    ) {
        if (!schedule.enabled || !schedule.warnOnLog || !isForToday) {
            action(LogDecision.NONE)
            return
        }
        val nowMin = nowMinutes()
        val mealMin = eatenAt?.let { it.hour * 60 + it.minute } ?: nowMin
        val mealInWindow = schedule.isEatingAt(mealMin)
        when {
            // The photo proves the meal was eaten inside the window — free, whatever the clock says.
            photoTime && eatenAt != null && mealInWindow -> action(LogDecision.NONE)
            // The window is open right now: nothing to stop. (A meal time set inside the fast is an
            // honest record — its own time breaks the day, no prompt needed.)
            schedule.isEatingAt(nowMin) -> action(LogDecision.NONE)
            else -> {
                pending = action
                promptForDialog = FastingPrompt(
                    now = schedule.stateAt(nowMin),
                    mealMinute = mealMin,
                    chosenTimeInWindow = eatenAt != null && !photoTime && mealInWindow,
                    fromPhoto = photoTime && eatenAt != null
                )
            }
        }
    }

    /** "Log anyway": saved as logged during the fast — the day's fast is broken for good. */
    internal fun logAnyway() = finish(LogDecision(loggedDuringFast = true))

    internal fun dismiss() {
        pending = null
        promptForDialog = null
    }

    /** Spend a pass on the time the user already chose (it's inside the eating window). */
    internal fun usePassAtChosenTime() {
        val prompt = promptForDialog ?: return
        backdate.onUse(LocalDate.now().toString(), prompt.mealMinute)
        finish(LogDecision(overrideTime = LocalTime.of(prompt.mealMinute / 60, prompt.mealMinute % 60)))
    }

    /**
     * "I actually ate this earlier" at [minute]. Only valid inside the eating window: it spends a pass
     * (so the day's fast stays kept) and logs the meal at that time. Returns false if the chosen time
     * is still in the fasting window — the prompt stays open.
     */
    internal fun applyBackdate(minute: Int): Boolean {
        if (!schedule.isEatingAt(minute)) return false
        backdate.onUse(LocalDate.now().toString(), minute)
        finish(LogDecision(overrideTime = LocalTime.of(minute / 60, minute % 60)))
        return true
    }

    private fun finish(decision: LogDecision) {
        val action = pending
        pending = null
        promptForDialog = null
        action?.invoke(decision)
    }

    private fun nowMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
}

@Composable
private fun FastingWarningDialog(
    prompt: FastingPrompt,
    remainingPasses: Int,
    onLogAnyway: () -> Unit,
    onDismiss: () -> Unit,
    onUsePassAtChosenTime: () -> Unit,
    onEatenEarlier: () -> Unit
) {
    val opens = "Your eating window opens at ${fastingClockLabel(prompt.now.nextChangeMin)} — " +
        "${fastingCountdownLabel(prompt.now.minutesLeftInPhase)} to go."
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("You're fasting") },
        text = {
            Column {
                when {
                    prompt.chosenTimeInWindow -> Text(
                        "$opens You set this meal to ${fastingClockLabel(prompt.mealMinute)}, inside your " +
                            "window — counting it then uses one of your \"ate earlier\" passes."
                    )
                    prompt.fromPhoto -> Text(
                        "$opens This photo was taken at ${fastingClockLabel(prompt.mealMinute)}, during your fast. Log it anyway?"
                    )
                    else -> Text("$opens Log this anyway?")
                }
                Spacer(Modifier.height(12.dp))
                when {
                    remainingPasses <= 0 -> Text(
                        "No \"ate earlier\" passes left this month, so logging now counts against today's fast.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    prompt.chosenTimeInWindow -> TextButton(onClick = onUsePassAtChosenTime) {
                        Text("Use a pass · $remainingPasses left this month")
                    }
                    else -> TextButton(onClick = onEatenEarlier) {
                        Text("I actually ate this earlier · $remainingPasses left this month")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onLogAnyway) { Text("Log anyway") } },
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

/** The same 12-hour label for a clock time. */
fun clockLabel(time: LocalTime): String = fastingClockLabel(time.hour * 60 + time.minute)
