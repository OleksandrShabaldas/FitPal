package com.fitpal.app.sync

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fire-and-forget "wake up and pull today's steps" signal to Calistapp.
 *
 * An EXPLICIT broadcast to Calistapp's [FitPalSyncContract.CALISTAPP_RECEIVER]; it carries no
 * payload — Calistapp pulls the numbers back through the provider itself, so there's nothing here
 * to trust. [Intent.FLAG_INCLUDE_STOPPED_PACKAGES] lets it wake Calistapp even if its process
 * isn't currently running (short of a force-stop). No-op if Calistapp isn't installed.
 */
object CalistappNudge {

    private const val TAG = "CalistappNudge"

    fun nudge(context: Context) {
        runCatching {
            val intent = Intent(FitPalSyncContract.ACTION_PULL_STEPS).apply {
                component = ComponentName(
                    FitPalSyncContract.CALISTAPP_PKG,
                    FitPalSyncContract.CALISTAPP_RECEIVER
                )
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
        }.onFailure { Log.d(TAG, "Calistapp nudge skipped: ${it.message}") }
    }
}
