package com.aionyxe.filebridge.ui.home

import app.cash.turbine.test
import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.ConnectionInfo
import com.aionyxe.filebridge.domain.model.Protocol
import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.domain.usecase.GetAppSettingsUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveConnectedClientsUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveConnectionInfoUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveServerStateUseCase
import com.aionyxe.filebridge.domain.usecase.StartServerViaServiceUseCase
import com.aionyxe.filebridge.domain.usecase.StopServerViaServiceUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [HomeViewModel].
 *
 * Key technique: [HomeViewModel.uiState] uses `SharingStarted.WhileSubscribed`, so the upstream
 * `combine` only runs while there is at least one active subscriber.  Every test that reads updated
 * state must subscribe first via [app.cash.turbine.test].
 */
class HomeViewModelTest {

    private val serverStateFlow = MutableStateFlow<ServerState>(ServerState.Stopped)
    private val connectionInfoFlow = MutableStateFlow<ConnectionInfo?>(null)
    private val clientCountFlow = MutableStateFlow(0)
    private val settingsFlow = MutableStateFlow(AppSettings())

    private val observeServerState: ObserveServerStateUseCase = mockk()
    private val observeConnectionInfo: ObserveConnectionInfoUseCase = mockk()
    private val observeClients: ObserveConnectedClientsUseCase = mockk()
    private val getSettings: GetAppSettingsUseCase = mockk()
    private val startViaService: StartServerViaServiceUseCase = mockk(relaxed = true)
    private val stopViaService: StopServerViaServiceUseCase = mockk(relaxed = true)

    private lateinit var viewModel: HomeViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeServerState.invoke() } returns serverStateFlow
        every { observeConnectionInfo.invoke() } returns connectionInfoFlow
        every { observeClients.invoke() } returns clientCountFlow
        every { getSettings.invoke() } returns settingsFlow

        viewModel = HomeViewModel(
            observeServerState,
            observeConnectionInfo,
            observeClients,
            getSettings,
            startViaService,
            stopViaService,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_serverStopped_noClients() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.serverState is ServerState.Stopped)
            assertNull(state.connectionInfo)
            assertEquals(0, state.connectedClients)
            assertFalse(state.isPasswordRevealed)
            assertNull(state.transientError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun serverRunning_connectionInfoPopulated() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial Stopped

            val info = ConnectionInfo(
                url = "ftp://192.168.1.1:2121",
                username = "ftpuser",
                password = "secret",
                protocol = Protocol.FTP,
            )
            serverStateFlow.value = ServerState.Running("192.168.1.1", 2121, Protocol.FTP, 0)
            connectionInfoFlow.value = info

            // Drain until we see Running + populated info.
            val finalState = generateSequence { runCatching { expectMostRecentItem() }.getOrNull() }
                .take(10)
                .firstOrNull { it.serverState is ServerState.Running && it.connectionInfo != null }
                ?: awaitItem()

            assertTrue(finalState.serverState is ServerState.Running)
            assertEquals("ftp://192.168.1.1:2121", finalState.connectionInfo?.url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clientCountUpdates_reflectedInState() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            clientCountFlow.value = 3

            val state = generateSequence { runCatching { expectMostRecentItem() }.getOrNull() }
                .take(10)
                .firstOrNull { it.connectedClients == 3 }
                ?: awaitItem()

            assertEquals(3, state.connectedClients)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onStartClicked_delegatesToUseCase() {
        viewModel.onStartClicked()
        verify { startViaService.invoke() }
    }

    @Test
    fun onStopClicked_delegatesToUseCase() {
        viewModel.onStopClicked()
        verify { stopViaService.invoke() }
    }

    @Test
    fun togglePasswordVisibility_flipsFlag() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial (revealed = false)

            viewModel.onTogglePasswordVisibility()
            val revealed = awaitItem()
            assertTrue(revealed.isPasswordRevealed)

            viewModel.onTogglePasswordVisibility()
            val hidden = awaitItem()
            assertFalse(hidden.isPasswordRevealed)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun isAnonymous_trueWhenSettingIsAnonymous() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial (SINGLE_USER)

            settingsFlow.value = AppSettings(authMode = AuthMode.ANONYMOUS)

            val state = generateSequence { runCatching { expectMostRecentItem() }.getOrNull() }
                .take(10)
                .firstOrNull { it.isAnonymous }
                ?: awaitItem()

            assertTrue(state.isAnonymous)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onErrorShown_clearsTransientError() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial (null error)

            viewModel.onServerError("Something went wrong")
            val withError = awaitItem()
            assertEquals("Something went wrong", withError.transientError)

            viewModel.onErrorShown()
            val cleared = awaitItem()
            assertNull(cleared.transientError)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
