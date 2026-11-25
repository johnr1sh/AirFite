package dev.johnrish.pocketengineer.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceSearchBudgetTest {
    @Test
    fun aggregateLimitUsesActualBytesReadInsteadOfFileMetadata() {
        val budget = WorkspaceSearchBudget(maxFiles = 5, maxTotalBytes = 10, maxFileBytes = 8)

        assertEquals(8, budget.beginFile())
        budget.finishFile(actualBytesRead = 3)
        assertEquals(7, budget.beginFile())
        budget.finishFile(actualBytesRead = 7)

        assertEquals(10, budget.bytesRead)
        assertNull(budget.beginFile())
    }

    @Test
    fun perFileLimitAndFileCountAreBothEnforced() {
        val budget = WorkspaceSearchBudget(maxFiles = 1, maxTotalBytes = 100, maxFileBytes = 12)

        assertEquals(12, budget.beginFile())
        budget.finishFile(actualBytesRead = 1)

        assertNull(budget.beginFile())
        assertEquals(1, budget.filesStarted)
    }

    @Test
    fun rejectsAccountingBeyondTheReservedReadLimit() {
        val budget = WorkspaceSearchBudget(maxFiles = 2, maxTotalBytes = 20, maxFileBytes = 4)
        assertEquals(4, budget.beginFile())

        assertThrows(IllegalArgumentException::class.java) {
            budget.finishFile(actualBytesRead = 5)
        }
        budget.abandonFile()
        assertEquals(4, budget.bytesRead)
    }

    @Test
    fun failedReadConservativelyConsumesItsReservation() {
        val budget = WorkspaceSearchBudget(maxFiles = 3, maxTotalBytes = 10, maxFileBytes = 8)
        assertEquals(8, budget.beginFile())

        budget.abandonFile()

        assertEquals(8, budget.bytesRead)
        assertEquals(2, budget.beginFile())
        budget.finishFile(actualBytesRead = 2)
        assertNull(budget.beginFile())
    }

    @Test(expected = IllegalStateException::class)
    fun requiresPreviousFileToBeFinishedBeforeReservingAnother() {
        val budget = WorkspaceSearchBudget(maxFiles = 3, maxTotalBytes = 10, maxFileBytes = 8)
        budget.beginFile()
        budget.beginFile()
    }
}
