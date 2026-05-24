package com.aionyxe.filebridge.ui.settings

import app.cash.turbine.test
import com.aionyxe.filebridge.data.credentials.CredentialsRepository
import com.aionyxe.filebridge.domain.model.AccessMode
import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.AuthMode
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
import com.aionyxe.filebridge.domain.usecase.UpdateAppSettingUseCase
import com.aionyxe.filebridge.domain.validation.SettingsValidator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private val settingsFlow = MutableStateFlow(AppSettings())
    private val serverStateFlow = MutableStateFlow<ServerState>(ServerState.Stopped)

    private val getSettings: GetAppSettingsUseCase = mockk()
    private val updateSetting: UpdateAppSettingUseCase = mockk()
    private val setCredentials: SetCredentialsUseCase = mockk()
    private val listStorageRoots: ListStorageRootsUseCase = mockk()
    private val isSdCardPresent: IsSdCardPresentUseCase = mockk()
    private val getCertificateInfo: GetCertificateInfoUseCase = mockk()
    private val regenerateCertificate: RegenerateCertificateUseCase = mockk()
    private val observeServerState: ObserveServerStateUseCase = mockk()
    private val credentialsRepository: CredentialsRepository = mockk()
    private val validator = SettingsValidator()

    private lateinit var viewModel: SettingsViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { getSettings.invoke() } returns settingsFlow
        every { observeServerState.invoke() } returns serverStateFlow
        every { isSdCardPresent.invoke() } returns false
        every { listStorageRoots.invoke() } returns emptyList()
        coEvery { getCertificateInfo.invoke() } returns null
        coEvery { credentialsRepository.getPassword() } returns "secret"
        coEvery { updateSetting.invoke(any()) } returns Unit
        coEvery { setCredentials.invoke(any(), any()) } returns Result.success(Unit)
        coEvery { regenerateCertificate.invoke() } returns Result.success(Unit)

        viewModel = SettingsViewModel(
            getSettings,
            updateSetting,
            setCredentials,
            listStorageRoots,
            isSdCardPresent,
            getCertificateInfo,
            regenerateCertificate,
            observeServerState,
            credentialsRepository,
            validator,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Initial state ----

    @Test
    fun initialState_defaults() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.serverRunning)
            assertTrue(state.validationErrors.isEmpty())
            assertFalse(state.isRegeneratingCert)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Protocol ----

    @Test
    fun onProtocolChanged_persistsToUseCase() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onProtocolChanged(Protocol.FTPS)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { updateSetting.invoke(any()) }
    }

    @Test
    fun onProtocolChanged_serverRunning_doesNothing() = runTest {
        serverStateFlow.value = ServerState.Running("1.2.3.4", 2121, Protocol.FTP, 1)
        viewModel.uiState.test {
            awaitItem() // consume initial
            viewModel.onProtocolChanged(Protocol.FTPS)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { updateSetting.invoke(any()) }
    }

    // ---- FTP port ----

    @Test
    fun validPort_clearsErrorAndPersists() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onFtpPortChanged("3000")
            val state = awaitItem()
            assertNull(state.validationErrors[SettingsField.FTP_PORT])
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { updateSetting.invoke(any()) }
    }

    @Test
    fun invalidPort_showsError_doesNotPersist() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onFtpPortChanged("80") // below 1024
            // Two state updates fire (portStr + validationErrors); drain to the final one.
            val state = generateSequence { runCatching { expectMostRecentItem() }.getOrNull() }
                .take(10)
                .firstOrNull { it.validationErrors[SettingsField.FTP_PORT] != null }
                ?: awaitItem()
            assertNotNull(state.validationErrors[SettingsField.FTP_PORT])
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { updateSetting.invoke(any()) }
    }

    @Test
    fun portField_serverRunning_doesNothing() = runTest {
        serverStateFlow.value = ServerState.Running("1.2.3.4", 2121, Protocol.FTP, 0)
        viewModel.uiState.test {
            awaitItem()
            viewModel.onFtpPortChanged("3000")
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { updateSetting.invoke(any()) }
    }

    // ---- PASV range ----

    @Test
    fun validPasvRange_clearsErrorAndPersists() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onPasvMinChanged("50000")
            viewModel.onPasvMaxChanged("51000")
            // Drain until both errors are gone (intermediate state may briefly show an error).
            val state = generateSequence { runCatching { expectMostRecentItem() }.getOrNull() }
                .take(20)
                .firstOrNull {
                    it.pasvMinStr == "50000" &&
                        it.pasvMaxStr == "51000" &&
                        it.validationErrors[SettingsField.PASV_MIN] == null
                }
                ?: awaitItem()
            assertNull(state.validationErrors[SettingsField.PASV_MIN])
            assertNull(state.validationErrors[SettingsField.PASV_MAX])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun invalidPasvRange_minGreaterThanMax_showsError() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onPasvMinChanged("51000")
            viewModel.onPasvMaxChanged("50000")
            // Drain until both fields are set and the error is present.
            val state = generateSequence { runCatching { expectMostRecentItem() }.getOrNull() }
                .take(20)
                .firstOrNull {
                    it.pasvMinStr == "51000" &&
                        it.pasvMaxStr == "50000" &&
                        it.validationErrors[SettingsField.PASV_MIN] != null
                }
                ?: awaitItem()
            assertNotNull(state.validationErrors[SettingsField.PASV_MIN])
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Auth mode ----

    @Test
    fun onAuthModeChanged_propagates() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAuthModeChanged(AuthMode.ANONYMOUS)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { updateSetting.invoke(any()) }
    }

    // ---- Username / password ----

    @Test
    fun blankUsername_showsError() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onUsernameChanged("")
            val state = awaitItem()
            assertNotNull(state.validationErrors[SettingsField.USERNAME])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun emptyPassword_showsError() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onPasswordChanged("")
            // passwordText update and validationErrors update are two separate flow emissions.
            val state = generateSequence { runCatching { expectMostRecentItem() }.getOrNull() }
                .take(10)
                .firstOrNull { it.validationErrors[SettingsField.PASSWORD] != null }
                ?: awaitItem()
            assertNotNull(state.validationErrors[SettingsField.PASSWORD])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun validCredentials_persist() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onPasswordChanged("newpass")
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { setCredentials.invoke(any(), "newpass") }
    }

    // ---- Access mode ----

    @Test
    fun onAccessModeChanged_propagates() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAccessModeChanged(AccessMode.READ_ONLY)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { updateSetting.invoke(any()) }
    }

    // ---- Behavior switches ----

    @Test
    fun onKeepScreenOnChanged_propagates() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onKeepScreenOnChanged(true)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { updateSetting.invoke(any()) }
    }

    // ---- Theme ----

    @Test
    fun onThemeModeChanged_propagates() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onThemeModeChanged(ThemeMode.DARK)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { updateSetting.invoke(any()) }
    }

    // ---- Password visibility toggle ----

    @Test
    fun togglePasswordVisibility_flipsFlag() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertFalse(initial.isPasswordRevealed)

            viewModel.onTogglePasswordVisibility()
            val revealed = awaitItem()
            assertTrue(revealed.isPasswordRevealed)

            viewModel.onTogglePasswordVisibility()
            val hidden = awaitItem()
            assertFalse(hidden.isPasswordRevealed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Settings locked while server running ----

    @Test
    fun settingsLocked_serverRunning_uiStateReflectsLock() = runTest {
        serverStateFlow.value = ServerState.Running("192.168.1.1", 2121, Protocol.FTP, 2)
        viewModel.uiState.test {
            val state = generateSequence { runCatching { expectMostRecentItem() }.getOrNull() }
                .take(10)
                .firstOrNull { it.serverRunning }
                ?: awaitItem()
            assertTrue(state.serverRunning)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
