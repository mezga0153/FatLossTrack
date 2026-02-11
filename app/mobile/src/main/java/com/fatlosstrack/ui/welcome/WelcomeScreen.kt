package com.fatlosstrack.ui.welcome

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.fatlosstrack.R
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

// Check system dark mode
import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * Onboarding wizard / "How it works" screen.
 *
 * **First launch** (isFirstLaunch=true):
 *   Step 0: Feature overview → "Get started"
 *     ↓  navigates to set_profile (onSetupProfile)
 *     ↓  navigates to set_goal   (onSetupGoal)
 *   Step 1: API key setup        (onboarding/apikey)
 *   Step 2: Usage tips           (onboarding/tips)
 *
 * **From Settings** (isFirstLaunch=false): shows all pages with back arrow.
 *
 * **API key nudge** (step="apikey", until key is set): shows API key page only.
 */

// ── Step identifiers ──
object OnboardingStep {
    const val WELCOME = "welcome"
    const val API_KEY = "apikey"
    const val TIPS = "tips"
}

/**
 * Supported app languages. Adding a new entry here + its values-XX/strings.xml
 * is all that's needed — the welcome screen and settings will pick it up.
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    EN("en", "English"),
    SL("sl", "Slovenščina"),
    HU("hu", "Magyar"),
    // Add more: DE("de", "Deutsch"), etc.
    ;
    companion object {
        /** Returns matching language only on exact language-code match, else EN. */
        fun fromDeviceLocale(): AppLanguage {
            val deviceLang = Locale.getDefault().language  // "en", "sl", "de", …
            return entries.find { it.code == deviceLang } ?: EN
        }
        fun fromCode(code: String): AppLanguage = entries.find { it.code == code } ?: EN
    }
}

@Composable
fun WelcomeScreen(
    step: String = OnboardingStep.WELCOME,
    isFirstLaunch: Boolean = true,
    preferencesManager: PreferencesManager? = null,
    onSetupProfile: () -> Unit = {},
    onSetupGoal: () -> Unit = {},
    onNext: () -> Unit = {},
    onDone: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    when (step) {
        OnboardingStep.WELCOME -> WelcomePage(
            isFirstLaunch = isFirstLaunch,
            preferencesManager = preferencesManager,
            onGetStarted = onSetupProfile,
            onBack = onBack,
        )
        OnboardingStep.API_KEY -> ApiKeyPage(
            isFirstLaunch = isFirstLaunch,
            preferencesManager = preferencesManager,
            onNext = onNext,
            onSkip = onNext,
            onBack = onBack,
        )
        OnboardingStep.TIPS -> TipsPage(
            isFirstLaunch = isFirstLaunch,
            onDone = onDone,
            onBack = onBack,
        )
    }
}

