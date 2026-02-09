package com.fatlosstrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fatlosstrack.auth.AuthManager
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.health.HealthConnectManager
import com.fatlosstrack.data.health.HealthConnectSyncService
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.WeightDao
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.camera.AnalysisResultScreen
import com.fatlosstrack.ui.camera.CameraModeSheet
import com.fatlosstrack.ui.camera.CaptureMode
import com.fatlosstrack.ui.camera.MealCaptureScreen
import com.fatlosstrack.ui.components.AiBar
import com.fatlosstrack.ui.home.HomeScreen
import com.fatlosstrack.ui.log.LogScreen
import com.fatlosstrack.R
import com.fatlosstrack.ui.settings.LogViewerScreen
import com.fatlosstrack.ui.settings.SetGoalScreen
import com.fatlosstrack.ui.settings.SettingsScreen
import com.fatlosstrack.ui.trends.TrendsScreen
import kotlinx.coroutines.launch

// ---- Navigation destinations ----

enum class Tab(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    Home("home", R.string.tab_home, Icons.Default.Home),
    Trends("trends", R.string.tab_trends, Icons.Default.Equalizer),
    Log("log", R.string.tab_log, Icons.AutoMirrored.Filled.List),
    Settings("settings", R.string.tab_settings, Icons.Default.Settings),
}

// ---- Root scaffold with bottom nav + floating AI bar ----

