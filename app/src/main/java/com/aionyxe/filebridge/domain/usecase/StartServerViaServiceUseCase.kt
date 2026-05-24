package com.aionyxe.filebridge.domain.usecase

import android.content.Context
import com.aionyxe.filebridge.service.ServiceLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Starts the FTP server by sending ACTION_START to [FtpForegroundService].
 * UI calls this instead of knowing about service intents directly.
 */
class StartServerViaServiceUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke() = ServiceLauncher.start(context)
}
