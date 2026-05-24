package com.aionyxe.filebridge.data.settings

import com.aionyxe.filebridge.domain.model.AccessMode
import com.aionyxe.filebridge.domain.model.AppSettings
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.Protocol
import com.aionyxe.filebridge.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setProtocol(value: Protocol)

    suspend fun setFtpPort(value: Int)

    suspend fun setPasvPortRange(min: Int, max: Int)

    suspend fun setAuthMode(value: AuthMode)

    suspend fun setUsername(value: String)

    suspend fun setRootDirUri(value: String)

    suspend fun setAccessMode(value: AccessMode)

    suspend fun setStartOnAppLaunch(value: Boolean)

    suspend fun setStartOnBoot(value: Boolean)

    suspend fun setKeepScreenOn(value: Boolean)

    suspend fun setThemeMode(value: ThemeMode)

    suspend fun setOnboardingComplete(value: Boolean)
}
