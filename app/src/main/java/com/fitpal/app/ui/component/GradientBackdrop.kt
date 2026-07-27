package com.fitpal.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import com.fitpal.app.ui.theme.InkBlack

/**
 * Per-section background "world": a warm gradient wash + a few soft glowing light
 * orbs + a fine grain, all generated (fully offline). Frosted-glass content sits
 * on top of it. This layered light + grain is what gives the premium depth.
 */
enum class BackdropTheme { TODAY, ACTIVITY, TRENDS, GARDEN }

private data class Orb(val color: Color, val cx: Float, val cy: Float, val rFrac: Float, val alpha: Float)

private fun baseColors(theme: BackdropTheme): List<Color> = when (theme) {
    BackdropTheme.TODAY -> listOf(Color(0xFF5A3A22), Color(0xFF3A261A), Color(0xFF241814), InkBlack)
    BackdropTheme.ACTIVITY -> listOf(Color(0xFF14403B), Color(0xFF0E2C2A), Color(0xFF11201F), InkBlack)
    BackdropTheme.TRENDS -> listOf(Color(0xFF33438C), Color(0xFF232C5E), Color(0xFF171C36), InkBlack)
    BackdropTheme.GARDEN -> listOf(Color(0xFF24401F), Color(0xFF182C16), Color(0xFF121E10), InkBlack)
}

private fun orbs(theme: BackdropTheme): List<Orb> = when (theme) {
    BackdropTheme.TODAY -> listOf(
        Orb(Color(0xFFF3C46E), 0.85f, 0.02f, 0.42f, 0.50f),
        Orb(Color(0xFFE89A4E), 0.05f, 0.30f, 0.34f, 0.30f),
        Orb(Color(0xFFB9603A), 0.90f, 0.70f, 0.40f, 0.22f)
    )
    BackdropTheme.ACTIVITY -> listOf(
        Orb(Color(0xFF6FE0D0), 0.85f, 0.04f, 0.40f, 0.34f),
        Orb(Color(0xFF3FA9C0), 0.05f, 0.40f, 0.34f, 0.24f)
    )
    BackdropTheme.TRENDS -> listOf(
        Orb(Color(0xFF8AB0FF), 0.85f, 0.03f, 0.46f, 0.55f),
        Orb(Color(0xFF9B7FE0), 0.00f, 0.34f, 0.40f, 0.40f),
        Orb(Color(0xFF6E90E0), 0.90f, 0.66f, 0.42f, 0.28f)
    )
    BackdropTheme.GARDEN -> listOf(
        Orb(Color(0xFFB6E06E), 0.82f, 0.04f, 0.40f, 0.34f),
        Orb(Color(0xFF5FB85A), 0.06f, 0.40f, 0.34f, 0.22f)
    )
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

@Composable
fun GradientBackdrop(
    modifier: Modifier = Modifier,
    theme: BackdropTheme = BackdropTheme.TODAY,
    content: @Composable () -> Unit
) {
    val base = baseColors(theme)
    val orbList = orbs(theme)
    val noise = rememberNoise()
    val noiseBrush = remember(noise) { ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)) }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = base,
                    center = Offset(size.width * 0.78f, -size.height * 0.06f),
                    radius = size.maxDimension * 1.15f
                )
            )
            orbList.forEach { o ->
                val c = Offset(size.width * o.cx, size.height * o.cy)
                val r = size.minDimension * o.rFrac
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(o.color.copy(alpha = o.alpha), Color.Transparent),
                        center = c,
                        radius = r
                    ),
                    radius = r,
                    center = c
                )
            }
            // Gentle global darkening so the frosted UI reads clearly on top — kept subtle so the
            // warm glow still shows through at the edges.
            drawRect(Color.Black, alpha = 0.22f)
            drawRect(brush = noiseBrush, alpha = 0.05f, blendMode = BlendMode.Softlight)
        }
        content()
    }
}
