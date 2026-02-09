package com.fatlosstrack.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.auth.AuthManager
import com.fatlosstrack.data.health.HealthConnectManager
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.ui.theme.Accent
import com.fatlosstrack.ui.theme.CardSurface
import com.fatlosstrack.ui.theme.Primary
import com.fatlosstrack.ui.theme.Secondary
import com.fatlosstrack.ui.theme.Tertiary
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
    healthConnectManager: HealthConnectManager? = null,
    onSyncHealthConnect: (() -> Unit)? = null,
    onViewLog: (() -> Unit)? = null,
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

    // Health Connect state
    val hcAvailable = healthConnectManager?.isAvailable() ?: false
    var hcPermGranted by remember { mutableStateOf(false) }
    var hcSyncing by remember { mutableStateOf(false) }
    var hcLastSyncMsg by remember { mutableStateOf<String?>(null) }

    // Check permissions on launch
    LaunchedEffect(hcAvailable) {
        if (hcAvailable && healthConnectManager != null) {
            hcPermGranted = healthConnectManager.hasAllPermissions()
        }
    }

    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        hcPermGranted = granted.containsAll(HealthConnectManager.PERMISSIONS)
        if (hcPermGranted) {
            hcLastSyncMsg = "Permissions granted — tap Sync now"
        }
    }

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
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // -- Profile --
        SettingsSection(stringResource(R.string.settings_section_profile)) {
            if (savedHeight != null) SettingsRow(stringResource(R.string.settings_height), stringResource(R.string.format_height_cm, savedHeight!!))
            SettingsRow(stringResource(R.string.settings_start_weight), savedStartWeight?.let { "%.1f kg".format(it) } ?: stringResource(R.string.settings_not_set))
            SettingsRow(stringResource(R.string.settings_goal_weight), savedGoalWeight?.let { "%.1f kg".format(it) } ?: stringResource(R.string.settings_not_set))
            val rateVal = savedRate ?: 0.5f
            SettingsRow(stringResource(R.string.settings_rate), stringResource(R.string.settings_rate_value, rateVal.toString()))
            SettingsRow(stringResource(R.string.settings_daily_deficit), stringResource(R.string.settings_deficit_value, (rateVal * 1100).toInt()))
            if (savedStartDate != null) SettingsRow(stringResource(R.string.settings_start_date), savedStartDate!!)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onEditGoal) {
                Text(stringResource(R.string.settings_edit_goal), color = Primary)
            }
        }

        // -- Coach tone --
        SettingsSection(stringResource(R.string.settings_section_coach_tone)) {
            Text(
                text = if (toneHonest) "Brutally honest" else stringResource(R.string.tone_supportive),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = if (toneHonest)
                    stringResource(R.string.tone_honest_desc)
                else
                    stringResource(R.string.tone_supportive_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToneChip(stringResource(R.string.tone_honest), toneHonest) {
                    toneHonest = true
                    scope.launch { preferencesManager?.setCoachTone("honest") }
                }
                ToneChip(stringResource(R.string.tone_supportive), !toneHonest) {
                    toneHonest = false
                    scope.launch { preferencesManager?.setCoachTone("supportive") }
                }
            }
        }

        // -- Language --
        val savedLanguage by preferencesManager?.language?.collectAsState(initial = "en")
            ?: remember { mutableStateOf("en") }

        SettingsSection(stringResource(R.string.settings_section_language)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToneChip(stringResource(R.string.language_english), savedLanguage == "en") {
                    scope.launch {
                        preferencesManager?.setLanguage("en")
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags("en")
                        )
                    }
                }
                ToneChip(stringResource(R.string.language_slovene), savedLanguage == "sl") {
                    scope.launch {
                        preferencesManager?.setLanguage("sl")
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags("sl")
                        )
                    }
                }
            }
        }

        // -- Data sources --
        SettingsSection(stringResource(R.string.settings_section_hc)) {
            if (!hcAvailable) {
                Text(
                    stringResource(R.string.hc_not_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.hc_install_prompt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Permission status
                SettingsRow(
                    stringResource(R.string.hc_status_label),
                    if (hcPermGranted) stringResource(R.string.hc_connected) else stringResource(R.string.hc_not_connected),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.hc_reads_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                if (!hcPermGranted) {
                    Button(
                        onClick = {
                            permissionLauncher.launch(HealthConnectManager.PERMISSIONS)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text(stringResource(R.string.hc_grant_permissions), color = MaterialTheme.colorScheme.onPrimary)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                hcSyncing = true
                                onSyncHealthConnect?.invoke()
                                // The caller will handle the sync and we'll get a message back
                                scope.launch {
                                    kotlinx.coroutines.delay(500)
                                    hcSyncing = false
                                    hcLastSyncMsg = "Sync started"
                                }
                            },
                            enabled = !hcSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) {
                            if (hcSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.hc_sync_now), color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }

                if (hcLastSyncMsg != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        hcLastSyncMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = Secondary,
                    )
                }
            }
        }

        // -- Backup --
        SettingsSection(stringResource(R.string.settings_section_backup)) {
            SwitchRow(stringResource(R.string.backup_google_drive), backupEnabled) {
                backupEnabled = it
            }
            if (backupEnabled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.backup_last_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { }) {
                    Text(stringResource(R.string.backup_now_button), color = Primary)
                }
            }
        }

        // -- AI --
        SettingsSection(stringResource(R.string.settings_section_ai)) {
            var apiKeyInput by remember(storedApiKey) { mutableStateOf(storedApiKey ?: "") }
            var showKey by remember { mutableStateOf(false) }
            var selectedModel by remember(storedModel) { mutableStateOf(storedModel ?: "gpt-5.2") }
            val isKeySaved = (storedApiKey ?: "").isNotBlank() && apiKeyInput == storedApiKey

            Text(
                text = stringResource(R.string.ai_api_key_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ai_api_key_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.ai_api_key_placeholder)) },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showKey) stringResource(R.string.cd_hide) else stringResource(R.string.cd_show),
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
                        text = stringResource(R.string.ai_key_saved),
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
                    Text(stringResource(R.string.ai_save_key), color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.ai_model_label),
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
        SettingsSection(stringResource(R.string.settings_section_about)) {
            SettingsRow(stringResource(R.string.about_version), stringResource(R.string.about_version_value))
            SettingsRow(stringResource(R.string.about_build), stringResource(R.string.about_build_value))
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
                Text(stringResource(R.string.about_licenses), style = MaterialTheme.typography.bodyLarge)
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
            SettingsSection(stringResource(R.string.settings_section_account)) {
                if (signedInState != null) {
                    SettingsRow(stringResource(R.string.account_signed_in_as), signedInState.user.email ?: stringResource(R.string.account_google_user))
                    SettingsRow(stringResource(R.string.account_name), signedInState.user.displayName ?: "—")
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
                    Text(stringResource(R.string.account_sign_out), color = Tertiary)
                }
            }
        }

        // -- Activity Log --
        if (onViewLog != null) {
            SettingsSection(stringResource(R.string.settings_section_debug)) {
                Text(
                    stringResource(R.string.debug_log_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onViewLog) {
                    Text(stringResource(R.string.debug_view_log), color = Primary)
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
