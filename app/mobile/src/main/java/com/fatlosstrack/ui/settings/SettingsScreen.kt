package com.fatlosstrack.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.ui.theme.CardSurface
import com.fatlosstrack.ui.theme.Primary

/**
 * Settings screen — configuration & preferences.
 *
 * Sections:
 * 1. Profile summary (goal, rate)
 * 2. Coach tone preference (honest / supportive)
 * 3. Data sources (Health Connect)
 * 4. Backup (Google Drive)
 * 5. About / version
 */
@Composable
fun SettingsScreen() {
    var toneHonest by remember { mutableStateOf(true) }
    var healthConnectEnabled by remember { mutableStateOf(true) }
    var backupEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // -- Profile --
        SettingsSection("Profile") {
            SettingsRow("Goal weight", "80.0 kg")
            SettingsRow("Rate", "0.5 kg / week")
            SettingsRow("Daily deficit target", "~550 kcal")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { }) {
                Text("Edit goal", color = Primary)
            }
        }

        // -- Coach tone --
        SettingsSection("Coach tone") {
            Text(
                text = if (toneHonest) "Brutally honest" else "Supportive",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = if (toneHonest)
                    "No sugar-coating. Calls out your excuses."
                else
                    "Encouraging. Highlights wins before problems.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToneChip("Honest", toneHonest) { toneHonest = true }
                ToneChip("Supportive", !toneHonest) { toneHonest = false }
            }
        }

        // -- Data sources --
        SettingsSection("Data sources") {
            SwitchRow("Health Connect (weight, steps, sleep)", healthConnectEnabled) {
                healthConnectEnabled = it
            }
        }

        // -- Backup --
        SettingsSection("Backup") {
            SwitchRow("Google Drive auto-backup", backupEnabled) {
                backupEnabled = it
            }
            if (backupEnabled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Last backup: Never",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { }) {
                    Text("Back up now", color = Primary)
                }
            }
        }

        // -- About --
        SettingsSection("About") {
            SettingsRow("Version", "0.1.0-alpha")
            SettingsRow("Build", "UI prototype")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Licenses", style = MaterialTheme.typography.bodyLarge)
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(72.dp))
    }
}

// ---- Composable building blocks ----

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Primary),
        )
    }
}

@Composable
private fun ToneChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
