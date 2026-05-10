package com.fatlosstrack.data.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.db.FatLossDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BackupInfo(
    val file: File,
    val name: String,
    val date: String,
    val sizeKb: Long,
    val lastModified: Instant,
)

/**
 * Automatic local backup: copies the Room DB + DataStore prefs
 * to Documents/FatLossTrack/ on every app open and after each meal insert.
 * Keeps the last 7 daily backups and prunes older ones.
 *
 * Uses public Documents directory so backups survive app uninstalls.
 */
@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FatLossDatabase,
    private val appLogger: AppLogger,
) {
    companion object {
        private const val BACKUP_DIR = "FatLossTrack"
        private const val PREFIX = "backup_"
        private const val EXTENSION = ".zip"
        private const val MAX_BACKUPS = 7

        /** Global instance set after DI, for use from composables without injection */
        var instance: AutoBackupManager? = null
            private set
    }

    init {
        instance = this
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // ── Backup ──

    suspend fun runBackup() = withContext(Dispatchers.IO) {
        try {
            val backupDir = getBackupDir() ?: run {
                appLogger.error("AutoBackup", "Cannot access Documents directory")
                return@withContext
            }

            val today = LocalDate.now().format(dateFormat)
            val backupFile = File(backupDir, "$PREFIX$today$EXTENSION")

            // Checkpoint the WAL so all data is in the main DB file
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)").close()

            val dbFile = context.getDatabasePath("fatloss_track.db")
            val dataStoreFile = File(context.filesDir, "datastore/settings.preferences_pb")

            if (!dbFile.exists()) {
                appLogger.error("AutoBackup", "DB file not found")
                return@withContext
            }

            ZipOutputStream(FileOutputStream(backupFile)).use { zip ->
                addFileToZip(zip, dbFile, "fatloss_track.db")
                if (dataStoreFile.exists()) {
                    addFileToZip(zip, dataStoreFile, "settings.preferences_pb")
                }
            }

            appLogger.user("AutoBackup: saved $backupFile (${backupFile.length() / 1024} KB)")
            pruneOldBackups(backupDir)
        } catch (e: Exception) {
            appLogger.error("AutoBackup", "Failed: ${e.message}", e)
        }
    }

    // ── List ──

    fun listBackups(): List<BackupInfo> {
        val dir = getBackupDir() ?: return emptyList()
        return dir.listFiles { f ->
            f.name.endsWith(".zip")
        }?.sortedByDescending { it.lastModified() }?.map { f ->
            val datePart = f.nameWithoutExtension.removePrefix(PREFIX)
            BackupInfo(
                file = f,
                name = f.name,
                date = datePart,
                sizeKb = f.length() / 1024,
                lastModified = Instant.ofEpochMilli(f.lastModified()),
            )
        } ?: emptyList()
    }

    // ── Restore ──

    suspend fun restoreFromFile(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        restoreFromZipStream { ZipInputStream(FileInputStream(file)) }
    }

    suspend fun restoreFromUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return@withContext Result.failure(Exception("Cannot open file"))
        restoreFromZipStream { ZipInputStream(inputStream) }
    }

    private fun restoreFromZipStream(zipFactory: () -> ZipInputStream): Result<Unit> {
        return try {
            // Close DB before restoring
            database.close()

            val dbTarget = context.getDatabasePath("fatloss_track.db")
            val walFile = File(dbTarget.path + "-wal")
            val shmFile = File(dbTarget.path + "-shm")
            val dsTarget = File(context.filesDir, "datastore/settings.preferences_pb")

            zipFactory().use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "fatloss_track.db" -> {
                            FileOutputStream(dbTarget).use { zip.copyTo(it) }
                            // Remove WAL/SHM so Room reopens cleanly
                            walFile.delete()
                            shmFile.delete()
                        }
                        "settings.preferences_pb" -> {
                            dsTarget.parentFile?.mkdirs()
                            FileOutputStream(dsTarget).use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            appLogger.user("AutoBackup: restored from zip")
            Result.success(Unit)
        } catch (e: Exception) {
            appLogger.error("AutoBackup", "Restore failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Delete ──

    fun deleteBackup(file: File): Boolean = file.delete()

    // ── Internals ──

    private fun addFileToZip(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun pruneOldBackups(dir: File) {
        val backups = dir.listFiles { f ->
            f.name.startsWith(PREFIX) && f.name.endsWith(EXTENSION)
        }?.sortedByDescending { it.name } ?: return

        backups.drop(MAX_BACKUPS).forEach { old ->
            if (old.delete()) {
                appLogger.user("AutoBackup: pruned ${old.name}")
            }
        }
    }

    private fun getBackupDir(): File? {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir = File(docs, BACKUP_DIR)
        if (!dir.exists() && !dir.mkdirs()) return null
        return dir
    }
}
