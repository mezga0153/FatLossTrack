package com.fatlosstrack.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.IntentCompat
import com.fatlosstrack.data.local.SharedContent
import com.fatlosstrack.ui.camera.photoTakenAt
import java.io.File
import java.time.ZoneId

/** Same limit as the capture screen. */
private const val MAX_SHARED_PHOTOS = 3

private const val SHARED_PHOTOS_DIR = "shared_photos"

/** Shared content read from an intent, plus how many photos were dropped over the limit. */
internal data class ShareResult(val content: SharedContent?, val droppedPhotos: Int = 0)

/** True for the share actions declared in the manifest (single + multiple images, plain text). */
internal fun Intent.isShareIntent(): Boolean =
    action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE

/**
 * Reads a share intent. Photos are copied into cacheDir first: the read grant on another
 * app's content:// URI can lapse once the activity goes away, and analysis happens later.
 * Blocking I/O — call off the main thread.
 */
internal fun readShareIntent(context: Context, intent: Intent): ShareResult {
    val type = intent.type.orEmpty()
    return when {
        type.startsWith("image/") -> {
            val uris = sharedStreamUris(intent)
            val copied = copySharedPhotos(context, uris.take(MAX_SHARED_PHOTOS))
            ShareResult(
                content = copied.takeIf { it.isNotEmpty() }?.let { SharedContent.Photos(it) },
                droppedPhotos = (uris.size - MAX_SHARED_PHOTOS).coerceAtLeast(0),
            )
        }
        type.startsWith("text/") && intent.action == Intent.ACTION_SEND -> {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
            ShareResult(text?.takeIf { it.isNotEmpty() }?.let { SharedContent.Text(it) })
        }
        else -> ShareResult(null)
    }
}

private fun sharedStreamUris(intent: Intent): List<Uri> {
    val extras = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
    }
    if (extras.isNotEmpty()) return extras
    // Some senders only fill ClipData
    val clip = intent.clipData ?: return emptyList()
    return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
}

/**
 * Copies shared photos into a private cache folder, replacing the previous share's copies.
 * The copy's mtime is set to the original capture time so the date/time prefill still works
 * for photos without EXIF, whose MediaStore date would otherwise be lost in the copy.
 */
private fun copySharedPhotos(context: Context, uris: List<Uri>): List<Uri> {
    val dir = File(context.cacheDir, SHARED_PHOTOS_DIR)
    dir.deleteRecursively()
    dir.mkdirs()
    val stamp = System.currentTimeMillis()
    return uris.mapIndexedNotNull { i, uri ->
        try {
            val takenAt = photoTakenAt(context, uri)
            val file = File(dir, "shared_${stamp}_$i.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return@mapIndexedNotNull null
            takenAt?.let { file.setLastModified(it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.w("ShareIntent", "Failed to copy shared photo: $uri", e)
            null
        }
    }
}
