package com.aionyxe.filebridge.domain.model

import java.io.File

/**
 * Fully-resolved configuration consumed by the FTP engine. Built by [com.aionyxe.filebridge.domain
 * .usecase.StartServerUseCase] from settings + credentials + a resolved root directory.
 */
data class ServerConfig(
    val protocol: Protocol,
    val port: Int,
    val pasvMinPort: Int,
    val pasvMaxPort: Int,
    val authMode: AuthMode,
    val username: String,
    val password: String?,
    val rootDir: File,
    val accessMode: AccessMode,
)
