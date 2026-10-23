package com.fitpal.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Material-style colour harmonization, for Material You's custom colours (fasting's violet, the
 * food limits' pink/orange/blue, caffeine's brown): the hue turns a little toward the scheme's
 * primary (at most 15°, like Material's own `Blend.harmonize`) and the lightness matches the
 * primary's, so the colour sits at the same tone as the scheme's roles and reads the same against
 * its surfaces. Worked in OKLCH (perceptual) so a hue turn doesn't change how light it looks.
 */
internal object Harmonize {
    private const val MAX_TURN = 15.0 * Math.PI / 180.0

    fun toward(design: Color, primary: Color): Color {
        val d = toOklch(design)
        val p = toOklch(primary)
        var diff = p.h - d.h
        while (diff > Math.PI) diff -= 2 * Math.PI
        while (diff < -Math.PI) diff += 2 * Math.PI
        val turn = Math.copySign(min(abs(diff) * 0.5, MAX_TURN), diff)
        // Keep the colour's own vividness, but not past what the primary's tone can hold.
        var c = min(d.c, 0.16)
        var out: Color
        do {
            out = fromOklch(p.l, c, d.h + turn) ?: Color.Unspecified
            c -= 0.01
        } while (out == Color.Unspecified && c > 0.0)
        return if (out == Color.Unspecified) primary else out
    }

    private data class Lch(val l: Double, val c: Double, val h: Double)

    private fun toLinear(v: Float): Double {
        val x = v.toDouble()
        return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    }

    private fun toGamma(x: Double): Double = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055

    private fun toOklch(color: Color): Lch {
        val r = toLinear(color.red)
        val g = toLinear(color.green)
        val b = toLinear(color.blue)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        return Lch(lightness, hypot(a, bb), atan2(bb, a))
    }

    /** Null when the colour falls outside sRGB. */
    private fun fromOklch(lightness: Double, chroma: Double, hue: Double): Color? {
        val a = chroma * cos(hue)
        val b = chroma * sin(hue)
        val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        val eps = 1e-4
        if (r < -eps || r > 1 + eps || g < -eps || g > 1 + eps || bl < -eps || bl > 1 + eps) return null
        return Color(
            toGamma(r.coerceIn(0.0, 1.0)).toFloat(),
            toGamma(g.coerceIn(0.0, 1.0)).toFloat(),
            toGamma(bl.coerceIn(0.0, 1.0)).toFloat()
        )
    }
}
