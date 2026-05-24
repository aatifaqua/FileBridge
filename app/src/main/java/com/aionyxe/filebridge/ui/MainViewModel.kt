package com.aionyxe.filebridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.domain.usecase.GetAppSettingsUseCase
import com.aionyxe.filebridge.domain.usecase.IsOnboardingCompleteUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveServerStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    getAppSettingsUseCase: GetAppSettingsUseCase,
    isOnboardingCompleteUseCase: IsOnboardingCompleteUseCase,
    observeServerStateUseCase: ObserveServerStateUseCase,
) : ViewModel() {

    val settings = getAppSettingsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val onboardingComplete = isOnboardingCompleteUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val serverState = observeServerStateUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerState.Stopped)
}
