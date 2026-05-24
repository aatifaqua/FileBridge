package com.aionyxe.filebridge.ui.onboarding

import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.usecase.CompleteOnboardingUseCase
import com.aionyxe.filebridge.domain.usecase.GetAppSettingsUseCase
import com.aionyxe.filebridge.domain.usecase.ListStorageRootsUseCase
import com.aionyxe.filebridge.domain.usecase.SetCredentialsUseCase
import com.aionyxe.filebridge.domain.usecase.UpdateAppSettingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @Suppress("UnusedPrivateMember") private val getSettings: GetAppSettingsUseCase,
    @Suppress("UnusedPrivateMember") private val updateSetting: UpdateAppSettingUseCase,
    @Suppress("UnusedPrivateMember") private val setCredentials: SetCredentialsUseCase,
    @Suppress("UnusedPrivateMember") private val listStorageRoots: ListStorageRootsUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            authMode = AuthMode.ANONYMOUS,
            // Pre-fill the Downloads folder — accessible without full storage permission.
            // Falls back to empty string in unit-test environments where the API returns null.
            rootDirUri = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?.absolutePath
                ?: "",
            // Notification permission doesn't exist below API 33; treat it as already resolved.
            notificationPermissionResolved = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
        ),
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    // ---- Navigation ----

    fun onNext() {
        _uiState.update { state ->
            when {
                state.currentStep < 2 -> state.copy(currentStep = state.currentStep + 1)
                else -> state
            }
        }
    }

    fun onBack() {
        _uiState.update { state ->
            when {
                state.currentStep > 0 -> state.copy(currentStep = state.currentStep - 1)
                else -> state
            }
        }
    }

    // ---- Permission callbacks ----

    fun onStoragePermissionResult(granted: Boolean) {
        _uiState.update { it.copy(storagePermissionGranted = granted, storagePermissionResolved = true) }
    }

    fun onStoragePermissionSkipped() {
        _uiState.update { it.copy(storagePermissionResolved = true) }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                notificationPermissionGranted = granted,
                notificationPermissionResolved = true,
            )
        }
    }

    fun onNotificationPermissionSkipped() {
        _uiState.update { it.copy(notificationPermissionResolved = true) }
    }

    // ---- Quick setup ----

    fun onAuthModeChanged(mode: AuthMode) {
        _uiState.update { it.copy(authMode = mode) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun onRootDirSelected(uriString: String) {
        _uiState.update { it.copy(rootDirUri = uriString) }
    }

    // ---- Finish ----

    /**
     * Persists auth settings and marks onboarding complete.
     * The [MainViewModel.onboardingComplete] flow will flip to `true`,
     * causing [MainScreen] to navigate to Home automatically.
     */
    fun onFinish() {
        val state = _uiState.value
        if (!state.canFinish || state.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            completeOnboarding(
                authMode = state.authMode,
                username = state.username,
                password = state.password,
                rootDirUri = state.rootDirUri,
            )
            // Navigation happens automatically via MainViewModel.onboardingComplete.
            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
