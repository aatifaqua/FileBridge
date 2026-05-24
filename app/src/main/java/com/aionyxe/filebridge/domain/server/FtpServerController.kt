package com.aionyxe.filebridge.domain.server

import com.aionyxe.filebridge.domain.model.ServerConfig
import com.aionyxe.filebridge.domain.model.ServerState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Controls the embedded FTP server lifecycle.
 */
interface FtpServerController {
    val state: StateFlow<ServerState>

    val events: SharedFlow<ServerEvent>

    val connectedClientCount: StateFlow<Int>

    suspend fun start(config: ServerConfig)

    suspend fun stop()
}