@Composable
fun FatLossTrackNavGraph(
    authManager: AuthManager,
    openAiService: OpenAiService,
    preferencesManager: PreferencesManager,
    mealDao: MealDao,
    dailyLogDao: DailyLogDao,
    weightDao: WeightDao,
    healthConnectManager: HealthConnectManager? = null,
    healthConnectSyncService: HealthConnectSyncService? = null,
    daySummaryGenerator: DaySummaryGenerator? = null,
    appLogger: AppLogger? = null,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Camera mode picker sheet
    var showCameraModeSheet by remember { mutableStateOf(false) }

    // Auto-sync Health Connect on first composition
    val syncScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val changedDates = healthConnectSyncService?.syncRecentDays(7) ?: emptyList()
        if (changedDates.isNotEmpty()) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                daySummaryGenerator?.generateForDates(changedDates)
            }
        }
    }

    // Hide bottom bar + AI bar on camera/analysis/goal/log viewer screens
    val hideChrome = currentRoute?.startsWith("capture") == true ||
            currentRoute?.startsWith("analysis") == true ||
            currentRoute == "set_goal" ||
            currentRoute == "log_viewer"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!hideChrome) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Tab.Home.route,
            ) {
                composable(Tab.Home.route) {
                    HomeScreen(
                        dailyLogDao = dailyLogDao,
                        mealDao = mealDao,
                        weightDao = weightDao,
                        preferencesManager = preferencesManager,
                        daySummaryGenerator = daySummaryGenerator,
                        openAiService = openAiService,
                        onCameraForDate = { date ->
                            navController.navigate("capture/log?targetDate=$date")
                        },
                    )
                }
                composable(Tab.Trends.route) {
                    TrendsScreen(
                        dailyLogDao = dailyLogDao,
                        mealDao = mealDao,
                        weightDao = weightDao,
                        preferencesManager = preferencesManager,
                    )
                }
                composable(Tab.Log.route) {
                    LogScreen(
                        mealDao = mealDao,
                        dailyLogDao = dailyLogDao,
                        preferencesManager = preferencesManager,
                        daySummaryGenerator = daySummaryGenerator,
                        onCameraForDate = { date ->
                            navController.navigate("capture/log?targetDate=$date")
                        },
                    )
                }
                composable(Tab.Settings.route) {
                    SettingsScreen(
                        onEditGoal = { navController.navigate("set_goal") },
                        authManager = authManager,
                        preferencesManager = preferencesManager,
                        healthConnectManager = healthConnectManager,
                        onSyncHealthConnect = {
                            syncScope.launch {
                                val changedDates = healthConnectSyncService?.syncRecentDays(7) ?: emptyList()
                                if (changedDates.isNotEmpty()) {
                                    launch {
                                        daySummaryGenerator?.generateForDates(changedDates)
                                    }
                                }
                            }
                        },
                        onViewLog = if (appLogger != null) {
                            { navController.navigate("log_viewer") }
                        } else null,
                    )
                }

                // Set Goal
                composable("set_goal") {
                    SetGoalScreen(
                        onBack = { navController.popBackStack() },
                        preferencesManager = preferencesManager,
                    )
                }

                // Log Viewer
                if (appLogger != null) {
                    composable("log_viewer") {
                        LogViewerScreen(
                            appLogger = appLogger,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }

                // Camera capture
                composable(
                    route = "capture/{mode}?targetDate={targetDate}",
                    arguments = listOf(
                        navArgument("mode") { type = NavType.StringType },
                        navArgument("targetDate") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val modeStr = entry.arguments?.getString("mode") ?: "log"
                    val targetDate = entry.arguments?.getString("targetDate") ?: ""
                    val mode = if (modeStr == "suggest") CaptureMode.SuggestMeal else CaptureMode.LogMeal
                    MealCaptureScreen(
                        mode = mode,
                        onAnalyze = { m, count ->
                            val dateParam = if (targetDate.isNotEmpty()) "?targetDate=$targetDate" else ""
                            navController.popBackStack()
                            navController.navigate("analysis/${m.name}/$count$dateParam")
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                // Text meal analysis (from AiBar)
                composable("analysis/text") {
                    AnalysisResultScreen(
                        mode = CaptureMode.LogMeal,
                        photoCount = 0,
                        openAiService = openAiService,
                        mealDao = mealDao,
                        isTextMode = true,
                        daySummaryGenerator = daySummaryGenerator,
                        onDone = {
                            navController.popBackStack()
                        },
                        onLogged = {
                            if (!navController.popBackStack(Tab.Log.route, inclusive = false)) {
                                navController.navigate(Tab.Log.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                // Analysis result
                composable(
                    route = "analysis/{mode}/{count}?targetDate={targetDate}",
                    arguments = listOf(
                        navArgument("mode") { type = NavType.StringType },
                        navArgument("count") { type = NavType.IntType },
                        navArgument("targetDate") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    val modeStr = entry.arguments?.getString("mode") ?: "LogMeal"
                    val count = entry.arguments?.getInt("count") ?: 1
                    val targetDateStr = entry.arguments?.getString("targetDate") ?: ""
                    val targetDate = if (targetDateStr.isNotEmpty()) {
                        try { java.time.LocalDate.parse(targetDateStr) } catch (_: Exception) { java.time.LocalDate.now() }
                    } else java.time.LocalDate.now()
                    val mode = if (modeStr == "SuggestMeal") CaptureMode.SuggestMeal else CaptureMode.LogMeal
                    AnalysisResultScreen(
                        mode = mode,
                        photoCount = count,
                        openAiService = openAiService,
                        mealDao = mealDao,
                        targetDate = targetDate,
                        daySummaryGenerator = daySummaryGenerator,
                        onDone = {
                            navController.popBackStack(Tab.Home.route, inclusive = false)
                        },
                        onLogged = {
                            if (!navController.popBackStack(Tab.Log.route, inclusive = false)) {
                                navController.navigate(Tab.Log.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // Floating AI bar (hidden on camera screens)
            if (!hideChrome) {
                AiBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    openAiService = openAiService,
                    mealDao = mealDao,
                    daySummaryGenerator = daySummaryGenerator,
                    onCameraClick = { showCameraModeSheet = true },
                    onTextMealAnalyzed = { _ ->
                        navController.navigate("analysis/text")
                    },
                )
            }
        }
    }

    // Camera mode picker bottom sheet
    if (showCameraModeSheet) {
        CameraModeSheet(
            onSelect = { mode ->
                showCameraModeSheet = false
                val modeArg = if (mode == CaptureMode.SuggestMeal) "suggest" else "log"
                navController.navigate("capture/$modeArg")
            },
            onDismiss = { showCameraModeSheet = false },
        )
    }
}
