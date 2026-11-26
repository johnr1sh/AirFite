package dev.johnrish.pocketengineer.workspace

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceOperationGateTest {
    @Test
    fun onlyTheNewestRefreshMayPublishResults() {
        val gate = WorkspaceOperationGate()
        val first = gate.beginRefresh()
        val second = gate.beginRefresh()

        assertFalse(gate.isCurrentRefresh(first))
        assertTrue(gate.isCurrentRefresh(second))
    }

    @Test
    fun onlyTheNewestFileReadMayPublishResults() {
        val gate = WorkspaceOperationGate()
        val first = gate.beginRead()
        val second = gate.beginRead()

        assertFalse(gate.isCurrentRead(first))
        assertTrue(gate.isCurrentRead(second))
    }

    @Test
    fun changingWorkspaceInvalidatesOutstandingOperations() {
        val gate = WorkspaceOperationGate()
        val refresh = gate.beginRefresh()
        val read = gate.beginRead()

        gate.workspaceChanged()

        assertFalse(gate.isCurrentRefresh(refresh))
        assertFalse(gate.isCurrentRead(read))
    }

    @Test
    fun refreshingDoesNotInvalidateAnActiveFileRead() {
        val gate = WorkspaceOperationGate()
        val read = gate.beginRead()

        gate.beginRefresh()

        assertTrue(gate.isCurrentRead(read))
    }
}
