package com.fatlosstrack.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import com.fatlosstrack.data.health.HealthConnectSyncService
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.AiUsageDao
import com.fatlosstrack.ui.chat.ChatStateHolder
import com.fatlosstrack.ui.camera.AnalysisResultStateHolder
import com.fatlosstrack.ui.home.HomeStateHolder
import com.fatlosstrack.ui.trends.TrendsStateHolder
import com.fatlosstrack.ui.log.LogStateHolder
import com.fatlosstrack.ui.settings.SettingsStateHolder
import com.fatlosstrack.ui.components.AiBarStateHolder
import com.fatlosstrack.ui.theme.FatLossTrackTheme
import com.fatlosstrack.ui.theme.ThemePreset
import com.fatlosstrack.ui.theme.buildAppColors
import com.fatlosstrack.ui.theme.purpleDarkColors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var healthConnectSyncService: HealthConnectSyncService

    @Inject
    lateinit var appLogger: AppLogger

    @Inject
    lateinit var aiUsageDao: AiUsageDao

    @Inject
    lateinit var chatStateHolder: ChatStateHolder

    @Inject
    lateinit var aiBarStateHolder: AiBarStateHolder

    @Inject
    lateinit var analysisResultStateHolder: AnalysisResultStateHolder

    @Inject
    lateinit var homeStateHolder: HomeStateHolder

    @Inject
    lateinit var trendsStateHolder: TrendsStateHolder

    @Inject
    lateinit var logStateHolder: LogStateHolder

    @Inject
    lateinit var settingsStateHolder: SettingsStateHolder

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
                FatLossTrackNavGraph(
                    preferencesManager = preferencesManager,
                    healthConnectSyncService = healthConnectSyncService,
                    appLogger = appLogger,
                    aiUsageDao = aiUsageDao,
                    chatStateHolder = chatStateHolder,
                    aiBarStateHolder = aiBarStateHolder,
                    analysisResultStateHolder = analysisResultStateHolder,
                    homeStateHolder = homeStateHolder,
                    trendsStateHolder = trendsStateHolder,
                    logStateHolder = logStateHolder,
                    settingsStateHolder = settingsStateHolder,
                )
            }
        }
    }
}
