package dev.johnrish.pocketengineer.agent

import dev.johnrish.pocketengineer.workspace.SensitiveFileGuard

/**
 * Observable lifecycle of one agent connection.
 *
 * - [DISCONNECTED]: nothing configured, or credentials were revoked.
 * - [CONFIGURED]: saved configuration exists but no session is active.
 * - [CONNECTING]: opening the transport.
 * - [AUTHENTICATING]: transport is open; handshake/authentication is in progress.
 * - [CONNECTED]: handshake completed and the peer identified itself. Nothing else counts as connected.
 * - [UNAVAILABLE]: the peer could not be reached (offline, refused, timed out).
 * - [FAILED]: authentication was rejected, the protocol was violated, or retries were exhausted.
 * - [RECONNECTING]: an established session dropped and recovery is in progress.
 */
enum class ConnectionState { DISCONNECTED, CONFIGURED, CONNECTING, AUTHENTICATING, CONNECTED, UNAVAILABLE, FAILED, RECONNECTING }

/** Identity reported by the remote side during a completed handshake. */
data class PeerInfo(val name: String, val version: String?, val protocolVersion: String) {
    init {
        require(name.isNotBlank()) { "Peer name is required." }
        require(protocolVersion.isNotBlank()) { "Negotiated protocol version is required." }
    }
}

/**
 * Immutable connection record. Every change is an explicit event method that validates the
 * transition and returns a new instance, so the UI can only show states that actually occurred.
 * Transport code (not yet implemented) is responsible for calling these at real protocol milestones.
 */
data class ConnectionSession(
    val id: String,
    val adapterId: String,
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val peer: PeerInfo? = null,
    val failure: String? = null,
    val reconnectAttempts: Int = 0,
    val lastActivityMs: Long? = null
) {
    companion object {
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val MAX_FAILURE_LENGTH = 300
    }

    /** True only for a live, handshaken session; tool invocation must require this. */
    val isLive: Boolean get() = state == ConnectionState.CONNECTED && peer != null

    /** Save configuration without opening anything. */
    fun configure(): ConnectionSession = move(setOf(ConnectionState.DISCONNECTED), ConnectionState.CONFIGURED)

    /** Start (or retry) a session. */
    fun connect(nowMs: Long): ConnectionSession = move(
        setOf(ConnectionState.CONFIGURED, ConnectionState.UNAVAILABLE, ConnectionState.FAILED),
        ConnectionState.CONNECTING, nowMs, failure = null, reconnectAttempts = 0, peer = null
    )

    /** The transport is open; authentication/handshake is next. Also ends a reconnect wait. */
    fun transportOpened(nowMs: Long): ConnectionSession = move(
        setOf(ConnectionState.CONNECTING, ConnectionState.RECONNECTING), ConnectionState.AUTHENTICATING, nowMs
    )

    /** The protocol handshake succeeded and the remote identified itself. */
    fun handshakeSucceeded(peer: PeerInfo, nowMs: Long): ConnectionSession = move(
        setOf(ConnectionState.AUTHENTICATING), ConnectionState.CONNECTED, nowMs,
        failure = null, reconnectAttempts = 0, peer = peer
    )

    /** An established session dropped. Begins recovery, or fails once the attempt budget is spent. */
    fun transportLost(reason: String, nowMs: Long): ConnectionSession {
        requireIn(setOf(ConnectionState.CONNECTED))
        return nextReconnect(reason, nowMs)
    }

    /** A reconnect attempt did not succeed. Stays in recovery until the attempt budget is spent. */
    fun reconnectAttemptFailed(reason: String, nowMs: Long): ConnectionSession {
        requireIn(setOf(ConnectionState.RECONNECTING))
        return nextReconnect(reason, nowMs)
    }

    /** The peer cannot be reached right now. */
    fun unreachable(reason: String, nowMs: Long): ConnectionSession = move(
        setOf(ConnectionState.CONNECTING, ConnectionState.RECONNECTING), ConnectionState.UNAVAILABLE, nowMs,
        failure = sanitize(reason)
    )

    /** Authentication rejected, protocol violation, or other non-retryable error. */
    fun fail(reason: String, nowMs: Long): ConnectionSession = move(
        setOf(ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING, ConnectionState.CONNECTED, ConnectionState.RECONNECTING),
        ConnectionState.FAILED, nowMs, failure = sanitize(reason), peer = null
    )

    /** User-initiated disconnect. Configuration is kept so the connection can be reopened. */
    fun disconnect(nowMs: Long): ConnectionSession = move(
        setOf(
            ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING, ConnectionState.CONNECTED,
            ConnectionState.RECONNECTING, ConnectionState.UNAVAILABLE, ConnectionState.FAILED
        ),
        ConnectionState.CONFIGURED, nowMs, failure = null, reconnectAttempts = 0, peer = null
    )

    /** Revoke credentials / remove the connection. Valid from any state and always clears the peer. */
    fun revoke(nowMs: Long): ConnectionSession = copy(
        state = ConnectionState.DISCONNECTED, peer = null, failure = null, reconnectAttempts = 0, lastActivityMs = nowMs
    )

    private fun nextReconnect(reason: String, nowMs: Long): ConnectionSession {
        val attempts = reconnectAttempts + 1
        return if (attempts > MAX_RECONNECT_ATTEMPTS) {
            copy(
                state = ConnectionState.FAILED, peer = null, reconnectAttempts = attempts, lastActivityMs = nowMs,
                failure = sanitize("Gave up after $MAX_RECONNECT_ATTEMPTS reconnect attempts: $reason")
            )
        } else {
            // Keep the previous peer so the UI can say whom we are trying to reach; isLive is false meanwhile.
            copy(state = ConnectionState.RECONNECTING, reconnectAttempts = attempts, lastActivityMs = nowMs, failure = sanitize(reason))
        }
    }

    private fun requireIn(allowed: Set<ConnectionState>) {
        check(state in allowed) { "Invalid connection transition from $state." }
    }

    private fun move(
        allowedFrom: Set<ConnectionState>,
        to: ConnectionState,
        nowMs: Long? = null,
        failure: String? = this.failure,
        reconnectAttempts: Int = this.reconnectAttempts,
        peer: PeerInfo? = this.peer
    ): ConnectionSession {
        requireIn(allowedFrom)
        return copy(
            state = to, failure = failure, reconnectAttempts = reconnectAttempts, peer = peer,
            lastActivityMs = nowMs ?: lastActivityMs
        )
    }

    /** Failure text may originate from a remote peer: bound it and never retain credential-looking content. */
    private fun sanitize(reason: String): String =
        if (SensitiveFileGuard.containsSecret(reason)) "Error details withheld: they appeared to contain a credential."
        else reason.replace(Regex("\\s+"), " ").trim().take(MAX_FAILURE_LENGTH)
}
