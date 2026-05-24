package com.aionyxe.filebridge.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Thin helper that hides the Intent construction from call sites. UI layers and use cases call
 * [start] / [stop] without knowing the service class name or action string.
 */
object ServiceLauncher {

    /** Starts the FTP server foreground service. Safe to call from any thread. */
    fun start(context: Context) {
        val intent = Intent(context, FtpForegroundService::class.java)
            .setAction(FtpForegroundService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    /** Sends ACTION_STOP to the already-running service. No-op if the service is not running. */
    fun stop(context: Context) {
        val intent = Intent(context, FtpForegroundService::class.java)
            .setAction(FtpForegroundService.ACTION_STOP)
        // Plain startService is fine for a stop command — we do not need FGS privileges here.
        context.startService(intent)
    }
}
