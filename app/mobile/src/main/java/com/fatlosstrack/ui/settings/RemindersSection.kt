package com.fatlosstrack.ui.settings

import android.Manifest
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.reminders.ReminderNotifier
import com.fatlosstrack.data.reminders.ReminderScheduler
import com.fatlosstrack.data.reminders.ReminderTiming
import com.fatlosstrack.data.reminders.ReminderType
import com.fatlosstrack.ui.theme.OnSurfaceVariant
import com.fatlosstrack.ui.theme.Primary
import java.time.format.DateTimeFormatter

/**
 * Settings section for the daily logging reminders: one toggle + time per
 * [ReminderType]. The first enable asks for POST_NOTIFICATIONS (Android 13+).
 */
@Composable
internal fun RemindersSection(
    preferencesManager: PreferencesManager,
    reminderScheduler: ReminderScheduler,
    reminderNotifier: ReminderNotifier,
) {
    val context = LocalContext.current
    // Reminder waiting for the permission dialog result
    var pendingEnable by remember { mutableStateOf<ReminderType?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val type = pendingEnable ?: return@rememberLauncherForActivityResult
        pendingEnable = null
        if (granted) {
            reminderScheduler.setEnabled(type, true)
        } else {
            Toast.makeText(context, R.string.reminders_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    SettingsSection(stringResource(R.string.settings_section_reminders)) {
        Text(
            stringResource(R.string.reminders_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        ReminderType.entries.forEach { type ->
            key(type) {
                ReminderRow(
                    type = type,
                    preferencesManager = preferencesManager,
                    onToggle = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !reminderNotifier.canNotify()) {
                            pendingEnable = type
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            reminderScheduler.setEnabled(type, enabled)
                        }
                    },
                    onTimePicked = { minutes -> reminderScheduler.setMinutes(type, minutes) },
                )
            }
        }
    }
}

@Composable
private fun ReminderRow(
    type: ReminderType,
    preferencesManager: PreferencesManager,
    onToggle: (Boolean) -> Unit,
    onTimePicked: (Int) -> Unit,
) {
    val context = LocalContext.current
    val enabled by remember(type) { preferencesManager.reminderEnabled(type.id) }.collectAsState(initial = false)
    val minutes by remember(type) { preferencesManager.reminderMinutes(type.id, type.defaultMinutes) }
        .collectAsState(initial = type.defaultMinutes)
    var showTimePicker by remember { mutableStateOf(false) }

    val (labelRes, descRes) = when (type) {
        ReminderType.MEAL_GAP -> R.string.reminder_meal_gap_label to R.string.reminder_meal_gap_desc
        ReminderType.WEIGH_IN -> R.string.reminder_weigh_in_label to R.string.reminder_weigh_in_desc
        ReminderType.EVENING_BUDGET -> R.string.reminder_budget_label to R.string.reminder_budget_desc
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(descRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { showTimePicker = true }) {
            Text(
                formatReminderTime(context, minutes),
                color = if (enabled) Primary else OnSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Primary),
        )
    }

    if (showTimePicker) {
        ReminderTimeDialog(
            initialMinutes = minutes,
            onConfirm = { picked ->
                showTimePicker = false
                onTimePicked(picked)
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminders_pick_time)) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) {
                Text(stringResource(R.string.button_ok), color = Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel), color = OnSurfaceVariant)
            }
        },
    )
}

/** "15:00" or "3:00 PM", following the device's 24-hour setting. */
private fun formatReminderTime(context: Context, minutes: Int): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return ReminderTiming.timeOf(minutes).format(DateTimeFormatter.ofPattern(pattern))
}
