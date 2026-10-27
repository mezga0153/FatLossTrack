package com.fatlosstrack.ui.camera

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val EXIF_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

/**
 * When the photo was taken, in the device's time zone. Tries EXIF first, then
 * MediaStore DATE_TAKEN (photo picker / gallery URIs), then the file's mtime
 * (fresh CameraX captures in cacheDir). Null if nothing usable or in the future.
 */
internal fun photoTakenAt(context: Context, uri: Uri): LocalDateTime? {
    val taken = exifTakenAt(context, uri)
        ?: mediaStoreTakenAt(context, uri)
        ?: fileModifiedAt(uri)
    return taken?.takeIf { !it.isAfter(LocalDateTime.now().plusMinutes(5)) }
}

private fun exifTakenAt(context: Context, uri: Uri): LocalDateTime? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val exif = ExifInterface(stream)
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return@use null
        val local = LocalDateTime.parse(raw.trim(), EXIF_DATE_FORMAT)
        // EXIF time is wall-clock at capture; shift to the current zone if the offset is known
        val offset = (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME))
            ?.let { runCatching { ZoneOffset.of(it.trim()) }.getOrNull() }
        offset?.let {
            OffsetDateTime.of(local, it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        } ?: local
    }
} catch (e: Exception) {
    Log.w("PhotoTakenAt", "No EXIF date for $uri", e)
    null
}

private fun mediaStoreTakenAt(context: Context, uri: Uri): LocalDateTime? {
    if (uri.scheme != "content") return null
    return try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)
            ?.use { c ->
                if (!c.moveToFirst() || c.isNull(0)) return@use null
                c.getLong(0).takeIf { it > 0 }?.let(::epochMillisToLocal)
            }
    } catch (e: Exception) {
        Log.w("PhotoTakenAt", "No DATE_TAKEN for $uri", e)
        null
    }
}

private fun fileModifiedAt(uri: Uri): LocalDateTime? {
    if (uri.scheme != "file") return null
    return uri.path?.let { File(it).lastModified() }?.takeIf { it > 0 }?.let(::epochMillisToLocal)
}

private fun epochMillisToLocal(millis: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
