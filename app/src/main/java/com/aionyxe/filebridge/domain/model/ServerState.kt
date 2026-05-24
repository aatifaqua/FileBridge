package com.aionyxe.filebridge.domain.model

sealed interface ServerState {
    data object Stopped : ServerState

    data object Starting : ServerState

    data class Running(
        val address: String,
        val port: Int,
        val protocol: Protocol,
        val connectedClients: Int,
    ) : ServerState

    data object Stopping : ServerState

    data class Error(val message: String) : ServerState
}
