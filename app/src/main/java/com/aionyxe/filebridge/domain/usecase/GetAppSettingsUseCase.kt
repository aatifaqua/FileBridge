package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.data.settings.SettingsRepository
import com.aionyxe.filebridge.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAppSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<AppSettings> = settingsRepository.settings
}
