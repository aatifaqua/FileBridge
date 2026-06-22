package com.aionyxe.filebridge.domain.model

/**
 * Immutable snapshot of all user-configurable settings. Lives in the domain layer so the UI can
 * consume it without depending on the data layer. The password is intentionally NOT part of this
 * model — it is read on demand from the credentials store and never held alongside other settings.
 */
data class AppSettings(
    val protocol: Protocol = Protocol.FTP,
    val ftpPort: Int = DEFAULT_FTP_PORT,
    val pasvMinPort: Int = DEFAULT_PASV_MIN_PORT,
    val pasvMaxPort: Int = DEFAULT_PASV_MAX_PORT,
    val authMode: AuthMode = AuthMode.SINGLE_USER,
    val username: String = DEFAULT_USERNAME,
    val rootDirUri: String = "",
    val accessMode: AccessMode = AccessMode.READ_WRITE,
    val startOnAppLaunch: Boolean = false,
    val startOnBoot: Boolean = false,
    val keepScreenOn: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingComplete: Boolean = false,
) {
    companion object {
        const val DEFAULT_FTP_PORT = 2221
        const val DEFAULT_PASV_MIN_PORT = 50000
        const val DEFAULT_PASV_MAX_PORT = 51000
        const val DEFAULT_USERNAME = "ftpuser"
    }
}
