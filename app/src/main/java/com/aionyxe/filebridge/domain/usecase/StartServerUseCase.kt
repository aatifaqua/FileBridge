package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.R
import com.aionyxe.filebridge.data.credentials.CredentialsRepository
import com.aionyxe.filebridge.data.settings.SettingsRepository
import com.aionyxe.filebridge.data.storage.StorageRepository
import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.ServerConfig
import com.aionyxe.filebridge.domain.server.FtpServerController
import com.aionyxe.filebridge.domain.validation.SettingsValidator
import com.aionyxe.filebridge.domain.validation.ValidationException
import com.aionyxe.filebridge.domain.validation.ValidationResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Validates settings, builds a [ServerConfig], and starts the FTP server. Returns a failed [Result]
 * carrying a [ValidationException] when inputs are invalid or the root directory cannot be resolved.
 */
class StartServerUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val credentialsRepository: CredentialsRepository,
    private val storageRepository: StorageRepository,
    private val controller: FtpServerController,
    private val validator: SettingsValidator,
) {
    suspend operator fun invoke(): Result<Unit> {
        val settings = settingsRepository.settings.first()

        validate(settings)?.let { return Result.failure(it) }

        val password = if (settings.authMode == AuthMode.SINGLE_USER) {
            credentialsRepository.getPassword()
        } else {
            null
        }
        if (settings.authMode == AuthMode.SINGLE_USER && password.isNullOrEmpty()) {
            return Result.failure(ValidationException(R.string.error_password_blank))
        }

        val rootDir = storageRepository.resolveRootDir(settings.rootDirUri)
            ?: return Result.failure(ValidationException(R.string.error_root_dir_unresolved))

        val config = ServerConfig(
            protocol = settings.protocol,
            port = settings.ftpPort,
            pasvMinPort = settings.pasvMinPort,
            pasvMaxPort = settings.pasvMaxPort,
            authMode = settings.authMode,
            username = settings.username,
            password = password,
            rootDir = rootDir,
            accessMode = settings.accessMode,
        )

        return runCatching { controller.start(config) }
    }

    private fun validate(settings: AppSettings): ValidationException? {
        (validator.validatePort(settings.ftpPort) as? ValidationResult.Invalid)?.let {
            return ValidationException(it.reason)
        }
        (
            validator.validatePasvRange(
                settings.pasvMinPort,
                settings.pasvMaxPort,
                settings.ftpPort,
            ) as? ValidationResult.Invalid
            )?.let { return ValidationException(it.reason) }
        if (settings.authMode == AuthMode.SINGLE_USER) {
            (validator.validateUsername(settings.username) as? ValidationResult.Invalid)?.let {
                return ValidationException(it.reason)
            }
        }
        return null
    }
}
