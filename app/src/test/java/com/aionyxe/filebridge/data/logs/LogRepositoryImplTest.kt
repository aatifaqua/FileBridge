package com.aionyxe.filebridge.data.logs

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRepositoryImplTest {

    private val repo = LogRepositoryImpl()

    private fun entry(message: String) =
        LogEntry(timestamp = 0L, type = LogType.CLIENT_CONNECTED, message = message)

    @Test
    fun `append adds entry`() {
        repo.append(entry("a"))
        assertEquals(1, repo.entries.value.size)
        assertEquals("a", repo.entries.value.first().message)
    }

    @Test
    fun `ring buffer caps at max entries in FIFO order`() {
        repeat(10_000) { i -> repo.append(entry("m$i")) }

        val entries = repo.entries.value
        assertEquals(LogRepositoryImpl.MAX_ENTRIES, entries.size)
        // Oldest retained is m9500, newest is m9999 (FIFO trim of the first 9500).
        assertEquals("m9500", entries.first().message)
        assertEquals("m9999", entries.last().message)
    }

    @Test
    fun `clear empties the buffer`() {
        repo.append(entry("a"))
        repo.clear()
        assertTrue(repo.entries.value.isEmpty())
    }

    @Test
    fun `entries emits on append`() = runTest {
        repo.entries.test {
            assertEquals(emptyList<LogEntry>(), awaitItem())
            repo.append(entry("a"))
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
