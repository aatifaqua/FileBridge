package com.aionyxe.filebridge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aionyxe.filebridge.data.credentials.CredentialsRepository
import com.aionyxe.filebridge.domain.model.AccessMode
import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.CertificateInfo
import com.aionyxe.filebridge.domain.model.Protocol
import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.domain.model.ThemeMode
import com.aionyxe.filebridge.domain.usecase.GetAppSettingsUseCase
import com.aionyxe.filebridge.domain.usecase.GetCertificateInfoUseCase
import com.aionyxe.filebridge.domain.usecase.IsSdCardPresentUseCase
import com.aionyxe.filebridge.domain.usecase.ListStorageRootsUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveServerStateUseCase
import com.aionyxe.filebridge.domain.usecase.RegenerateCertificateUseCase
import com.aionyxe.filebridge.domain.usecase.SetCredentialsUseCase
import com.aionyxe.filebridge.domain.usecase.SettingPatch
import com.aionyxe.filebridge.domain.usecase.UpdateAppSettingUseCase
import com.aionyxe.filebridge.domain.validation.SettingsValidator
import com.aionyxe.filebridge.domain.validation.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettings: GetAppSettingsUseCase,
    private val updateSetting: UpdateAppSettingUseCase,
    private val setCredentials: SetCredentialsUseCase,
    @Suppress("UnusedPrivateMember") private val listStorageRoots: ListStorageRootsUseCase,
    private val isSdCardPresent: IsSdCardPresentUseCase,
    private val getCertificateInfo: GetCertificateInfoUseCase,
    private val regenerateCertificate: RegenerateCertificateUseCase,
    private val observeServerState: ObserveServerStateUseCase,
    private val credentialsRepository: CredentialsRepository,
    private val validator: SettingsValidator,
) : ViewModel() {

    // ---- Mutable editing buffers ----

    /** Port field text (keeps partially-typed strings visible without conversion). */
    private val _ftpPortStr = MutableStateFlow("")
    private val _pasvMinStr = MutableStateFlow("")
    private val _pasvMaxStr = MutableStateFlow("")

    /** Password plaintext held only in the ViewModel (never written to AppSettings). */
    private val _passwordText = MutableStateFlow("")
    private val _isPasswordRevealed = MutableStateFlow(false)
    private val _certInfo = MutableStateFlow<CertificateInfo?>(null)
    private val _isSdCard = MutableStateFlow(false)
    private val _isRegeneratingCert = MutableStateFlow(false)
    private val _validationErrors = MutableStateFlow<Map<SettingsField, Int>>(emptyMap())

    // ---- Derived UI state ----

    /**
     * Nested [combine] to handle more than 5 upstream flows.
     * Block A: settings snapshot + server lock state + validation errors.
     * Block B: local editing buffers (ports + password + reveals).
     * Outer combine merges the two groups into [SettingsUiState].
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            getSettings(),
            observeServerState(),
            _validationErrors,
        ) { settings, serverState, errors ->
            PartialA(settings, serverState, errors)
        },
        combine(
            _ftpPortStr,
            _pasvMinStr,
            _pasvMaxStr,
            _passwordText,
            _isPasswordRevealed,
        ) { port, min, max, pwd, revealed ->
            PartialB(port, min, max, pwd, revealed)
        },
        _certInfo,
        _isSdCard,
        _isRegeneratingCert,
    ) { a, b, certInfo, isSdCard, isRegen ->
        val serverRunning =
            a.serverState is ServerState.Running || a.serverState is ServerState.Starting
        SettingsUiState(
            settings = a.settings,
            ftpPortStr = b.portStr.ifEmpty { a.settings.ftpPort.toString() },
            pasvMinStr = b.pasvMinStr.ifEmpty { a.settings.pasvMinPort.toString() },
            pasvMaxStr = b.pasvMaxStr.ifEmpty { a.settings.pasvMaxPort.toString() },
            passwordPlaintext = b.passwordText,
            isPasswordRevealed = b.isPasswordRevealed,
            isSdCardAvailable = isSdCard,
            certInfo = certInfo,
            serverRunning = serverRunning,
            validationErrors = a.validationErrors,
            isRegeneratingCert = isRegen,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    init {
        _isSdCard.value = isSdCardPresent()
        viewModelScope.launch(Dispatchers.IO) {
            _certInfo.value = getCertificateInfo()
            _passwordText.value = credentialsRepository.getPassword() ?: ""
        }
    }

    // ---- Server section ----

    fun onProtocolChanged(protocol: Protocol) {
        if (uiState.value.serverRunning) return
        viewModelScope.launch {
            updateSetting(SettingPatch.ProtocolPatch(protocol))
            // If switching to FTPS and no cert exists yet, kick off cert generation.
            if (protocol == Protocol.FTPS && _certInfo.value == null) {
                generateCertInBackground()
            }
        }
    }

    fun onFtpPortChanged(text: String) {
        if (uiState.value.serverRunning) return
        val digits = text.filter { it.isDigit() }.take(5)
        _ftpPortStr.value = digits
        val port = digits.toIntOrNull() ?: -1
        val result = validator.validatePort(port)
        updateError(SettingsField.FTP_PORT, result)
        if (result is ValidationResult.Valid) {
            viewModelScope.launch { updateSetting(SettingPatch.FtpPort(port)) }
            revalidatePasv()
        }
    }

    fun onPasvMinChanged(text: String) {
        if (uiState.value.serverRunning) return
        _pasvMinStr.value = text.filter { it.isDigit() }.take(5)
        validateAndSavePasv()
    }

    fun onPasvMaxChanged(text: String) {
        if (uiState.value.serverRunning) return
        _pasvMaxStr.value = text.filter { it.isDigit() }.take(5)
        validateAndSavePasv()
    }

    // ---- Auth section ----

    fun onAuthModeChanged(mode: AuthMode) {
        if (uiState.value.serverRunning) return
        viewModelScope.launch { updateSetting(SettingPatch.AuthModePatch(mode)) }
    }

    fun onUsernameChanged(value: String) {
        if (uiState.value.serverRunning) return
        val result = validator.validateUsername(value)
        updateError(SettingsField.USERNAME, result)
        if (result is ValidationResult.Valid) {
            viewModelScope.launch { setCredentials(value, _passwordText.value) }
        }
    }

    fun onPasswordChanged(value: String) {
        if (uiState.value.serverRunning) return
        _passwordText.value = value
        val result = validator.validatePassword(value)
        updateError(SettingsField.PASSWORD, result)
        if (result is ValidationResult.Valid) {
            viewModelScope.launch { setCredentials(uiState.value.settings.username, value) }
        }
    }

    fun onTogglePasswordVisibility() {
        _isPasswordRevealed.update { !it }
    }

    // ---- Storage section ----

    fun onRootDirSelected(uriString: String) {
        viewModelScope.launch { updateSetting(SettingPatch.RootDirUri(uriString)) }
    }

    fun onAccessModeChanged(mode: AccessMode) {
        if (uiState.value.serverRunning) return
        viewModelScope.launch { updateSetting(SettingPatch.AccessModePatch(mode)) }
    }

    // ---- Behavior section ----

    fun onStartOnAppLaunchChanged(value: Boolean) {
        viewModelScope.launch { updateSetting(SettingPatch.StartOnAppLaunch(value)) }
    }

    fun onStartOnBootChanged(value: Boolean) {
        viewModelScope.launch { updateSetting(SettingPatch.StartOnBoot(value)) }
    }

    fun onKeepScreenOnChanged(value: Boolean) {
        viewModelScope.launch { updateSetting(SettingPatch.KeepScreenOn(value)) }
    }

    // ---- Appearance section ----

    fun onThemeModeChanged(mode: ThemeMode) {
        viewModelScope.launch { updateSetting(SettingPatch.ThemeModePatch(mode)) }
    }

    // ---- Security section ----

    fun onRegenerateCertConfirmed() {
        generateCertInBackground()
    }

    // ---- Private helpers ----

    private fun validateAndSavePasv() {
        val min = _pasvMinStr.value.toIntOrNull() ?: -1
        val max = _pasvMaxStr.value.toIntOrNull() ?: -1
        val ftpPort = _ftpPortStr.value.toIntOrNull() ?: uiState.value.settings.ftpPort
        val result = validator.validatePasvRange(min, max, ftpPort)
        updateError(SettingsField.PASV_MIN, result)
        updateError(SettingsField.PASV_MAX, result)
        if (result is ValidationResult.Valid) {
            viewModelScope.launch { updateSetting(SettingPatch.PasvRange(min, max)) }
        }
    }

    private fun revalidatePasv() {
        val min = _pasvMinStr.value.toIntOrNull() ?: uiState.value.settings.pasvMinPort
        val max = _pasvMaxStr.value.toIntOrNull() ?: uiState.value.settings.pasvMaxPort
        val ftpPort = _ftpPortStr.value.toIntOrNull() ?: uiState.value.settings.ftpPort
        val result = validator.validatePasvRange(min, max, ftpPort)
        updateError(SettingsField.PASV_MIN, result)
        updateError(SettingsField.PASV_MAX, result)
    }

    private fun updateError(field: SettingsField, result: ValidationResult) {
        _validationErrors.update { current ->
            if (result is ValidationResult.Invalid) current + (field to result.reason)
            else current - field
        }
    }

    private fun generateCertInBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRegeneratingCert.value = true
            regenerateCertificate()
            _certInfo.value = getCertificateInfo()
            _isRegeneratingCert.value = false
        }
    }

    // ---- Internal data carriers for combine grouping ----

    private data class PartialA(
        val settings: AppSettings,
        val serverState: ServerState,
        val validationErrors: Map<SettingsField, Int>,
    )

    private data class PartialB(
        val portStr: String,
        val pasvMinStr: String,
        val pasvMaxStr: String,
        val passwordText: String,
        val isPasswordRevealed: Boolean,
    )
}
