package com.aionyxe.filebridge.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aionyxe.filebridge.R
import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.domain.usecase.ObserveConnectionInfoUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveServerStateUseCase
import com.aionyxe.filebridge.domain.usecase.StartServerUseCase
import com.aionyxe.filebridge.domain.usecase.StopServerUseCase
import com.aionyxe.filebridge.service.notification.NotificationFactory
import com.aionyxe.filebridge.service.notification.NotificationFactory.Companion.NOTIF_ID
import com.aionyxe.filebridge.widget.FtpServerWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.glance.appwidget.GlanceAppWidgetManager

/**
 * Foreground service that keeps the FTP engine alive independent of the UI.
 *
 * Lifecycle:
 *  - Started via [ServiceLauncher.start] → `ACTION_START` → `startForeground` immediately,
 *    then calls [StartServerUseCase] and mirrors state into the notification.
 *  - Stopped via [ServiceLauncher.stop] → `ACTION_STOP` → calls [StopServerUseCase], waits for
 *    [ServerState.Stopped], then removes the notification and self-stops.
 *  - System kill (swipe from recents) → `onDestroy` ensures the server is stopped cleanly.
 *
 * UI never binds to this service; it observes [FtpServerController.state] via use cases directly
 * because the controller is a `@Singleton` shared via Hilt.
 */
@AndroidEntryPoint
class FtpForegroundService : LifecycleService() {

    @Inject lateinit var startServerUseCase: StartServerUseCase
    @Inject lateinit var stopServerUseCase: StopServerUseCase
    @Inject lateinit var observeServerStateUseCase: ObserveServerStateUseCase
    @Inject lateinit var observeConnectionInfoUseCase: ObserveConnectionInfoUseCase
    @Inject lateinit var notificationFactory: NotificationFactory

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /** Set to true once we have initiated a stop so the state observer skips re-posting. */
    @Volatile
    private var stopInitiated = false

    override fun onCreate() {
        super.onCreate()
        // Observe state and keep the notification in sync for the whole service lifetime.
        lifecycleScope.launch {
            observeServerStateUseCase().collect { state ->
                if (stopInitiated) return@collect
                when (state) {
                    is ServerState.Running -> {
                        val info = observeConnectionInfoUseCase().first()
                        notificationManager.notify(NOTIF_ID, notificationFactory.running(info))
                        updateWidget(isRunning = true, address = info?.url ?: "${state.address}:${state.port}")
                    }

                    is ServerState.Error -> {
                        // Post dismissible error notification; do not call stopSelf here
                        // — the user may retry. The FGS notification stays until the user
                        // dismisses or the service is explicitly stopped.
                        notificationManager.notify(NOTIF_ID, notificationFactory.error(state.message))
                        updateWidget(isRunning = false, address = "")
                    }

                    is ServerState.Stopped -> {
                        updateWidget(isRunning = false, address = "")
                    }

                    else -> {
                        /* Starting / Stopping: keep the existing notification. */
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // If the system kills the service (e.g. low memory) make sure the engine stops too.
        if (!stopInitiated) {
            lifecycleScope.launch { stopServerUseCase() }
        }
        super.onDestroy()
    }

    // ---- Intent handlers ----

    private fun handleStart() {
        stopInitiated = false

        // Call startForeground immediately to satisfy the FGS contract (5 s window on Android 14+).
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notificationFactory.starting(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        lifecycleScope.launch {
            startServerUseCase().onFailure { error ->
                // Surface error in notification; do not crash the service.
                notificationManager.notify(
                    NOTIF_ID,
                    notificationFactory.error(error.message ?: getString(R.string.error_unknown)),
                )
            }
        }
    }

    private fun handleStop() {
        stopInitiated = true
        lifecycleScope.launch {
            stopServerUseCase()
            // Wait until the controller confirms it is fully stopped before removing the FGS.
            observeServerStateUseCase().first { it is ServerState.Stopped }
            @Suppress("DEPRECATION")
            stopForeground(true) // removes notification; compat with API < 33
            stopSelf()
        }
    }

    // ---- Widget sync ----

    private fun updateWidget(isRunning: Boolean, address: String) {
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@FtpForegroundService)
            val ids = manager.getGlanceIds(FtpServerWidget::class.java)
            ids.forEach { id ->
                FtpServerWidget.updateState(this@FtpForegroundService, id, isRunning, address)
            }
        }
    }

    // ---- Constants ----

    companion object {
        const val ACTION_START = "com.aionyxe.filebridge.action.START_SERVER"
        const val ACTION_STOP = "com.aionyxe.filebridge.action.STOP_SERVER"
    }
}
