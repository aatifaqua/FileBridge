package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.data.credentials.CredentialsRepository
import com.aionyxe.filebridge.data.settings.SettingsRepository
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.ConnectionInfo
import com.aionyxe.filebridge.domain.model.Protocol
import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.domain.server.FtpServerController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Emits connection details while the server is running, or null when it is not.
 */
class ObserveConnectionInfoUseCase @Inject constructor(
    private val controller: FtpServerController,
    private val settingsRepository: SettingsRepository,
    private val credentialsRepository: CredentialsRepository,
) {
    operator fun invoke(): Flow<ConnectionInfo?> =
        combine(controller.state, settingsRepository.settings) { state, settings ->
            val running = state as? ServerState.Running ?: return@combine null
            val scheme = if (running.protocol == Protocol.FTPS) "ftps" else "ftp"
            val anonymous = settings.authMode == AuthMode.ANONYMOUS
            ConnectionInfo(
                url = "$scheme://${running.address}:${running.port}",
                username = if (anonymous) null else settings.username,
                password = if (anonymous) null else credentialsRepository.getPassword(),
                protocol = running.protocol,
            )
        }
}
