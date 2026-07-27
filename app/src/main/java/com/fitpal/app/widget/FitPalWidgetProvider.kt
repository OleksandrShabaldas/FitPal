package com.fitpal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fitpal.app.MainActivity
import com.fitpal.app.R
import com.fitpal.app.ui.navigation.Screen

/**
 * Home-screen widget: a two-button quick-log card. "Snap a meal" opens the camera, "Add food"
 * opens the Add screen (with its one-tap recents). Stateless on purpose — it only routes into the
 * app via [MainActivity.EXTRA_NAV_ROUTE], reusing the same launch path as the analysis notification.
 */
class FitPalWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add).apply {
                setOnClickPendingIntent(R.id.widget_title, open(context, Screen.AddFood.route))
                setOnClickPendingIntent(R.id.widget_snap, open(context, Screen.Camera.route))
                setOnClickPendingIntent(R.id.widget_add, open(context, Screen.AddFood.route))
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun open(context: Context, route: String): PendingIntent {
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
}
