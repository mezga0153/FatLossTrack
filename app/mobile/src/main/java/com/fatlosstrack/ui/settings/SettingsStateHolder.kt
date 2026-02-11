package com.fatlosstrack.ui.settings

import androidx.compose.runtime.Stable
import com.fatlosstrack.auth.AuthManager
import com.fatlosstrack.data.backup.DriveBackupManager
import com.fatlosstrack.data.health.HealthConnectManager
import com.fatlosstrack.data.local.PreferencesManager
import javax.inject.Inject

/**
 * State holder for [SettingsScreen].
 * Proxies auth, preferences, health-connect, and backup managers
 * so the composable signature stays slim.
 */
@Stable
class SettingsStateHolder @Inject constructor(
    private val _authManager: AuthManager,
    private val _preferencesManager: PreferencesManager,
    private val _healthConnectManager: HealthConnectManager,
    private val _driveBackupManager: DriveBackupManager,
) {
    val authManager get() = _authManager
    val preferencesManager get() = _preferencesManager
    val healthConnectManager get() = _healthConnectManager
    val driveBackupManager get() = _driveBackupManager
}
