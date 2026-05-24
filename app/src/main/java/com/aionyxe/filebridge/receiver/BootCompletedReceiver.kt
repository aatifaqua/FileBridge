package com.aionyxe.filebridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aionyxe.filebridge.data.settings.SettingsRepository
import com.aionyxe.filebridge.service.ServiceLauncher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Receives [Intent.ACTION_BOOT_COMPLETED] and starts [FtpForegroundService] if the user has
 * enabled "Auto-start on device boot".
 *
 * Uses [runBlocking] which is acceptable in a short-lived BroadcastReceiver context.
 * The receiver is NOT `directBootAware` — auto-start waits for the first user unlock.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) return

        val startOnBoot = runBlocking { settingsRepository.settings.first().startOnBoot }
        if (startOnBoot) {
            ServiceLauncher.start(context)
        }
    }
}
