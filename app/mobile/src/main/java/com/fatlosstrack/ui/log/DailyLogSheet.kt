package com.fatlosstrack.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.ui.theme.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ══════════════════════════════════════════════════
// ── Daily log edit sheet ──
// ══════════════════════════════════════════════════

@Composable
internal fun DailyLogEditSheet(
    date: LocalDate,
    existingLog: DailyLog?,
    onSave: (DailyLog) -> Unit,
    onDismiss: () -> Unit,
) {
    var weightStr by remember { mutableStateOf(existingLog?.weightKg?.let { String.format(Locale.US, "%.1f", it) } ?: "") }
    var stepsStr by remember { mutableStateOf(existingLog?.steps?.toString() ?: "") }
    var sleepStr by remember { mutableStateOf(existingLog?.sleepHours?.let { String.format(Locale.US, "%.1f", it) } ?: "") }
    var hrStr by remember { mutableStateOf(existingLog?.restingHr?.toString() ?: "") }
    var notes by remember { mutableStateOf(existingLog?.notes ?: "") }

    val exercises = remember { mutableStateListOf<ExerciseItem>() }
    LaunchedEffect(existingLog) { exercises.clear(); exercises.addAll(parseExercises(existingLog?.exercisesJson)) }
    var newExName by remember { mutableStateOf("") }
    var newExDuration by remember { mutableStateOf("") }
    var newExKcal by remember { mutableStateOf("") }

    val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(date.format(dateFmt), style = MaterialTheme.typography.titleMedium, color = OnSurface)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = OnSurfaceVariant) }
        }

        EditField(icon = Icons.Default.Scale, label = stringResource(R.string.field_weight_kg), value = weightStr, onValueChange = { weightStr = it }, keyboardType = KeyboardType.Decimal)
        EditField(icon = Icons.AutoMirrored.Filled.DirectionsWalk, label = stringResource(R.string.field_steps), value = stepsStr, onValueChange = { stepsStr = it }, keyboardType = KeyboardType.Number)
        EditField(icon = Icons.Default.Bedtime, label = stringResource(R.string.field_sleep_hours), value = sleepStr, onValueChange = { sleepStr = it }, keyboardType = KeyboardType.Decimal)
        EditField(icon = Icons.Default.FavoriteBorder, label = stringResource(R.string.field_resting_hr), value = hrStr, onValueChange = { hrStr = it }, keyboardType = KeyboardType.Number)

        Text(stringResource(R.string.section_exercises), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)

        exercises.forEachIndexed { idx, ex ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ex.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    Text(
                        buildString {
                            if (ex.durationMin > 0) append("${ex.durationMin} min")
                            if (ex.kcal > 0) { if (isNotEmpty()) append(" \u00b7 "); append("${ex.kcal} kcal") }
                        },
                        style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant,
                    )
                }
                IconButton(onClick = { exercises.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_remove), tint = Tertiary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = newExName, onValueChange = { newExName = it }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.field_exercise_name)) }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall.copy(color = OnSurface), colors = editFieldColors(), shape = RoundedCornerShape(8.dp))
            OutlinedTextField(value = newExDuration, onValueChange = { newExDuration = it }, modifier = Modifier.width(60.dp), label = { Text(stringResource(R.string.field_exercise_min)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = MaterialTheme.typography.bodySmall.copy(color = OnSurface), colors = editFieldColors(), shape = RoundedCornerShape(8.dp))
            OutlinedTextField(value = newExKcal, onValueChange = { newExKcal = it }, modifier = Modifier.width(70.dp), label = { Text(stringResource(R.string.field_exercise_kcal)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = MaterialTheme.typography.bodySmall.copy(color = OnSurface), colors = editFieldColors(), shape = RoundedCornerShape(8.dp))
            FilledIconButton(
                onClick = {
                    if (newExName.isNotBlank()) {
                        exercises.add(ExerciseItem(newExName.trim(), newExDuration.toIntOrNull() ?: 0, newExKcal.toIntOrNull() ?: 0))
                        newExName = ""; newExDuration = ""; newExKcal = ""
                    }
                },
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Primary),
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add), tint = Surface, modifier = Modifier.size(18.dp)) }
        }

        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            label = { Text(stringResource(R.string.field_notes)) }, placeholder = { Text(stringResource(R.string.placeholder_notes)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface), colors = editFieldColors(), shape = RoundedCornerShape(10.dp),
        )

        Button(
            onClick = {
                val exercisesJson = if (exercises.isEmpty()) null else buildJsonArray {
                    exercises.forEach { ex -> add(buildJsonObject { put("name", ex.name); put("durationMin", ex.durationMin); put("kcal", ex.kcal) }) }
                }.toString()
                onSave(DailyLog(
                    date = date,
                    weightKg = weightStr.replace(',', '.').toDoubleOrNull(),
                    steps = stepsStr.toIntOrNull(),
                    sleepHours = sleepStr.replace(',', '.').toDoubleOrNull(),
                    restingHr = hrStr.toIntOrNull(),
                    exercisesJson = exercisesJson,
                    notes = notes.ifBlank { null },
                    offPlan = existingLog?.offPlan ?: false,
                    daySummary = existingLog?.daySummary,
                ))
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp),
        ) { Text(stringResource(R.string.button_save), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = Surface) }

        Spacer(Modifier.height(32.dp))
    }
}
