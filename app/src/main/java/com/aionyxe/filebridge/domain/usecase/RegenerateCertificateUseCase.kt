package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.domain.server.CertificateManager
import javax.inject.Inject

class RegenerateCertificateUseCase @Inject constructor(
    private val certificateManager: CertificateManager,
) {
    suspend operator fun invoke(): Result<Unit> =
        runCatching { certificateManager.regenerate() }
}
