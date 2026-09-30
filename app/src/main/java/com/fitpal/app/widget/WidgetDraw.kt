package com.fitpal.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import com.fitpal.app.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The widgets' drawing kit. Widgets are painted as bitmaps (not launcher-rendered text) because a
 * home-screen widget can't load an app's own fonts — drawing them ourselves keeps Fraunces + Inter,
 * the glow, the grain and the rings, i.e. FitPal's look, on the home screen.
 */

/** The bundled variable fonts, loaded once. */
internal object WidgetFonts {
    @Volatile private var inter: Typeface? = null
    @Volatile private var fraunces: Typeface? = null

    fun inter(context: Context): Typeface = inter ?: (
        runCatching { ResourcesCompat.getFont(context, R.font.inter_variable) }.getOrNull() ?: Typeface.SANS_SERIF
        ).also { inter = it }

    fun fraunces(context: Context): Typeface = fraunces ?: (
        runCatching { ResourcesCompat.getFont(context, R.font.fraunces_variable) }.getOrNull() ?: Typeface.SERIF
        ).also { fraunces = it }

    /** What the live countdown (a launcher-drawn Chronometer) uses — matched in previews. */
    val timer: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

/** Material icon outlines (24×24), drawn straight onto the widget. */
internal enum class WidgetIcon(vararg val paths: String) {
    ADD("M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"),
    CAMERA(
        "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0",
        "M9,2L7.17,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5z"
    ),
    CHAT("M20,2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z"),
    SEARCH(
        "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"
    ),
    DROP("M12,2C12,2 6,9.2 6,14a6,6 0 0,0 12,0C18,9.2 12,2 12,2z"),
    HOURGLASS(
        "M6,2v6h0.01L6,8.01 10,12l-4,4 0.01,0.01H6V22h12v-5.99h-0.01L18,16l-4,-4 4,-3.99 -0.01,-0.01H18V2H6zM16,16.5V20H8v-3.5l4,-4 4,4zM12,11.5l-4,-4V4h8v3.5l-4,4z"
    ),
    FORK_KNIFE(
        "M11,9H9V2H7v7H5V2H3v7c0,2.12 1.66,3.84 3.75,3.97V22h2.5v-9.03C11.34,12.84 13,11.12 13,9V2h-2V9zM16,6v8h2.5v8H21V2C18.24,2 16,4.24 16,6z"
    ),
    COFFEE("M20,3H4v10c0,2.21 1.79,4 4,4h6c2.21,0 4,-1.79 4,-4v-3h2c1.11,0 2,-0.9 2,-2V5c0,-1.11 -0.89,-2 -2,-2zM20,8h-2V5h2v3zM4,19h16v2H4z"),
    FLAME(
        "M13.5,0.67s0.74,2.65 0.74,4.8c0,2.06 -1.35,3.73 -3.41,3.73 -2.07,0 -3.63,-1.67 -3.63,-3.73l0.03,-0.36C5.21,7.51 4,10.62 4,14c0,4.42 3.58,8 8,8s8,-3.58 8,-8C20,8.61 17.41,3.8 13.5,0.67zM11.71,19c-1.78,0 -3.22,-1.4 -3.22,-3.14 0,-1.62 1.05,-2.76 2.81,-3.12 1.77,-0.36 3.6,-1.21 4.62,-2.58 0.39,1.29 0.59,2.65 0.59,4.04 0,2.65 -2.15,4.8 -4.8,4.8z"
    ),
    BOOKMARK("M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z");

    /** Parsed once; an icon that somehow fails to parse is simply skipped, never a crash. */
    val parsed: List<Path> by lazy { paths.mapNotNull { runCatching { PathParser.createPathFromPathData(it) }.getOrNull() } }
}

/** Colours for the four looks: ink, the quieter inks, and the ring/bar track. */
internal class WidgetInk(style: WidgetStyle) {
    val main: Int = if (style.isLight) 0xFF2B211B.toInt() else 0xFFF5ECE0.toInt()
    val muted: Int = if (style.isLight) 0xA62B211B.toInt() else 0xA6F5ECE0.toInt()
    val faint: Int = if (style.isLight) 0x802B211B.toInt() else 0x80F5ECE0.toInt()
    val track: Int = if (style.isLight) 0x1F2B211B.toInt() else 0x24FFFFFF.toInt()
}

/**
 * Paints one widget at [scale] pixels per dp. Every size passed in is in pixels; [dp] converts the
 * few fixed measurements. [WidgetStyle.CLEAR] adds a soft shadow under everything so it reads on any
 * wallpaper.
 */
internal class WidgetPainter(val context: Context, val style: WidgetStyle, val scale: Float) {
    val ink = WidgetInk(style)
    private val shadowed = style == WidgetStyle.CLEAR

