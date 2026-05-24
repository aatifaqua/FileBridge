package com.aionyxe.filebridge

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.service.ServiceLauncher
import com.aionyxe.filebridge.ui.MainScreen
import com.aionyxe.filebridge.ui.MainViewModel
import com.aionyxe.filebridge.ui.onboarding.isStoragePermissionGranted
import com.aionyxe.filebridge.ui.theme.FileBridgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val serverState by viewModel.serverState.collectAsStateWithLifecycle()

            // Keep screen on while the server is running and the setting is enabled.
            if (settings.keepScreenOn && serverState is ServerState.Running) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            FileBridgeTheme(
                themeMode = settings.themeMode,
                dynamicColor = true,
            ) {
                MainScreen(viewModel = viewModel)
            }
        }

        // Check once synchronously because viewModel.settings is a StateFlow with a cached value
        // and this only needs to fire once per cold start.
        maybeAutoStart()
    }

    private fun maybeAutoStart() {
        val settings = viewModel.settings.value
        val serverState = viewModel.serverState.value
        if (settings.startOnAppLaunch &&
            settings.onboardingComplete &&
            isStoragePermissionGranted(this) &&
            serverState is ServerState.Stopped
        ) {
            ServiceLauncher.start(this)
        }
    }
}
