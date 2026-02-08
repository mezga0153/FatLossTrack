package com.fatlosstrack.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fatlosstrack.ui.theme.*

/**
 * Bottom sheet chooser: "Log a meal" vs "Suggest a meal".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraModeSheet(
    onSelect: (CaptureMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "What do you want to do?",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
            )

            ModeOption(
                icon = Icons.Default.CameraAlt,
                title = "Log a meal",
                subtitle = "Take a photo of what you're eating",
                accentColor = Primary,
                onClick = { onSelect(CaptureMode.LogMeal) },
            )

            ModeOption(
                icon = Icons.Default.Lightbulb,
                title = "Suggest a meal",
                subtitle = "Snap your fridge or ingredients",
                accentColor = Secondary,
                onClick = { onSelect(CaptureMode.SuggestMeal) },
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
            )
        }
    }
}
