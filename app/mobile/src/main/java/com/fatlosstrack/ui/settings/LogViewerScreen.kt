package com.fatlosstrack.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.fatlosstrack.R
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.ui.theme.*
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    appLogger: AppLogger,
    onBack: () -> Unit,
) {
    var logFiles by remember { mutableStateOf(appLogger.listLogFiles()) }
    var selectedFile by remember { mutableStateOf(logFiles.firstOrNull()?.file) }
    var logText by remember { mutableStateOf(selectedFile?.let { appLogger.read(it) } ?: appLogger.read()) }
    var sizeText by remember { mutableStateOf(formatSize(appLogger.sizeBytes(selectedFile))) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    val emptyLogText = stringResource(R.string.log_viewer_empty)
    val retentionText = stringResource(R.string.log_viewer_retention)

    fun refresh(selectionName: String? = selectedFile?.name) {
        val latest = appLogger.listLogFiles()
        logFiles = latest
        selectedFile = latest.firstOrNull { it.file.name == selectionName }?.file ?: latest.firstOrNull()?.file
        logText = selectedFile?.let { appLogger.read(it) } ?: emptyLogText
        sizeText = formatSize(appLogger.sizeBytes(selectedFile))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.log_viewer_title), color = OnSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = OnSurface)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        refresh()
                    }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.cd_refresh), tint = OnSurfaceVariant)
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(logText))
                    }) {
                        Icon(Icons.Default.ContentCopy, stringResource(R.string.cd_copy), tint = OnSurfaceVariant)
                    }
                    IconButton(onClick = {
                        try {
                            val logFile = selectedFile
                            if (logFile != null && logFile.exists()) {
                                val shareDir = File(context.cacheDir, "shared_logs")
                                shareDir.mkdirs()
                                val shareFile = File(shareDir, logFile.name)
                                logFile.copyTo(shareFile, overwrite = true)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    shareFile,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        } catch (e: Exception) {
                            AppLogger.instance?.error("LogViewer", "Share failed", e)
                        }
                    }) {
                        Icon(Icons.Default.Share, stringResource(R.string.cd_share), tint = OnSurfaceVariant)
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteForever, stringResource(R.string.cd_clear), tint = Tertiary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
            )
        },
        containerColor = Surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.log_viewer_files_heading),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurface,
                )
                if (logFiles.isEmpty()) {
                    Text(
                        stringResource(R.string.log_viewer_no_files),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        logFiles.forEach { info ->
                            val label = formatLogLabel(info)
                            FilterChip(
                                selected = info.file == selectedFile,
                                onClick = {
                                    selectedFile = info.file
                                    logText = appLogger.read(info.file)
                                    sizeText = formatSize(appLogger.sizeBytes(info.file))
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.log_viewer_size, sizeText),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.log_viewer_newest_bottom),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                    )
                }
                Text(
                    retentionText,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                )
            }

            // Log content (monospace, scrollable both ways)
            val vScroll = rememberScrollState(Int.MAX_VALUE) // start at bottom
            val hScroll = rememberScrollState()

            // Scroll to bottom on first load
            LaunchedEffect(logText) {
                vScroll.animateScrollTo(vScroll.maxValue)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardSurface)
                    .padding(8.dp),
            ) {
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    ),
                    color = OnSurface,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll),
                )
            }
        }
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_log_title), color = OnSurface) },
            text = { Text(stringResource(R.string.dialog_clear_log_text), color = OnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    appLogger.clear()
                            refresh(null)
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.dialog_clear_confirm), color = Tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.button_cancel), color = OnSurfaceVariant)
                }
            },
            containerColor = CardSurface,
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatLogLabel(info: AppLogger.LogFileInfo): String {
    val today = LocalDate.now()
    val date = info.date
    return when {
        date == null -> info.file.nameWithoutExtension
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        else -> date.toString()
    }
}
