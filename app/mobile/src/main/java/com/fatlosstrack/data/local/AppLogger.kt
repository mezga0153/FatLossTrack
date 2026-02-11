package com.fatlosstrack.data.local

import android.content.Context
import java.io.File
import java.time.LocalDate
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
        private const val LOG_PREFIX = "app_activity-"
        private const val LOG_SUFFIX = ".log"
        private const val MAX_SIZE_BYTES = 512 * 1024 // 500 KB
        private const val RETENTION_DAYS = 7
        private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        /** Global instance set after DI, for use from composables without injection */
        @Volatile
        var instance: AppLogger? = null
            private set
    }

    init {
        instance = this
    }

    private val logDir: File by lazy { File(context.filesDir, "logs").also { it.mkdirs() } }
    private val logFilter = java.io.FileFilter { it.isFile && it.name.startsWith(LOG_PREFIX) && it.name.endsWith(LOG_SUFFIX) }
    private val lock = Any()

    fun log(tag: String, message: String) {
        val ts = LocalDateTime.now().format(timeFmt)
        val line = "$ts [$tag] $message\n"
        synchronized(lock) {
            try {
                pruneOldLogsLocked()
                val file = logFileFor(LocalDate.now())
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

    /** Read the provided log file, or the most recent if null */
    fun read(file: File? = null): String {
        val target = file ?: latestLogFile()
        return try {
            if (target != null && target.exists()) target.readText() else "(empty log)"
        } catch (_: Exception) {
            "(error reading log)"
        }
    }

    /** Delete all log files */
    fun clear() {
        synchronized(lock) {
            try { logDir.listFiles(logFilter)?.forEach { it.delete() } } catch (_: Exception) {}
        }
    }

    /** File size in bytes */
    fun sizeBytes(file: File? = null): Long = try {
        val target = file ?: latestLogFile()
        if (target != null && target.exists()) target.length() else 0
    } catch (_: Exception) { 0 }

    /** Return today's log file (created on demand) */
    fun getLogFile(): File = logFileFor(LocalDate.now())

    /** List available log files (sorted newest first, retained for last 7 days) */
    fun listLogFiles(): List<LogFileInfo> = synchronized(lock) {
        pruneOldLogsLocked()
        logDir.listFiles(logFilter)
            ?.sortedByDescending { extractDate(it.name) ?: LocalDate.MIN }
            ?.map { LogFileInfo(it, extractDate(it.name), it.length()) }
            ?: emptyList()
    }

    /** Most recent log file, or null if none exist */
    fun latestLogFile(): File? = listLogFiles().firstOrNull()?.file

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

    data class LogFileInfo(
        val file: File,
        val date: LocalDate?,
        val sizeBytes: Long,
    )

    private fun logFileFor(date: LocalDate): File = File(logDir, "$LOG_PREFIX${date}$LOG_SUFFIX")

    private fun extractDate(name: String): LocalDate? {
        val datePart = name.removePrefix(LOG_PREFIX).removeSuffix(LOG_SUFFIX)
        return try { LocalDate.parse(datePart) } catch (_: Exception) { null }
    }

    private fun pruneOldLogsLocked() {
        val files = logDir.listFiles(logFilter) ?: return
        val sorted = files.sortedByDescending { extractDate(it.name) ?: LocalDate.MIN }
        val toDelete = sorted.drop(RETENTION_DAYS)
        toDelete.forEach { runCatching { it.delete() } }
    }
}
