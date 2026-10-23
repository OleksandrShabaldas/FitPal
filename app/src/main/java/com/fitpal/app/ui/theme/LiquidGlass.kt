package com.fitpal.app.ui.theme

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * Liquid glass's surfaces (Settings → Appearance → Liquid glass).
 *
 * The backdrop behind them is ours — drifting colour blobs, or the user's photo — so the glass
 * doesn't have to read pixels back to look like glass; it draws what's behind it itself:
 *  - Frosted: over a photo, each surface draws the blurred copy of the part of the photo under it
 *    (BackdropPhoto.blurred), so the card reads as frosted glass over a sharp photo. The blobs are
 *    soft already, so over them the film stays see-through.
 *  - Lensing (Android 13+): the band along each edge bends what's behind it inward — the signature
 *    liquid-glass edge — with an AGSL shader that samples the same backdrop a little displaced.
 *  - A rim light and a soft specular spot along the top that follow the phone's tilt (TiltState).
 *  - A contrast guard: where the backdrop under a card is bright (a light photo, a vivid blob), a
 *    thin scrim thickens on its own so the text on the card keeps about 4.5:1.
 */

/** What the glass needs to know about the backdrop: where it is, and the blobs' drift. */
object LiquidBackdrop {
    /** The backdrop's bounds in the window (the screen), set by GradientBackdrop. */
    var frame by mutableStateOf(Rect.Zero)
    /** The blobs' drift (radians), advanced by GradientBackdrop while Liquid glass drifts. */
    var phase by mutableFloatStateOf(0f)

    /** Where orb [i] is at [phase] on a [w]×[h] backdrop: centre and the gradient's radius. */
    fun orb(i: Int, o: BackdropOrb, phase: Float, w: Float, h: Float): Pair<Offset, Float> {
        val a = phase + i * 2.1f
        val c = Offset(w * (o.cx + 0.10f * cos(a)), h * (o.cy + 0.06f * sin(a * 0.8f)))
        val r = min(w, h) * o.rFrac * (1f + 0.08f * sin(a * 1.3f))
        return c to r * 1.6f
    }

    /** The blob backdrop's colour at a point (same maths as the drawn gradients), for the guard. */
    fun blobColorAt(p: Offset, w: Float, h: Float): Color {
        val colors = LookState.colors
        val spec = colors.backdrops.values.first()
        var r = colors.inkBlack.red; var g = colors.inkBlack.green; var b = colors.inkBlack.blue
        spec.orbs.forEachIndexed { i, o ->
            val (c, radius) = orb(i, o, phase, w, h)
            val d = ((p - c).getDistance() / radius).coerceIn(0f, 1f)
            val a = if (d < 0.5f) o.alpha * (1f - 0.65f * d / 0.5f) else o.alpha * 0.35f * (1f - (d - 0.5f) / 0.5f)
            r += (o.color.red - r) * a; g += (o.color.green - g) * a; b += (o.color.blue - b) * a
        }
        val shade = 1f - colors.backdropShade
        return Color(r * shade, g * shade, b * shade)
    }
}

/**
 * A Liquid glass surface: [lens] for the main surfaces (cards, overlays, accent cards) — nested
 * rows pass false (iOS doesn't re-refract glass inside glass). [fill] is read at draw time.
 */
internal fun Modifier.liquidGlass(shape: Shape, strength: Float, lens: Boolean, fill: () -> Color): Modifier =
    this.then(LiquidGlassElement(shape, strength, lens, fill))

