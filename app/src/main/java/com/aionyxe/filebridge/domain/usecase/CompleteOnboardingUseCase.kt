package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.data.credentials.CredentialsRepository
import com.aionyxe.filebridge.data.settings.SettingsRepository
import com.aionyxe.filebridge.domain.model.AuthMode
import javax.inject.Inject

/**
 * Saves the initial auth mode, credentials and root directory chosen during onboarding, then marks
 * onboarding complete so it is never shown again.
 */
class CompleteOnboardingUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val credentialsRepository: CredentialsRepository,
) {
    suspend operator fun invoke(
        authMode: AuthMode,
        username: String,
        password: String,
        rootDirUri: String,
    ): Result<Unit> = runCatching {
        settingsRepository.setAuthMode(authMode)
        if (authMode == AuthMode.SINGLE_USER) {
            settingsRepository.setUsername(username)
            credentialsRepository.setPassword(password)
        } else {
            credentialsRepository.clear()
        }
        settingsRepository.setRootDirUri(rootDirUri)
        settingsRepository.setOnboardingComplete(true)
    }
}
