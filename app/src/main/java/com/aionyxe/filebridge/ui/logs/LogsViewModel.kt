package com.aionyxe.filebridge.ui.logs

import androidx.lifecycle.ViewModel
import com.aionyxe.filebridge.data.logs.LogEntry
import com.aionyxe.filebridge.domain.usecase.ClearLogsUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveLogsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    observeLogs: ObserveLogsUseCase,
    private val clearLogs: ClearLogsUseCase,
) : ViewModel() {

    /** Live list of log entries (newest entries appended at the end, displayed in reverse). */
    val entries: StateFlow<List<LogEntry>> = observeLogs()

    fun onClearConfirmed() {
        clearLogs()
    }
}
