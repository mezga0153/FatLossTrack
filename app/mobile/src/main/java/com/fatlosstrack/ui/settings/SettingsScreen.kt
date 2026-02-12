package com.fatlosstrack.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.auth.AuthManager
import com.fatlosstrack.data.backup.DriveBackupManager
import com.fatlosstrack.data.health.HealthConnectManager
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.ui.theme.CardSurface
import com.fatlosstrack.ui.theme.OnSurfaceVariant
import com.fatlosstrack.ui.theme.Primary
import com.fatlosstrack.ui.theme.Secondary
import com.fatlosstrack.ui.theme.SurfaceVariant
import com.fatlosstrack.ui.theme.Tertiary
import com.fatlosstrack.ui.theme.ThemeMode
import com.fatlosstrack.ui.theme.ThemePreset
import com.fatlosstrack.ui.theme.buildAppColors
import com.fatlosstrack.domain.TdeeCalculator
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    state: SettingsStateHolder,
    onEditGoal: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onSyncHealthConnect: (() -> Unit)? = null,
    onViewLog: (() -> Unit)? = null,
    onViewAiUsage: (() -> Unit)? = null,
    onViewModelSelector: (() -> Unit)? = null,
    onViewWelcome: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val authManager = state.authManager
    val preferencesManager = state.preferencesManager
    val healthConnectManager = state.healthConnectManager
    val authState by authManager.authState.collectAsState()

    // Goal data
    val savedStartWeight by preferencesManager.startWeight.collectAsState(initial = null)
    val savedGoalWeight by preferencesManager.goalWeight.collectAsState(initial = null)
    val savedRate by preferencesManager.weeklyRate.collectAsState(initial = 0.5f)
    val savedTone by preferencesManager.coachTone.collectAsState(initial = "honest")
    val savedHeight by preferencesManager.heightCm.collectAsState(initial = null)
    val savedStartDate by preferencesManager.startDate.collectAsState(initial = null)
    val savedSex by preferencesManager.sex.collectAsState(initial = null)
    val savedAge by preferencesManager.age.collectAsState(initial = null)
    val savedActivityLevel by preferencesManager.activityLevel.collectAsState(initial = "light")

    var toneHonest by remember { mutableStateOf(true) }
    LaunchedEffect(savedTone) { toneHonest = savedTone == "honest" }

    // Health Connect state
    val hcAvailable = healthConnectManager.isAvailable()
    var hcPermGranted by remember { mutableStateOf(false) }
    var hcSyncing by remember { mutableStateOf(false) }
    var hcLastSyncMsg by remember { mutableStateOf<String?>(null) }

    // Check permissions on launch
    LaunchedEffect(hcAvailable) {
        if (hcAvailable) {
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

    // Backup/restore state
    val driveBackupManager = state.driveBackupManager
    val backupState by driveBackupManager.state.collectAsState()
    val lastBackupTime by preferencesManager.lastBackupTime.collectAsState(initial = null)
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showLocalRestoreDialog by remember { mutableStateOf(false) }
    var pendingLocalRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingDriveAction by remember { mutableStateOf<DriveBackupManager.PendingAction?>(null) }

    // SAF launcher: export zip to device
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            driveBackupManager.resetState()
            scope.launch { driveBackupManager.localBackup(uri) }
        }
    }

    // SAF launcher: import zip from device
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingLocalRestoreUri = uri
            showLocalRestoreDialog = true
        }
    }

    // Launcher for Drive consent screen (if user hasn't yet granted appdata scope)
    val driveConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val action = pendingDriveAction
            pendingDriveAction = null
            scope.launch {
                when (action) {
                    DriveBackupManager.PendingAction.BACKUP -> driveBackupManager.backup()
                    DriveBackupManager.PendingAction.RESTORE -> driveBackupManager.restore()
                    null -> {}
                }
            }
        } else {
            driveBackupManager.resetState()
        }
    }

    // Google Sign-In launcher (for signing in from Settings)
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        scope.launch {
            authManager.handleSignInResult(result.data)
        }
    }

    // Auto-launch consent intent when BackupState.NeedsConsent is emitted
    LaunchedEffect(backupState) {
        val consent = backupState as? DriveBackupManager.BackupState.NeedsConsent ?: return@LaunchedEffect
        pendingDriveAction = consent.action
        driveConsentLauncher.launch(consent.consentIntent)
    }

    // AI settings
    val storedApiKey by preferencesManager.openAiApiKey.collectAsState(initial = "")
    val storedModel by preferencesManager.openAiModel.collectAsState(initial = "gpt-5-mini")

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = statusBarTop + 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // -- User Profile --
        SettingsSection(stringResource(R.string.settings_section_user_profile)) {
            SettingsRow(stringResource(R.string.settings_height), savedHeight?.let { stringResource(R.string.format_height_cm, it) } ?: stringResource(R.string.settings_not_set))
            SettingsRow(stringResource(R.string.settings_age), savedAge?.toString() ?: stringResource(R.string.settings_not_set))
            SettingsRow(stringResource(R.string.settings_sex), savedSex?.let { sex ->
                when (sex) {
                    "male" -> stringResource(R.string.sex_male)
                    "female" -> stringResource(R.string.sex_female)
                    else -> stringResource(R.string.sex_yes)
                }
            } ?: stringResource(R.string.settings_not_set))
            SettingsRow(stringResource(R.string.settings_activity_level), when (savedActivityLevel) {
                "sedentary" -> stringResource(R.string.activity_sedentary)
                "light" -> stringResource(R.string.activity_light)
                "moderate" -> stringResource(R.string.activity_moderate)
                "active" -> stringResource(R.string.activity_active)
                else -> stringResource(R.string.activity_light)
            })
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onEditProfile) {
                Text(stringResource(R.string.settings_edit_profile), color = Primary)
            }
        }

        // -- Goal --
        SettingsSection(stringResource(R.string.settings_section_goal)) {
            SettingsRow(stringResource(R.string.settings_start_weight), savedStartWeight?.let { "%.1f kg".format(it) } ?: stringResource(R.string.settings_not_set))
            SettingsRow(stringResource(R.string.settings_goal_weight), savedGoalWeight?.let { "%.1f kg".format(it) } ?: stringResource(R.string.settings_not_set))
            val rateVal = savedRate ?: 0.5f
            SettingsRow(stringResource(R.string.settings_rate), stringResource(R.string.settings_rate_value, rateVal.toString()))
            SettingsRow(stringResource(R.string.settings_daily_deficit), stringResource(R.string.settings_deficit_value, (rateVal * 1100).toInt()))

            // TDEE and daily target
            val weightForTdee = savedStartWeight
            val heightForTdee = savedHeight
            val ageForTdee = savedAge
            val sexForTdee = savedSex
            if (weightForTdee != null && heightForTdee != null && ageForTdee != null && sexForTdee != null) {
                val tdeeVal = TdeeCalculator.tdee(weightForTdee, heightForTdee, ageForTdee, sexForTdee, savedActivityLevel)
                val dailyTarget = TdeeCalculator.dailyTarget(weightForTdee, heightForTdee, ageForTdee, sexForTdee, savedActivityLevel, rateVal)
                SettingsRow(stringResource(R.string.settings_tdee), stringResource(R.string.settings_tdee_value, tdeeVal))
                SettingsRow(stringResource(R.string.settings_daily_target), stringResource(R.string.settings_daily_target_value, dailyTarget))
            } else {
                SettingsRow(stringResource(R.string.settings_tdee), stringResource(R.string.settings_tdee_incomplete))
            }

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
                    scope.launch { preferencesManager.setCoachTone("honest") }
                }
                ToneChip(stringResource(R.string.tone_supportive), !toneHonest) {
                    toneHonest = false
                    scope.launch { preferencesManager.setCoachTone("supportive") }
                }
            }
        }

        // -- Language --
        val savedLanguage by preferencesManager.language.collectAsState(initial = "en")

        SettingsSection(stringResource(R.string.settings_section_language)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToneChip(stringResource(R.string.language_english), savedLanguage == "en") {
                    scope.launch {
                        preferencesManager.setLanguage("en")
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags("en")
                        )
                    }
                }
                ToneChip(stringResource(R.string.language_slovene), savedLanguage == "sl") {
                    scope.launch {
                        preferencesManager.setLanguage("sl")
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags("sl")
                        )
                    }
                }
                ToneChip(stringResource(R.string.language_hungarian), savedLanguage == "hu") {
                    scope.launch {
                        preferencesManager.setLanguage("hu")
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags("hu")
                        )
                    }
                }
            }
        }

        // -- Theme --
        val savedTheme by preferencesManager.themePreset.collectAsState(initial = "PURPLE_DARK")
        val currentPreset = try { ThemePreset.valueOf(savedTheme) } catch (_: Exception) { ThemePreset.PURPLE_DARK }
        val currentMode = currentPreset.mode

        SettingsSection(stringResource(R.string.settings_section_theme)) {
            // Dark / Light toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToneChip(stringResource(R.string.theme_dark), currentMode == ThemeMode.DARK) {
                    val newPreset = ThemePreset.entries.first {
                        it.accentHue == currentPreset.accentHue && it.mode == ThemeMode.DARK
                    }
                    scope.launch { preferencesManager.setThemePreset(newPreset.name) }
                }
                ToneChip(stringResource(R.string.theme_light), currentMode == ThemeMode.LIGHT) {
                    val newPreset = ThemePreset.entries.first {
                        it.accentHue == currentPreset.accentHue && it.mode == ThemeMode.LIGHT
                    }
                    scope.launch { preferencesManager.setThemePreset(newPreset.name) }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Color swatches
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val colorGroups = ThemePreset.entries.groupBy { it.accentHue }
                colorGroups.forEach { (hue, presets) ->
                    val selected = currentPreset.accentHue == hue
                    val previewColor = buildAppColors(hue, currentMode).primary
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .then(
                                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable {
                                val target = presets.first { it.mode == currentMode }
                                scope.launch { preferencesManager.setThemePreset(target.name) }
                            },
                    )
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

        // -- Account & Backup (merged) --
        val signedInState = authState as? AuthManager.AuthState.SignedIn
        SettingsSection(stringResource(R.string.settings_section_account)) {
            if (signedInState != null) {
                SettingsRow(stringResource(R.string.account_signed_in_as), signedInState.user.email ?: stringResource(R.string.account_google_user))
                SettingsRow(stringResource(R.string.account_name), signedInState.user.displayName ?: "—")

                // ---- Google Drive Backup ----
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.backup_google_drive),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))

                // Last backup info
                val formattedBackupTime = remember(lastBackupTime) {
                    if (lastBackupTime != null) {
                        try {
                            val instant = Instant.parse(lastBackupTime)
                            val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                                .withZone(ZoneId.systemDefault())
                            formatter.format(instant)
                        } catch (_: Exception) {
                            null
                        }
                    } else null
                }
                val backupText = if (formattedBackupTime != null) {
                    stringResource(R.string.backup_last_format, formattedBackupTime)
                } else {
                    stringResource(R.string.backup_last_never)
                }
                Text(
                    text = backupText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                // Progress / status
                val isWorking = backupState is DriveBackupManager.BackupState.InProgress
                when (val bs = backupState) {
                    is DriveBackupManager.BackupState.InProgress -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Primary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.backup_in_progress),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is DriveBackupManager.BackupState.Done -> {
                        Text(bs.message, style = MaterialTheme.typography.bodySmall, color = Secondary)
                    }
                    is DriveBackupManager.BackupState.Error -> {
                        Text(bs.message, style = MaterialTheme.typography.bodySmall, color = Tertiary)
                    }
                    else -> {}
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            driveBackupManager.resetState()
                            scope.launch { driveBackupManager.backup() }
                        },
                        enabled = !isWorking,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text(stringResource(R.string.backup_now_button), color = MaterialTheme.colorScheme.onPrimary)
                    }
                    OutlinedButton(
                        onClick = { showRestoreDialog = true },
                        enabled = !isWorking,
                    ) {
                        Text(stringResource(R.string.backup_restore_button), color = Primary)
                    }
                }
            } else {
                // Not signed in — offer sign-in for Drive backup
                Text(
                    text = stringResource(R.string.account_sign_in_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        signInLauncher.launch(authManager.getSignInIntent())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text(stringResource(R.string.account_sign_in), color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            // ---- Local device backup ----
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.backup_local),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))

            // Status messages for local backup too
            if (signedInState == null) {
                val isLocalWorking = backupState is DriveBackupManager.BackupState.InProgress
                when (val bs = backupState) {
                    is DriveBackupManager.BackupState.InProgress -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Primary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.backup_in_progress),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    is DriveBackupManager.BackupState.Done -> {
                        Text(bs.message, style = MaterialTheme.typography.bodySmall, color = Secondary)
                        Spacer(Modifier.height(8.dp))
                    }
                    is DriveBackupManager.BackupState.Error -> {
                        Text(bs.message, style = MaterialTheme.typography.bodySmall, color = Tertiary)
                        Spacer(Modifier.height(8.dp))
                    }
                    else -> {}
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isWorking2 = backupState is DriveBackupManager.BackupState.InProgress
                OutlinedButton(
                    onClick = {
                        exportLauncher.launch("fatloss_track_backup.zip")
                    },
                    enabled = !isWorking2,
                ) {
                    Text(stringResource(R.string.backup_save_to_device), color = Primary)
                }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    enabled = !isWorking2,
                ) {
                    Text(stringResource(R.string.backup_load_from_device), color = Primary)
                }
            }

            if (signedInState != null) {
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

        // Restore confirmation dialog
        if (showRestoreDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreDialog = false },
                title = { Text(stringResource(R.string.restore_confirm_title)) },
                text = { Text(stringResource(R.string.restore_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreDialog = false
                        driveBackupManager.resetState()
                        scope.launch { driveBackupManager.restore() }
                    }) {
                        Text(stringResource(R.string.restore_confirm_yes), color = Tertiary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreDialog = false }) {
                        Text(stringResource(R.string.restore_confirm_no))
                    }
                },
            )
        }

        // Local restore confirmation dialog
        if (showLocalRestoreDialog) {
            AlertDialog(
                onDismissRequest = { showLocalRestoreDialog = false },
                title = { Text(stringResource(R.string.restore_confirm_title)) },
                text = { Text(stringResource(R.string.restore_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showLocalRestoreDialog = false
                        val uri = pendingLocalRestoreUri ?: return@TextButton
                        pendingLocalRestoreUri = null
                        driveBackupManager.resetState()
                        scope.launch { driveBackupManager.localRestore(uri) }
                    }) {
                        Text(stringResource(R.string.restore_confirm_yes), color = Tertiary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showLocalRestoreDialog = false
                        pendingLocalRestoreUri = null
                    }) {
                        Text(stringResource(R.string.restore_confirm_no))
                    }
                },
            )
        }

        // -- AI --
        SettingsSection(stringResource(R.string.settings_section_ai)) {
            var apiKeyInput by remember(storedApiKey) { mutableStateOf(storedApiKey) }
            var showKey by remember { mutableStateOf(false) }
            val isKeySaved = storedApiKey.isNotBlank() && apiKeyInput == storedApiKey

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
                            preferencesManager.setOpenAiApiKey(apiKeyInput.trim())
                        }
                    },
                    enabled = apiKeyInput.isNotBlank() && apiKeyInput != storedApiKey,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text(stringResource(R.string.ai_save_key), color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Spacer(Modifier.height(16.dp))
            if (onViewModelSelector != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewModelSelector)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            stringResource(R.string.ai_model_label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            modelDisplayName(storedModel),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (onViewAiUsage != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewAiUsage)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.ai_usage_button),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // -- About --
        SettingsSection(stringResource(R.string.settings_section_about)) {
            SettingsRow(stringResource(R.string.about_version), stringResource(R.string.about_version_value))
            SettingsRow(stringResource(R.string.about_build), stringResource(R.string.about_build_value))
            Spacer(Modifier.height(8.dp))
            if (onViewWelcome != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onViewWelcome)
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.about_how_it_works), style = MaterialTheme.typography.bodyLarge)
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        context.startActivity(
                            Intent(context, OssLicensesMenuActivity::class.java)
                        )
                    }
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
