package com.aionyxe.filebridge.domain.usecase

import com.aionyxe.filebridge.data.logs.LogEntry
import com.aionyxe.filebridge.data.logs.LogRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveLogsUseCase @Inject constructor(
    private val logRepository: LogRepository,
) {
    operator fun invoke(): StateFlow<List<LogEntry>> = logRepository.entries
}
