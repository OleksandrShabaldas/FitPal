package com.fitpal.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fitpal.app.MainActivity
import com.fitpal.app.domain.model.DietaryRuleKind
import com.fitpal.app.ui.navigation.Screen
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the one-off "you've reached your … limit for today" notification the moment a dietary-rule
 * cap is passed. Unlike the fasting notifications (scheduled by the clock via [ReminderManager]),
 * this one is **event-driven** — fired from the log path when the day's tally crosses the cap — so
 * it lives here rather than on an alarm. The once-a-day guard lives in the caller
 * (`MealRepository` + `SettingsRepository.markDietaryNotified`), so this just posts.
 */
@Singleton
class DietaryNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun notifyLimitReached(kind: DietaryRuleKind, consumedKcal: Int, limitKcal: Int) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val (title, text) = copyFor(kind, consumedKcal, limitKcal)
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openHome(NOTIF_BASE + kind.ordinal))
            .build()
        runCatching { manager.notify(NOTIF_BASE + kind.ordinal, notification) }
    }

    private fun copyFor(kind: DietaryRuleKind, consumed: Int, limit: Int): Pair<String, String> = when (kind) {
        DietaryRuleKind.DESSERT ->
            "Dessert limit reached" to "You're at $consumed of your $limit kcal for dessert today. Maybe save the rest for tomorrow."
        DietaryRuleKind.FRIED ->
            "Fried & fast food limit reached" to "You're at $consumed of your $limit kcal for fried and fast food today."
        DietaryRuleKind.SUGARY_DRINK ->
            "Sugary drinks limit reached" to "You're at $consumed of your $limit kcal from sugary drinks today. Water from here?"
    }

    private fun openHome(requestCode: Int): PendingIntent {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(MainActivity.EXTRA_NAV_ROUTE, Screen.Home.route)
        }
        return PendingIntent.getActivity(
            context, requestCode, open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private companion object {
        const val CHANNEL_REMINDERS = "fitpal_reminders"
        const val NOTIF_BASE = 5250
    }
}
