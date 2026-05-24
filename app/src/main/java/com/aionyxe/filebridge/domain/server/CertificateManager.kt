package com.aionyxe.filebridge.domain.server

import com.aionyxe.filebridge.domain.model.CertificateInfo
import java.security.KeyStore

/**
 * Manages the self-signed FTPS certificate.
 */
interface CertificateManager {
    /** Returns the existing keystore, generating a self-signed certificate on first use. */
    suspend fun getOrCreate(): KeyStore

    /** Deletes the existing certificate/key and generates a fresh one. */
    suspend fun regenerate()

    /** Parsed details of the current certificate, or null if none exists yet. */
    suspend fun info(): CertificateInfo?

    /** The password protecting the keystore (generated once, stored encrypted). */
    suspend fun keystorePassword(): String
}
