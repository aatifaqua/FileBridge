package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.domain.server.FtpServerController
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveServerStateUseCase @Inject constructor(
    private val controller: FtpServerController,
) {
    operator fun invoke(): StateFlow<ServerState> = controller.state
}
