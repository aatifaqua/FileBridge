package com.aionyxe.filebridge.domain.usecase

import app.cash.turbine.test
import com.aionyxe.filebridge.data.credentials.CredentialsRepository
import com.aionyxe.filebridge.data.settings.SettingsRepository
import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.Protocol
import com.aionyxe.filebridge.domain.model.ServerState
import com.aionyxe.filebridge.domain.server.FtpServerController
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObserveConnectionInfoUseCaseTest {

    private val controller = mockk<FtpServerController>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val credentialsRepository = mockk<CredentialsRepository>()

    @Test
    fun `null when stopped, populated when running`() = runTest {
        val state = MutableStateFlow<ServerState>(ServerState.Stopped)
        every { controller.state } returns state
        every { settingsRepository.settings } returns
            flowOf(AppSettings(authMode = AuthMode.SINGLE_USER, username = "bob"))
        coEvery { credentialsRepository.getPassword() } returns "pw"

        val useCase = ObserveConnectionInfoUseCase(
            controller,
            settingsRepository,
            credentialsRepository,
        )

        useCase().test {
            assertNull(awaitItem())

            state.value = ServerState.Running(
                address = "192.168.1.5",
                port = 2121,
                protocol = Protocol.FTP,
                connectedClients = 0,
            )

            val info = awaitItem()!!
            assertEquals("ftp://192.168.1.5:2121", info.url)
            assertEquals("bob", info.username)
            assertEquals("pw", info.password)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `anonymous mode hides credentials`() = runTest {
        every { controller.state } returns MutableStateFlow(
            ServerState.Running("10.0.0.2", 2121, Protocol.FTPS, 1),
        )
        every { settingsRepository.settings } returns
            flowOf(AppSettings(authMode = AuthMode.ANONYMOUS))

        val useCase = ObserveConnectionInfoUseCase(
            controller,
            settingsRepository,
            credentialsRepository,
        )

        useCase().test {
            val info = awaitItem()!!
            assertEquals("ftps://10.0.0.2:2121", info.url)
            assertNull(info.username)
            assertNull(info.password)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
