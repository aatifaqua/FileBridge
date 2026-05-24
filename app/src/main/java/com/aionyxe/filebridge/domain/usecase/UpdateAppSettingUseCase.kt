package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.data.settings.SettingsRepository
import com.aionyxe.filebridge.domain.model.AccessMode
import com.aionyxe.filebridge.domain.model.AuthMode
import com.aionyxe.filebridge.domain.model.Protocol
import com.aionyxe.filebridge.domain.model.ThemeMode
import javax.inject.Inject

/** Typed patch describing a single settings field change. */
sealed interface SettingPatch {
    data class ProtocolPatch(val value: Protocol) : SettingPatch

    data class FtpPort(val value: Int) : SettingPatch

    data class PasvRange(val min: Int, val max: Int) : SettingPatch

    data class AuthModePatch(val value: AuthMode) : SettingPatch

    data class Username(val value: String) : SettingPatch

    data class RootDirUri(val value: String) : SettingPatch

    data class AccessModePatch(val value: AccessMode) : SettingPatch

    data class StartOnAppLaunch(val value: Boolean) : SettingPatch

    data class StartOnBoot(val value: Boolean) : SettingPatch

    data class KeepScreenOn(val value: Boolean) : SettingPatch

    data class ThemeModePatch(val value: ThemeMode) : SettingPatch

    data class OnboardingComplete(val value: Boolean) : SettingPatch
}

class UpdateAppSettingUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(patch: SettingPatch) {
        when (patch) {
            is SettingPatch.ProtocolPatch -> settingsRepository.setProtocol(patch.value)
            is SettingPatch.FtpPort -> settingsRepository.setFtpPort(patch.value)
            is SettingPatch.PasvRange -> settingsRepository.setPasvPortRange(patch.min, patch.max)
            is SettingPatch.AuthModePatch -> settingsRepository.setAuthMode(patch.value)
            is SettingPatch.Username -> settingsRepository.setUsername(patch.value)
            is SettingPatch.RootDirUri -> settingsRepository.setRootDirUri(patch.value)
            is SettingPatch.AccessModePatch -> settingsRepository.setAccessMode(patch.value)
            is SettingPatch.StartOnAppLaunch ->
                settingsRepository.setStartOnAppLaunch(patch.value)
            is SettingPatch.StartOnBoot -> settingsRepository.setStartOnBoot(patch.value)
            is SettingPatch.KeepScreenOn -> settingsRepository.setKeepScreenOn(patch.value)
            is SettingPatch.ThemeModePatch -> settingsRepository.setThemeMode(patch.value)
            is SettingPatch.OnboardingComplete ->
                settingsRepository.setOnboardingComplete(patch.value)
        }
    }
}
