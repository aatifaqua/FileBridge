package com.aionyxe.filebridge.ui.logs

import app.cash.turbine.test
import com.aionyxe.filebridge.data.logs.LogEntry
import com.aionyxe.filebridge.data.logs.LogType
import com.aionyxe.filebridge.domain.usecase.ClearLogsUseCase
import com.aionyxe.filebridge.domain.usecase.ObserveLogsUseCase
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogsViewModelTest {

    private val logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())

    private val observeLogs: ObserveLogsUseCase = mockk()
    private val clearLogs: ClearLogsUseCase = mockk()

    private lateinit var viewModel: LogsViewModel

    @Before
    fun setUp() {
        every { observeLogs.invoke() } returns logsFlow
        every { clearLogs.invoke() } just runs
        viewModel = LogsViewModel(observeLogs, clearLogs)
    }

    @Test
    fun initialState_isEmpty() = runTest {
        viewModel.entries.test {
            val entries = awaitItem()
            assertTrue(entries.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun appendEntry_appearsInState() = runTest {
        viewModel.entries.test {
            awaitItem() // empty initial

            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                type = LogType.CLIENT_CONNECTED,
                message = "Client connected",
                ip = "192.168.1.5",
            )
            logsFlow.value = listOf(entry)

            val entries = awaitItem()
            assertEquals(1, entries.size)
            assertEquals("Client connected", entries[0].message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun multipleEntries_allReflected() = runTest {
        viewModel.entries.test {
            awaitItem() // empty initial

            val now = System.currentTimeMillis()
            logsFlow.value = listOf(
                LogEntry(now, LogType.SERVER_STARTED, "Server started"),
                LogEntry(now + 1, LogType.CLIENT_CONNECTED, "Client A", ip = "10.0.0.1"),
                LogEntry(now + 2, LogType.FILE_UPLOADED, "upload.txt", ip = "10.0.0.1"),
            )

            val entries = awaitItem()
            assertEquals(3, entries.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onClearConfirmed_delegatesToUseCase() {
        viewModel.onClearConfirmed()
        verify { clearLogs.invoke() }
    }

    @Test
    fun onClearConfirmed_stateBecomesEmpty() = runTest {
        // Pre-populate log
        val entry = LogEntry(System.currentTimeMillis(), LogType.AUTH_FAILURE, "Bad login", ip = "1.2.3.4")
        logsFlow.value = listOf(entry)

        viewModel.entries.test {
            awaitItem() // may get initial empty or populated depending on timing
            // Drain until we have the populated state
            val populated = generateSequence { runCatching { expectMostRecentItem() }.getOrNull() }
                .take(10)
                .firstOrNull { it.isNotEmpty() }

            // Simulate clear: the repository clears its flow
            logsFlow.value = emptyList()

            val empty = awaitItem()
            assertTrue(empty.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
