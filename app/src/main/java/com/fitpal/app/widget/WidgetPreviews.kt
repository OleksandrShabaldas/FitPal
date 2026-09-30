package com.fitpal.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.SizeF
import com.fitpal.app.BuildConfig

/**
 * On Android 15+, hands the launcher's widget picker real previews — the widgets drawn by the same
 * renderer, in the Glow look with sample numbers — instead of the static stand-in layouts. Done once
 * per app version (the system rate-limits it); a launcher that doesn't use them keeps the stand-ins.
 */
internal object WidgetPreviews {
    fun publishOnce(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        if (WidgetPrefs.previewsVersion(context) == BuildConfig.VERSION_CODE) return
        val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
        val data = WidgetData.sample()
        var allPublished = true
        WidgetKind.entries.forEach { kind ->
            val published = runCatching {
                val views = WidgetRender.remoteViews(
                    context, kind, WidgetStyle.DEFAULT, data,
                    SizeF(kind.defaultWidthDp.toFloat(), kind.defaultHeightDp.toFloat()),
                    appWidgetId = 0
                )
                manager.setWidgetPreview(
                    ComponentName(context, kind.provider),
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                    views
                )
            }.getOrDefault(false)
            if (!published) allPublished = false
        }
        // Rate-limited or refused: try again on the next launch.
        if (allPublished) WidgetPrefs.setPreviewsVersion(context, BuildConfig.VERSION_CODE)
    }
}
