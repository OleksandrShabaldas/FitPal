package com.fitpal.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.text.TextPaint
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import com.fitpal.app.MainActivity
import com.fitpal.app.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns a [WidgetScene] into what the launcher shows: one bitmap for the card + main area, one small
 * bitmap per button (laid out by the launcher, so taps line up exactly), a live Chronometer when the
 * scene has a countdown, and the tap targets. [previewBitmap] draws the very same thing into a single
 * image for the in-app gallery and the style picker.
 */
internal object WidgetRender {
    /** Keeps a big widget's bitmap to a sensible size; the launcher scales it up by a hair if needed. */
    private const val MAX_PIXELS = 1_600_000f
    private val ACTION_IDS = intArrayOf(R.id.w_act0, R.id.w_act1, R.id.w_act2, R.id.w_act3)

    fun remoteViews(
        context: Context,
        kind: WidgetKind,
        style: WidgetStyle,
        data: WidgetData,
        size: SizeF,
        appWidgetId: Int
    ): RemoteViews {
        val density = context.resources.displayMetrics.density
        val w = size.width.coerceAtLeast(40f)
        val h = size.height.coerceAtLeast(40f)
        val scale = cappedScale(w, h, density)
        val p = WidgetPainter(context, style, scale)
        val pad = paddingFor(w, h, style)
        val cw = w - pad * 2
        val ch = h - pad * 2
        val scene = WidgetScenes.build(kind, data, cw, ch)

        val rv = RemoteViews(context.packageName, if (scene.actionsFill) R.layout.widget_buttons else R.layout.widget_canvas)
        val card = Bitmap.createBitmap((w * scale).roundToInt(), (h * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        card.density = (scale * 160).roundToInt()
        val canvas = Canvas(card)
        p.background(canvas, card.width.toFloat(), card.height.toFloat(), kind.theme, cornerRadiusDp(context) * scale)
        drawMain(canvas, p, scene, pad, cw, scene.mainHeightDp(ch))
        rv.setImageViewBitmap(R.id.w_canvas, card)
        rv.setContentDescription(R.id.w_canvas, scene.description)

        val padPx = (pad * density).roundToInt()
        if (scene.actionsFill) rv.setViewPadding(R.id.w_actions, padPx, padPx, padPx, padPx)
        else rv.setViewPadding(R.id.w_content, padPx, padPx, padPx, padPx)

        val actions = scene.actions.take(ACTION_IDS.size)
        if (actions.isNotEmpty()) {
            if (!scene.actionsFill) {
                rv.setViewVisibility(R.id.w_actions, View.VISIBLE)
                rv.setViewPadding(R.id.w_actions, 0, (WidgetScenes.ACTION_GAP_DP * density).roundToInt(), 0, 0)
            }
            // Buttons are small, so they're always drawn at full resolution.
            val bp = WidgetPainter(context, style, density)
            val bw = (cw - WidgetScenes.ACTION_SPACING_DP * (actions.size - 1)) / actions.size
            val bh = if (scene.actionsFill) ch else scene.actionHeightDp
            actions.forEachIndexed { i, a ->
                val id = ACTION_IDS[i]
                val bmp = bp.button((bw * density).roundToInt(), (bh * density).roundToInt(), a.icon, a.label, a.primary)
                bmp.density = (density * 160).roundToInt()
                rv.setViewVisibility(id, View.VISIBLE)
                rv.setImageViewBitmap(id, bmp)
                rv.setContentDescription(id, a.label)
                rv.setOnClickPendingIntent(id, WidgetIntents.forTarget(context, a.target, kind, appWidgetId, slot = i))
            }
        }

        scene.timer?.let { t ->
            // The launcher ticks this itself, every second, without waking FitPal.
            val id = if (style == WidgetStyle.CLEAR) R.id.w_timer_shadow else R.id.w_timer
            val base = SystemClock.elapsedRealtime() + (t.untilMillis - System.currentTimeMillis())
            rv.setViewVisibility(id, View.VISIBLE)
            rv.setChronometer(id, base, null, true)
            rv.setChronometerCountDown(id, true)
            rv.setTextColor(id, p.ink.main)
            rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, t.sizeDp)
        }

        rv.setOnClickPendingIntent(android.R.id.background, WidgetIntents.forTarget(context, scene.tap, kind, appWidgetId, slot = 9))
        return rv
    }

    /**
     * The widget as one image at [sizeDp], exactly as it renders on the home screen — card, content,
     * buttons and (a still of) the countdown. For the gallery, the style picker and the launcher preview.
     */
    fun previewBitmap(context: Context, kind: WidgetKind, style: WidgetStyle, data: WidgetData, sizeDp: SizeF, scale: Float): Bitmap {
        val w = sizeDp.width
        val h = sizeDp.height
        val p = WidgetPainter(context, style, scale)
        val pad = paddingFor(w, h, style)
        val cw = w - pad * 2
        val ch = h - pad * 2
        val scene = WidgetScenes.build(kind, data, cw, ch)
        val bmp = Bitmap.createBitmap((w * scale).roundToInt(), (h * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        p.background(canvas, bmp.width.toFloat(), bmp.height.toFloat(), kind.theme, cornerRadiusDp(context) * scale)
        val mainH = scene.mainHeightDp(ch)
        drawMain(canvas, p, scene, pad, cw, mainH)
        val actions = scene.actions.take(ACTION_IDS.size)
        if (actions.isNotEmpty()) {
            val bw = (cw - WidgetScenes.ACTION_SPACING_DP * (actions.size - 1)) / actions.size
            val top = if (scene.actionsFill) pad else pad + mainH + WidgetScenes.ACTION_GAP_DP
            val bh = if (scene.actionsFill) ch else scene.actionHeightDp
            actions.forEachIndexed { i, a ->
                val left = pad + i * (bw + WidgetScenes.ACTION_SPACING_DP)
                p.drawButton(canvas, RectF(left * scale, top * scale, (left + bw) * scale, (top + bh) * scale), a.icon, a.label, a.primary)
            }
        }
        scene.timer?.let { t ->
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = WidgetFonts.timer
                textSize = t.sizeDp * scale
                color = p.ink.main
                textAlign = Paint.Align.CENTER
                if (style == WidgetStyle.CLEAR) setShadowLayer(6f, 0f, 1f, 0x99000000.toInt())
            }
            val left = ((t.untilMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            val text = "%d:%02d:%02d".format(left / 3600, (left / 60) % 60, left % 60)
            val cx = (pad + cw / 2f) * scale
            val cy = (pad + mainH / 2f) * scale
            canvas.drawText(text, cx, p.baselineFor(paint, cy), paint)
        }
        return bmp
    }

    /** Paints the scene's main area at its place inside the padding. */
    private fun drawMain(canvas: Canvas, p: WidgetPainter, scene: WidgetScene, pad: Float, cw: Float, mainH: Float) {
        if (mainH <= 1f) return
        val s = p.scale
        canvas.save()
        // No clip: content stays inside the area by design, and a ring's glow may spill softly into
        // the padding — clipping it left a visible square edge.
        canvas.translate(pad * s, pad * s)
        runCatching { scene.draw(p, canvas, cw * s, mainH * s) }
        canvas.restore()
    }

    /** Roomier padding on bigger widgets; the card-less look sits closer to the edge. */
    private fun paddingFor(w: Float, h: Float, style: WidgetStyle): Float = when {
        style == WidgetStyle.CLEAR -> if (min(w, h) < 100f) 6f else 8f
        min(w, h) < 100f -> 11f
        else -> 15f
    }

    private fun cappedScale(w: Float, h: Float, density: Float): Float {
        val px = w * h * density * density
        return if (px <= MAX_PIXELS) density else density * sqrt(MAX_PIXELS / px)
    }

    /** The launcher's own widget corner radius on Android 12+, so FitPal's cards match the others. */
    fun cornerRadiusDp(context: Context): Float {
        val density = context.resources.displayMetrics.density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val px = runCatching {
                context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
            }.getOrNull()
            if (px != null && px > 0f) return px / density
        }
        return 22f
    }

    /**
     * The size (dp) a placed widget is actually drawn at: in portrait that's its narrowest width and
     * tallest height (the launcher reports both orientations).
     */
    fun sizeOf(context: Context, manager: AppWidgetManager, appWidgetId: Int, kind: WidgetKind): SizeF {
        val o = manager.getAppWidgetOptions(appWidgetId)
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = BundleCompat.getParcelableArrayList(o, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            val pick = sizes?.filter { it.width > 0f && it.height > 0f }?.let { list ->
                if (landscape) list.maxByOrNull { it.width } else list.minByOrNull { it.width }
            }
            if (pick != null) return pick
        }
        val w = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = o.getInt(if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (w > 0 && h > 0) SizeF(w.toFloat(), h.toFloat())
        else SizeF(kind.defaultWidthDp.toFloat(), kind.defaultHeightDp.toFloat())
    }
}

/** PendingIntents for widget taps: open a screen in the app, or log water in the background. */
internal object WidgetIntents {
    fun forTarget(context: Context, target: WidgetTarget, kind: WidgetKind, appWidgetId: Int, slot: Int): PendingIntent {
        // A distinct data URI per widget + slot keeps every PendingIntent separate (extras don't).
        val uri = Uri.parse("fitpal://widget/${kind.id}/$appWidgetId/$slot")
        val code = (appWidgetId * 16 + slot) * 16 + kind.ordinal
        return when (target) {
            is WidgetTarget.Open -> PendingIntent.getActivity(
                context, code,
                Intent(context, MainActivity::class.java).apply {
                    data = uri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra(MainActivity.EXTRA_NAV_ROUTE, target.route)
                    if (target.focusToday) putExtra(MainActivity.EXTRA_FOCUS_TODAY, true)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            is WidgetTarget.AddWater -> PendingIntent.getBroadcast(
                context, code,
                Intent(context, WidgetActionReceiver::class.java).apply {
                    action = WidgetActionReceiver.ACTION_ADD_WATER
                    data = uri
                    putExtra(WidgetActionReceiver.EXTRA_ML, target.ml)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}

/** A bitmap's width over height, for laying previews out. */
internal fun SizeF.aspect(): Float = if (height > 0f) width / height else 1f

/** The larger of two sizes' dimensions — used to pick a preview scale. */
internal fun SizeF.maxSide(): Float = max(width, height)
