package com.fitpal.wear

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fitpal.wear.data.PhoneComms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Logs a fixed amount of water to the phone in response to a tile clickable or a complication tap —
 * i.e. quick-add water without opening the watch app. After sending it asks the phone for a fresh
 * stats snapshot; when that arrives, [WearListenerService] refreshes the tile + complications.
 */
class QuickLogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ml = intent.getIntExtra(EXTRA_ML, 0)
        if (ml <= 0) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PhoneComms.logWater(app, ml)
                PhoneComms.requestStats(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ML = "ml"
        const val ACTION_LOG_WATER = "com.fitpal.wear.action.LOG_WATER"

        fun intent(context: Context, ml: Int): Intent =
            Intent(context, QuickLogReceiver::class.java).apply {
                action = ACTION_LOG_WATER
                putExtra(EXTRA_ML, ml)
            }

        /** Broadcast PendingIntent for complication taps + tile clickables. */
        fun pendingIntent(context: Context, ml: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            ml, // distinct request code per amount so the extras aren't collapsed
            intent(context, ml),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
