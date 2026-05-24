package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.data.credentials.CredentialsRepository
import com.aionyxe.filebridge.data.settings.SettingsRepository
import com.aionyxe.filebridge.domain.validation.SettingsValidator
import com.aionyxe.filebridge.domain.validation.ValidationException
import com.aionyxe.filebridge.domain.validation.ValidationResult
import javax.inject.Inject

/**
 * Persists single-user credentials: username to settings, password to encrypted storage.
 */
class SetCredentialsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val credentialsRepository: CredentialsRepository,
    private val validator: SettingsValidator,
) {
    suspend operator fun invoke(username: String, password: String): Result<Unit> {
        (validator.validateUsername(username) as? ValidationResult.Invalid)?.let {
            return Result.failure(ValidationException(it.reason))
        }
        (validator.validatePassword(password) as? ValidationResult.Invalid)?.let {
            return Result.failure(ValidationException(it.reason))
        }
        return runCatching {
            settingsRepository.setUsername(username)
            credentialsRepository.setPassword(password)
        }
    }

    /** Clears the stored password (used when switching to anonymous mode). */
    suspend fun clearForAnonymous(): Result<Unit> =
        runCatching { credentialsRepository.clear() }
}
