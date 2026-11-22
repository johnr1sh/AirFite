package dev.johnrish.pocketengineer.agent

import dev.johnrish.pocketengineer.agent.CapabilityTier.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPolicyTest {
    private val peer = PeerInfo("agent", null, "2025-06-18")
    private val live = ConnectionSession("c", "mcp").configure().connect(1).transportOpened(2).handshakeSucceeded(peer, 3)

    private val read = ToolSpec("workspace.read_file", READ)
    private val propose = ToolSpec("patch.propose", PROPOSE)
    private val write = ToolSpec("patch.apply", WRITE)
    private val delete = ToolSpec("workspace.delete_file", DESTRUCTIVE)
    private val run = ToolSpec("shell.run", EXECUTE)
    private val policy = ToolPolicy(setOf(read.id, propose.id, write.id, delete.id, run.id))

    private fun reason(d: PolicyDecision) = (d as PolicyDecision.Deny).reason

    @Test
    fun deniesByDefaultWithNoGrants() {
        listOf(read, propose, write, delete).forEach {
            assertTrue("${it.id} must be denied", policy.decide(live, emptySet(), it) is PolicyDecision.Deny)
        }
    }

    @Test
    fun readAndProposeAllowedOnceGranted() {
        assertEquals(PolicyDecision.Allow, policy.decide(live, setOf(READ), read))
        assertEquals(PolicyDecision.Allow, policy.decide(live, setOf(PROPOSE), propose))
    }

    @Test
    fun tiersAreIndependent() {
        assertTrue(policy.decide(live, setOf(WRITE), read) is PolicyDecision.Deny)
        assertTrue(policy.decide(live, setOf(READ, PROPOSE), write) is PolicyDecision.Deny)
        assertTrue(policy.decide(live, setOf(WRITE), delete) is PolicyDecision.Deny)
    }

    @Test
    fun writeNeedsPerActionApprovalAndDestructiveNeedsStrong() {
        assertEquals(PolicyDecision.RequiresApproval(ApprovalLevel.PER_ACTION), policy.decide(live, setOf(WRITE), write))
        assertEquals(PolicyDecision.RequiresApproval(ApprovalLevel.STRONG), policy.decide(live, setOf(DESTRUCTIVE), delete))
    }

    @Test
    fun executeIsDeniedEvenWhenGrantedUntilEnabled() {
        val d = policy.decide(live, setOf(EXECUTE), run)
        assertEquals("Command execution is not available yet.", reason(d))
        val enabled = ToolPolicy(setOf(run.id), executeEnabled = true)
        assertEquals(PolicyDecision.RequiresApproval(ApprovalLevel.STRONG), enabled.decide(live, setOf(EXECUTE), run))
    }

    @Test
    fun unregisteredToolsAreDeniedRegardlessOfGrants() {
        val ghost = ToolSpec("workspace.exfiltrate", READ)
        assertEquals("Unknown tool.", reason(policy.decide(live, CapabilityTier.entries.toSet(), ghost)))
    }

    @Test
    fun noToolRunsUnlessConnectionIsLive() {
        val all = CapabilityTier.entries.toSet()
        val notLive = listOf(
            ConnectionSession("c", "mcp"),
            live.disconnect(10),
            live.transportLost("drop", 10),
            live.revoke(10),
            ConnectionSession("c", "mcp").configure().connect(1).transportOpened(2)
        )
        notLive.forEach {
            assertEquals("Agent connection is not active.", reason(policy.decide(it, all, read)))
        }
    }

    @Test
    fun revokingGrantTakesEffectOnNextCall() {
        assertEquals(PolicyDecision.Allow, policy.decide(live, setOf(READ), read))
        assertTrue(policy.decide(live, emptySet(), read) is PolicyDecision.Deny)
    }
}
