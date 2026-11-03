package com.fitpal.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.fitpal.app.ui.component.kit.LocalTopBarScroll
import com.fitpal.app.ui.component.kit.rememberTopBarScroll
import com.fitpal.app.ui.theme.BackdropPhoto
import com.fitpal.app.ui.theme.BackdropWorld
import com.fitpal.app.ui.theme.LiquidBackdrop
import com.fitpal.app.ui.theme.LookState
import com.fitpal.app.ui.theme.LookStyle
import com.fitpal.app.ui.theme.PhotoTreatment

/**
 * Per-section background "world" in the current look, all generated on the phone:
 *  - Glow: a warm gradient wash + soft glowing light orbs + a fine grain.
 *  - Liquid glass: big vivid colour blobs drifting slowly (still with reduce-motion) — the colour
 *    the thick glass on top has to catch.
 *  - Glow and Liquid glass can show the user's own photo instead (Settings → Appearance →
 *    Background), under a thick frost: blurred, darkened (lightened on light Liquid), the look's
 *    own light on top and grain — how much is the "Photo strength" ([PhotoTreatment]).
 *  - Paper: a flat sheet with a faint wash and paper grain.
 *  - Material You: the plain surface colour.
 * Content sits on top. The world (Today / Trends / …) keeps each section its own colour where the
 * look has one.
 */
enum class BackdropTheme { TODAY, ACTIVITY, TRENDS, GARDEN }

private fun BackdropTheme.world() = when (this) {
    BackdropTheme.TODAY -> BackdropWorld.TODAY
    BackdropTheme.ACTIVITY -> BackdropWorld.ACTIVITY
    BackdropTheme.TRENDS -> BackdropWorld.TRENDS
    BackdropTheme.GARDEN -> BackdropWorld.GARDEN
}

