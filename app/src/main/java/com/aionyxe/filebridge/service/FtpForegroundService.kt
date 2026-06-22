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
import com.aionyxe.filebridge.domain.server.ServerEvent
import com.aionyxe.filebridge.domain.usecase.ObserveConnectionInfoUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveServerEventsUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveServerStateUseCase
import com.aionyxe.filebridge.domain.usecase.StartServerUseCase
import com.aionyxe.filebridge.domain.usecase.StopServerUseCase
import com.aionyxe.filebridge.service.notification.NotificationFactory
import com.aionyxe.filebridge.service.notification.NotificationFactory.Companion.NOTIF_ID
import com.aionyxe.filebridge.widget.FtpServerWidget
import com.aionyxe.filebridge.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
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
    @Inject lateinit var observeServerEventsUseCase: ObserveServerEventsUseCase
    @Inject lateinit var notificationFactory: NotificationFactory
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * Set once the server has reached [ServerState.Running] in this service instance. Guards the
     * teardown-on-[ServerState.Stopped] path so the service does not stop itself on the initial
     * Stopped value observed before the server has even started.
     */
    @Volatile
    private var everRunning = false

    /** Running count of completed file transfers (uploads + downloads) since service start. */
    private var filesTransferred = 0

    override fun onCreate() {
        super.onCreate()
        // Observe state and keep the notification in sync for the whole service lifetime.
        lifecycleScope.launch {
            observeServerStateUseCase().collect { state ->
                when (state) {
                    is ServerState.Running -> {
                        everRunning = true
                        val info = observeConnectionInfoUseCase().first()
                        notificationManager.notify(NOTIF_ID, notificationFactory.running(info))
                        updateWidget(isRunning = true, address = info?.url ?: "${state.address}:${state.port}")
                    }

                    is ServerState.Error -> {
                        // The engine is not running in an Error state (e.g. start failed, or Wi-Fi
                        // dropped). Surface why, then tear the service down. Error never appears as
                        // the initial state, so this is always safe.
                        updateWidget(isRunning = false, address = "")
                        finishWithErrorNotification(state.message)
                    }

                    is ServerState.Stopped -> {
                        updateWidget(isRunning = false, address = "")
                        // Whoever stopped the engine (Stop button, UI, or system), the foreground
                        // service has no reason to stay alive. Guarded by everRunning so the
                        // initial Stopped value at startup does not kill the service prematurely.
                        if (everRunning) finishCleanly()
                    }

                    else -> {
                        /* Starting / Stopping: keep the existing notification. */
                    }
                }
            }
        }

        // Update the notification on every completed file transfer so large batches show live
        // progress. This also keeps the foreground service visibly active for the whole batch.
        lifecycleScope.launch {
            observeServerEventsUseCase().collect { event ->
                val lastPath = when (event) {
                    is ServerEvent.FileUploaded -> event.path
                    is ServerEvent.FileDownloaded -> event.path
                    else -> return@collect // ignore connect/disconnect/auth events here
                }
                // Only reflect progress while the server is actually running.
                if (observeServerStateUseCase().value !is ServerState.Running) return@collect
                filesTransferred++
                val info = observeConnectionInfoUseCase().first()
                notificationManager.notify(
                    NOTIF_ID,
                    notificationFactory.transferring(info, filesTransferred, lastPath),
                )
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
        // If the system kills the service (e.g. low memory, swipe from recents) make sure the
        // engine stops too. Use appScope (not lifecycleScope) so the stop call is not cancelled
        // when the lifecycle moves to DESTROYED immediately after super.onDestroy(). If the engine
        // is already stopped this is a no-op (stop() short-circuits).
        appScope.launch { stopServerUseCase() }
        super.onDestroy()
    }

    // ---- Intent handlers ----

    private fun handleStart() {
        filesTransferred = 0

        // Call startForeground immediately to satisfy the FGS contract (5 s window on Android 14+).
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notificationFactory.starting(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        lifecycleScope.launch {
            startServerUseCase().onFailure { error ->
                // Validation failures never reach the controller, so no ServerState.Error is
                // emitted for them — surface the error and tear down here. (Runtime start failures
                // do set Error and are also handled by the state observer; the teardown is
                // idempotent.)
                finishWithErrorNotification(error.message ?: getString(R.string.error_unknown))
            }
        }
    }

    private fun handleStop() {
        // Trigger the engine stop; the state observer reacts to the resulting Stopped/Error
        // transition and tears the foreground service down. Use appScope so the stop completes
        // even if the service lifecycle is torn down first.
        appScope.launch { stopServerUseCase() }
    }

    // ---- Teardown helpers ----

    /** Removes the foreground notification and stops the service. */
    private fun finishCleanly() {
        @Suppress("DEPRECATION")
        stopForeground(true) // removes notification; compat with API < 33
        stopSelf()
    }

    /**
     * Posts a dismissible error notification, detaches it from the foreground service (so it stays
     * visible after the service stops), and stops the service.
     */
    private fun finishWithErrorNotification(message: String) {
        notificationManager.notify(NOTIF_ID, notificationFactory.error(message))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        stopSelf()
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
