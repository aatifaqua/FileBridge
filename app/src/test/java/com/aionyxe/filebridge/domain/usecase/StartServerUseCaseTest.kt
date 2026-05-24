package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.R
import com.aionyxe.filebridge.data.credentials.CredentialsRepository
import com.aionyxe.filebridge.data.settings.SettingsRepository
import com.aionyxe.filebridge.data.storage.StorageRepository
import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.ServerConfig
import com.aionyxe.filebridge.domain.server.FtpServerController
import com.aionyxe.filebridge.domain.validation.SettingsValidator
import com.aionyxe.filebridge.domain.validation.ValidationException
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StartServerUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>()
    private val credentialsRepository = mockk<CredentialsRepository>(relaxed = true)
    private val storageRepository = mockk<StorageRepository>()
    private val controller = mockk<FtpServerController>(relaxed = true)
    private val useCase = StartServerUseCase(
        settingsRepository,
        credentialsRepository,
        storageRepository,
        controller,
        SettingsValidator(),
    )

    private fun stubSettings(settings: AppSettings) {
        coEvery { settingsRepository.settings } returns flowOf(settings)
    }

    @Test
    fun `single-user happy path builds config and starts`() = runTest {
        val root = File(System.getProperty("java.io.tmpdir"))
        stubSettings(AppSettings(authMode = AuthMode.SINGLE_USER, username = "bob"))
        coEvery { credentialsRepository.getPassword() } returns "pw"
        coEvery { storageRepository.resolveRootDir(any()) } returns root
        val configSlot = slot<ServerConfig>()
        coEvery { controller.start(capture(configSlot)) } just Runs

        val result = useCase()

        assertTrue(result.isSuccess)
        coVerify { controller.start(any()) }
        assertEquals("bob", configSlot.captured.username)
        assertEquals("pw", configSlot.captured.password)
        assertEquals(root, configSlot.captured.rootDir)
    }

    @Test
    fun `invalid port fails before starting`() = runTest {
        stubSettings(AppSettings(ftpPort = 80))

        val result = useCase()

        assertFalse(result.isSuccess)
        assertEquals(
            R.string.error_port_range,
            (result.exceptionOrNull() as ValidationException).reason,
        )
        coVerify(exactly = 0) { controller.start(any()) }
    }

    @Test
    fun `single-user with no password fails`() = runTest {
        stubSettings(AppSettings(authMode = AuthMode.SINGLE_USER, username = "bob"))
        coEvery { credentialsRepository.getPassword() } returns null
        coEvery { storageRepository.resolveRootDir(any()) } returns
            File(System.getProperty("java.io.tmpdir"))

        val result = useCase()

        assertFalse(result.isSuccess)
        assertEquals(
            R.string.error_password_blank,
            (result.exceptionOrNull() as ValidationException).reason,
        )
        coVerify(exactly = 0) { controller.start(any()) }
    }

    @Test
    fun `unresolved root dir fails`() = runTest {
        stubSettings(AppSettings(authMode = AuthMode.ANONYMOUS))
        coEvery { storageRepository.resolveRootDir(any()) } returns null

        val result = useCase()

        assertFalse(result.isSuccess)
        assertEquals(
            R.string.error_root_dir_unresolved,
            (result.exceptionOrNull() as ValidationException).reason,
        )
    }
}