@Composable
private fun rememberNoise() = remember {
    val n = 96
    val pixels = IntArray(n * n)
    val rnd = java.util.Random(11L)
    for (i in pixels.indices) {
        val v = rnd.nextInt(256)
        pixels[i] = android.graphics.Color.argb(255, v, v, v)
    }
    android.graphics.Bitmap
        .createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GradientBackdrop(
    modifier: Modifier = Modifier,
    theme: BackdropTheme = BackdropTheme.TODAY,
    content: @Composable () -> Unit
) {
    val look = LookState.look
    val colors = look.colors
    val spec = colors.backdrops[theme.world()] ?: colors.backdrops.values.first()
    val noise = rememberNoise()
    val noiseBrush = remember(noise) { ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)) }
    val grain = look.appearance.grain

    // Liquid glass's drift: one slow loop the blobs ride around (a still frame with reduce-motion).
    // The phase is shared (LiquidBackdrop) because the glass draws the same blobs at its edges; it's
    // read only inside draw phases, so the drift redraws without recomposing.
    val drifting = look.style == LookStyle.LIQUID && !look.appearance.reduceMotion
    if (drifting) {
        LaunchedEffect(Unit) {
            while (true) {
                withFrameMillis { ms -> LiquidBackdrop.phase = ((ms % 36_000L) / 36_000f) * (2 * Math.PI).toFloat() }
            }
        }
    } else if (look.style == LookStyle.LIQUID) {
        SideEffect { LiquidBackdrop.phase = 0f }
    }

    // The user's own photo (Glow and Liquid glass), loaded once per pick, and its blurred copy for
    // the treatment's blur level (made once per photo and level).
    val context = LocalContext.current
    val photo = if (look.hasPhoto) BackdropPhoto.image else null
    val treatment = PhotoTreatment.at(look.appearance.photoStrength)
    if (look.hasPhoto) {
        LaunchedEffect(look.appearance.photoVersion) { BackdropPhoto.ensureLoaded(context, look.appearance.photoVersion) }
        LaunchedEffect(photo, treatment.blurLevel) { BackdropPhoto.ensureBackdrop(treatment.blurLevel) }
    }

    val frameModifier = if (look.style == LookStyle.LIQUID) {
        Modifier.onGloballyPositioned { coords ->
            val r = coords.boundsInWindow()
            if (r != LiquidBackdrop.frame) LiquidBackdrop.frame = r
        }
    } else Modifier

    // Liquid glass and Material You collapse the screen's top bar as its content scrolls.
    val topBarScroll = if (look.style == LookStyle.LIQUID || look.style == LookStyle.MATERIAL) rememberTopBarScroll() else null
    val scrollModifier = if (topBarScroll != null) Modifier.nestedScroll(topBarScroll.nestedScrollConnection) else Modifier

    Box(modifier.fillMaxSize().then(frameModifier).then(scrollModifier)) {
        Canvas(Modifier.fillMaxSize()) {
            when (look.style) {
                LookStyle.GLOW -> if (photo != null) {
                    drawTreatedPhoto(
                        photo, treatment, light = false, noise = noiseBrush,
                        glows = spec.orbs.map { o ->
                            Triple(Offset(size.width * o.cx, size.height * o.cy), size.minDimension * o.rFrac, o.color.copy(alpha = o.alpha))
                        }
                    )
                } else {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = spec.base,
                            center = Offset(size.width * 0.78f, -size.height * 0.06f),
                            radius = size.maxDimension * 1.15f
                        )
                    )
                    spec.orbs.forEach { o ->
                        val c = Offset(size.width * o.cx, size.height * o.cy)
                        val r = size.minDimension * o.rFrac
                        drawCircle(
                            brush = Brush.radialGradient(listOf(o.color.copy(alpha = o.alpha), Color.Transparent), center = c, radius = r),
                            radius = r, center = c
                        )
                    }
                    // Gentle global darkening so the frosted UI reads clearly on top — kept subtle so the
                    // glow still shows through at the edges.
                    drawRect(Color.Black, alpha = colors.backdropShade)
                    if (grain) drawRect(brush = noiseBrush, alpha = 0.05f, blendMode = BlendMode.Softlight)
                }
                LookStyle.LIQUID -> if (photo != null) {
                    // The drifting blobs are the light here; the glass surfaces draw the same ones.
                    val phase = LiquidBackdrop.phase
                    drawTreatedPhoto(
                        photo, treatment, light = colors.isLight, noise = noiseBrush,
                        glows = spec.orbs.mapIndexed { i, o ->
                            val (c, r) = LiquidBackdrop.orb(i, o, phase, size.width, size.height)
                            Triple(c, r, o.color.copy(alpha = o.alpha))
                        }
                    )
                } else {
                    val phase = LiquidBackdrop.phase
                    drawRect(colors.inkBlack)
                    spec.orbs.forEachIndexed { i, o ->
                        val (c, r) = LiquidBackdrop.orb(i, o, phase, size.width, size.height)
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(o.color.copy(alpha = o.alpha), o.color.copy(alpha = o.alpha * 0.35f), Color.Transparent),
                                center = c, radius = r
                            ),
                            radius = r, center = c
                        )
                    }
                    if (colors.backdropShade > 0f) drawRect(Color.Black, alpha = colors.backdropShade)
                }
                LookStyle.PAPER -> {
                    drawRect(colors.inkBlack)
                    spec.orbs.forEach { o ->
                        val c = Offset(size.width * o.cx, size.height * o.cy)
                        val r = size.maxDimension * o.rFrac
                        drawCircle(
                            brush = Brush.radialGradient(listOf(o.color.copy(alpha = o.alpha), Color.Transparent), center = c, radius = r),
                            radius = r, center = c
                        )
                    }
                    if (grain) {
                        drawRect(
                            brush = noiseBrush,
                            alpha = if (colors.isLight) 0.07f else 0.05f,
                            blendMode = if (colors.isLight) BlendMode.Multiply else BlendMode.Softlight
                        )
                    }
                }
                LookStyle.MATERIAL -> drawRect(colors.inkBlack)
            }
        }
        if (topBarScroll != null) CompositionLocalProvider(LocalTopBarScroll provides topBarScroll) { content() } else content()
    }
}

/**
 * The user's photo under the look's frost: its blurred copy (the sharp photo until that's made),
 * darkened (or lightened on a light look), the look's light orbs over it, then grain.
 */
private fun DrawScope.drawTreatedPhoto(
    photo: ImageBitmap,
    t: PhotoTreatment,
    light: Boolean,
    noise: Brush,
    glows: List<Triple<Offset, Float, Color>>
) {
    drawCover(BackdropPhoto.backdrop ?: photo)
    drawRect(if (light) Color.White else Color.Black, alpha = t.darkAlpha)
    val g = t.glowAmount
    if (g > 0f) glows.forEach { (c, r, color) ->
        drawCircle(
            brush = Brush.radialGradient(listOf(photoGlow(color, g), Color.Transparent), center = c, radius = r),
            radius = r, center = c
        )
    }
    if (t.grainAlpha > 0f) drawRect(brush = noise, alpha = t.grainAlpha, blendMode = BlendMode.Overlay)
}

/** An orb's colour as light over the photo: its own alpha, scaled by the treatment's light. */
internal fun photoGlow(color: Color, amount: Float): Color = color.copy(alpha = (color.alpha * amount * 1.4f).coerceIn(0f, 1f))

/** The photo, centre-cropped to cover the whole backdrop (the glass uses the same mapping). */
private fun DrawScope.drawCover(photo: ImageBitmap) {
    val src = BackdropPhoto.coverSource(photo.width, photo.height, size.width, size.height)
    drawImage(
        photo,
        srcOffset = IntOffset(src.left.toInt(), src.top.toInt()),
        srcSize = IntSize(src.width.toInt(), src.height.toInt()),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        filterQuality = FilterQuality.Medium
    )
}
