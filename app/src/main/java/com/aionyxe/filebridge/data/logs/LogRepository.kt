package com.aionyxe.filebridge.data.logs

import kotlinx.coroutines.flow.StateFlow

interface LogRepository {
    val entries: StateFlow<List<LogEntry>>

    fun append(entry: LogEntry)

    fun clear()
}
