package com.aionyxe.filebridge.domain.model

data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val expiresAt: Long,
    val sha256Fingerprint: String,
)
