package com.aionyxe.filebridge.data.logs

enum class LogType {
    SERVER_STARTED,
    SERVER_STOPPED,
    CLIENT_CONNECTED,
    CLIENT_DISCONNECTED,
    FILE_UPLOADED,
    FILE_DOWNLOADED,
    TRANSFER_FAILED,
    AUTH_FAILURE,
}
