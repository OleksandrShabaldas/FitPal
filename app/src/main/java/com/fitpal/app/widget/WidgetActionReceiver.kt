package com.fitpal.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Widget work that happens without opening the app: logging water from a widget's "+" button, the
 * [WidgetClock] tick, and finishing a pin started from the in-app gallery (the new widget's id only
 * arrives here, so the look picked there is applied now).
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val entry = EntryPointAccessors.fromApplication(app, WidgetEntryPoint::class.java)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_ADD_WATER -> {
                        val ml = intent.getIntExtra(EXTRA_ML, 0)
                        if (ml > 0) {
                            entry.mealRepository().logWater(ml.toFloat())
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(app, "Added $ml ml of water", Toast.LENGTH_SHORT).show()
                            }
                        }
                        entry.widgetUpdater().updateAll()
                        runCatching { entry.watchLink().pushIfConnected() }
                    }
                    WidgetClock.ACTION_TICK -> entry.widgetUpdater().updateAll()
                    ACTION_PINNED -> {
                        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                        val kind = WidgetKind.fromId(intent.getStringExtra(EXTRA_KIND))
                        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && kind != null) {
                            WidgetPrefs.takePendingPinStyle(app, kind)?.let { WidgetPrefs.setStyle(app, id, it) }
                            entry.widgetUpdater().update(kind, intArrayOf(id))
                        }
                    }
                }
            } catch (_: Exception) {
                // A widget refresh is never worth a crash; the next tick or change redraws it.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ADD_WATER = "com.fitpal.app.widget.ADD_WATER"
        const val ACTION_PINNED = "com.fitpal.app.widget.PINNED"
        const val EXTRA_ML = "ml"
        const val EXTRA_KIND = "kind"
    }
}
