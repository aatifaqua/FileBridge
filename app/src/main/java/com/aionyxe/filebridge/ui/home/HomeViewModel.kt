package com.aionyxe.filebridge.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.ConnectionInfo
import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.domain.model.ServerStats
import com.aionyxe.filebridge.domain.usecase.GetAppSettingsUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveConnectedClientsUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveConnectionInfoUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveServerStateUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveServerStatsUseCase
import com.aionyxe.filebridge.domain.usecase.StartServerViaServiceUseCase
import com.aionyxe.filebridge.domain.usecase.StopServerViaServiceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeServerStateUseCase: ObserveServerStateUseCase,
    observeConnectionInfoUseCase: ObserveConnectionInfoUseCase,
    observeConnectedClientsUseCase: ObserveConnectedClientsUseCase,
    observeServerStatsUseCase: ObserveServerStatsUseCase,
    getAppSettingsUseCase: GetAppSettingsUseCase,
    private val startServerViaServiceUseCase: StartServerViaServiceUseCase,
    private val stopServerViaServiceUseCase: StopServerViaServiceUseCase,
) : ViewModel() {

    private val _isPasswordRevealed = MutableStateFlow(false)
    private val _transientError = MutableStateFlow<String?>(null)

    private data class Snapshot(
        val state: ServerState,
        val info: ConnectionInfo?,
        val clients: Int,
        val stats: ServerStats,
    )

    val uiState = combine(
        combine(
            observeServerStateUseCase(),
            observeConnectionInfoUseCase(),
            observeConnectedClientsUseCase(),
            observeServerStatsUseCase(),
        ) { state, info, clients, stats -> Snapshot(state, info, clients, stats) },
        combine(
            getAppSettingsUseCase(),
            _isPasswordRevealed,
            _transientError,
        ) { settings, revealed, error -> Triple(settings, revealed, error) },
    ) { snap, (settings, revealed, error) ->
        HomeUiState(
            serverState = snap.state,
            connectionInfo = snap.info,
            connectedClients = snap.clients,
            stats = snap.stats,
            isAnonymous = settings.authMode == AuthMode.ANONYMOUS,
            isPasswordRevealed = revealed,
            transientError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    // ---- Events ----

    fun onStartClicked() {
        startServerViaServiceUseCase()
    }

    fun onStopClicked() {
        stopServerViaServiceUseCase()
    }

    fun onTogglePasswordVisibility() {
        _isPasswordRevealed.update { !it }
    }

    fun onErrorShown() {
        _transientError.value = null
    }

    fun onServerError(message: String) {
        _transientError.value = message
    }
}