    fun dp(v: Float): Float = v * scale
    fun dp(v: Int): Float = v * scale
    fun tone(t: Tone): Int = t.on(style)

    fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        if (shadowed) setShadowLayer(dp(3f), 0f, dp(1f), 0x66000000)
    }

    fun stroke(color: Int, width: Float, round: Boolean = true): Paint = fill(color).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        if (round) strokeCap = Paint.Cap.ROUND
    }

    /** Inter (or Fraunces with [serif]) at [sizePx], pinned to [weight]; [tnum] for steady digits. */
    fun text(
        sizePx: Float,
        color: Int = ink.main,
        weight: Int = 400,
        serif: Boolean = false,
        tnum: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = if (serif) WidgetFonts.fraunces(context) else WidgetFonts.inter(context)
        textSize = sizePx
        this.color = color
        textAlign = align
        runCatching { setFontVariationSettings("'wght' $weight") }
        if (tnum) fontFeatureSettings = "tnum"
        if (shadowed) setShadowLayer(dp(4f), 0f, dp(1f), 0x8C000000.toInt())
    }

    /** Baseline that centres [paint]'s text vertically on [cy]. */
    fun baselineFor(paint: Paint, cy: Float): Float {
        val fm = paint.fontMetrics
        return cy - (fm.ascent + fm.descent) / 2f
    }

    /** Height of a line of [paint] text (ascent to descent). */
    fun lineHeight(paint: Paint): Float = paint.fontMetrics.let { it.descent - it.ascent }

    /**
     * Draws [text] with its baseline at [y], shrinking it (to [minScale] at most) to fit [maxWidth],
     * then ellipsizing whatever still doesn't. Hero numbers pass a lower [minScale] — a number cut to
     * "200…" is worse than a smaller one. Returns the width actually drawn.
     */
    fun drawFit(canvas: Canvas, text: String, x: Float, y: Float, paint: TextPaint, maxWidth: Float, minScale: Float = 0.7f): Float {
        if (maxWidth <= 0f || text.isEmpty()) return 0f
        val original = paint.textSize
        var t = text
        var w = paint.measureText(t)
        if (w > maxWidth) {
            paint.textSize = original * (maxWidth / w).coerceAtLeast(minScale)
            w = paint.measureText(t)
            if (w > maxWidth) {
                t = TextUtils.ellipsize(t, paint, maxWidth, TextUtils.TruncateAt.END).toString()
                w = paint.measureText(t)
            }
        }
        canvas.drawText(t, x, y, paint)
        paint.textSize = original
        return w
    }

    /** A ring (or arc of one): faint track, then the filled part with a soft glow on the dark looks. */
    fun ring(
        canvas: Canvas, cx: Float, cy: Float, radius: Float, width: Float, fraction: Float, color: Int,
        startDeg: Float = -90f, sweepDeg: Float = 360f
    ) {
        val r = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(r, startDeg, sweepDeg, false, stroke(ink.track, width))
        val f = fraction.coerceIn(0f, 1f)
        if (f <= 0.003f) return
        if (!style.isLight) {
            val glow = stroke(withAlpha(color, 0x55), width * 2.2f).apply {
                maskFilter = BlurMaskFilter(width * 1.2f, BlurMaskFilter.Blur.NORMAL)
                clearShadowLayer()
            }
            canvas.drawArc(r, startDeg, sweepDeg * f, false, glow)
        }
        canvas.drawArc(r, startDeg, sweepDeg * f, false, stroke(color, width))
    }

    /** A rounded progress bar. */
    fun bar(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, fraction: Float, color: Int) {
        val rad = height / 2f
        canvas.drawRoundRect(RectF(left, top, left + width, top + height), rad, rad, fill(ink.track))
        val f = fraction.coerceIn(0f, 1f)
        if (f <= 0f) return
        canvas.drawRoundRect(RectF(left, top, left + max(height, width * f), top + height), rad, rad, fill(color))
    }

    /** [icon] centred on ([cx], [cy]), [size] px square. */
    fun icon(canvas: Canvas, icon: WidgetIcon, cx: Float, cy: Float, size: Float, color: Int) {
        val m = Matrix().apply {
            setScale(size / 24f, size / 24f)
            postTranslate(cx - size / 2f, cy - size / 2f)
        }
        val p = fill(color)
        icon.parsed.forEach { src -> canvas.drawPath(Path(src).apply { transform(m) }, p) }
    }

    // ---- The card behind everything ----

    /** Paints the look's card over the whole [w]×[h] canvas with corner radius [radius] (px). */
    fun background(canvas: Canvas, w: Float, h: Float, theme: WidgetTheme, radius: Float) {
        val card = RectF(0f, 0f, w, h)
        when (style) {
            WidgetStyle.CLEAR -> return
            WidgetStyle.GLOW -> {
                rounded(canvas, card, radius) {
                    canvas.drawRect(card, Paint().apply {
                        shader = RadialGradient(w * 0.78f, -h * 0.06f, max(w, h) * 1.15f, themeBase(theme), null, Shader.TileMode.CLAMP)
                    })
                    themeOrbs(theme).forEach { o -> orb(canvas, w, h, o, 1f) }
                    canvas.drawRect(card, Paint().apply { color = 0x38000000 })
                    grain(canvas, card, 0.06f)
                }
                canvas.drawRoundRect(inset(card, 0.5f), radius, radius, hairline(0x24FFFFFF))
            }
            WidgetStyle.GLASS -> {
                rounded(canvas, card, radius) {
                    canvas.drawRect(card, Paint().apply { color = 0x9E120D0B.toInt() })
                    themeOrbs(theme).firstOrNull()?.let { orb(canvas, w, h, it, 0.45f) }
                    canvas.drawRect(card, Paint().apply {
                        shader = LinearGradient(0f, 0f, 0f, h * 0.55f, 0x14FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
                    })
                }
                canvas.drawRoundRect(inset(card, 0.5f), radius, radius, hairline(0x40FFFFFF))
            }
            WidgetStyle.PAPER -> {
                rounded(canvas, card, radius) {
                    canvas.drawRect(card, Paint().apply {
                        shader = LinearGradient(0f, 0f, 0f, h, 0xFFF8F0E4.toInt(), 0xFFEEE1CE.toInt(), Shader.TileMode.CLAMP)
                    })
                    themeOrbs(theme).firstOrNull()?.let { orb(canvas, w, h, it, 0.30f) }
                    grain(canvas, card, 0.035f)
                }
                canvas.drawRoundRect(inset(card, 0.5f), radius, radius, hairline(0x1F2B211B))
            }
        }
    }

    /**
     * Draws [content] full-bleed on its own layer, then trims it to a rounded rectangle with an
     * anti-aliased mask — a plain clipPath on a bitmap canvas would leave jagged corners.
     */
    private inline fun rounded(canvas: Canvas, card: RectF, radius: Float, content: () -> Unit) {
        val layer = canvas.saveLayer(card, null)
        content()
        clearOutside(canvas, Path().apply { addRoundRect(card, radius, radius, Path.Direction.CW) })
        canvas.restoreToCount(layer)
    }

    private fun hairline(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = max(1f, dp(0.8f))
    }

    private fun inset(r: RectF, by: Float) = RectF(r.left + by, r.top + by, r.right - by, r.bottom - by)

    private data class Orb(val color: Int, val cx: Float, val cy: Float, val rFrac: Float, val alpha: Float)

    /** The app's own backdrop colours ([com.fitpal.app.ui.component.GradientBackdrop]). */
    private fun themeBase(theme: WidgetTheme): IntArray = when (theme) {
        WidgetTheme.WARM -> intArrayOf(0xFF5A3A22.toInt(), 0xFF3A261A.toInt(), 0xFF241814.toInt(), 0xFF0E0B0A.toInt())
        WidgetTheme.BLUE -> intArrayOf(0xFF33438C.toInt(), 0xFF232C5E.toInt(), 0xFF171C36.toInt(), 0xFF0E0B0A.toInt())
        WidgetTheme.GREEN -> intArrayOf(0xFF24401F.toInt(), 0xFF182C16.toInt(), 0xFF121E10.toInt(), 0xFF0E0B0A.toInt())
    }

    private fun themeOrbs(theme: WidgetTheme): List<Orb> = when (theme) {
        WidgetTheme.WARM -> listOf(
            Orb(0xFFF3C46E.toInt(), 0.85f, 0.02f, 0.62f, 0.50f),
            Orb(0xFFE89A4E.toInt(), 0.05f, 0.40f, 0.50f, 0.30f),
            Orb(0xFFB9603A.toInt(), 0.90f, 0.95f, 0.55f, 0.22f)
        )
        WidgetTheme.BLUE -> listOf(
            Orb(0xFF8AB0FF.toInt(), 0.85f, 0.03f, 0.66f, 0.55f),
            Orb(0xFF9B7FE0.toInt(), 0.00f, 0.44f, 0.55f, 0.40f),
            Orb(0xFF6E90E0.toInt(), 0.90f, 0.95f, 0.55f, 0.28f)
        )
        WidgetTheme.GREEN -> listOf(
            Orb(0xFFB6E06E.toInt(), 0.82f, 0.04f, 0.60f, 0.34f),
            Orb(0xFF5FB85A.toInt(), 0.06f, 0.50f, 0.50f, 0.22f)
        )
    }

    private fun orb(canvas: Canvas, w: Float, h: Float, o: Orb, strength: Float) {
        val cx = w * o.cx
        val cy = h * o.cy
        val r = min(w, h) * o.rFrac
        val a = (o.alpha * strength * 255).roundToInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, r, withAlpha(o.color, a), o.color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
    }

    /** The fine film grain the app lays over its backdrops. */
    private fun grain(canvas: Canvas, area: RectF, alpha: Float) {
        canvas.drawRect(area, Paint().apply {
            shader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            this.alpha = (alpha * 255).roundToInt()
            xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        })
    }

    // ---- Buttons (each its own small bitmap, so taps line up exactly) ----

    /**
     * A pill button [wPx]×[hPx]: gold for the main action, glass for the rest. Icon and label sit side
     * by side when it's wide, stacked when it's tall, icon alone when it's small.
     */
    fun button(wPx: Int, hPx: Int, icon: WidgetIcon?, label: String, primary: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, wPx), max(1, hPx), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawButton(c, RectF(0f, 0f, wPx.toFloat(), hPx.toFloat()), icon, label, primary)
        return bmp
    }

    fun drawButton(c: Canvas, r: RectF, icon: WidgetIcon?, label: String, primary: Boolean) {
        val w = r.width()
        val h = r.height()
        val rad = min(h / 2f, dp(20f))
        val body = RectF(r.left + dp(1f), r.top + dp(1f), r.right - dp(1f), r.bottom - dp(1f))
        val (bg, fg) = when {
            primary && style.isLight -> 0xFF2B211B.toInt() to 0xFFF5ECE0.toInt()
            primary -> 0xFFF3CE7C.toInt() to 0xFF1C140E.toInt()
            style.isLight -> 0x122B211B to ink.main
            style == WidgetStyle.CLEAR -> 0x40000000 to ink.main
            else -> 0x1FFFFFFF to ink.main
        }
        c.drawRoundRect(body, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
        if (!primary) {
            c.drawRoundRect(body, rad, rad, hairline(if (style.isLight) 0x1F2B211B else 0x33FFFFFF))
        }
        val cx = r.centerX()
        val cy = r.centerY()
        val iconSize = min(dp(22f), h * 0.42f)
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = WidgetFonts.inter(context)
            runCatching { setFontVariationSettings("'wght' 600") }
            textSize = min(dp(13f), h * 0.24f)
            color = fg
        }
        if (icon == null) {
            labelPaint.textAlign = Paint.Align.CENTER
            labelPaint.textSize = min(dp(14f), h * 0.3f)
            drawFit(c, label, cx, baselineFor(labelPaint, cy), labelPaint, w - dp(10f))
            return
        }
        val labelW = labelPaint.measureText(label)
        val side = w >= iconSize + labelW + dp(34f)
        val stacked = !side && h >= dp(54f) && w >= min(labelW, dp(56f)) + dp(10f)
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg }
        fun drawIcon(x: Float, y: Float) {
            val m = Matrix().apply { setScale(iconSize / 24f, iconSize / 24f); postTranslate(x - iconSize / 2f, y - iconSize / 2f) }
            icon.parsed.forEach { src -> c.drawPath(Path(src).apply { transform(m) }, iconPaint) }
        }
        when {
            side -> {
                val total = iconSize + dp(8f) + labelW
                val x0 = cx - total / 2f
                drawIcon(x0 + iconSize / 2f, cy)
                c.drawText(label, x0 + iconSize + dp(8f), baselineFor(labelPaint, cy), labelPaint)
            }
            stacked -> {
                val lh = lineHeight(labelPaint)
                val total = iconSize + dp(4f) + lh
                drawIcon(cx, cy - total / 2f + iconSize / 2f)
                labelPaint.textAlign = Paint.Align.CENTER
                val t = TextUtils.ellipsize(label, labelPaint, w - dp(10f), TextUtils.TruncateAt.END).toString()
                c.drawText(t, cx, baselineFor(labelPaint, cy + total / 2f - lh / 2f), labelPaint)
            }
            else -> drawIcon(cx, cy)
        }
    }

    companion object {
        fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

        /**
         * Erases everything on the current layer outside [shape], with a smooth (anti-aliased) edge.
         * An inverse fill is needed: a blend mode only touches the pixels a shape covers, so masking
         * *with* the shape would leave the outside untouched.
         */
        fun clearOutside(canvas: Canvas, shape: Path) {
            val outside = Path(shape).apply { fillType = Path.FillType.INVERSE_WINDING }
            canvas.drawPath(outside, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            })
        }

        /** 96×96 of grey noise, like the app backdrop's grain tile. */
        private val noise: Bitmap by lazy {
            val n = 96
            val rnd = java.util.Random(11L)
            val px = IntArray(n * n) { val v = rnd.nextInt(256); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
            Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
        }
    }
}
