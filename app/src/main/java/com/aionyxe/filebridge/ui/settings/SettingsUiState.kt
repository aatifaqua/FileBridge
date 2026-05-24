package com.aionyxe.filebridge.ui.settings

import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.CertificateInfo

/**
 * Snapshot of everything the Settings screen needs to render.
 *
 * Port numbers are stored as plain Strings so the UI can display partially-typed values without
 * immediately converting to Int (which would reset the cursor or truncate leading zeros).
 * [validationErrors] maps each [SettingsField] to a string-resource id for the error message.
 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** Current text in the FTP port field (may be invalid). */
    val ftpPortStr: String = AppSettings.DEFAULT_FTP_PORT.toString(),
    /** Current text in the PASV min-port field. */
    val pasvMinStr: String = AppSettings.DEFAULT_PASV_MIN_PORT.toString(),
    /** Current text in the PASV max-port field. */
    val pasvMaxStr: String = AppSettings.DEFAULT_PASV_MAX_PORT.toString(),
    /** Password as held in the ViewModel's editing buffer (never persisted here). */
    val passwordPlaintext: String = "",
    /** Whether the password characters are visible in the UI. */
    val isPasswordRevealed: Boolean = false,
    val isSdCardAvailable: Boolean = false,
    val certInfo: CertificateInfo? = null,
    /** True when the server is Starting or Running — locks protocol/port/auth/root-dir. */
    val serverRunning: Boolean = false,
    /** Per-field inline error string resources. */
    val validationErrors: Map<SettingsField, Int> = emptyMap(),
    /** True while [RegenerateCertificateUseCase] is executing in the background. */
    val isRegeneratingCert: Boolean = false,
)
