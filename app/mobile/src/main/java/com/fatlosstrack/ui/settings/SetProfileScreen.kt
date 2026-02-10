package com.fatlosstrack.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Edit Profile screen — edit height, age, sex, and activity level.
 * Follows the same pattern as SetGoalScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetProfileScreen(
    onBack: () -> Unit,
    preferencesManager: PreferencesManager,
) {
    val scope = rememberCoroutineScope()

    // Load saved values
    val savedHeight by preferencesManager.heightCm.collectAsState(initial = null)
    val savedAge by preferencesManager.age.collectAsState(initial = null)
    val savedSex by preferencesManager.sex.collectAsState(initial = null)
    val savedActivityLevel by preferencesManager.activityLevel.collectAsState(initial = "light")

    var heightCm by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("") }
    var activityLevel by remember { mutableStateOf("light") }
    var initialized by remember { mutableStateOf(false) }

    // Seed fields once from saved values
    LaunchedEffect(savedHeight, savedAge, savedSex, savedActivityLevel) {
        if (!initialized) {
            heightCm = savedHeight?.toString() ?: ""
            age = savedAge?.toString() ?: ""
            sex = savedSex ?: ""
            activityLevel = savedActivityLevel
            if (savedHeight != null || savedAge != null || savedSex != null) {
                initialized = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface),
    ) {
        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = OnSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.set_profile_title),
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Height section ──
            ProfileSection(icon = Icons.Default.Height, title = stringResource(R.string.settings_height)) {
                ProfileTextField(
                    value = heightCm,
                    onValueChange = { heightCm = it.filter { c -> c.isDigit() }.take(3) },
                    label = stringResource(R.string.field_cm),
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
            }

            // ── Age section ──
            ProfileSection(icon = Icons.Default.Cake, title = stringResource(R.string.settings_age)) {
                ProfileTextField(
                    value = age,
                    onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
                    label = stringResource(R.string.field_years),
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
            }

            // ── Sex section ──
            ProfileSection(icon = Icons.Default.Person, title = stringResource(R.string.settings_sex)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SexChip("male", stringResource(R.string.sex_male), sex == "male") { sex = "male" }
                    SexChip("female", stringResource(R.string.sex_female), sex == "female") { sex = "female" }
                    SexChip("yes", stringResource(R.string.sex_yes), sex == "yes") { sex = "yes" }
                }
            }

            // ── Activity level section ──
            ProfileSection(icon = Icons.AutoMirrored.Filled.DirectionsRun, title = stringResource(R.string.settings_activity_level)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ActivityChip("sedentary", stringResource(R.string.activity_sedentary), activityLevel == "sedentary") { activityLevel = "sedentary" }
                    ActivityChip("light", stringResource(R.string.activity_light), activityLevel == "light") { activityLevel = "light" }
                    ActivityChip("moderate", stringResource(R.string.activity_moderate), activityLevel == "moderate") { activityLevel = "moderate" }
                    ActivityChip("active", stringResource(R.string.activity_active), activityLevel == "active") { activityLevel = "active" }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (activityLevel) {
                        "sedentary" -> stringResource(R.string.activity_sedentary_desc)
                        "light" -> stringResource(R.string.activity_light_desc)
                        "moderate" -> stringResource(R.string.activity_moderate_desc)
                        else -> stringResource(R.string.activity_active_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
            }

            // ── Save button ──
            Button(
                onClick = {
                    scope.launch {
                        val h = heightCm.toIntOrNull()
                        val a = age.toIntOrNull()
                        if (h != null) preferencesManager.setHeightCm(h)
                        if (a != null) preferencesManager.setAge(a)
                        if (sex.isNotBlank()) preferencesManager.setSex(sex)
                        preferencesManager.setActivityLevel(activityLevel)
                        AppLogger.instance?.user("Profile saved: height=${heightCm}cm, age=$age, sex=$sex, activity=$activityLevel")
                    }
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    stringResource(R.string.button_save_changes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.Black,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Building blocks ──

@Composable
private fun ProfileSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = OnSurface,
            )
        }
        content()
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceVariant,
            unfocusedContainerColor = SurfaceVariant,
            focusedBorderColor = Primary.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.Transparent,
            cursorColor = Primary,
        ),
        shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Medium,
            color = OnSurface,
        ),
    )
}

@Composable
private fun SexChip(
    value: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary.copy(alpha = 0.18f) else SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Primary else OnSurface,
        )
    }
}

@Composable
private fun ActivityChip(
    value: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary.copy(alpha = 0.18f) else SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Primary else OnSurface,
        )
    }
}
