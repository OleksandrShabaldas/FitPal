package com.fitpal.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The user's own photo behind Glow and Liquid glass (Settings → Appearance → Background).
 *
 * Picking one copies it into the app's own storage, scaled to about the screen's size, so the
 * backdrop never depends on the gallery (or the network) afterwards. Loading it also makes a small
 * blurred copy — Liquid glass draws the part of it under each card, which is what makes the cards
 * read as frosted glass over the photo — and a coarse brightness map for its contrast guard.
 * The file isn't in the backup (like the app icon); if it's missing the look falls back to its own
 * backdrop.
 */
object BackdropPhoto {
    /** The loaded photo (null when there's none, or it isn't loaded yet). */
    var image: ImageBitmap? by mutableStateOf(null)
        private set
    /** A small, heavily blurred copy for Liquid glass's surfaces (drawn scaled up). */
    var blurred: ImageBitmap? by mutableStateOf(null)
        private set
    /** Brightness 0–1 on a [GRID_W]×[GRID_H] grid over the photo, for the contrast guard. */
    var luma: FloatArray? = null
        private set

    const val GRID_W = 6
    const val GRID_H = 12

    private var loadedVersion = Long.MIN_VALUE

    private fun file(context: Context) = File(File(context.filesDir, "backdrop"), "photo.jpg")

    /** Copies the picked image in, scaled to at most [maxSide] px on its long side. False if it can't be read. */
    suspend fun import(context: Context, uri: Uri, maxSide: Int = 1600): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxSide.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt(), (h * scale).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val f = file(context)
            f.parentFile?.mkdirs()
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            loadedVersion = Long.MIN_VALUE
            true
        }.getOrDefault(false)
    }

    fun exists(context: Context): Boolean = file(context).exists()

    fun delete(context: Context) {
        runCatching { file(context).delete() }
        image = null
        blurred = null
        luma = null
        loadedVersion = Long.MIN_VALUE
    }

    /** Loads the stored photo for [version] once (no-op if it's already the loaded one). */
    suspend fun ensureLoaded(context: Context, version: Long) {
        if (version == loadedVersion && image != null) return
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val f = file(context)
                if (!f.exists()) return@runCatching null
                val bmp = android.graphics.BitmapFactory.decodeFile(f.path) ?: return@runCatching null
                Triple(bmp.asImageBitmap(), blur(bmp).asImageBitmap(), brightness(bmp))
            }.getOrNull()
        }
        loadedVersion = version
        image = loaded?.first
        blurred = loaded?.second
        luma = loaded?.third
    }

    /**
     * Where the photo lands on a [w]×[h] screen when drawn to cover it (centre-cropped): the source
     * rectangle in photo pixels. The backdrop and the glass surfaces use the same mapping.
     */
    fun coverSource(photoW: Int, photoH: Int, w: Float, h: Float): Rect {
        val scale = max(w / photoW, h / photoH)
        val sw = w / scale
        val sh = h / scale
        val left = (photoW - sw) / 2f
        val top = (photoH - sh) / 2f
        return Rect(left, top, left + sw, top + sh)
    }

    /** Average brightness of the photo under a screen rectangle (fractions of the screen), or null. */
    fun brightnessUnder(fx0: Float, fy0: Float, fx1: Float, fy1: Float): Float? {
        val grid = luma ?: return null
        var sum = 0f
        var n = 0
        val x0 = (fx0 * GRID_W).toInt().coerceIn(0, GRID_W - 1)
        val x1 = (fx1 * GRID_W).toInt().coerceIn(0, GRID_W - 1)
        val y0 = (fy0 * GRID_H).toInt().coerceIn(0, GRID_H - 1)
        val y1 = (fy1 * GRID_H).toInt().coerceIn(0, GRID_H - 1)
        for (y in y0..y1) for (x in x0..x1) { sum += grid[y * GRID_W + x]; n++ }
        return if (n == 0) null else sum / n
    }

    /** A strongly blurred thumbnail: 1/10 size, three box-blur passes (≈ a Gaussian). */
    private fun blur(src: Bitmap): Bitmap {
        val w = max(8, src.width / 10)
        val h = max(8, src.height / 10)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { boxBlur(px, w, h, 3) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        pass(px, tmp, w, h, r, horizontal = true)
        pass(tmp, px, w, h, r, horizontal = false)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            for (i in 0 until len) {
                var rs = 0; var gs = 0; var bs = 0; var n = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val c = if (horizontal) src[line * w + j] else src[j * w + line]
                    rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF; n++
                }
                val idx = if (horizontal) line * w + i else i * w + line
                dst[idx] = (0xFF shl 24) or ((rs / n) shl 16) or ((gs / n) shl 8) or (bs / n)
            }
        }
    }

    private fun brightness(src: Bitmap): FloatArray {
        val grid = Bitmap.createScaledBitmap(src, GRID_W, GRID_H, true)
        val out = FloatArray(GRID_W * GRID_H)
        for (y in 0 until GRID_H) for (x in 0 until GRID_W) {
            val c = grid.getPixel(x, y)
            val r = ((c shr 16) and 0xFF) / 255f
            val g = ((c shr 8) and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            out[y * GRID_W + x] = 0.2126f * r + 0.7152f * g + 0.0722f * b
        }
        return out
    }
}
