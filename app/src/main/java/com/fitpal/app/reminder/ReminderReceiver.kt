package com.fitpal.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fitpal.app.MainActivity
import com.fitpal.app.domain.model.ReminderKind
import com.fitpal.app.ml.ReviewGenerator
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * Fires the app's reminders, re-arms alarms after a reboot, and (for the AI-overview kinds)
 * generates the overview in the background before its notification deep-links into it.
 */
class ReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReminderEntryPoint {
        fun reminderManager(): ReminderManager
        fun reviewGenerator(): ReviewGenerator
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> entryPoint(context).reminderManager().reschedule()
            ReminderManager.ACTION_FIRE -> {
                postLogReminder(context)
                // One-shot alarm — arm the next day's occurrence now that this one has fired.
                entryPoint(context).reminderManager().reschedule()
            }
            ReminderManager.ACTION_FIRE_KIND -> {
                val kind = ReminderKind.entries.firstOrNull { it.key == intent.getStringExtra(ReminderManager.EXTRA_KIND) }
                if (kind != null) handleKind(context, kind)
                // One-shot alarm — arm the next occurrence (re-schedules every enabled reminder).
                entryPoint(context).reminderManager().reschedule()
            }
        }
    }

    private fun handleKind(context: Context, kind: ReminderKind) {
        if (kind.isOverview) {
            val (period, key) = overviewTarget(kind)
            // Post the notification immediately (it always works, generating on open if needed),
            // then best-effort pre-generate in the background so opening it is instant.
            postNotification(context, kind, Screen.AiReview.buildRoute(period, key), CHANNEL_OVERVIEWS, "AI overviews")
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    withTimeoutOrNull(45_000) {
                        runCatching { entryPoint(context).reviewGenerator().generateAndCache(period, key) }
                    }
                } finally {
                    pending.finish()
                }
            }
        } else {
            // Meals nudge the Add screen; the weigh-in nudge opens Home (where the weight card lives).
            val route = if (kind == ReminderKind.WEIGHT) Screen.Home.route else Screen.AddFood.route
            postNotification(context, kind, route, CHANNEL_REMINDERS, "Reminders")
        }
    }

    /** daily → today; weekly → the start (Monday) of the week that just ended. */
    private fun overviewTarget(kind: ReminderKind): Pair<String, String> {
        val today = LocalDate.now()
        return if (kind == ReminderKind.WEEKLY_OVERVIEW) {
            val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
            "weekly" to weekStart.toString()
        } else {
            "daily" to today.toString()
        }
    }

    /** The legacy "log your day" reminder. */
    private fun postLogReminder(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Gentle nudges to log your day" }
        )
        val pi = openIntent(context, Screen.AddFood.route, NOTIF_LOG)
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Log today's meals")
            .setContentText("Keep your streak going — tap to add what you've eaten.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { manager.notify(NOTIF_LOG, notification) }
    }

    private fun postNotification(context: Context, kind: ReminderKind, route: String, channelId: String, channelName: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
        )
        val notifId = NOTIF_KIND_BASE + kind.ordinal
        val pi = openIntent(context, route, notifId)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(kind.notifTitle)
            .setContentText(kind.notifText)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { manager.notify(notifId, notification) }
    }

    private fun openIntent(context: Context, route: String, requestCode: Int): PendingIntent {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(MainActivity.EXTRA_NAV_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context, requestCode, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun entryPoint(context: Context): ReminderEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java)

    companion object {
        private const val CHANNEL_REMINDERS = "fitpal_reminders"
        private const val CHANNEL_OVERVIEWS = "fitpal_overviews"
        private const val NOTIF_LOG = 5202
        private const val NOTIF_KIND_BASE = 5220
    }
}
