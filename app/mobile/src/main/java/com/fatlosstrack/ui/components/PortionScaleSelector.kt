package com.fatlosstrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.domain.PortionScale
import com.fatlosstrack.ui.theme.CardSurface
import com.fatlosstrack.ui.theme.OnSurface
import com.fatlosstrack.ui.theme.OnSurfaceVariant
import com.fatlosstrack.ui.theme.Primary

/**
 * Quick portion chips (½×, 1×, 1.5×, 2×, custom). Stateless: the caller owns [scale]
 * and applies it to the unscaled meal, so tapping 1× restores the original values.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortionScaleSelector(
    scale: Double,
    onScaleChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    val isCustom = scale !in PortionScale.PRESETS
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = Primary.copy(alpha = 0.15f),
        selectedLabelColor = Primary,
    )

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PortionScale.PRESETS.forEach { preset ->
            FilterChip(
                selected = scale == preset,
                onClick = { onScaleChange(preset) },
                label = { Text(portionLabel(preset), style = MaterialTheme.typography.labelMedium) },
                colors = chipColors,
            )
        }
        FilterChip(
            selected = isCustom,
            onClick = { showCustomDialog = true },
            label = {
                Text(
                    if (isCustom) portionLabel(scale) else stringResource(R.string.portion_custom),
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            colors = chipColors,
        )
    }

    if (showCustomDialog) {
        var input by remember { mutableStateOf(if (isCustom) PortionScale.format(scale) else "") }
        val parsed = PortionScale.parse(input)
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text(stringResource(R.string.portion_custom_title)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.portion_custom_label)) },
                    singleLine = true,
                    isError = input.isNotBlank() && parsed == null,
                    supportingText = if (input.isNotBlank() && parsed == null) {
                        { Text(stringResource(R.string.portion_custom_error)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnSurface),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = OnSurfaceVariant.copy(alpha = 0.3f),
                        cursorColor = Primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        parsed?.let(onScaleChange)
                        showCustomDialog = false
                    },
                    enabled = parsed != null,
                ) { Text(stringResource(R.string.button_ok), color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(stringResource(R.string.button_cancel))
                }
            },
            containerColor = CardSurface,
        )
    }
}

private fun portionLabel(factor: Double): String =
    if (factor == 0.5) "½×" else "${PortionScale.format(factor)}×"
