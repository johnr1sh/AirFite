package dev.johnrish.pocketengineer.agent

import dev.johnrish.pocketengineer.agent.ConnectionState.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSessionTest {
    private val peer = PeerInfo("reference-agent", "1.2.0", "2025-06-18")

    private fun fresh() = ConnectionSession(id = "c1", adapterId = "mcp")
    private fun connected(): ConnectionSession =
        fresh().configure().connect(1).transportOpened(2).handshakeSucceeded(peer, 3)

    @Test
    fun startsDisconnectedAndNotLive() {
        val s = fresh()
        assertEquals(DISCONNECTED, s.state)
        assertFalse(s.isLive)
    }

    @Test
    fun happyPathReachesConnectedOnlyAfterHandshake() {
        val s = fresh().configure()
        assertEquals(CONFIGURED, s.state)
        val connecting = s.connect(10)
        assertEquals(CONNECTING, connecting.state)
        val auth = connecting.transportOpened(11)
        assertEquals(AUTHENTICATING, auth.state)
        assertFalse("authenticating is not live", auth.isLive)
        val live = auth.handshakeSucceeded(peer, 12)
        assertEquals(CONNECTED, live.state)
        assertTrue(live.isLive)
        assertEquals(peer, live.peer)
        assertEquals(12L, live.lastActivityMs)
    }

    @Test
    fun cannotJumpToConnectedWithoutHandshakePath() {
        assertThrows(IllegalStateException::class.java) { fresh().handshakeSucceeded(peer, 1) }
        assertThrows(IllegalStateException::class.java) { fresh().configure().handshakeSucceeded(peer, 1) }
        assertThrows(IllegalStateException::class.java) { fresh().configure().connect(1).handshakeSucceeded(peer, 2) }
    }

    @Test
    fun cannotConnectWithoutConfiguration() {
        assertThrows(IllegalStateException::class.java) { fresh().connect(1) }
    }

    @Test
    fun peerInfoRequiresIdentityAndProtocolVersion() {
        assertThrows(IllegalArgumentException::class.java) { PeerInfo(" ", null, "2025-06-18") }
        assertThrows(IllegalArgumentException::class.java) { PeerInfo("agent", null, "") }
    }

    @Test
    fun unreachablePeerIsUnavailableNotFailed() {
        val s = fresh().configure().connect(1).unreachable("timeout", 2)
        assertEquals(UNAVAILABLE, s.state)
        assertEquals("timeout", s.failure)
        assertFalse(s.isLive)
    }

    @Test
    fun rejectedAuthenticationFailsAndClearsPeer() {
        val s = fresh().configure().connect(1).transportOpened(2).fail("401 unauthorized", 3)
        assertEquals(FAILED, s.state)
        assertNull(s.peer)
        assertEquals("401 unauthorized", s.failure)
    }

    @Test
    fun failedAndUnavailableCanRetryWithCleanState() {
        val failed = fresh().configure().connect(1).fail("boom", 2)
        val retry = failed.connect(3)
        assertEquals(CONNECTING, retry.state)
        assertNull(retry.failure)
        assertEquals(0, retry.reconnectAttempts)
        assertEquals(CONNECTING, fresh().configure().connect(1).unreachable("x", 2).connect(3).state)
    }

    @Test
    fun lostSessionEntersReconnectingAndStopsBeingLive() {
        val s = connected().transportLost("socket closed", 20)
        assertEquals(RECONNECTING, s.state)
        assertEquals(1, s.reconnectAttempts)
        assertFalse("tools must not run while reconnecting", s.isLive)
    }

    @Test
    fun reconnectRequiresFreshHandshake() {
        val reconnecting = connected().transportLost("drop", 20)
        val auth = reconnecting.transportOpened(21)
        assertEquals(AUTHENTICATING, auth.state)
        assertFalse(auth.isLive)
        val live = auth.handshakeSucceeded(peer, 22)
        assertTrue(live.isLive)
        assertEquals(0, live.reconnectAttempts)
    }

    @Test
    fun reconnectBudgetIsBoundedThenFails() {
        var s = connected().transportLost("drop", 1)
        repeat(ConnectionSession.MAX_RECONNECT_ATTEMPTS - 1) { s = s.reconnectAttemptFailed("still down", 2) }
        assertEquals(RECONNECTING, s.state)
        assertEquals(ConnectionSession.MAX_RECONNECT_ATTEMPTS, s.reconnectAttempts)
        s = s.reconnectAttemptFailed("still down", 3)
        assertEquals(FAILED, s.state)
        assertNull(s.peer)
    }

    @Test
    fun reconnectAttemptFailedOnlyValidWhileReconnecting() {
        assertThrows(IllegalStateException::class.java) { connected().reconnectAttemptFailed("x", 1) }
        assertThrows(IllegalStateException::class.java) { fresh().transportLost("x", 1) }
    }

    @Test
    fun userDisconnectKeepsConfigurationAndDropsPeer() {
        val s = connected().disconnect(30)
        assertEquals(CONFIGURED, s.state)
        assertNull(s.peer)
        assertFalse(s.isLive)
    }

    @Test
    fun disconnectIsRejectedWhenNothingIsActive() {
        assertThrows(IllegalStateException::class.java) { fresh().disconnect(1) }
        assertThrows(IllegalStateException::class.java) { fresh().configure().disconnect(1) }
    }

    @Test
    fun revokeFromEveryStateEndsDisconnectedWithoutPeer() {
        val states = listOf(
            fresh(), fresh().configure(), fresh().configure().connect(1),
            fresh().configure().connect(1).transportOpened(2), connected(),
            connected().transportLost("x", 4), fresh().configure().connect(1).unreachable("x", 2),
            fresh().configure().connect(1).fail("x", 2)
        )
        states.forEach {
            val revoked = it.revoke(99)
            assertEquals(DISCONNECTED, revoked.state)
            assertNull(revoked.peer)
            assertNull(revoked.failure)
            assertFalse(revoked.isLive)
        }
    }

    @Test
    fun failureReasonIsBoundedAndSingleLine() {
        val s = fresh().configure().connect(1).fail("line1\n\n  line2 " + "x".repeat(1000), 2)
        assertTrue(s.failure!!.length <= ConnectionSession.MAX_FAILURE_LENGTH)
        assertFalse(s.failure!!.contains('\n'))
    }

    @Test
    fun failureReasonNeverRetainsCredentials() {
        val s = fresh().configure().connect(1).fail("auth failed for key sk-proj-" + "Ab1_".repeat(8), 2)
        assertFalse(s.failure!!.contains("sk-proj"))
    }
}
