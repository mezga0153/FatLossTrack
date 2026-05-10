package com.fatlosstrack.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.backup.AutoBackupManager
import com.fatlosstrack.data.backup.BackupInfo
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupManagerScreen(
    autoBackupManager: AutoBackupManager,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var backups by remember { mutableStateOf(autoBackupManager.listBackups()) }
    var showRestoreConfirm by remember { mutableStateOf<BackupInfo?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<BackupInfo?>(null) }
    var showImportConfirm by remember { mutableStateOf<android.net.Uri?>(null) }
    var isRestoring by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    val timeFormat = remember { DateTimeFormatter.ofPattern("MMM d, yyyy  HH:mm").withZone(ZoneId.systemDefault()) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            showImportConfirm = uri
        }
    }

    fun refresh() {
        backups = autoBackupManager.listBackups()
    }

    fun doRestore(block: suspend () -> Result<Unit>) {
        isRestoring = true
        statusMessage = null
        scope.launch {
            val result = block()
            isRestoring = false
            if (result.isSuccess) {
                statusMessage = "Restore complete. Restarting…"
                statusIsError = false
                // Restart the app so Room/DataStore reopen with new data
                kotlinx.coroutines.delay(800)
                val activity = context as? Activity
                val intent = activity?.intent
                activity?.finish()
                if (intent != null) context.startActivity(intent)
            } else {
                statusMessage = "Restore failed: ${result.exceptionOrNull()?.message}"
                statusIsError = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    // Import from file picker
                    IconButton(onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }) {
                        Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.backup_import))
                    }
                    // Create backup now
                    IconButton(onClick = {
                        scope.launch {
                            autoBackupManager.runBackup()
                            refresh()
                            statusMessage = "Backup created"
                            statusIsError = false
                        }
                    }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = stringResource(R.string.backup_now_button))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = OnSurface,
                ),
            )
        },
        containerColor = Surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Status banner
            if (isRestoring) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Primary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.restore_in_progress),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurface,
                        )
                    }
                }
            }
            if (statusMessage != null && !isRestoring) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (statusIsError) Tertiary.copy(alpha = 0.15f) else Secondary.copy(alpha = 0.15f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        statusMessage!!,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (statusIsError) Tertiary else Secondary,
                    )
                }
            }

            // Info text
            Text(
                stringResource(R.string.backup_manager_info),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (backups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.backup_last_never),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(backups, key = { it.name }) { backup ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardSurface),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        backup.date,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = OnSurface,
                                    )
                                    Text(
                                        "${backup.sizeKb} KB  •  ${timeFormat.format(backup.lastModified)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariant,
                                    )
                                }
                                // Restore
                                IconButton(onClick = { showRestoreConfirm = backup }) {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = stringResource(R.string.backup_restore_button),
                                        tint = Primary,
                                    )
                                }
                                // Delete
                                IconButton(onClick = { showDeleteConfirm = backup }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.cd_delete),
                                        tint = Tertiary.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Restore confirmation dialog ──
    if (showRestoreConfirm != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val file = showRestoreConfirm!!.file
                    showRestoreConfirm = null
                    doRestore { autoBackupManager.restoreFromFile(file) }
                }) {
                    Text(stringResource(R.string.restore_confirm_yes), color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = null }) {
                    Text(stringResource(R.string.restore_confirm_no))
                }
            },
        )
    }

    // ── Delete confirmation dialog ──
    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.backup_delete_title)) },
            text = { Text(stringResource(R.string.backup_delete_message, showDeleteConfirm!!.date)) },
            confirmButton = {
                TextButton(onClick = {
                    autoBackupManager.deleteBackup(showDeleteConfirm!!.file)
                    showDeleteConfirm = null
                    refresh()
                }) {
                    Text(stringResource(R.string.cd_delete), color = Tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(stringResource(R.string.restore_confirm_no))
                }
            },
        )
    }

    // ── Import confirmation dialog ──
    if (showImportConfirm != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val uri = showImportConfirm!!
                    showImportConfirm = null
                    doRestore { autoBackupManager.restoreFromUri(uri) }
                }) {
                    Text(stringResource(R.string.restore_confirm_yes), color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = null }) {
                    Text(stringResource(R.string.restore_confirm_no))
                }
            },
        )
    }
}
