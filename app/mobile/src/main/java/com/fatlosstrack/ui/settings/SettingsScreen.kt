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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fatlosstrack.auth.AuthManager
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.ui.theme.Accent
import com.fatlosstrack.ui.theme.CardSurface
import com.fatlosstrack.ui.theme.Primary
import com.fatlosstrack.ui.theme.Secondary
import com.fatlosstrack.ui.theme.Tertiary
import kotlinx.coroutines.launch

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
fun SettingsScreen(
    onEditGoal: () -> Unit = {},
    authManager: AuthManager? = null,
    preferencesManager: PreferencesManager? = null,
) {
    val scope = rememberCoroutineScope()
    val authState by authManager?.authState?.collectAsState()
        ?: remember { mutableStateOf(null) }

    // Goal data
    val savedStartWeight by preferencesManager?.startWeight?.collectAsState(initial = null)
        ?: remember { mutableStateOf(null) }
    val savedGoalWeight by preferencesManager?.goalWeight?.collectAsState(initial = null)
        ?: remember { mutableStateOf(null) }
    val savedRate by preferencesManager?.weeklyRate?.collectAsState(initial = 0.5f)
        ?: remember { mutableStateOf(0.5f) }
    val savedTone by preferencesManager?.coachTone?.collectAsState(initial = "honest")
        ?: remember { mutableStateOf("honest") }
    val savedHeight by preferencesManager?.heightCm?.collectAsState(initial = null)
        ?: remember { mutableStateOf(null) }
    val savedStartDate by preferencesManager?.startDate?.collectAsState(initial = null)
        ?: remember { mutableStateOf(null) }

    var toneHonest by remember { mutableStateOf(true) }
    LaunchedEffect(savedTone) { toneHonest = savedTone == "honest" }
    var healthConnectEnabled by remember { mutableStateOf(true) }
    var backupEnabled by remember { mutableStateOf(false) }

    // AI settings
    val storedApiKey by preferencesManager?.openAiApiKey?.collectAsState(initial = "")
        ?: remember { mutableStateOf("") }
    val storedModel by preferencesManager?.openAiModel?.collectAsState(initial = "gpt-5.2")
        ?: remember { mutableStateOf("gpt-5.2") }

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
            if (savedHeight != null) SettingsRow("Height", "$savedHeight cm")
            SettingsRow("Starting weight", savedStartWeight?.let { "%.1f kg".format(it) } ?: "Not set")
            SettingsRow("Goal weight", savedGoalWeight?.let { "%.1f kg".format(it) } ?: "Not set")
            val rateVal = savedRate ?: 0.5f
            SettingsRow("Rate", "$rateVal kg / week")
            SettingsRow("Daily deficit target", "~${(rateVal * 1100).toInt()} kcal")
            if (savedStartDate != null) SettingsRow("Start date", savedStartDate!!)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onEditGoal) {
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
                ToneChip("Honest", toneHonest) {
                    toneHonest = true
                    scope.launch { preferencesManager?.setCoachTone("honest") }
                }
                ToneChip("Supportive", !toneHonest) {
                    toneHonest = false
                    scope.launch { preferencesManager?.setCoachTone("supportive") }
                }
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

        // -- AI --
        SettingsSection("AI") {
            var apiKeyInput by remember(storedApiKey) { mutableStateOf(storedApiKey ?: "") }
            var showKey by remember { mutableStateOf(false) }
            var selectedModel by remember(storedModel) { mutableStateOf(storedModel ?: "gpt-5.2") }
            val isKeySaved = (storedApiKey ?: "").isNotBlank() && apiKeyInput == storedApiKey

            Text(
                text = "OpenAI API Key",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Used for meal analysis, chat, and coaching. Your key is stored on-device only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-...") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKey) "Hide" else "Show",
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    cursorColor = Primary,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isKeySaved) {
                    Text(
                        text = "✓ Key saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = Secondary,
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Button(
                    onClick = {
                        scope.launch {
                            preferencesManager?.setOpenAiApiKey(apiKeyInput.trim())
                        }
                    },
                    enabled = apiKeyInput.isNotBlank() && apiKeyInput != storedApiKey,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("Save key", color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Model",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("gpt-5.2", "gpt-5.2-codex", "gpt-5.2-pro").forEach { model ->
                    ToneChip(
                        label = model.removePrefix("gpt-"),
                        selected = selectedModel == model,
                    ) {
                        selectedModel = model
                        scope.launch { preferencesManager?.setOpenAiModel(model) }
                    }
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

        // -- Account --
        if (authManager != null) {
            val signedInState = authState as? AuthManager.AuthState.SignedIn
            SettingsSection("Account") {
                if (signedInState != null) {
                    SettingsRow("Signed in as", signedInState.user.email ?: "Google user")
                    SettingsRow("Name", signedInState.user.displayName ?: "—")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        authManager.signOut()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Tertiary,
                    ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out", color = Tertiary)
                }
            }
        }

        Spacer(Modifier.height(80.dp)) // clearance for floating AI bar
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
