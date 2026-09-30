package com.fitpal.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The shared body of every FitPal widget: the launcher asks, [WidgetUpdater] draws. One subclass per
 * [WidgetKind], because each needs its own entry in the manifest (and so in the widget picker).
 */
abstract class FitPalAppWidget(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        work(context) { updater ->
            updater.update(kind, appWidgetIds)
            updater.startWatching()
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        // Resized: lay it out again for its new size.
        work(context) { it.update(kind, intArrayOf(appWidgetId)) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPrefs.forget(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        // The last of this kind was removed; stop the clock if no FitPal widget is left at all.
        work(context) { if (!it.hasAnyWidget()) WidgetClock.cancel(context) }
    }

    private fun work(context: Context, block: suspend (WidgetUpdater) -> Unit) {
        val updater = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).widgetUpdater()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { block(updater) }
            } finally {
                pending.finish()
            }
        }
    }
}

class TodayWidgetProvider : FitPalAppWidget(WidgetKind.TODAY)
class MacrosWidgetProvider : FitPalAppWidget(WidgetKind.MACROS)
class WaterWidgetProvider : FitPalAppWidget(WidgetKind.WATER)
class FastingWidgetProvider : FitPalAppWidget(WidgetKind.FASTING)
class WeekWidgetProvider : FitPalAppWidget(WidgetKind.WEEK)
class WeightWidgetProvider : FitPalAppWidget(WidgetKind.WEIGHT)
class CaffeineWidgetProvider : FitPalAppWidget(WidgetKind.CAFFEINE)
