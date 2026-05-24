package com.aionyxe.filebridge.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aionyxe.filebridge.ui.home.HomeScreen
import com.aionyxe.filebridge.ui.logs.LogsScreen
import com.aionyxe.filebridge.ui.onboarding.OnboardingScreen
import com.aionyxe.filebridge.ui.settings.SettingsScreen

@Composable
fun FileBridgeNavHost(
    navController: NavHostController,
    startDestination: Destination,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier,
    ) {
        composable(Destination.Onboarding.route) {
            OnboardingScreen()
        }
        composable(Destination.Home.route) {
            HomeScreen(snackbarHostState = snackbarHostState)
        }
        composable(Destination.Logs.route) {
            LogsScreen()
        }
        composable(Destination.Settings.route) {
            SettingsScreen()
        }
    }
}

@Composable
fun FileBridgeBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Hide bottom bar on Onboarding.
    val onOnboarding = currentDestination?.hierarchy
        ?.any { it.route == Destination.Onboarding.route } == true
    if (onOnboarding) return

    NavigationBar {
        Destination.bottomBarDestinations.forEach { dest ->
            val selected = currentDestination?.hierarchy
                ?.any { it.route == dest.route } == true
            val label = when (dest) {
                is Destination.Home -> stringResource(Destination.Home.label)
                is Destination.Logs -> stringResource(Destination.Logs.label)
                is Destination.Settings -> stringResource(Destination.Settings.label)
                else -> ""
            }
            val icon = when (dest) {
                is Destination.Home -> Destination.Home.icon
                is Destination.Logs -> Destination.Logs.icon
                is Destination.Settings -> Destination.Settings.icon
                else -> return@forEach
            }
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}
