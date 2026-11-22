# Agent Connections: Architecture and Security

Status: design baseline. Implemented so far: the connection session state machine (`agent/ConnectionSession`) and the deny-by-default tool policy (`agent/ToolPolicy`), both pure Kotlin with unit tests and **not yet wired to any transport or UI**. There is no MCP transport and no external agent connection yet.

AirFite is intended to let an external AI agent work with a user-approved Android workspace without requiring AirFite to host or pay for the model. AirFite provides tools and execution boundaries; the connected agent provides reasoning and inference.

## Product principles

- **Bring your own agent:** external-agent workflows do not require a bundled model or AirFite API key.
- **One connection model:** connection types are adapters over a shared session, tool registry, permissions, and audit model.
- **User-owned workspace:** access remains limited to the directory granted through Android's Storage Access Framework.
- **Least privilege:** begin read-only. Write, delete, shell, build, and Git mutation capabilities are separate grants and require explicit approval.
- **Visible execution:** show requesting agent, tool, target, argument summary, progress, result, and approval state.
- **Revocable access:** every connection has a disconnect/revoke control. Never display or log session secrets.
- **Honest status:** distinguish configured, authenticated, reachable, and active session states.

## Proposed layers

1. **Connection adapters** — MCP-compatible transport, app-to-app intents/deep links where supported, LAN pairing, and optional outbound relay. Unsupported transports must not appear as working.
2. **Session manager** — lifecycle, handshake, heartbeat, expiry, reconnection, and session identity.
3. **Tool registry** — typed schemas, capability metadata, versioned definitions, stable identifiers.
4. **Policy/approval engine** — checks before every invocation; risk tiers; per-workspace and per-connection grants; deny by default.
5. **Execution engine** — bounded concurrency, timeout/cancellation, validation, structured results, redacted logs.
6. **Workspace boundary** — SAF-only access; root-bound path validation; bounded reads/indexing; explicit write proposals.
7. **Activity/audit UI** — connections, tool catalog, approvals, execution history, errors, and revocation.

## Initial connection strategy

Prefer phone-initiated connections instead of exposing an unauthenticated listener. Pairing should use a short-lived challenge and confirm remote identity before accepting requests. LAN connectivity is opt-in, authenticated, and discloses network exposure. A relay is a separate capability using scoped, expiring credentials.

MCP transport must follow the current protocol specification and live behind a transport interface. Existing MCP-style tool descriptors are not a functioning MCP connection.

## Capability tiers

| Tier | Examples | Default |
|---|---|---|
| Read | list, read, search workspace files | Only inside selected workspace |
| Propose | generate patch, show diff | No mutation |
| Write | apply approved patch, create file | Per-action approval |
| Destructive | delete, overwrite, bulk edits | Strong confirmation and backup |
| Execute | shell, build, tests | Disabled until isolation is reviewed |

## Threat model and mitigations

- **Prompt/tool injection:** workspace content and remote tool descriptions/results are untrusted; they cannot override policy or authorization.
- **Tool poisoning/schema changes:** show origin and permission impact; renew approval for materially changed tool definitions.
- **Path traversal:** resolve and validate paths against granted root; reject absolute paths and traversal; test document-provider edge cases.
- **Credential leakage:** redact secrets in UI, logs, tool output, exceptions, and exports; use Keystore-backed storage.
- **Replay/impersonation:** authenticated pairing, expiring tokens, capability-bound credentials, and request/session IDs.
- **Over-privileged agent:** capability grants; no implicit access to unrelated apps/accounts; approval for mutations.
- **Denial of service:** bound request size, tool output, concurrency, duration, indexing, and retries.
- **Network exposure:** no open listener by default; prefer outbound connections; TLS; document local-network mode.

## Implementation gates

1. Add connection/session models and state tests without claiming interoperability.
2. Implement one supported MCP transport and handshake; test with a reference peer.
3. Add read-only workspace tools and capability discovery.
4. Add connection management, permission review, and activity log UI.
5. Implement patch proposal/diff/backup/undo before enabling writes.
6. Evaluate shell/build execution separately; no arbitrary execution without credible isolation and resource limits.

## Definition of done

- Protocol interoperability tests pass.
- Connection/identity states are observable and recoverable.
- Invocations pass schema validation and policy checks.
- Tool scope is restricted to granted capabilities.
- Revocation immediately blocks future calls.
- Errors/logs are redacted and bounded.
- UI/docs accurately describe supported transports and limitations.
