package com.fitpal.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import com.fitpal.app.BuildConfig
import com.fitpal.app.R
import kotlin.math.min
import kotlin.math.sqrt

/**
 * On Android 15+, hands the launcher's widget picker real previews — the widgets drawn by the same
 * renderer, in the Glow look with sample numbers — instead of the static stand-in layouts. Done once
 * per app version (the system rate-limits it); a launcher that doesn't use them keeps the stand-ins.
 *
 * Each preview is a still picture ([WidgetRender.previewBitmap]) in three shapes, and the launcher
 * shows the one closest to its preview box. A single square picture used to be stretched into
 * Samsung's tall boxes (squeezed from the sides), and a live countdown frozen into the saved preview
 * kept counting for days ("−213:03:54"); nothing in a preview ticks now.
 */
internal object WidgetPreviews {
    /** The tall shape's width and the wide shape's height, as shares of the kind's own size. */
    private const val TALL = 0.69f
    private const val WIDE = 0.72f
    /** Pixels per preview picture; three go in each preview. */
    private const val MAX_PIXELS = 400_000f

    fun publishOnce(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        if (WidgetPrefs.previewsVersion(context) == BuildConfig.VERSION_CODE) return
        val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
        val data = WidgetData.sample()
        var allPublished = true
        WidgetKind.entries.forEach { kind ->
            val published = runCatching {
                manager.setWidgetPreview(
                    ComponentName(context, kind.provider),
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                    RemoteViews(shapesFor(kind).associateWith { still(context, kind, data, it) })
                )
            }.getOrDefault(false)
            if (!published) allPublished = false
        }
        // Rate-limited or refused: try again on the next launch.
        if (allPublished) WidgetPrefs.setPreviewsVersion(context, BuildConfig.VERSION_CODE)
    }

    /**
     * The kind's own size, a narrower (tall) one and a shorter (wide) one. The launcher picks the
     * closest that fits its box, or the smallest when none does — the tall one, since tall boxes are
     * the ones a square picture can't fill.
     */
    private fun shapesFor(kind: WidgetKind): List<SizeF> {
        val w = kind.defaultWidthDp.toFloat()
        val h = kind.defaultHeightDp.toFloat()
        return listOf(SizeF(w * TALL, h), SizeF(w, h), SizeF(w, h * WIDE))
    }

    private fun still(context: Context, kind: WidgetKind, data: WidgetData, size: SizeF): RemoteViews {
        val density = context.resources.displayMetrics.density
        val scale = min(density, sqrt(MAX_PIXELS / (size.width * size.height)))
        val bitmap = WidgetRender.previewBitmap(context, kind, WidgetStyle.DEFAULT, data, size, scale)
        return RemoteViews(context.packageName, R.layout.widget_preview_still).apply {
            setImageViewBitmap(R.id.w_still, bitmap)
            setContentDescription(R.id.w_still, context.getString(kind.label))
        }
    }
}
