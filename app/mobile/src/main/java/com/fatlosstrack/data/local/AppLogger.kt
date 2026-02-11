package com.fatlosstrack.data.local

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple file-based logger for in-app activity tracking.
 * Logs are written to app-internal storage (no extra permissions needed).
 * File rolls over at 500 KB to keep storage bounded.
 */
@Singleton
class AppLogger @Inject constructor(
    private val context: Context,
) {
    companion object {
        private const val LOG_FILE = "app_activity.log"
        private const val MAX_SIZE_BYTES = 512 * 1024 // 500 KB
        private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        /** Global instance set after DI, for use from composables without injection */
        @Volatile
        var instance: AppLogger? = null
            private set
    }

    init {
        instance = this
    }

    private val file: File by lazy { File(context.filesDir, LOG_FILE) }
    private val lock = Any()

    fun log(tag: String, message: String) {
        val ts = LocalDateTime.now().format(timeFmt)
        val line = "$ts [$tag] $message\n"
        synchronized(lock) {
            try {
                // Roll over if too large
                if (file.exists() && file.length() > MAX_SIZE_BYTES) {
                    val trimmed = file.readText().takeLast(MAX_SIZE_BYTES / 2)
                    file.writeText("--- log trimmed ---\n$trimmed")
                }
                file.appendText(line)
            } catch (_: Exception) {
                // Silently ignore write failures
            }
        }
    }

    /** Read entire log contents (most recent at bottom) */
    fun readAll(): String {
        return try {
            if (file.exists()) file.readText() else "(empty log)"
        } catch (_: Exception) {
            "(error reading log)"
        }
    }

    /** Clear the log file */
    fun clear() {
        synchronized(lock) {
            try { file.delete() } catch (_: Exception) {}
        }
    }

    /** File size in bytes */
    fun sizeBytes(): Long = try { if (file.exists()) file.length() else 0 } catch (_: Exception) { 0 }

    /** Return the log file for sharing / saving */
    fun getLogFile(): File = file

    // ── Convenience methods ──

    fun hc(message: String) = log("HC", message)
    fun ai(message: String) = log("AI", message)
    fun meal(message: String) = log("MEAL", message)
    fun sync(message: String) = log("SYNC", message)
    fun user(message: String) = log("USER", message)
    fun nav(message: String) = log("NAV", message)
    fun error(tag: String, message: String, e: Throwable? = null) {
        val msg = if (e != null) "$message — ${e::class.simpleName}: ${e.message}" else message
        log("ERR/$tag", msg)
        if (e != null) {
            // Log full stack trace + cause chain
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            log("ERR/$tag", "Stack trace:\n$sw")
        }
    }
}
