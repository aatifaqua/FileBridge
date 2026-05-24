package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.domain.server.FtpServerController
import javax.inject.Inject

class StopServerUseCase @Inject constructor(
    private val controller: FtpServerController,
) {
    suspend operator fun invoke(): Result<Unit> = runCatching { controller.stop() }
}
