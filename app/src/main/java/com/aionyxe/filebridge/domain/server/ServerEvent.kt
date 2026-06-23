package com.aionyxe.filebridge.domain.server

sealed interface ServerEvent {
    data class ClientConnected(val ip: String) : ServerEvent

    /**
     * Emitted when a client session ends. Carries a summary of that session's transfers so the
     * log can show "<ip> disconnected · N files · <size> · M failed".
     */
    data class ClientDisconnected(
        val ip: String,
        val filesTransferred: Int = 0,
        val totalBytes: Long = 0,
        val failedCount: Int = 0,
    ) : ServerEvent

    data class FileUploaded(val ip: String, val path: String, val size: Long = 0) : ServerEvent

    data class FileDownloaded(val ip: String, val path: String, val size: Long = 0) : ServerEvent

    /** A STOR/RETR that ended in an FTP error reply (4xx/5xx). */
    data class TransferFailed(val ip: String, val path: String, val upload: Boolean) : ServerEvent

    data class AuthFailure(val ip: String, val username: String) : ServerEvent
}
