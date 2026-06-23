package com.aionyxe.filebridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aionyxe.filebridge.di.ApplicationScope
import com.aionyxe.filebridge.domain.usecase.StopServerUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles notification action buttons (currently only Stop).
 *
 * Why a receiver instead of a `getService` PendingIntent: tapping a notification action runs while
 * the app is in the background, where `Context.startService()` is unreliable. A broadcast is always
 * delivered, and we stop the engine in-process via the shared `@Singleton` controller. The
 * foreground service then observes the resulting [com.aionyxe.filebridge.domain.model.ServerState]
 * transition and tears itself down — so the notification's Stop button works regardless of the
 * background-start restrictions that affect direct service starts.
 *
 * Dependencies are pulled via [EntryPointAccessors] rather than `@AndroidEntryPoint` to avoid the
 * Kotlin "abstract member cannot be accessed directly" issue with `super.onReceive`.
 */
class ServerActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun stopServerUseCase(): StopServerUseCase

        @ApplicationScope
        fun appScope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java,
        )

        // Stopping drains in-flight transfers (~5 s); keep the receiver alive until it completes.
        val pendingResult = goAsync()
        entryPoint.appScope().launch {
            try {
                entryPoint.stopServerUseCase().invoke()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.aionyxe.filebridge.action.STOP_SERVER_FROM_NOTIFICATION"
    }
}
