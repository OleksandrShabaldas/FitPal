package com.fitpal.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.io.File

/**
 * Handles a new watch APK pushed over from the phone: stages the file, then posts a notification
 * the user taps to run the system installer.
 *
 * A watch app can't install anything silently (same Android rule as on the phone), so the most it
 * can do is put the confirm screen one tap away. If the watch refuses to open an installer at all,
 * sideloading over ADB is still the fallback — see the project README.
 */
object WatchUpdateInstaller {

    private const val PREFS = "fitpal_wear_update"
    private const val KEY_INCOMING_VERSION = "incoming_version"
    private const val CHANNEL_ID = "fitpal_updates"
    private const val NOTIF_ID = 7401

    /** Where the phone's streamed APK is written. Kept in cache — it's disposable once installed. */
    fun stagingFile(context: Context): File =
        File(context.cacheDir, "update").apply { mkdirs() }.let { File(it, "fitpal-watch-update.apk") }

    fun rememberIncomingVersion(context: Context, version: String) {
        if (version.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_INCOMING_VERSION, version).apply()
    }

    fun incomingVersion(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_INCOMING_VERSION, "").orEmpty()

    /** The APK finished transferring — nudge the user to install it. */
    fun onApkReceived(context: Context, apk: File) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val version = incomingVersion(context)
        val intent = Intent(context, InstallUpdateActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(if (version.isBlank()) "FitPal update ready" else "FitPal $version ready")
            .setContentText("Tap to install on your watch")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching { manager.notify(NOTIF_ID, notification) }
    }
}
