package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.data.logs.LogRepository
import javax.inject.Inject

class ClearLogsUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {
    operator fun invoke() = logRepository.clear()
}
