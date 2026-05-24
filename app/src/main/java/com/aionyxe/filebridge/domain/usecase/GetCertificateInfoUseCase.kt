package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.domain.model.CertificateInfo
import com.aionyxe.filebridge.domain.server.CertificateManager
import javax.inject.Inject

class GetCertificateInfoUseCase @Inject constructor(
    private val certificateManager: CertificateManager,
) {
    suspend operator fun invoke(): CertificateInfo? = certificateManager.info()
}
