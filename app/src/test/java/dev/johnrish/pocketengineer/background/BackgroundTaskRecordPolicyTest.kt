package dev.johnrish.pocketengineer.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundTaskRecordPolicyTest {
    private fun record(id: String, status: String, createdAt: Long = id.toLongOrNull() ?: 0L) =
        BackgroundTaskRecord(id = id, status = status, createdAt = createdAt)

    @Test
    fun activeTasksAreCountedAcrossQueuedRunningAndRetrying() {
        val records = listOf(record("1", "Queued"), record("2", "Running"), record("3", "Retrying"), record("4", "Failed"))
        assertEquals(3, BackgroundTaskRecordPolicy.activeCount(records))
    }

    @Test
    fun mergeReplacesSameIdInsteadOfDuplicating() {
        val result = BackgroundTaskRecordPolicy.merge(
            listOf(record("task", "Queued", 1)),
            record("task", "Completed", 2)
        )
        assertEquals(1, result.records.size)
        assertEquals("Completed", result.records.single().status)
        assertTrue(result.removedIds.isEmpty())
    }

    @Test
    fun evictsOldestTerminalRecordsAndKeepsNewestHistory() {
        val existing = (1L..4L).map { record(it.toString(), "Completed", it) }
        val result = BackgroundTaskRecordPolicy.merge(existing, record("5", "Failed", 5), maxRecords = 3)
        assertEquals(listOf("5", "4", "3"), result.records.map { it.id })
        assertEquals(listOf("1", "2"), result.removedIds)
    }

    @Test
    fun neverEvictsActiveTasksEvenWhenHistoryLimitIsExceeded() {
        val existing = (1L..4L).map { record(it.toString(), "Running", it) }
        val result = BackgroundTaskRecordPolicy.merge(existing, record("5", "Queued", 5), maxRecords = 3)
        assertEquals(5, result.records.size)
        assertTrue(result.removedIds.isEmpty())
        assertEquals(5, BackgroundTaskRecordPolicy.activeCount(result.records))
    }

    @Test
    fun terminalIncomingRecordCanBeEvictedWhenOlderThanHistory() {
        val existing = listOf(record("2", "Completed", 2), record("3", "Completed", 3))
        val result = BackgroundTaskRecordPolicy.merge(existing, record("1", "Failed", 1), maxRecords = 2)
        assertEquals(listOf("3", "2"), result.records.map { it.id })
        assertEquals(listOf("1"), result.removedIds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidHistoryLimit() {
        BackgroundTaskRecordPolicy.merge(emptyList(), record("1", "Queued"), maxRecords = 0)
    }
}
