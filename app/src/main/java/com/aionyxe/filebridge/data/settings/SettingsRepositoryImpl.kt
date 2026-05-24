package com.aionyxe.filebridge.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.aionyxe.filebridge.domain.model.AccessMode
import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.Protocol
import com.aionyxe.filebridge.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> =
        dataStore.data.map { prefs -> prefs.toAppSettings() }

    override suspend fun setProtocol(value: Protocol) =
        editValue(SettingsKeys.PROTOCOL, value.name)

    override suspend fun setFtpPort(value: Int) =
        editValue(SettingsKeys.FTP_PORT, value)

    override suspend fun setPasvPortRange(min: Int, max: Int) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.PASV_MIN_PORT] = min
            prefs[SettingsKeys.PASV_MAX_PORT] = max
        }
    }

    override suspend fun setAuthMode(value: AuthMode) =
        editValue(SettingsKeys.AUTH_MODE, value.name)

    override suspend fun setUsername(value: String) =
        editValue(SettingsKeys.USERNAME, value)

    override suspend fun setRootDirUri(value: String) =
        editValue(SettingsKeys.ROOT_DIR_URI, value)

    override suspend fun setAccessMode(value: AccessMode) =
        editValue(SettingsKeys.ACCESS_MODE, value.name)

    override suspend fun setStartOnAppLaunch(value: Boolean) =
        editValue(SettingsKeys.START_ON_APP_LAUNCH, value)

    override suspend fun setStartOnBoot(value: Boolean) =
        editValue(SettingsKeys.START_ON_BOOT, value)

    override suspend fun setKeepScreenOn(value: Boolean) =
        editValue(SettingsKeys.KEEP_SCREEN_ON, value)

    override suspend fun setThemeMode(value: ThemeMode) =
        editValue(SettingsKeys.THEME_MODE, value.name)

    override suspend fun setOnboardingComplete(value: Boolean) =
        editValue(SettingsKeys.ONBOARDING_COMPLETE, value)

    private suspend fun <T> editValue(key: Preferences.Key<T>, value: T) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            protocol = this[SettingsKeys.PROTOCOL]?.toEnum(defaults.protocol) ?: defaults.protocol,
            ftpPort = this[SettingsKeys.FTP_PORT] ?: defaults.ftpPort,
            pasvMinPort = this[SettingsKeys.PASV_MIN_PORT] ?: defaults.pasvMinPort,
            pasvMaxPort = this[SettingsKeys.PASV_MAX_PORT] ?: defaults.pasvMaxPort,
            authMode = this[SettingsKeys.AUTH_MODE]?.toEnum(defaults.authMode) ?: defaults.authMode,
            username = this[SettingsKeys.USERNAME] ?: defaults.username,
            rootDirUri = this[SettingsKeys.ROOT_DIR_URI] ?: defaults.rootDirUri,
            accessMode = this[SettingsKeys.ACCESS_MODE]?.toEnum(defaults.accessMode)
                ?: defaults.accessMode,
            startOnAppLaunch = this[SettingsKeys.START_ON_APP_LAUNCH] ?: defaults.startOnAppLaunch,
            startOnBoot = this[SettingsKeys.START_ON_BOOT] ?: defaults.startOnBoot,
            keepScreenOn = this[SettingsKeys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            themeMode = this[SettingsKeys.THEME_MODE]?.toEnum(defaults.themeMode)
                ?: defaults.themeMode,
            onboardingComplete = this[SettingsKeys.ONBOARDING_COMPLETE]
                ?: defaults.onboardingComplete,
        )
    }
}

private inline fun <reified T : Enum<T>> String.toEnum(default: T): T =
    runCatching { enumValueOf<T>(this) }.getOrDefault(default)
