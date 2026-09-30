package com.fitpal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * "Add to home screen" from inside FitPal: asks the launcher to pin a widget, remembering the look
 * that was picked so [WidgetActionReceiver] can apply it once the launcher reports the new widget.
 */
object WidgetPinning {
    fun isSupported(context: Context): Boolean =
        runCatching { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }.getOrDefault(false)

    /** Returns false when the launcher can't pin from an app (then it's long-press → Widgets). */
    fun request(context: Context, kind: WidgetKind, style: WidgetStyle): Boolean {
        val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        WidgetPrefs.setPendingPinStyle(context, kind, style)
        // Mutable: the launcher fills in the new widget's id. Explicit, so nothing else can receive it.
        val callback = PendingIntent.getBroadcast(
            context, 7400 + kind.ordinal,
            Intent(context, WidgetActionReceiver::class.java)
                .setAction(WidgetActionReceiver.ACTION_PINNED)
                .putExtra(WidgetActionReceiver.EXTRA_KIND, kind.id),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )
        return runCatching { manager.requestPinAppWidget(ComponentName(context, kind.provider), null, callback) }
            .getOrDefault(false)
    }
}
