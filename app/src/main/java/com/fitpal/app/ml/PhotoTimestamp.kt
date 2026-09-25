package com.fitpal.app.ml

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * When a food photo was actually taken — so a meal photographed at 7:40 PM and logged at 10 PM is
 * filed at 7:40 PM (and, if that's inside the eating window, doesn't trip the fasting warning).
 *
 * Tries, in order: the photo's own EXIF capture time (with its time-zone offset when the camera wrote
 * one), the gallery's "date taken", and — for a shot from FitPal's own camera — the file's time.
 * Everything is local and read-only; null when none of them can say.
 */
object PhotoTimestamp {

    private val EXIF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun read(context: Context, uri: Uri): LocalDateTime? {
        fromExif(context, uri)?.let { return it }
        if (uri.scheme == "content") fromMediaStore(context, uri)?.let { return it }
        if (uri.scheme == "file") {
            uri.path?.let { File(it) }?.takeIf { it.exists() && it.lastModified() > 0L }
                ?.let { return toLocal(it.lastModified()) }
        }
        return null
    }

    private fun fromExif(context: Context, uri: Uri): LocalDateTime? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                ?: return@use null
            val local = LocalDateTime.parse(raw.trim(), EXIF_FORMAT)
            val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
            if (offset.isNullOrBlank()) {
                local   // no zone recorded — the camera's clock was local time
            } else {
                // Taken in another zone (travel): shift to the phone's current zone.
                OffsetDateTime.of(local, ZoneOffset.of(offset.trim()))
                    .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            }
        }
    }.getOrNull()

    private fun fromMediaStore(context: Context, uri: Uri): LocalDateTime? {
        // "datetaken" for gallery images; "date_taken_ms" for Android's photo-picker URIs.
        for (column in listOf("datetaken", "date_taken_ms")) {
            val millis = runCatching {
                context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(column)
                    if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) c.getLong(idx) else null
                }
            }.getOrNull()
            if (millis != null && millis > 0L) return toLocal(millis)
        }
        return null
    }

    private fun toLocal(epochMillis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
}
