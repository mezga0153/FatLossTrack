package com.fatlosstrack.data.backup

import android.content.Context
import android.os.Environment
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.db.FatLossDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatic local backup: copies the Room DB + DataStore prefs
 * to Documents/FatLossTrack/ on every app open.
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

            // Gather files to back up
            val dbFile = context.getDatabasePath("fatloss_track.db")
            val dataStoreFile = File(context.filesDir, "datastore/settings.preferences_pb")

            if (!dbFile.exists()) {
                appLogger.error("AutoBackup", "DB file not found")
                return@withContext
            }

            // Write zip
            ZipOutputStream(FileOutputStream(backupFile)).use { zip ->
                addFileToZip(zip, dbFile, "fatloss_track.db")
                if (dataStoreFile.exists()) {
                    addFileToZip(zip, dataStoreFile, "settings.preferences_pb")
                }
            }

            appLogger.user("AutoBackup: saved $backupFile (${backupFile.length() / 1024} KB)")

            // Prune old backups
            pruneOldBackups(backupDir)
        } catch (e: Exception) {
            appLogger.error("AutoBackup", "Failed: ${e.message}", e)
        }
    }

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