// ──────────────────────────────────────────────────
// Step 0 — Feature overview
// ──────────────────────────────────────────────────
@Composable
private fun WelcomePage(
    isFirstLaunch: Boolean,
    preferencesManager: PreferencesManager?,
    onGetStarted: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // ── Language auto-detect on first launch ──
    val savedLang by (preferencesManager?.language?.collectAsState(initial = "en")
        ?: remember { mutableStateOf("en") })
    var selectedLang by remember { mutableStateOf(AppLanguage.EN) }
    var autoDetected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isFirstLaunch && !autoDetected) {
            autoDetected = true
            val detected = AppLanguage.fromDeviceLocale()
            if (detected != AppLanguage.EN) {
                selectedLang = detected
                preferencesManager?.setLanguage(detected.code)
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(detected.code),
                )
            }
        }
    }

    // Keep chip state in sync with saved value
    LaunchedEffect(savedLang) {
        selectedLang = AppLanguage.fromCode(savedLang)
    }

    // ── Theme auto-detect on first launch ──
    val systemIsDark = isSystemInDarkTheme()
    val savedTheme by (preferencesManager?.themePreset?.collectAsState(initial = "PURPLE_DARK")
        ?: remember { mutableStateOf("PURPLE_DARK") })
    val currentPreset = try { ThemePreset.valueOf(savedTheme) } catch (_: Exception) { ThemePreset.PURPLE_DARK }
    var isDark by remember { mutableStateOf(currentPreset.mode == ThemeMode.DARK) }
    var themeAutoDetected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isFirstLaunch && !themeAutoDetected) {
            themeAutoDetected = true
            if (!systemIsDark) {
                // System is light → switch to light variant of current accent
                val lightPreset = ThemePreset.entries.first {
                    it.accentHue == ThemePreset.PURPLE_DARK.accentHue && it.mode == ThemeMode.LIGHT
                }
                isDark = false
                preferencesManager?.setThemePreset(lightPreset.name)
            }
        }
    }

    // Keep chip in sync
    LaunchedEffect(savedTheme) {
        isDark = currentPreset.mode == ThemeMode.DARK
    }

    OnboardingScaffold(showBack = !isFirstLaunch, onBack = onBack) {
        if (isFirstLaunch) Spacer(Modifier.height(16.dp))

        // ── Language switcher ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            AppLanguage.entries.forEach { lang ->
                LanguageChip(
                    label = lang.nativeName,
                    selected = selectedLang == lang,
                    onClick = {
                        selectedLang = lang
                        scope.launch {
                            preferencesManager?.setLanguage(lang.code)
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(lang.code),
                            )
                        }
                    },
                )
                Spacer(Modifier.width(6.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Dark / Light toggle ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            LanguageChip(
                label = stringResource(R.string.theme_dark),
                selected = isDark,
                onClick = {
                    isDark = true
                    val newPreset = ThemePreset.entries.first {
                        it.accentHue == currentPreset.accentHue && it.mode == ThemeMode.DARK
                    }
                    scope.launch { preferencesManager?.setThemePreset(newPreset.name) }
                },
            )
            Spacer(Modifier.width(6.dp))
            LanguageChip(
                label = stringResource(R.string.theme_light),
                selected = !isDark,
                onClick = {
                    isDark = false
                    val newPreset = ThemePreset.entries.first {
                        it.accentHue == currentPreset.accentHue && it.mode == ThemeMode.LIGHT
                    }
                    scope.launch { preferencesManager?.setThemePreset(newPreset.name) }
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // App icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.TrendingDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.welcome_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )

        Spacer(Modifier.height(32.dp))

        FeatureCard(Icons.Default.MonitorWeight, stringResource(R.string.welcome_feature_weight_title), stringResource(R.string.welcome_feature_weight_desc))
        Spacer(Modifier.height(12.dp))
        FeatureCard(Icons.Default.CameraAlt, stringResource(R.string.welcome_feature_camera_title), stringResource(R.string.welcome_feature_camera_desc))
        Spacer(Modifier.height(12.dp))
        FeatureCard(Icons.Default.AutoAwesome, stringResource(R.string.welcome_feature_ai_title), stringResource(R.string.welcome_feature_ai_desc))
        Spacer(Modifier.height(12.dp))
        FeatureCard(Icons.Default.FavoriteBorder, stringResource(R.string.welcome_feature_health_title), stringResource(R.string.welcome_feature_health_desc))
        Spacer(Modifier.height(12.dp))
        FeatureCard(Icons.Default.Equalizer, stringResource(R.string.welcome_feature_trends_title), stringResource(R.string.welcome_feature_trends_desc))

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.welcome_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = stringResource(if (isFirstLaunch) R.string.welcome_get_started else R.string.welcome_got_it),
            onClick = if (isFirstLaunch) onGetStarted else onBack,
        )
    }
}

// ──────────────────────────────────────────────────
// Step 1 — API Key setup
// ──────────────────────────────────────────────────
@Composable
private fun ApiKeyPage(
    isFirstLaunch: Boolean,
    preferencesManager: PreferencesManager?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val storedKey by (preferencesManager?.openAiApiKey?.collectAsState(initial = "") ?: remember { mutableStateOf("") })
    var keyInput by remember(storedKey) { mutableStateOf(storedKey) }
    var showKey by remember { mutableStateOf(false) }
    val hasKey = storedKey.isNotBlank()

    OnboardingScaffold(showBack = !isFirstLaunch || true, onBack = onBack) {
        Spacer(Modifier.height(16.dp))

        // Step indicator
        StepIndicator(current = 2, total = 3)

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.setup_apikey_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.setup_apikey_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.height(8.dp))

        // "How to get a key" link
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://platform.openai.com/api-keys"),
                    )
                    context.startActivity(intent)
                }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.setup_apikey_guide_link),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Key input field
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text(stringResource(R.string.ai_api_key_label)) },
            placeholder = { Text(stringResource(R.string.ai_api_key_placeholder)) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            ),
        )

        if (hasKey) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ai_key_saved),
                style = MaterialTheme.typography.bodySmall,
                color = Secondary,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Save key button
        if (keyInput.isNotBlank() && keyInput != storedKey) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        preferencesManager?.setOpenAiApiKey(keyInput.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ai_save_key), color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Features that require the key
        Text(
            text = stringResource(R.string.setup_apikey_features_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        AiFeatureRow(Icons.Default.CameraAlt, stringResource(R.string.setup_apikey_feature_photo))
        AiFeatureRow(Icons.Default.Restaurant, stringResource(R.string.setup_apikey_feature_text))
        AiFeatureRow(Icons.Default.AutoAwesome, stringResource(R.string.setup_apikey_feature_coach))
        AiFeatureRow(Icons.Default.Summarize, stringResource(R.string.setup_apikey_feature_summary))

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = stringResource(if (hasKey) R.string.setup_continue else R.string.setup_continue),
            onClick = onNext,
        )

        if (!hasKey && isFirstLaunch) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSkip) {
                Text(
                    stringResource(R.string.setup_skip_for_now),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────
// Step 2 — Usage tips
// ──────────────────────────────────────────────────
@Composable
private fun TipsPage(
    isFirstLaunch: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    OnboardingScaffold(showBack = !isFirstLaunch || true, onBack = onBack) {
        Spacer(Modifier.height(16.dp))

        StepIndicator(current = 3, total = 3)

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.setup_tips_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // Tip 1: text meals
        TipCard(
            icon = Icons.Default.EditNote,
            title = stringResource(R.string.setup_tip_text_title),
            description = stringResource(R.string.setup_tip_text_desc),
            example = stringResource(R.string.setup_tip_text_example),
        )

        Spacer(Modifier.height(12.dp))

        // Tip 2: photo meals
        TipCard(
            icon = Icons.Default.CameraAlt,
            title = stringResource(R.string.setup_tip_photo_title),
            description = stringResource(R.string.setup_tip_photo_desc),
            example = null,
        )

        Spacer(Modifier.height(12.dp))

        // Tip 3: chat
        TipCard(
            icon = Icons.Default.AutoAwesome,
            title = stringResource(R.string.setup_tip_chat_title),
            description = stringResource(R.string.setup_tip_chat_desc),
            example = stringResource(R.string.setup_tip_chat_example),
        )

        Spacer(Modifier.height(32.dp))

        PrimaryButton(
            text = stringResource(R.string.setup_done),
            onClick = onDone,
        )
    }
}

// ──────────────────────────────────────────────────
// Shared composables
// ──────────────────────────────────────────────────

@Composable
private fun OnboardingScaffold(
    showBack: Boolean,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = statusBarTop + 12.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showBack) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            val step = i + 1
            Box(
                modifier = Modifier
                    .size(if (step == current) 24.dp else 8.dp, 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (step == current) MaterialTheme.colorScheme.primary
                        else if (step < current) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    ),
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AiFeatureRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun TipCard(
    icon: ImageVector,
    title: String,
    description: String,
    example: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
        if (example != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = example,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(10.dp),
            )
        }
    }
}
