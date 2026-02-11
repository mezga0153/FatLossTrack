package com.fatlosstrack.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import com.fatlosstrack.auth.AuthManager
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.health.HealthConnectManager
import com.fatlosstrack.data.health.HealthConnectSyncService
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.ui.chat.ChatStateHolder
import com.fatlosstrack.data.local.db.WeightDao
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.login.LoginScreen
import com.fatlosstrack.ui.theme.FatLossTrackTheme
import com.fatlosstrack.ui.theme.ThemePreset
import com.fatlosstrack.ui.theme.buildAppColors
import com.fatlosstrack.ui.theme.purpleDarkColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var openAiService: OpenAiService

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var mealDao: MealDao

    @Inject
    lateinit var dailyLogDao: DailyLogDao

    @Inject
    lateinit var weightDao: WeightDao

    @Inject
    lateinit var healthConnectManager: HealthConnectManager

    @Inject
    lateinit var healthConnectSyncService: HealthConnectSyncService

    @Inject
    lateinit var daySummaryGenerator: DaySummaryGenerator

    @Inject
    lateinit var appLogger: AppLogger

    @Inject
    lateinit var chatStateHolder: ChatStateHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLogger.user("App opened")
        enableEdgeToEdge()
        setContent {
            val themePresetName by preferencesManager.themePreset.collectAsState(initial = "PURPLE_DARK")
            val preset = try { ThemePreset.valueOf(themePresetName) } catch (_: Exception) { ThemePreset.PURPLE_DARK }
            val appColors = remember(preset) { buildAppColors(preset.accentHue, preset.mode) }

            // Update status bar icon colors when the theme changes
            LaunchedEffect(appColors.isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (appColors.isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (appColors.isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
            }

            FatLossTrackTheme(appColors = appColors) {
                val authState by authManager.authState.collectAsState()

                when (authState) {
                    is AuthManager.AuthState.Loading -> {
                        // Could show a splash — for now just blank
                    }
                    is AuthManager.AuthState.SignedOut,
                    is AuthManager.AuthState.Error -> {
                        LoginScreen(
                            authManager = authManager,
                            onSignedIn = { /* state flow will trigger recomposition */ },
                        )
                    }
                    is AuthManager.AuthState.SignedIn -> {
                        FatLossTrackNavGraph(
                            authManager = authManager,
                            openAiService = openAiService,
                            preferencesManager = preferencesManager,
                            mealDao = mealDao,
                            dailyLogDao = dailyLogDao,
                            weightDao = weightDao,
                            healthConnectManager = healthConnectManager,
                            healthConnectSyncService = healthConnectSyncService,
                            daySummaryGenerator = daySummaryGenerator,
                            appLogger = appLogger,
                            chatStateHolder = chatStateHolder,
                        )
                    }
                }
            }
        }
    }
}
