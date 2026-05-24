package com.aionyxe.filebridge.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.aionyxe.filebridge.ui.navigation.Destination
import com.aionyxe.filebridge.ui.navigation.FileBridgeBottomBar
import com.aionyxe.filebridge.ui.navigation.FileBridgeNavHost

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val startDestination = if (onboardingComplete) Destination.Home else Destination.Onboarding

    // When onboarding completes mid-session (ViewModel flips the flag), navigate to Home and
    // clear the onboarding route from the back stack so Back doesn't return to it.
    LaunchedEffect(onboardingComplete) {
        if (onboardingComplete) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute == Destination.Onboarding.route) {
                navController.navigate(Destination.Home.route) {
                    popUpTo(Destination.Onboarding.route) { inclusive = true }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { FileBridgeBottomBar(navController) },
    ) { innerPadding ->
        FileBridgeNavHost(
            navController = navController,
            startDestination = startDestination,
            snackbarHostState = snackbarHostState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}
