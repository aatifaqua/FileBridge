package com.aionyxe.filebridge.data.server

import com.aionyxe.filebridge.domain.server.ServerEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-wide bus for FTP server events. Collectors receive events without backpressure;
 * the oldest event is dropped when the buffer (64) is full so the server thread is never blocked.
 */
@Singleton
class ServerEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<ServerEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    /** Non-suspending emit suitable for calling from FTPServer's internal threads. */
    fun tryEmit(event: ServerEvent) {
        _events.tryEmit(event)
    }
}
