package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.R
import com.aionyxe.filebridge.data.credentials.CredentialsRepository
import com.aionyxe.filebridge.data.settings.SettingsRepository
import com.aionyxe.filebridge.domain.validation.SettingsValidator
import com.aionyxe.filebridge.domain.validation.ValidationException
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetCredentialsUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val credentialsRepository = mockk<CredentialsRepository>(relaxed = true)
    private val useCase = SetCredentialsUseCase(
        settingsRepository,
        credentialsRepository,
        SettingsValidator(),
    )

    @Test
    fun `valid credentials are persisted`() = runTest {
        val result = useCase("alice", "secret")

        assertTrue(result.isSuccess)
        coVerify { settingsRepository.setUsername("alice") }
        coVerify { credentialsRepository.setPassword("secret") }
    }

    @Test
    fun `blank username is rejected`() = runTest {
        val result = useCase("  ", "secret")

        assertFalse(result.isSuccess)
        assertEquals(
            R.string.error_username_blank,
            (result.exceptionOrNull() as ValidationException).reason,
        )
        coVerify(exactly = 0) { credentialsRepository.setPassword(any()) }
    }

    @Test
    fun `empty password is rejected`() = runTest {
        val result = useCase("alice", "")

        assertFalse(result.isSuccess)
        assertEquals(
            R.string.error_password_blank,
            (result.exceptionOrNull() as ValidationException).reason,
        )
    }

    @Test
    fun `clearForAnonymous clears stored password`() = runTest {
        val result = useCase.clearForAnonymous()

        assertTrue(result.isSuccess)
        coVerify { credentialsRepository.clear() }
    }
}
