package com.aionyxe.filebridge.domain.server

import com.aionyxe.filebridge.domain.model.ServerConfig
import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.domain.model.ServerStats
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Controls the embedded FTP server lifecycle.
 */
interface FtpServerController {
    val state: StateFlow<ServerState>

    val events: SharedFlow<ServerEvent>

    val connectedClientCount: StateFlow<Int>

    /** Aggregate transfer stats for the current session; resets on [start]. */
    val stats: StateFlow<ServerStats>

    suspend fun start(config: ServerConfig)

    suspend fun stop()
}
