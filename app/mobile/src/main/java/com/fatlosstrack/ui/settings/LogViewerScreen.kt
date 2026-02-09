package com.fatlosstrack.ui.settings

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    appLogger: AppLogger,
    onBack: () -> Unit,
) {
    var logText by remember { mutableStateOf(appLogger.readAll()) }
    var sizeText by remember { mutableStateOf(formatSize(appLogger.sizeBytes())) }
    val clipboard = LocalClipboardManager.current
    var showClearDialog by remember { mutableStateOf(false) }
    val emptyLogText = stringResource(R.string.log_viewer_empty)

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
                        logText = appLogger.readAll()
                        sizeText = formatSize(appLogger.sizeBytes())
                    }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.cd_refresh), tint = OnSurfaceVariant)
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(logText))
                    }) {
                        Icon(Icons.Default.ContentCopy, stringResource(R.string.cd_copy), tint = OnSurfaceVariant)
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
            // Size indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    logText = emptyLogText
                    sizeText = formatSize(0)
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
