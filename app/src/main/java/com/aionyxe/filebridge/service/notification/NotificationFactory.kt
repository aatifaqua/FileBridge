package com.aionyxe.filebridge.service.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aionyxe.filebridge.MainActivity
import com.aionyxe.filebridge.R
import com.aionyxe.filebridge.domain.model.ConnectionInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds all notifications used by [FtpForegroundService].
 * The notification channel is registered in [FileBridgeApp.onCreate]; this class only creates
 * [Notification] instances.
 */
@Singleton
class NotificationFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // ---- Channel ----

    fun registerChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    // ---- Notification builders ----

    /** Shown immediately when ACTION_START is received, before the server has started. */
    fun starting(): Notification =
        baseBuilder()
            .setContentTitle(context.getString(R.string.notif_title_starting))
            .setProgress(0, 0, true) // indeterminate
            .setOngoing(true)
            .build()

    /**
     * Shown once the server is [Running]. Includes the server URL and a Stop action.
     * The notification is ongoing and non-dismissible.
     */
    fun running(info: ConnectionInfo?): Notification {
        val stopPi = stopPendingIntent()

        val body = info?.url ?: ""

        return baseBuilder()
            .setContentTitle(context.getString(R.string.notif_title_running))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .addAction(0, context.getString(R.string.notif_action_stop), stopPi)
            .build()
    }

    /**
     * Shown while files are actively being transferred. Identical to [running] but with a live
     * count of completed transfers and the most recent file path, so the user sees progress and
     * the foreground service stays visibly active during large batches.
     */
    fun transferring(info: ConnectionInfo?, filesTransferred: Int, lastPath: String): Notification {
        val stopPi = stopPendingIntent()
        val summary = context.getString(R.string.notif_transfer_summary, filesTransferred)
        val body = buildString {
            append(summary)
            if (lastPath.isNotBlank()) {
                append('\n')
                append(lastPath)
            }
            info?.url?.takeIf { it.isNotBlank() }?.let {
                append('\n')
                append(it)
            }
        }

        return baseBuilder()
            .setContentTitle(context.getString(R.string.notif_title_running))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(0, context.getString(R.string.notif_action_stop), stopPi)
            .build()
    }

    /** Shown when the server has encountered an error. Dismissible. */
    fun error(message: String): Notification =
        baseBuilder()
            .setContentTitle(context.getString(R.string.notif_title_error))
            .setContentText(message)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()

    // ---- Private helpers ----

    private fun stopPendingIntent(): PendingIntent {
        // Broadcast (not getService): notification taps run in the background where starting a
        // service is unreliable. ServerActionReceiver stops the engine in-process instead.
        val stopIntent = Intent(context, com.aionyxe.filebridge.service.ServerActionReceiver::class.java)
            .setAction(com.aionyxe.filebridge.service.ServerActionReceiver.ACTION_STOP)
        return PendingIntent.getBroadcast(
            context, REQUEST_STOP, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context, REQUEST_CONTENT, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentPi)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    companion object {
        const val CHANNEL_ID = "server_status"
        const val NOTIF_ID = 1

        private const val REQUEST_CONTENT = 100
        private const val REQUEST_STOP = 101
    }
}
