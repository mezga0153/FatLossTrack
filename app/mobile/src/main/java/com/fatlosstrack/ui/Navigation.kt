package com.fatlosstrack.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fatlosstrack.ui.chat.ChatScreen
import com.fatlosstrack.ui.dashboard.DashboardScreen
import com.fatlosstrack.ui.insights.InsightsScreen
import com.fatlosstrack.ui.logging.LoggingScreen

enum class TopLevelRoute(val route: String, val label: String, val icon: ImageVector) {
    Dashboard("dashboard", "Trend", Icons.Default.ShowChart),
    Logging("logging", "Log", Icons.Default.Restaurant),
    Insights("insights", "Insights", Icons.Default.Insights),
    Chat("chat", "Ask AI", Icons.Default.ChatBubble),
}

@Composable
fun FatLossTrackNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelRoute.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelRoute.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopLevelRoute.Dashboard.route) { DashboardScreen() }
            composable(TopLevelRoute.Logging.route) { LoggingScreen() }
            composable(TopLevelRoute.Insights.route) { InsightsScreen() }
            composable(TopLevelRoute.Chat.route) { ChatScreen() }
        }
    }
}
