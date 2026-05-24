package com.aionyxe.filebridge.ui.onboarding

import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.AuthMode

/**
 * State for the three-step onboarding pager.
 *
 * Steps:
 *  0 — Welcome
 *  1 — Permissions (storage + notifications)
 *  2 — Quick setup (auth mode, credentials, root dir)
 */
data class OnboardingUiState(
    val currentStep: Int = 0,
    val storagePermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    /** Anonymous by default so Quick Setup is zero-friction. */
    val authMode: AuthMode = AuthMode.ANONYMOUS,
    val username: String = AppSettings.DEFAULT_USERNAME,
    val password: String = "",
    /** Pre-populated with the Downloads folder path; user may override via the picker. */
    val rootDirUri: String = "",
    /** True while [CompleteOnboardingUseCase] is executing. */
    val isLoading: Boolean = false,
    /**
     * True when the user has resolved the storage permission step (granted OR explicitly skipped)
     * so the Next button on step 1 is enabled.
     */
    val storagePermissionResolved: Boolean = false,
    /**
     * True when the user has resolved the notification permission step (granted, denied via the
     * system dialog, or explicitly skipped). On devices below API 33 this starts as `true` because
     * the permission does not exist.
     */
    val notificationPermissionResolved: Boolean = false,
) {
    /**
     * Whether the Finish button on step 2 is enabled.
     * Password/username are only required in SINGLE_USER mode.
     */
    val canFinish: Boolean
        get() = currentStep == 2 && when (authMode) {
            AuthMode.ANONYMOUS -> true
            AuthMode.SINGLE_USER -> username.isNotBlank() && password.isNotEmpty()
        }
}
