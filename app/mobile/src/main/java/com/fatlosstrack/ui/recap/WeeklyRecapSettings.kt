package com.fatlosstrack.ui.recap

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fatlosstrack.R
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.recap.WeeklyRecapNotifier
import com.fatlosstrack.data.recap.WeeklyRecapScheduler
import com.fatlosstrack.ui.theme.Primary
import com.fatlosstrack.ui.theme.Tertiary
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/**
 * Settings section body for the weekly recap: on/off, day and time.
 * Enabling asks for the notification permission (Android 13+); every
 * change reschedules the worker.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeeklyRecapSettings(
    preferencesManager: PreferencesManager,
    onViewRecap: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]

    val enabled by preferencesManager.weeklyRecapEnabled.collectAsState(initial = false)
    val day by preferencesManager.weeklyRecapDay.collectAsState(initial = 7)
    val minute by preferencesManager.weeklyRecapMinute.collectAsState(initial = 19 * 60)
    var canNotify by remember { mutableStateOf(WeeklyRecapNotifier.canPost(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    // Pick up changes made in system settings while the app was in the background
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canNotify = WeeklyRecapNotifier.canPost(context)
    }
    var showTimePicker by remember { mutableStateOf(false) }

    fun setEnabled(value: Boolean) {
        scope.launch {
            preferencesManager.setWeeklyRecapEnabled(value)
            WeeklyRecapScheduler.reschedule(context, preferencesManager)
        }
    }

    fun setSchedule(newDay: Int, newMinute: Int) {
        scope.launch {
            preferencesManager.setWeeklyRecapSchedule(newDay, newMinute)
            WeeklyRecapScheduler.reschedule(context, preferencesManager)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        canNotify = WeeklyRecapNotifier.canPost(context)
        permissionDenied = !granted
        // Only switch on once the user allowed notifications, otherwise it would never show
        if (granted) setEnabled(true)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.recap_settings_toggle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.recap_settings_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { checked ->
                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !WeeklyRecapNotifier.canPost(context)) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    canNotify = WeeklyRecapNotifier.canPost(context)
                    setEnabled(checked)
                }
            },
            colors = SwitchDefaults.colors(checkedTrackColor = Primary),
        )
    }

    // Permission denied or notifications blocked at system level — point to system settings
    if (!canNotify && (enabled || permissionDenied)) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.recap_settings_blocked),
            style = MaterialTheme.typography.bodySmall,
            color = Tertiary,
        )
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(stringResource(R.string.recap_settings_open_system), color = Primary)
        }
    }

    if (enabled) {
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DayOfWeek.entries.forEach { dow ->
                DayChip(
                    label = dow.getDisplayName(TextStyle.SHORT, locale),
                    selected = dow.value == day,
                    onClick = { setSchedule(dow.value, minute) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val timeLabel = remember(minute, locale) {
            LocalTime.of(minute / 60, minute % 60)
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { showTimePicker = true }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.recap_settings_time), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(timeLabel, style = MaterialTheme.typography.bodyLarge, color = Primary)
        }
    }

    if (onViewRecap != null) {
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onViewRecap) {
            Text(stringResource(R.string.recap_settings_view), color = Primary)
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = minute / 60,
            initialMinute = minute % 60,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    setSchedule(day, pickerState.hour * 60 + pickerState.minute)
                }) { Text(stringResource(R.string.button_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.button_cancel))
                }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
