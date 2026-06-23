package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.domain.server.FtpServerController
import com.aionyxe.filebridge.domain.server.ServerEvent
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

/**
 * Streams individual server-side events (connections, uploads, downloads, auth failures).
 *
 * Used by the foreground service to keep its notification fresh during large transfer batches,
 * which also keeps the foreground service from being considered idle.
 */
class ObserveServerEventsUseCase @Inject constructor(
    private val controller: FtpServerController,
) {
    operator fun invoke(): SharedFlow<ServerEvent> = controller.events
}
