package com.fitpal.app.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface

/**
 * Shared helpers for "snap a nutrition-facts label and let the AI fill the values" — used by both
 * the standalone Custom food screen and the Custom tab of the meal builder, so the decode and the
 * prompt live in exactly one place.
 */

/** Decode a captured photo file, downsampled and rotated upright (from its EXIF orientation). */
fun decodeUprightBitmap(path: String, maxDim: Int = 1600): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
    val bmp = BitmapFactory.decodeFile(
        path, BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return null
    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return bmp
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

/**
 * The note that tells the vision model the photo is a packaged product's nutrition panel (not a
 * plate), so it returns ONE food whose per-100 g values come straight from the label.
 */
const val NUTRITION_LABEL_PROMPT: String =
    "IMPORTANT: this photo is the NUTRITION FACTS panel of ONE packaged product — it is NOT a " +
        "plate of prepared food. Ignore any 'identify the foods on the plate' instructions. Read the " +
        "label and return EXACTLY ONE food item whose per-100 g values (kcalPer100g, proteinPer100g, " +
        "fatPer100g, carbsPer100g, fiberPer100g) come straight from the label. If the label lists " +
        "values per serving, convert to per 100 g using the serving size printed on it. Set \"grams\" " +
        "to the amount a person most likely ate: the serving size printed on the label, or the net " +
        "package weight if it's a single-serving pack; if no serving size is given, use 100. Use the " +
        "product's name from the label."
