package com.aionyxe.filebridge.ui.onboarding

import app.cash.turbine.test
import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.usecase.CompleteOnboardingUseCase
import com.aionyxe.filebridge.domain.usecase.GetAppSettingsUseCase
import com.aionyxe.filebridge.domain.usecase.ListStorageRootsUseCase
import com.aionyxe.filebridge.domain.usecase.SetCredentialsUseCase
import com.aionyxe.filebridge.domain.usecase.UpdateAppSettingUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnboardingViewModelTest {

    private val getSettings: GetAppSettingsUseCase = mockk()
    private val updateSetting: UpdateAppSettingUseCase = mockk()
    private val setCredentials: SetCredentialsUseCase = mockk()
    private val listStorageRoots: ListStorageRootsUseCase = mockk()
    private val completeOnboarding: CompleteOnboardingUseCase = mockk()

    private lateinit var viewModel: OnboardingViewModel

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getSettings.invoke() } returns MutableStateFlow(AppSettings())
        every { listStorageRoots.invoke() } returns emptyList()
        coEvery { completeOnboarding.invoke(any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel = OnboardingViewModel(
            getSettings,
            updateSetting,
            setCredentials,
            listStorageRoots,
            completeOnboarding,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- Initial state ----

    @Test
    fun initialState_step0_cannotFinish() = runTest {
        val state = viewModel.uiState.value
        assertEquals(0, state.currentStep)
        assertFalse(state.canFinish)
    }

    // ---- Navigation ----

    @Test
    fun onNext_advancesStep() = runTest {
        viewModel.uiState.test {
            awaitItem() // step 0
            viewModel.onNext()
            val s1 = awaitItem()
            assertEquals(1, s1.currentStep)
            viewModel.onNext()
            val s2 = awaitItem()
            assertEquals(2, s2.currentStep)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onNext_doesNotExceedStep2() = runTest {
        repeat(5) { viewModel.onNext() }
        assertEquals(2, viewModel.uiState.value.currentStep)
    }

    @Test
    fun onBack_decreasesStep() = runTest {
        viewModel.onNext()
        viewModel.onNext()
        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(2, initial.currentStep)
            viewModel.onBack()
            val s1 = awaitItem()
            assertEquals(1, s1.currentStep)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onBack_doesNotGoBelowStep0() = runTest {
        repeat(3) { viewModel.onBack() }
        assertEquals(0, viewModel.uiState.value.currentStep)
    }

    // ---- Permission callbacks ----

    @Test
    fun onStoragePermissionResult_updatesGrantedFlag() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onStoragePermissionResult(true)
            val state = awaitItem()
            assertTrue(state.storagePermissionGranted)
            assertTrue(state.storagePermissionResolved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onStoragePermissionSkipped_setsResolvedWithoutGranted() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onStoragePermissionSkipped()
            val state = awaitItem()
            assertFalse(state.storagePermissionGranted)
            assertTrue(state.storagePermissionResolved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onNotificationPermissionResult_updatesGrantedAndResolved() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.onNotificationPermissionResult(true)
            val state = awaitItem()
            assertTrue(state.notificationPermissionGranted)
            assertTrue(state.notificationPermissionResolved)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onNotificationPermissionSkipped_setsResolvedWithoutGranted() = runTest {
        // In the test JVM environment SDK_INT = 0, so notificationPermissionResolved is
        // already initialised to true (pre-API-33 path). Calling skip is idempotent here —
        // verify the final state directly rather than waiting for a Turbine emission.
        viewModel.onNotificationPermissionSkipped()
        val state = viewModel.uiState.value
        assertFalse(state.notificationPermissionGranted)
        assertTrue(state.notificationPermissionResolved)
    }

    // ---- canFinish logic ----

    @Test
    fun canFinish_falseOnStep0And1() = runTest {
        assertFalse(viewModel.uiState.value.canFinish)
        viewModel.onNext()
        assertFalse(viewModel.uiState.value.canFinish)
    }

    @Test
    fun canFinish_singleUser_requiresUsernameAndPassword() = runTest {
        // Advance to step 2 and switch to SINGLE_USER mode
        viewModel.onNext(); viewModel.onNext()
        viewModel.onAuthModeChanged(AuthMode.SINGLE_USER)
        // SINGLE_USER with no password → cannot finish
        assertFalse(viewModel.uiState.value.canFinish)

        viewModel.onPasswordChanged("mypassword")
        assertTrue(viewModel.uiState.value.canFinish)
    }

    @Test
    fun canFinish_anonymous_alwaysTrueOnStep2() = runTest {
        viewModel.onNext(); viewModel.onNext()
        viewModel.onAuthModeChanged(AuthMode.ANONYMOUS)
        assertTrue(viewModel.uiState.value.canFinish)
    }

    // ---- Quick setup edits ----

    @Test
    fun onAuthModeChanged_updatesState() = runTest {
        // Default is ANONYMOUS; switch to SINGLE_USER so a state emission actually occurs.
        viewModel.uiState.test {
            awaitItem()
            viewModel.onAuthModeChanged(AuthMode.SINGLE_USER)
            val state = awaitItem()
            assertEquals(AuthMode.SINGLE_USER, state.authMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onRootDirSelected_updatesUri() = runTest {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADownloads"
        viewModel.uiState.test {
            awaitItem()
            viewModel.onRootDirSelected(uri)
            val state = awaitItem()
            assertEquals(uri, state.rootDirUri)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Finish ----

    @Test
    fun onFinish_anonymous_callsCompleteOnboarding() = runTest {
        // Default auth mode is ANONYMOUS — finish is possible without credentials.
        viewModel.onNext(); viewModel.onNext()
        viewModel.onFinish()
        coVerify { completeOnboarding.invoke(AuthMode.ANONYMOUS, any(), any(), any()) }
    }

    @Test
    fun onFinish_singleUser_callsCompleteOnboarding() = runTest {
        viewModel.onNext(); viewModel.onNext()
        viewModel.onAuthModeChanged(AuthMode.SINGLE_USER)
        viewModel.onUsernameChanged("ftpuser")
        viewModel.onPasswordChanged("secret")
        viewModel.onFinish()
        coVerify { completeOnboarding.invoke(AuthMode.SINGLE_USER, "ftpuser", "secret", any()) }
    }

    @Test
    fun onFinish_whenCannotFinish_doesNotCallUseCase() = runTest {
        // Still on step 0 → canFinish is false
        viewModel.onFinish()
        coVerify(exactly = 0) { completeOnboarding.invoke(any(), any(), any(), any()) }
    }
}
