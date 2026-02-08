package com.fatlosstrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fatlosstrack.ui.theme.Accent
import com.fatlosstrack.ui.theme.AiBarBg

/**
 * Floating AI bar — persistent pill above bottom nav.
 * Text input + voice + camera.
 */
@Composable
fun AiBar(
    modifier: Modifier = Modifier,
    onSend: (String) -> Unit = {},
    onCameraClick: () -> Unit = {},
) {
    var text by remember { mutableStateOf("") }

    val pillShape = RoundedCornerShape(28.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(pillShape)
            .border(width = 1.dp, color = Accent.copy(alpha = 0.3f), shape = pillShape)
            .background(AiBarBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Ask anything...", style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.weight(1f),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AiBarBg,
                unfocusedContainerColor = AiBarBg,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        IconButton(onClick = { /* voice input placeholder */ }) {
            Icon(
                Icons.Default.KeyboardVoice,
                contentDescription = "Voice input",
                tint = Accent.copy(alpha = 0.7f),
            )
        }

        IconButton(onClick = onCameraClick) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = "Camera",
                tint = Accent.copy(alpha = 0.7f),
            )
        }
    }
}
