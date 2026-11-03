package com.fitpal.app.ml

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fitpal.app.FitPalActivity
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cook mode's step timers (docs/redesign/14). Each timer belongs to one step of one recipe; starting
 * it schedules an alarm (exact when allowed), so "Timer done · Sear the chicken" arrives as a
 * notification even with FitPal closed. The countdown on screen reads the end time from here.
 * Several can run at once. Held in memory: after the app is killed the alarm still rings, the
 * on-screen countdown just starts over.
 */
@Singleton
class CookTimers @Inject constructor(@ApplicationContext private val context: Context) {

    /** A step's timer: running ([endsAt] set) or paused with [leftMs] to go. */
    data class Timer(val label: String, val totalMs: Long, val endsAt: Long?, val leftMs: Long) {
        val running: Boolean get() = endsAt != null
        fun remaining(now: Long): Long = if (endsAt != null) (endsAt - now).coerceAtLeast(0L) else leftMs
    }

    private val _timers = MutableStateFlow<Map<String, Timer>>(emptyMap())
    val timers: StateFlow<Map<String, Timer>> = _timers

    /** Start (or resume) the timer for [key] — [minutes] long, or what was left when it was paused. */
    fun start(key: String, label: String, minutes: Int, recipeId: Long) {
        ensureChannel(context)
        val now = System.currentTimeMillis()
        val total = minutes * 60_000L
        val left = _timers.value[key]?.takeIf { !it.running && it.leftMs > 0L }?.leftMs ?: total
        val ends = now + left
        _timers.update { it + (key to Timer(label, total, ends, left)) }
        schedule(key, label, ends, recipeId)
    }

    fun pause(key: String) {
        val t = _timers.value[key] ?: return
        cancel(key)
        _timers.update { it + (key to t.copy(endsAt = null, leftMs = t.remaining(System.currentTimeMillis()))) }
    }

    fun reset(key: String) {
        cancel(key)
        _timers.update { it - key }
    }

    private fun schedule(key: String, label: String, at: Long, recipeId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(key, label, recipeId)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(key: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(alarmIntent(key, "", 0L)) }
    }

    private fun alarmIntent(key: String, label: String, recipeId: Long): PendingIntent {
        val intent = Intent(context, CookTimerReceiver::class.java)
            .setAction(ACTION_DONE)
            .setData(android.net.Uri.parse("fitpal://cook-timer/$key"))
            .putExtra(EXTRA_LABEL, label)
            .putExtra(EXTRA_RECIPE, recipeId)
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val ACTION_DONE = "com.fitpal.app.COOK_TIMER_DONE"
        const val EXTRA_LABEL = "label"
        const val EXTRA_RECIPE = "recipe"
        const val CHANNEL = "cook_timers"

        /** The key for step [stepIndex] of planned meal [recipeId]. */
        fun keyFor(recipeId: Long, stepIndex: Int): String = "$recipeId-$stepIndex"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Cooking timers", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "When a timer you started in a recipe's cook mode is done"
                    }
                )
            }
        }
    }
}

/** Posts "Timer done · <step>" when a cook-mode timer's alarm rings; tapping it opens the recipe. */
class CookTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CookTimers.ACTION_DONE) return
        CookTimers.ensureChannel(context)
        val label = intent.getStringExtra(CookTimers.EXTRA_LABEL).orEmpty().ifBlank { "Your recipe" }
        val recipeId = intent.getLongExtra(CookTimers.EXTRA_RECIPE, -1L)
        val open = Intent(context, FitPalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            if (recipeId > 0L) putExtra(FitPalActivity.EXTRA_NAV_ROUTE, Screen.PlannedMeal.buildRoute(recipeId))
        }
        val tap = PendingIntent.getActivity(context, intent.data.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CookTimers.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Timer done")
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(intent.data.hashCode(), notification) }
    }
}
