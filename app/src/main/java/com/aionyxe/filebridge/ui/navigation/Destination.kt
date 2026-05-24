package com.aionyxe.filebridge.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String) {

    /** Full-screen onboarding flow — no bottom bar. */
    data object Onboarding : Destination("onboarding")

    /** Home tab — server status + start/stop. */
    data object Home : Destination("home") {
        val label: Int = com.aionyxe.filebridge.R.string.nav_home
        val icon: ImageVector = Icons.Outlined.Wifi
    }

    /** Logs tab — connection and transfer events. */
    data object Logs : Destination("logs") {
        val label: Int = com.aionyxe.filebridge.R.string.nav_logs
        val icon: ImageVector = Icons.Outlined.History
    }

    /** Settings tab — all configurable options. */
    data object Settings : Destination("settings") {
        val label: Int = com.aionyxe.filebridge.R.string.nav_settings
        val icon: ImageVector = Icons.Outlined.Settings
    }

    companion object {
        val bottomBarDestinations: List<Destination> = listOf(Home, Logs, Settings)
    }
}
