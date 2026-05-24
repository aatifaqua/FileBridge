package com.aionyxe.filebridge.data.logs

data class LogEntry(
    val timestamp: Long,
    val type: LogType,
    val message: String,
    val ip: String? = null,
)
