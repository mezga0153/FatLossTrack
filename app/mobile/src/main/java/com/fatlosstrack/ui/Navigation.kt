package com.fatlosstrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.sp
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
import com.fatlosstrack.data.health.HealthConnectSyncService
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.AiUsageDao
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.camera.AnalysisResultScreen
import com.fatlosstrack.ui.camera.AnalysisResultStateHolder
import com.fatlosstrack.ui.camera.CameraModeSheet
import com.fatlosstrack.ui.camera.CaptureMode
import com.fatlosstrack.ui.camera.MealCaptureScreen
import com.fatlosstrack.ui.chat.ChatScreen
import com.fatlosstrack.ui.chat.ChatStateHolder
import com.fatlosstrack.ui.components.AiBar
import com.fatlosstrack.ui.components.AiBarStateHolder
import com.fatlosstrack.ui.home.HomeScreen
import com.fatlosstrack.ui.home.HomeStateHolder
import com.fatlosstrack.ui.log.LogScreen
import com.fatlosstrack.ui.log.LogStateHolder
import com.fatlosstrack.R
import com.fatlosstrack.ui.settings.LogViewerScreen
import com.fatlosstrack.ui.settings.SetGoalScreen
import com.fatlosstrack.ui.settings.SetProfileScreen
import com.fatlosstrack.ui.settings.SettingsScreen
import com.fatlosstrack.ui.settings.SettingsStateHolder
import com.fatlosstrack.ui.settings.AiUsageScreen
import com.fatlosstrack.ui.settings.ModelSelectorScreen
import com.fatlosstrack.ui.trends.TrendsScreen
import com.fatlosstrack.ui.trends.TrendsStateHolder
import com.fatlosstrack.ui.welcome.OnboardingStep
import com.fatlosstrack.ui.welcome.WelcomeScreen

// ---- Navigation destinations ----

enum class Tab(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    Home("home", R.string.tab_home, Icons.Default.Home),
    Trends("trends", R.string.tab_trends, Icons.Default.Equalizer),
    Log("log", R.string.tab_log, Icons.AutoMirrored.Filled.List),
    Chat("chat", R.string.tab_chat, Icons.Default.AutoAwesome),
    Settings("settings", R.string.tab_settings, Icons.Default.Settings),
}

// ---- Root scaffold with bottom nav + floating AI bar ----

