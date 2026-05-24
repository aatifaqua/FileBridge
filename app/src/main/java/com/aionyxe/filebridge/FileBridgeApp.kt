package com.aionyxe.filebridge

import android.app.Application
import com.aionyxe.filebridge.service.notification.NotificationFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FileBridgeApp : Application() {

    // Injected lazily after Hilt initialises the component graph.
    @Inject lateinit var notificationFactory: NotificationFactory

    override fun onCreate() {
        super.onCreate()
        // Register the server-status notification channel once at startup (idempotent).
        notificationFactory.registerChannel()
    }
}
