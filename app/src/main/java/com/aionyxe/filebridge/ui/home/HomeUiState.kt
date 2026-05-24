package com.aionyxe.filebridge.ui.home

import com.aionyxe.filebridge.domain.model.ConnectionInfo
import com.aionyxe.filebridge.domain.model.ServerState

data class HomeUiState(
    val serverState: ServerState = ServerState.Stopped,
    val connectionInfo: ConnectionInfo? = null,
    val connectedClients: Int = 0,
    val isAnonymous: Boolean = false,
    val isPasswordRevealed: Boolean = false,
    /** One-shot message to show in the Snackbar; null after it has been consumed. */
    val transientError: String? = null,
)
