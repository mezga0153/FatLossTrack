package com.fatlosstrack.ui.settings

import androidx.compose.runtime.Stable
import com.fatlosstrack.auth.AuthManager
import com.fatlosstrack.data.backup.AutoBackupManager
import com.fatlosstrack.data.backup.DriveBackupManager
import com.fatlosstrack.data.health.HealthConnectManager
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLogDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    private val _autoBackupManager: AutoBackupManager,
    private val _dailyLogDao: DailyLogDao,
) {
    val authManager get() = _authManager
    val preferencesManager get() = _preferencesManager
    val healthConnectManager get() = _healthConnectManager
    val driveBackupManager get() = _driveBackupManager
    val autoBackupManager get() = _autoBackupManager

    /** Most recent lean body mass (kg) from Health Connect, or null if not synced. */
    val latestLeanMassKg: Flow<Double?> = _dailyLogDao.getAllLogs().map { logs ->
        logs.firstOrNull { it.leanBodyMassKg != null }?.leanBodyMassKg
    }
}