private data class LiquidGlassElement(
    val shape: Shape,
    val strength: Float,
    val lens: Boolean,
    val fill: () -> Color
) : ModifierNodeElement<LiquidGlassNode>() {
    override fun create() = LiquidGlassNode(shape, strength, lens, fill)
    override fun update(node: LiquidGlassNode) {
        node.shape = shape; node.strength = strength; node.lens = lens; node.fill = fill
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() { name = "liquidGlass" }
}

private class LiquidGlassNode(
    var shape: Shape,
    var strength: Float,
    var lens: Boolean,
    var fill: () -> Color
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {

    private var origin = Offset.Unspecified
    private var lensShader: Any? = null       // RuntimeShader on 33+, kept untyped for older APIs
    private var photoShader: BitmapShader? = null
    private var photoShaderFor: Any? = null
    private val matrix = Matrix()

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val p = coordinates.positionInWindow()
        if (p != origin) {
            origin = p
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        sizeW = size.width; sizeH = size.height
        val outline = shape.createOutline(size, layoutDirection, this)
        val light = LookState.colors.isLight
        val frame = LiquidBackdrop.frame
        val look = LookState.look
        val photo = if (look.hasPhoto) BackdropPhoto.blurred else null
        val full = BackdropPhoto.image
        val known = origin.isSpecified && frame.width > 0f
        // Where this surface sits on the backdrop.
        val at = if (known) origin - frame.topLeft else Offset.Zero

        // 1. The frosted backdrop (over a photo), or nothing (over the blobs: the film is see-through).
        val useLens = lens && known && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (photo != null && full != null && known) {
            val bs = photoShader(photo, full.width, full.height, frame.size, at)
            if (useLens) drawLens(outline, at, frame.size, bs, look.appearance.photoDim, light)
            else {
                drawOutline(outline, ShaderBrush(bs))
                drawOutline(outline, if (light) Color.White else Color.Black, alpha = look.appearance.photoDim)
            }
        } else if (useLens) {
            drawLens(outline, at, frame.size, null, 0f, light)
        }

        // 2. The contrast guard: a scrim that thickens where what's behind is bright (dark looks)
        //    or dark (light looks), so the text on the card holds its contrast.
        if (known) {
            val guard = guardAlpha(at, frame.size, photo != null, light, look.appearance.photoDim)
            if (guard > 0f) drawOutline(outline, if (light) Color.White else LookState.colors.inkBlack, alpha = guard)
        }

        // 3. The film, the content, then the light on top.
        drawOutline(outline, fill())
        drawContent()
        drawRim(outline, light)
    }

    /** How thick the scrim must be for ~4.5:1 text on this card (0 when the backdrop is fine). */
    private fun guardAlpha(at: Offset, screen: Size, photo: Boolean, light: Boolean, dim: Float): Float {
        if (screen.width <= 0f || screen.height <= 0f) return 0f
        // Brightness (gamma, 0–1) of what's behind, sampled over the card.
        val behind: Float = if (photo) {
            val raw = BackdropPhoto.brightnessUnder(
                at.x / screen.width, at.y / screen.height,
                (at.x + sizeW) / screen.width, (at.y + sizeH) / screen.height
            ) ?: return 0f
            if (light) raw + (1f - raw) * dim else raw * (1f - dim)
        } else {
            val pts = listOf(Offset(0.2f, 0.25f), Offset(0.8f, 0.25f), Offset(0.5f, 0.5f), Offset(0.2f, 0.75f), Offset(0.8f, 0.75f))
            pts.map { f ->
                val c = LiquidBackdrop.blobColorAt(Offset(at.x + sizeW * f.x, at.y + sizeH * f.y), screen.width, screen.height)
                // Back to gamma brightness, like the photo's map.
                c.luminance().toDouble().pow(1 / 2.2).toFloat()
            }.max()
        }
        val film = LookState.colors.glassFill
        val fa = film.alpha
        return if (!light) {
            // White text: the result (scrim, then the white film) must stay at or under ~0.46 gamma.
            val target = ((0.46f - fa) / (1f - fa)).coerceAtLeast(0.05f)
            if (behind <= target) 0f else (1f - target / behind).coerceIn(0f, 0.7f)
        } else {
            // Dark text: the result must stay at or over ~0.5 gamma.
            val after = behind * (1f - fa) + fa
            if (after >= 0.5f) 0f else ((0.5f - after) / (1f - after)).coerceIn(0f, 0.7f)
        }
    }

    private var sizeW = 0f
    private var sizeH = 0f

    private fun photoShader(photo: androidx.compose.ui.graphics.ImageBitmap, photoW: Int, photoH: Int, screen: Size, at: Offset): BitmapShader {
        val bmp = photo.asAndroidBitmap()
        val shader = if (photoShaderFor === bmp && photoShader != null) photoShader!! else {
            BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { photoShader = it; photoShaderFor = bmp }
        }
        // Blurred-bitmap pixels → photo pixels → screen pixels (cover crop) → this surface's pixels.
        val src = BackdropPhoto.coverSource(photoW, photoH, screen.width, screen.height)
        val kx = bmp.width.toFloat() / photoW
        val ky = bmp.height.toFloat() / photoH
        val s = screen.width / src.width
        matrix.reset()
        matrix.setScale(s / kx, s / ky)
        matrix.postTranslate(-src.left * s - at.x, -src.top * s - at.y)
        shader.setLocalMatrix(matrix)
        return shader
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun ContentDrawScope.drawLens(outline: Outline, at: Offset, screen: Size, photo: BitmapShader?, dim: Float, light: Boolean) {
        val shader = (lensShader as? RuntimeShader) ?: RuntimeShader(LENS_AGSL).also { lensShader = it }
        val radius = when (outline) {
            is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
            else -> 0f
        }
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("origin", at.x, at.y)
        shader.setFloatUniform("band", 14.dp.toPx())
        shader.setFloatUniform("bend", 9.dp.toPx() * strength)
        shader.setIntUniform("mode", if (photo != null) 1 else 0)
        shader.setFloatUniform("dim", dim)
        shader.setFloatUniform("dimTo", if (light) 1f else 0f)
        shader.setInputShader("photo", photo ?: BitmapShader(EMPTY, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        // The blobs, where the backdrop has them right now.
        val colors = LookState.colors
        val spec = colors.backdrops.values.first()
        val base = colors.inkBlack
        shader.setFloatUniform("base", base.red, base.green, base.blue)
        shader.setFloatUniform("shade", colors.backdropShade)
        val phase = LiquidBackdrop.phase
        for (i in 0 until 3) {
            val o = spec.orbs.getOrNull(i)
            if (o == null) {
                shader.setFloatUniform("orb$i", 0f, 0f, 1f, 0f)
                shader.setFloatUniform("col$i", 0f, 0f, 0f)
            } else {
                val (c, r) = LiquidBackdrop.orb(i, o, phase, screen.width, screen.height)
                shader.setFloatUniform("orb$i", c.x, c.y, r, o.alpha)
                shader.setFloatUniform("col$i", o.color.red, o.color.green, o.color.blue)
            }
        }
        drawOutline(outline, ShaderBrush(shader))
    }

    /** The bright rim (its light follows the tilt), a sheen inside the top edge and a moving specular spot. */
    private fun ContentDrawScope.drawRim(outline: Outline, light: Boolean) {
        val t = TiltState.tilt
        val cx = size.width * (0.5f - t.x * 0.45f)
        val cy = size.height * (0.0f - t.y * 0.25f).coerceIn(-0.3f, 0.3f)
        val rim = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (if (light) 0.95f else 0.85f) * strength),
                Color.White.copy(alpha = 0.18f * strength),
                Color.White.copy(alpha = 0.45f * strength)
            ),
            start = Offset(cx, cy),
            end = Offset(size.width - cx, size.height)
        )
        drawOutline(outline, rim, style = Stroke(width = 1.2.dp.toPx()))
        val sheen = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.16f * strength), Color.Transparent),
            startY = 0f, endY = size.height * 0.35f
        )
        drawOutline(outline, sheen)
        // The specular: a bright stretch of the top rim, with a faint glow just under it, that slides
        // along the edge as the phone tilts (centred and still with Reduce motion, where the tilt
        // stays at zero).
        val spotX = size.width * (0.5f - t.x * 0.35f).coerceIn(0.12f, 0.88f)
        val reach = max(36.dp.toPx(), size.width * 0.32f)
        drawOutline(
            outline,
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.95f * strength), Color.White.copy(alpha = 0.35f * strength), Color.Transparent),
                center = Offset(spotX, 0f), radius = reach
            ),
            style = Stroke(width = 1.8.dp.toPx())
        )
        drawOutline(
            outline,
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = (if (light) 0.18f else 0.08f) * strength), Color.Transparent),
                center = Offset(spotX, 0f), radius = min(reach, 22.dp.toPx())
            )
        )
    }

    companion object {
        private val EMPTY: android.graphics.Bitmap by lazy { android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888) }

        /**
         * The lens. In this surface's pixels: inside a band along the edge, sample the backdrop a
         * little displaced along the edge's inward normal (more at the very edge), so the edge bends
         * the light like a thick glass lens; brighten and saturate it slightly. Over the blobs only
         * the band is drawn (the film is see-through inside); over a photo the whole surface draws
         * the blurred photo, displaced at the edge.
         */
        private const val LENS_AGSL = """
            uniform float2 size;
            uniform float radius;
            uniform float2 origin;
            uniform float band;
            uniform float bend;
            uniform int mode;
            uniform float dim;
            uniform float dimTo;
            uniform shader photo;
            uniform float3 base;
            uniform float shade;
            uniform float4 orb0; uniform float4 orb1; uniform float4 orb2;
            uniform float3 col0; uniform float3 col1; uniform float3 col2;

            float3 blob(float3 c, float4 o, float3 col, float2 p) {
                float d = clamp(length(p - o.xy) / o.z, 0.0, 1.0);
                float a = d < 0.5 ? o.w * (1.0 - 0.65 * d / 0.5) : o.w * 0.35 * (1.0 - (d - 0.5) / 0.5);
                return mix(c, col, a);
            }

            half4 backdrop(float2 local) {
                if (mode == 1) {
                    half4 c = photo.eval(local);
                    return half4(mix(c.rgb, half3(dimTo), dim), 1.0);
                }
                float2 p = local + origin;
                float3 c = base;
                c = blob(c, orb0, col0, p);
                c = blob(c, orb1, col1, p);
                c = blob(c, orb2, col2, p);
                return half4(c * (1.0 - shade), 1.0);
            }

            half4 main(float2 p) {
                float2 hs = size * 0.5;
                float r = min(radius, min(hs.x, hs.y));
                float2 rel = p - hs;
                float2 q = abs(rel) - (hs - r);
                float dist = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;   // < 0 inside
                float inside = -dist;
                float t = clamp(1.0 - inside / band, 0.0, 1.0);
                float2 n;
                if (q.x > 0.0 && q.y > 0.0) n = normalize(q);
                else n = q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0);
                n *= sign(rel + 0.0001);
                float k = t * t;
                half4 c = backdrop(p - n * bend * k);
                // A touch brighter and more saturated where the glass is thickest.
                float l = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
                half3 sat = mix(half3(l), c.rgb, 1.0 + 0.35 * k);
                c.rgb = clamp(sat * (1.0 + 0.10 * k), 0.0, 1.0);
                float alpha = mode == 1 ? 1.0 : smoothstep(0.0, 1.0, t) * 0.85;
                return half4(c.rgb * alpha, alpha);
            }
        """
    }
}
