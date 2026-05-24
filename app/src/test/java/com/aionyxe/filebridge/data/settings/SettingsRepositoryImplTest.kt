package com.aionyxe.filebridge.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.aionyxe.filebridge.domain.model.AccessMode
import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.Protocol
import com.aionyxe.filebridge.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    // DataStore performs real file IO, so it is backed by a real dispatcher rather than a virtual
    // test scheduler (mixing the two causes the IO coroutine to never complete).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepositoryImpl

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tmpFolder.newFile("settings_test.preferences_pb")
        }
        repo = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `defaults are returned when unset`() = runBlocking {
        assertEquals(AppSettings(), repo.settings.first())
    }

    @Test
    fun `each setting round-trips`() = runBlocking {
        repo.setProtocol(Protocol.FTPS)
        repo.setFtpPort(2200)
        repo.setPasvPortRange(40000, 41000)
        repo.setAuthMode(AuthMode.ANONYMOUS)
        repo.setUsername("alice")
        repo.setRootDirUri("/sdcard/Shared")
        repo.setAccessMode(AccessMode.READ_ONLY)
        repo.setStartOnAppLaunch(true)
        repo.setStartOnBoot(true)
        repo.setKeepScreenOn(true)
        repo.setThemeMode(ThemeMode.DARK)
        repo.setOnboardingComplete(true)

        val result = repo.settings.first()
        assertEquals(
            AppSettings(
                protocol = Protocol.FTPS,
                ftpPort = 2200,
                pasvMinPort = 40000,
                pasvMaxPort = 41000,
                authMode = AuthMode.ANONYMOUS,
                username = "alice",
                rootDirUri = "/sdcard/Shared",
                accessMode = AccessMode.READ_ONLY,
                startOnAppLaunch = true,
                startOnBoot = true,
                keepScreenOn = true,
                themeMode = ThemeMode.DARK,
                onboardingComplete = true,
            ),
            result,
        )
    }
}
