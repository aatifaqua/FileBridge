package com.aionyxe.filebridge.data.logs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory, non-persisted activity log backed by a [MutableStateFlow]. Trims to the most recent
 * [MAX_ENTRIES] in FIFO order.
 */
@Singleton
class LogRepositoryImpl @Inject constructor() : LogRepository {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    override val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    override fun append(entry: LogEntry) {
        _entries.update { current ->
            val next = current + entry
            if (next.size > MAX_ENTRIES) {
                next.subList(next.size - MAX_ENTRIES, next.size).toList()
            } else {
                next
            }
        }
    }

    override fun clear() {
        _entries.value = emptyList()
    }

    companion object {
        const val MAX_ENTRIES = 500
    }
}
