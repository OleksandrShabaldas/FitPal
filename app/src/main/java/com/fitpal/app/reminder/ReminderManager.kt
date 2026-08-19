package com.fitpal.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fitpal.app.data.repository.SettingsRepository
import com.fitpal.app.domain.model.ReminderKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules all of the app's optional reminders via [AlarmManager] — no extra dependency, fully
 * offline. Each alarm is a ONE-SHOT `…AndAllowWhileIdle` alarm (so it still fires in Doze, unlike a
 * repeating alarm which the OS throttles after a few days) and is re-armed for the next occurrence
 * whenever it fires (see [ReminderReceiver]) and on boot / app start.
 *
 * Uses exact alarms when the user has granted "Alarms & reminders" (Android 12+), otherwise a still-
 * Doze-friendly inexact one. Covers the legacy "log your day" reminder plus the per-[ReminderKind]
 * meal/weigh-in reminders and the daily/weekly AI overview reminders (which also generate the
 * overview when they fire).
 */
@Singleton
class ReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) {
    /** True if we're allowed exact alarms (always on <31; user-granted on 31+). */
    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= 31)
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
        else true
    // --- Legacy "log your day" reminder ---
    fun setEnabled(enabled: Boolean) {
        settings.setReminderEnabled(enabled)
        reschedule()
    }

    fun setTime(minutesSinceMidnight: Int) {
        settings.setReminderMinutes(minutesSinceMidnight)
        reschedule()
    }

    // --- Per-kind reminders (meals, weigh-in, AI overviews) ---
    fun setReminder(kind: ReminderKind, enabled: Boolean, minutes: Int) {
        settings.setReminder(kind, enabled, minutes)
        reschedule()
    }

    /** Apply all current settings: arm the enabled alarms (one-shot, re-armed on fire), cancel the rest. */
    fun reschedule() {
        val am = context.getSystemService(AlarmManager::class.java) ?: return

        // Legacy daily "log your day" reminder.
        val logPi = pendingIntent(context)
        am.cancel(logPi)
        if (settings.reminderEnabled.value) {
            scheduleOneShot(am, nextDaily(settings.reminderMinutes.value), logPi)
        }

        // Meal / weigh-in / overview reminders.
        ReminderKind.entries.forEach { kind ->
            val pi = kindPendingIntent(context, kind)
            am.cancel(pi)
            val state = settings.reminderStateFor(kind)
            if (state.enabled) {
                val at = if (kind.weekly) nextWeekly(state.minutes, Calendar.SUNDAY) else nextDaily(state.minutes)
                scheduleOneShot(am, at, pi)
            }
        }

        // Fasting window notifications — the two times come from the schedule itself (eating opens at
        // the window start, the fast begins at the window end), not from a user-set reminder time.
        val fasting = settings.fastingSchedule.value
        listOf(FASTING_EAT_OPEN to fasting.eatStartMin, FASTING_FAST_BEGIN to fasting.eatEndMin).forEach { (event, minutes) ->
            val pi = fastingPendingIntent(context, event)
            am.cancel(pi)
            if (fasting.enabled && fasting.notify) scheduleOneShot(am, nextDaily(minutes), pi)
        }
    }

    /**
     * Arm a single one-shot wake-up that survives Doze. Exact when the user granted the permission,
     * otherwise a Doze-friendly inexact alarm. Re-armed for the next day/week when it fires.
     */
    private fun scheduleOneShot(am: AlarmManager, triggerAtMillis: Long, pi: PendingIntent) {
        try {
            if (canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked between the check and the call — fall back.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun nextDaily(minutes: Int): Long {
        val now = Calendar.getInstance()
        val target = atTime(minutes)
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    private fun nextWeekly(minutes: Int, dayOfWeek: Int): Long {
        val now = Calendar.getInstance()
        val target = atTime(minutes)
        while (target.get(Calendar.DAY_OF_WEEK) != dayOfWeek || !target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    private fun atTime(minutes: Int): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minutes / 60)
        set(Calendar.MINUTE, minutes % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    companion object {
        const val ACTION_FIRE = "com.fitpal.app.REMINDER_FIRE"
        const val ACTION_FIRE_KIND = "com.fitpal.app.REMINDER_FIRE_KIND"
        const val ACTION_FIRE_FASTING = "com.fitpal.app.REMINDER_FIRE_FASTING"
        const val EXTRA_KIND = "kind"
        const val EXTRA_FASTING_EVENT = "fasting_event"
        /** The eating window opened (fast ended). */
        const val FASTING_EAT_OPEN = "eat_open"
        /** The eating window closed (fast began). */
        const val FASTING_FAST_BEGIN = "fast_begin"
        private const val REQUEST_CODE = 5201
        private const val REQUEST_CODE_KIND_BASE = 5210
        private const val REQUEST_CODE_FASTING_BASE = 5230

        fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
            return PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun kindPendingIntent(context: Context, kind: ReminderKind): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_FIRE_KIND)
                .putExtra(EXTRA_KIND, kind.key)
            return PendingIntent.getBroadcast(
                context, REQUEST_CODE_KIND_BASE + kind.ordinal, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        fun fastingPendingIntent(context: Context, event: String): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_FIRE_FASTING)
                .putExtra(EXTRA_FASTING_EVENT, event)
            val code = REQUEST_CODE_FASTING_BASE + if (event == FASTING_FAST_BEGIN) 1 else 0
            return PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
