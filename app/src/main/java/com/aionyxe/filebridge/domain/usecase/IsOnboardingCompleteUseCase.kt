package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class IsOnboardingCompleteUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<Boolean> =
        settingsRepository.settings.map { it.onboardingComplete }
}
