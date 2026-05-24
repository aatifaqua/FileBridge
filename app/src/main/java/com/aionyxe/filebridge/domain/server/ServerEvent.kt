package com.aionyxe.filebridge.domain.server

sealed interface ServerEvent {
    data class ClientConnected(val ip: String) : ServerEvent

    data class ClientDisconnected(val ip: String) : ServerEvent

    data class FileUploaded(val ip: String, val path: String) : ServerEvent

    data class FileDownloaded(val ip: String, val path: String) : ServerEvent

    data class AuthFailure(val ip: String, val username: String) : ServerEvent
}
