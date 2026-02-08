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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fatlosstrack.auth.AuthManager
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.camera.AnalysisResultScreen
import com.fatlosstrack.ui.camera.CameraModeSheet
import com.fatlosstrack.ui.camera.CaptureMode
import com.fatlosstrack.ui.camera.MealCaptureScreen
import com.fatlosstrack.ui.components.AiBar
import com.fatlosstrack.ui.home.HomeScreen
import com.fatlosstrack.ui.log.LogScreen
import com.fatlosstrack.ui.settings.SetGoalScreen
import com.fatlosstrack.ui.settings.SettingsScreen
import com.fatlosstrack.ui.trends.TrendsScreen

// ---- Navigation destinations ----

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home),
    Trends("trends", "Trends", Icons.Default.Equalizer),
    Log("log", "Log", Icons.AutoMirrored.Filled.List),
    Settings("settings", "Settings", Icons.Default.Settings),
}

// ---- Root scaffold with bottom nav + floating AI bar ----

@Composable
fun FatLossTrackNavGraph(
    authManager: AuthManager,
    openAiService: OpenAiService,
    preferencesManager: PreferencesManager,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Camera mode picker sheet
    var showCameraModeSheet by remember { mutableStateOf(false) }

    // Hide bottom bar + AI bar on camera/analysis/goal screens
    val hideChrome = currentRoute?.startsWith("capture") == true ||
            currentRoute?.startsWith("analysis") == true ||
            currentRoute == "set_goal"

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
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
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
                composable(Tab.Home.route) { HomeScreen() }
                composable(Tab.Trends.route) { TrendsScreen() }
                composable(Tab.Log.route) { LogScreen() }
                composable(Tab.Settings.route) {
                    SettingsScreen(
                        onEditGoal = { navController.navigate("set_goal") },
                        authManager = authManager,
                        preferencesManager = preferencesManager,
                    )
                }

                // Set Goal
                composable("set_goal") {
                    SetGoalScreen(onBack = { navController.popBackStack() })
                }

                // Camera capture
                composable(
                    route = "capture/{mode}",
                    arguments = listOf(navArgument("mode") { type = NavType.StringType }),
                ) { entry ->
                    val modeStr = entry.arguments?.getString("mode") ?: "log"
                    val mode = if (modeStr == "suggest") CaptureMode.SuggestMeal else CaptureMode.LogMeal
                    MealCaptureScreen(
                        mode = mode,
                        onAnalyze = { m, count ->
                            navController.navigate("analysis/${m.name}/$count") {
                                popUpTo("capture/$modeStr") { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                // Analysis result
                composable(
                    route = "analysis/{mode}/{count}",
                    arguments = listOf(
                        navArgument("mode") { type = NavType.StringType },
                        navArgument("count") { type = NavType.IntType },
                    ),
                ) { entry ->
                    val modeStr = entry.arguments?.getString("mode") ?: "LogMeal"
                    val count = entry.arguments?.getInt("count") ?: 1
                    val mode = if (modeStr == "SuggestMeal") CaptureMode.SuggestMeal else CaptureMode.LogMeal
                    AnalysisResultScreen(
                        mode = mode,
                        photoCount = count,
                        openAiService = openAiService,
                        onDone = {
                            navController.popBackStack(Tab.Home.route, inclusive = false)
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
                    onCameraClick = { showCameraModeSheet = true },
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