@Composable
fun FatLossTrackNavGraph(
    preferencesManager: PreferencesManager,
    healthConnectSyncService: HealthConnectSyncService? = null,
    appLogger: AppLogger? = null,
    aiUsageDao: AiUsageDao,
    chatStateHolder: ChatStateHolder,
    aiBarStateHolder: AiBarStateHolder,
    analysisResultStateHolder: AnalysisResultStateHolder,
    homeStateHolder: HomeStateHolder,
    trendsStateHolder: TrendsStateHolder,
    logStateHolder: LogStateHolder,
    settingsStateHolder: SettingsStateHolder,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Camera mode picker sheet
    var showCameraModeSheet by remember { mutableStateOf(false) }

    // Auto-sync Health Connect on first composition
    LaunchedEffect(Unit) {
        healthConnectSyncService?.launchSync(7, "Navigation:autoHcSync")
    }

    // Show welcome screen on first launch (no goal set yet)
    // Show API key nudge once per session if goal is set but key is missing
    val goalWeight by preferencesManager.goalWeight.collectAsState(initial = -1f)
    val apiKey by preferencesManager.openAiApiKey.collectAsState(initial = "\u0000") // sentinel = still loading
    val hasCheckedGoal = goalWeight != -1f  // -1f = still loading
    val hasCheckedKey = apiKey != "\u0000"
    var dismissedKeyNudge by remember { mutableStateOf(false) }
    LaunchedEffect(hasCheckedGoal, goalWeight, hasCheckedKey, apiKey) {
        if (hasCheckedGoal && goalWeight == null) {
            navController.navigate("onboarding/welcome") {
                launchSingleTop = true
            }
        } else if (hasCheckedGoal && hasCheckedKey && goalWeight != null && apiKey.isBlank() && !dismissedKeyNudge) {
            dismissedKeyNudge = true
            navController.navigate("onboarding/apikey") {
                launchSingleTop = true
            }
        }
    }

    // Hide bottom bar + AI bar on camera/analysis/goal/log viewer screens
    val hideChrome = currentRoute?.startsWith("capture") == true ||
            currentRoute?.startsWith("analysis") == true ||
            currentRoute?.startsWith("onboarding") == true ||
            currentRoute == "set_goal" ||
            currentRoute == "set_profile" ||
            currentRoute == "set_profile_onboarding" ||
            currentRoute == "set_goal_onboarding" ||
            currentRoute == "log_viewer" ||
            currentRoute == "ai_usage" ||
            currentRoute == "model_selector"

    // Hide AiBar on chat tab too (it has its own input)
    val hideAiBar = hideChrome || currentRoute == Tab.Chat.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                                AppLogger.instance?.nav("Tab: ${tab.route}")
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = stringResource(tab.labelRes),
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            label = {
                                Text(
                                    stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                )
                            },
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
        Box(Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
            NavHost(
                navController = navController,
                startDestination = Tab.Home.route,
            ) {
                composable(Tab.Home.route) {
                    HomeScreen(
                        state = homeStateHolder,
                        onCameraForDate = { date ->
                            navController.navigate("capture/log?targetDate=$date")
                        },
                    )
                }
                composable(Tab.Trends.route) {
                    TrendsScreen(
                        state = trendsStateHolder,
                    )
                }
                composable(Tab.Log.route) {
                    LogScreen(
                        state = logStateHolder,
                        onCameraForDate = { date ->
                            navController.navigate("capture/log?targetDate=$date")
                        },
                    )
                }
                composable(Tab.Settings.route) {
                    SettingsScreen(
                        state = settingsStateHolder,
                        onEditGoal = { navController.navigate("set_goal") },
                        onEditProfile = { navController.navigate("set_profile") },
                        onSyncHealthConnect = {
                            healthConnectSyncService?.launchSync(7, "Settings:manualHcSync")
                        },
                        onViewLog = if (appLogger != null) {
                            { navController.navigate("log_viewer") }
                        } else null,
                        onViewAiUsage = { navController.navigate("ai_usage") },
                        onViewModelSelector = { navController.navigate("model_selector") },
                        onViewWelcome = { navController.navigate("onboarding/welcome?first=false") },
                    )
                }

                // Set Goal
                composable("set_goal") {
                    SetGoalScreen(
                        onBack = { navController.popBackStack() },
                        preferencesManager = preferencesManager,
                    )
                }

                // Set Profile
                composable("set_profile") {
                    SetProfileScreen(
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

                // AI Usage
                composable("ai_usage") {
                    AiUsageScreen(
                        aiUsageDao = aiUsageDao,
                        onBack = { navController.popBackStack() },
                    )
                }

                // Model Selector
                composable("model_selector") {
                    ModelSelectorScreen(
                        preferencesManager = preferencesManager,
                        onBack = { navController.popBackStack() },
                    )
                }

                // ── Onboarding flow ──

                // Step 0: Welcome (feature overview)
                composable(
                    route = "onboarding/welcome?first={first}",
                    arguments = listOf(
                        navArgument("first") { type = NavType.BoolType; defaultValue = true },
                    ),
                ) { entry ->
                    val isFirst = entry.arguments?.getBoolean("first") ?: true
                    WelcomeScreen(
                        step = OnboardingStep.WELCOME,
                        isFirstLaunch = isFirst,
                        preferencesManager = preferencesManager,
                        onSetupProfile = {
                            // Chain: welcome → profile → goal → apikey → tips
                            navController.navigate("set_profile_onboarding")
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                // Profile (during onboarding — same screen, different onBack target)
                composable("set_profile_onboarding") {
                    SetProfileScreen(
                        showBackArrow = false,
                        onBack = {
                            navController.popBackStack()
                            navController.navigate("set_goal_onboarding") {
                                launchSingleTop = true
                            }
                        },
                        preferencesManager = preferencesManager,
                    )
                }

                // Goal (during onboarding)
                composable("set_goal_onboarding") {
                    SetGoalScreen(
                        showBackArrow = false,
                        onBack = {
                            navController.popBackStack()
                            navController.navigate("onboarding/apikey") {
                                launchSingleTop = true
                            }
                        },
                        preferencesManager = preferencesManager,
                    )
                }

                // Step 1: API key setup
                composable("onboarding/apikey") {
                    WelcomeScreen(
                        step = OnboardingStep.API_KEY,
                        isFirstLaunch = true,
                        preferencesManager = preferencesManager,
                        onNext = {
                            navController.popBackStack()
                            navController.navigate("onboarding/tips") {
                                launchSingleTop = true
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                // Step 2: Usage tips
                composable("onboarding/tips") {
                    WelcomeScreen(
                        step = OnboardingStep.TIPS,
                        isFirstLaunch = true,
                        onDone = {
                            // Clear the entire onboarding back stack → go home
                            navController.popBackStack(Tab.Home.route, inclusive = false)
                        },
                        onBack = { navController.popBackStack() },
                    )
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
                    val mode = when (modeStr) {
                        "suggest" -> CaptureMode.SuggestMeal
                        "chat" -> CaptureMode.ChatPhoto
                        else -> CaptureMode.LogMeal
                    }
                    val captureContext = androidx.compose.ui.platform.LocalContext.current
                    MealCaptureScreen(
                        mode = mode,
                        onAnalyze = { m, count ->
                            when (m) {
                                CaptureMode.ChatPhoto -> {
                                    // Photos already in CapturedPhotoStore — go back to chat
                                    navController.popBackStack()
                                }
                                CaptureMode.SuggestMeal -> {
                                    // Redirect suggest mode to chat with photos + prompt
                                    val photos = com.fatlosstrack.data.local.CapturedPhotoStore.peek()
                                    com.fatlosstrack.data.local.CapturedPhotoStore.clear()
                                    com.fatlosstrack.data.local.PendingChatStore.store(
                                        captureContext.getString(R.string.chat_suggest_prompt),
                                        photos,
                                    )
                                    navController.popBackStack()
                                    navController.navigate(Tab.Chat.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                else -> {
                                    val dateParam = if (targetDate.isNotEmpty()) "?targetDate=$targetDate" else ""
                                    navController.popBackStack()
                                    navController.navigate("analysis/${m.name}/$count$dateParam")
                                }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                // Text meal analysis (from AiBar)
                composable("analysis/text") {
                    AnalysisResultScreen(
                        state = analysisResultStateHolder,
                        mode = CaptureMode.LogMeal,
                        photoCount = 0,
                        isTextMode = true,
                        onDone = {
                            navController.popBackStack()
                        },
                        onLogged = {
                            navController.popBackStack()
                            navController.navigate(Tab.Log.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
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
                        state = analysisResultStateHolder,
                        mode = mode,
                        photoCount = count,
                        targetDate = targetDate,
                        onDone = {
                            navController.popBackStack(Tab.Home.route, inclusive = false)
                        },
                        onLogged = {
                            navController.popBackStack()
                            navController.navigate(Tab.Log.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                // Chat tab
                composable(Tab.Chat.route) {
                    ChatScreen(
                        state = chatStateHolder,
                        onNavigateToCamera = { navController.navigate("capture/chat") },
                    )
                }
            }

            // Floating AI bar (hidden on camera/chat screens)
            if (!hideAiBar) {
                AiBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    state = aiBarStateHolder,
                    onCameraClick = { showCameraModeSheet = true },
                    onTextMealAnalyzed = { _ ->
                        navController.navigate("analysis/text")
                    },
                    onChatOpen = { message ->
                        com.fatlosstrack.data.local.PendingChatStore.store(message)
                        navController.navigate(Tab.Chat.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
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
                AppLogger.instance?.user("Camera opened: mode=$modeArg")
                navController.navigate("capture/$modeArg")
            },
            onDismiss = { showCameraModeSheet = false },
        )
    }
}
