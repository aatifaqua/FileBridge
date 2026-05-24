package com.aionyxe.filebridge.domain.usecase

import android.content.Context
import com.aionyxe.filebridge.service.ServiceLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Stops the FTP server by sending ACTION_STOP to [FtpForegroundService].
 * UI calls this instead of knowing about service intents directly.
 */
class StopServerViaServiceUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke() = ServiceLauncher.stop(context)
}
