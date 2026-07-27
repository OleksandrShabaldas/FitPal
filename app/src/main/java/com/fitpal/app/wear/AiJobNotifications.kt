package com.fitpal.app.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fitpal.app.MainActivity
import com.fitpal.app.ui.navigation.Screen

/**
 * Notifications for watch-triggered AI jobs that run in a [MealAnalysisWorker] /
 * [ExerciseAnalysisWorker]. The "Analysing…" notice is the worker's foreground notification; when
 * the job finishes we post a "tap to review" notice that deep-links into the phone app the same
 * way [com.fitpal.app.ml.AnalysisService] and the home-screen widget do
 * ([MainActivity.EXTRA_NAV_ROUTE]).
 */
object AiJobNotifications {

    const val CHANNEL_MEAL = "meal_analysis"        // shared with AnalysisService
    const val CHANNEL_EXERCISE = "exercise_analysis"

    // Foreground (ongoing) ids while the worker runs…
    const val NOTIF_MEAL_PROGRESS = 4211
    const val NOTIF_EXERCISE_PROGRESS = 4212
    // …and separate ids for the "done, tap to review" notice so it survives the worker ending.
    private const val NOTIF_MEAL_DONE = 4213
    private const val NOTIF_EXERCISE_DONE = 4214

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_MEAL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_MEAL, "Meal analysis", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress and results of AI meal analysis"
                    setShowBadge(false)
                }
            )
        }
        if (mgr.getNotificationChannel(CHANNEL_EXERCISE) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_EXERCISE, "Exercise analysis", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress and results of AI exercise estimates"
                    setShowBadge(false)
                }
            )
        }
    }

    fun mealProgress(context: Context, message: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_MEAL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Analysing your meal…")
            .setContentText(message)
            .setContentIntent(openIntent(context, Screen.DescribeFood.route))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    fun exerciseProgress(context: Context, message: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_EXERCISE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Estimating your workout…")
            .setContentText(message)
            .setContentIntent(openIntent(context, Screen.LogExercise.route))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    fun postMealDone(context: Context) = notify(
        context, NOTIF_MEAL_DONE,
        NotificationCompat.Builder(context, CHANNEL_MEAL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Meal analysed")
            .setContentText("Tap to review and log it")
            .setContentIntent(openIntent(context, Screen.DescribeFood.route))
            .setAutoCancel(true)
            .build()
    )

    fun postExerciseDone(context: Context) = notify(
        context, NOTIF_EXERCISE_DONE,
        NotificationCompat.Builder(context, CHANNEL_EXERCISE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Workout estimated")
            .setContentText("Tap to review and log it")
            .setContentIntent(openIntent(context, Screen.LogExercise.route))
            .setAutoCancel(true)
            .build()
    )

    fun postMealFailed(context: Context) = notify(
        context, NOTIF_MEAL_DONE,
        NotificationCompat.Builder(context, CHANNEL_MEAL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Couldn't analyse the meal")
            .setContentText("Open FitPal to try again")
            .setAutoCancel(true)
            .build()
    )

    fun postExerciseFailed(context: Context) = notify(
        context, NOTIF_EXERCISE_DONE,
        NotificationCompat.Builder(context, CHANNEL_EXERCISE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Couldn't estimate the workout")
            .setContentText("Open FitPal to try again")
            .setAutoCancel(true)
            .build()
    )

    private fun openIntent(context: Context, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(MainActivity.EXTRA_NAV_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context, route.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, notification) }
    }
}
