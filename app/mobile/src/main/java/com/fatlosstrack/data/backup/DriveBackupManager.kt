package com.fatlosstrack.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.FatLossDatabase
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages backup / restore of the Room database + user preferences
 * to the Google Drive **appDataFolder** (hidden, app-private storage).
 *
 * Prerequisites:
 * - Google Sign-In active (via [com.fatlosstrack.auth.AuthManager])
 * - Drive API enabled in the Google Cloud project
 *
 * Uses the `drive.appdata` scope which only accesses data
 * created by this app — no access to the user's personal Drive files.
 */
@Singleton
class DriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FatLossDatabase,
    private val preferencesManager: PreferencesManager,
) {
    companion object {
        private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val BACKUP_FILENAME = "fatloss_track_backup.zip"
    }

    /** Possible actions that may require user consent. */
    enum class PendingAction { BACKUP, RESTORE }

    sealed class BackupState {
        data object Idle : BackupState()
        data object InProgress : BackupState()
        data class Done(val message: String) : BackupState()
        data class NeedsConsent(
            val consentIntent: Intent,
            val action: PendingAction,
        ) : BackupState()
        data class Error(val message: String) : BackupState()
    }

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun resetState() {
        _state.value = BackupState.Idle
    }

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /** Create a backup and upload it to Google Drive. */
    suspend fun backup() {
        _state.value = BackupState.InProgress
        try {
            val token = acquireToken(PendingAction.BACKUP) ?: return

            // Checkpoint the WAL so every write is in the main .db file
            checkpoint()

            val dbFile = context.getDatabasePath("fatloss_track.db")
            val zipFile = File(context.cacheDir, BACKUP_FILENAME)
            createZip(dbFile, zipFile)

            deleteRemoteBackups(token)
            upload(token, zipFile)

            zipFile.delete()

            preferencesManager.setLastBackupTime(Instant.now().toString())
            _state.value = BackupState.Done("Backup complete")
        } catch (e: Exception) {
            _state.value = BackupState.Error(e.message ?: "Backup failed")
        }
    }

    /** Download the latest backup from Drive and overwrite local data. */
    suspend fun restore() {
        _state.value = BackupState.InProgress
        try {
            val token = acquireToken(PendingAction.RESTORE) ?: return

            val zipFile = downloadLatest(token)
                ?: throw IllegalStateException("No backup found on Google Drive")

            // Close Room so we can overwrite the file
            database.close()
            extractZip(zipFile)
            zipFile.delete()

            _state.value = BackupState.Done("Restore complete — restarting…")

            // Room / Hilt singletons are stale → restart process
            restartApp()
        } catch (e: Exception) {
            _state.value = BackupState.Error(e.message ?: "Restore failed")
        }
    }

    // ------------------------------------------------------------------ //
    //  Local (device) backup / restore
    // ------------------------------------------------------------------ //

    /**
     * Create a zip in the cache dir, then copy it to [destUri]
     * (chosen by the user via the system file-picker).
     */
    suspend fun localBackup(destUri: Uri) {
        _state.value = BackupState.InProgress
        try {
            checkpoint()
            val dbFile = context.getDatabasePath("fatloss_track.db")
            val zipFile = File(context.cacheDir, BACKUP_FILENAME)
            createZip(dbFile, zipFile)

            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    FileInputStream(zipFile).use { it.copyTo(out) }
                } ?: throw IOException("Cannot write to selected location")
            }
            zipFile.delete()
            _state.value = BackupState.Done("Saved to device")
        } catch (e: Exception) {
            _state.value = BackupState.Error(e.message ?: "Local backup failed")
        }
    }

    /**
     * Read a zip from [srcUri] (chosen by the user via file-picker)
     * and overwrite the local database + preferences.
     */
    suspend fun localRestore(srcUri: Uri) {
        _state.value = BackupState.InProgress
        try {
            val zipFile = File(context.cacheDir, "restore_local_$BACKUP_FILENAME")
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(srcUri)?.use { inp ->
                    FileOutputStream(zipFile).use { out -> inp.copyTo(out) }
                } ?: throw IOException("Cannot read selected file")
            }

            database.close()
            extractZip(zipFile)
            zipFile.delete()

            _state.value = BackupState.Done("Restore complete — restarting…")
            restartApp()
        } catch (e: Exception) {
            _state.value = BackupState.Error(e.message ?: "Local restore failed")
        }
    }

    /** Returns the modified-time ISO string of the latest remote backup, or null. */
    suspend fun remoteBackupDate(): String? = withContext(Dispatchers.IO) {
        val token = try {
            acquireTokenSilent()
        } catch (_: Exception) {
            return@withContext null
        } ?: return@withContext null

        val req = Request.Builder()
            .url(
                "$DRIVE_FILES_URL?spaces=appDataFolder" +
                    "&q=name%3D%27$BACKUP_FILENAME%27" +
                    "&fields=files(id,modifiedTime)" +
                    "&orderBy=modifiedTime%20desc&pageSize=1",
            )
            .addHeader("Authorization", "Bearer $token")
            .get().build()

        val res = httpClient.newCall(req).execute()
        if (!res.isSuccessful) return@withContext null

        val files = JSONObject(res.body?.string() ?: "{}").optJSONArray("files")
        if (files == null || files.length() == 0) return@withContext null
        files.getJSONObject(0).optString("modifiedTime", null)
    }

    // ------------------------------------------------------------------ //
    //  Token helpers
    // ------------------------------------------------------------------ //

    /**
     * Returns an OAuth2 access token for the Drive appdata scope.
     *
     * If the user has not yet consented, emits [BackupState.NeedsConsent]
     * and returns `null`. The UI should launch the consent intent and
     * re-call the pending action on success.
     */
    private suspend fun acquireToken(action: PendingAction): String? {
        return try {
            acquireTokenSilent() ?: throw IllegalStateException("Not signed in")
        } catch (e: UserRecoverableAuthException) {
            val consentIntent = e.intent
            if (consentIntent != null) {
                _state.value = BackupState.NeedsConsent(consentIntent, action)
            } else {
                _state.value = BackupState.Error("Cannot request Drive permission")
            }
            null
        }
    }

    private suspend fun acquireTokenSilent(): String? = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)?.account
            ?: return@withContext null
        GoogleAuthUtil.getToken(context, account, "oauth2:$DRIVE_SCOPE")
    }

    // ------------------------------------------------------------------ //
    //  Database helpers
    // ------------------------------------------------------------------ //

    private fun checkpoint() {
        database.openHelper.writableDatabase.apply {
            query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        }
    }

    // ------------------------------------------------------------------ //
    //  Zip helpers
    // ------------------------------------------------------------------ //

    private suspend fun createZip(dbFile: File, outZip: File) = withContext(Dispatchers.IO) {
        ZipOutputStream(FileOutputStream(outZip)).use { zip ->
            // 1. Database file
            zip.putNextEntry(ZipEntry("fatloss_track.db"))
            FileInputStream(dbFile).use { it.copyTo(zip) }
            zip.closeEntry()

            // 2. User preferences snapshot (JSON)
            zip.putNextEntry(ZipEntry("preferences.json"))
            val prefs = snapshotPreferences()
            zip.write(prefs.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private suspend fun snapshotPreferences(): String {
        val json = JSONObject()
        preferencesManager.startWeight.first()?.let { json.put("startWeight", it.toDouble()) }
        preferencesManager.goalWeight.first()?.let { json.put("goalWeight", it.toDouble()) }
        json.put("weeklyRate", preferencesManager.weeklyRate.first().toDouble())
        json.put("coachTone", preferencesManager.coachTone.first())
        preferencesManager.heightCm.first()?.let { json.put("heightCm", it) }
        preferencesManager.startDate.first()?.let { json.put("startDate", it) }
        preferencesManager.sex.first()?.let { json.put("sex", it) }
        preferencesManager.age.first()?.let { json.put("age", it) }
        json.put("activityLevel", preferencesManager.activityLevel.first())
        json.put("language", preferencesManager.language.first())
        json.put("themePreset", preferencesManager.themePreset.first())
        return json.toString(2)
    }

    private suspend fun extractZip(zipFile: File) = withContext(Dispatchers.IO) {
        val dbPath = context.getDatabasePath("fatloss_track.db")

        // Remove WAL / SHM side-car files
        File(dbPath.path + "-wal").delete()
        File(dbPath.path + "-shm").delete()

        var prefsJson: String? = null

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "fatloss_track.db" -> {
                        FileOutputStream(dbPath).use { out -> zis.copyTo(out) }
                    }
                    "preferences.json" -> {
                        prefsJson = zis.bufferedReader().readText()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (prefsJson != null) {
            importPreferences(JSONObject(prefsJson!!))
        }
    }

    private suspend fun importPreferences(json: JSONObject) {
        if (json.has("startWeight")) {
            val sw = json.getDouble("startWeight").toFloat()
            val gw = json.optDouble("goalWeight", sw.toDouble()).toFloat()
            val wr = json.optDouble("weeklyRate", 0.5).toFloat()
            val hc = if (json.has("heightCm")) json.getInt("heightCm") else null
            val sd = json.optString("startDate", "")
            if (sd.isNotBlank()) preferencesManager.setGoal(sw, gw, wr, "", hc, sd)
        }
        if (json.has("coachTone")) preferencesManager.setCoachTone(json.getString("coachTone"))
        if (json.has("sex")) preferencesManager.setSex(json.getString("sex"))
        if (json.has("age")) preferencesManager.setAge(json.getInt("age"))
        if (json.has("activityLevel")) preferencesManager.setActivityLevel(json.getString("activityLevel"))
        if (json.has("language")) preferencesManager.setLanguage(json.getString("language"))
        if (json.has("themePreset")) preferencesManager.setThemePreset(json.getString("themePreset"))
    }

    // ------------------------------------------------------------------ //
    //  Drive REST API helpers (via OkHttp)
    // ------------------------------------------------------------------ //

    private suspend fun upload(token: String, file: File) = withContext(Dispatchers.IO) {
        val metadata = JSONObject().apply {
            put("name", BACKUP_FILENAME)
            put("parents", JSONArray().put("appDataFolder"))
        }

        val boundary = "fatlosstrackboundary${System.currentTimeMillis()}"
        val baos = ByteArrayOutputStream()
        baos.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
        baos.write(metadata.toString().toByteArray())
        baos.write("\r\n--$boundary\r\nContent-Type: application/zip\r\n\r\n".toByteArray())
        FileInputStream(file).use { it.copyTo(baos) }
        baos.write("\r\n--$boundary--".toByteArray())

        val body = baos.toByteArray()
            .toRequestBody("multipart/related; boundary=$boundary".toMediaType())

        val req = Request.Builder()
            .url("$DRIVE_UPLOAD_URL?uploadType=multipart")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        val res = httpClient.newCall(req).execute()
        if (!res.isSuccessful) {
            throw IOException("Drive upload failed: ${res.code} ${res.body?.string()}")
        }
    }

    private suspend fun deleteRemoteBackups(token: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(
                "$DRIVE_FILES_URL?spaces=appDataFolder" +
                    "&q=name%3D%27$BACKUP_FILENAME%27&fields=files(id)",
            )
            .addHeader("Authorization", "Bearer $token")
            .get().build()

        val res = httpClient.newCall(req).execute()
        if (!res.isSuccessful) return@withContext

        val files = JSONObject(res.body?.string() ?: "{}").optJSONArray("files")
            ?: return@withContext

        for (i in 0 until files.length()) {
            val fileId = files.getJSONObject(i).getString("id")
            httpClient.newCall(
                Request.Builder()
                    .url("$DRIVE_FILES_URL/$fileId")
                    .addHeader("Authorization", "Bearer $token")
                    .delete().build(),
            ).execute().close()
        }
    }

    private suspend fun downloadLatest(token: String): File? = withContext(Dispatchers.IO) {
        val listReq = Request.Builder()
            .url(
                "$DRIVE_FILES_URL?spaces=appDataFolder" +
                    "&q=name%3D%27$BACKUP_FILENAME%27" +
                    "&fields=files(id)&orderBy=modifiedTime%20desc&pageSize=1",
            )
            .addHeader("Authorization", "Bearer $token")
            .get().build()

        val listRes = httpClient.newCall(listReq).execute()
        if (!listRes.isSuccessful) return@withContext null

        val files = JSONObject(listRes.body?.string() ?: "{}").optJSONArray("files")
        if (files == null || files.length() == 0) return@withContext null

        val fileId = files.getJSONObject(0).getString("id")

        val dlReq = Request.Builder()
            .url("$DRIVE_FILES_URL/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $token")
            .get().build()

        val dlRes = httpClient.newCall(dlReq).execute()
        if (!dlRes.isSuccessful) return@withContext null

        val zipFile = File(context.cacheDir, "restore_$BACKUP_FILENAME")
        FileOutputStream(zipFile).use { out ->
            dlRes.body?.byteStream()?.copyTo(out)
        }
        zipFile
    }

    // ------------------------------------------------------------------ //
    //  Process restart
    // ------------------------------------------------------------------ //

    private fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
