# AirFite Engineering Roadmap

Updated: 2026-11-22

## Active focus — product foundation and agent connectivity
- [x] Establish secure external-agent connection architecture and threat model (design baseline)
- [ ] Rebuild navigation and UX around projects, chat, workspace, connections, activity, and settings
- [ ] Add connection/session state model and tests
- [ ] Implement and verify a first interoperable MCP transport
- [ ] Add agent connection management, capability grants, and revocation
- [ ] Add invocation/activity history and approval queue

## Milestone 1 — Scoped workspace foundation
- [x] Android project at repository root
- [x] SAF folder selection and persisted grant
- [x] Workspace file listing and text preview
- [x] Transport-neutral read-only tool registry with MCP-style schemas
- [x] Path validation, file/depth/read-size limits
- [x] Keystore-backed AES-GCM provider key storage and plaintext-preference migration
- [x] Mask API key input and surface secure-save errors
- [x] Skip hidden and common generated/dependency folders
- [x] Explicit, bounded single-file context attachment to chat
- [x] Sensitive filename and common credential-pattern attachment checks
- [x] Treat attached file content as untrusted input in model instructions
- [ ] Add unit tests for path validation and tool dispatch

## Milestone 2 — Reliable provider and sessions
- [x] Keystore-backed encrypted API-key storage
- [x] HTTPS endpoint and model validation at settings save
- [x] Multi-turn chat context (bounded to last 12 messages)
- [x] Keep failed prompts available for retry
- [ ] Provider presets and endpoint validation
- [ ] Streaming, cancellation, retry/backoff, request limits
- [ ] Persistent conversation sessions and interrupted-task recovery
- [ ] Provider abstraction

## Milestone 3 — Safe agent editing
- [ ] Planner/executor/reviewer cycle with bounded iterations
- [ ] Patch proposals, diff preview, backups, undo
- [ ] Explicit approval for write/delete/command tools
- [ ] Context indexing with ignore rules and secret exclusion
- [ ] Tool-call schema validation and malformed-response recovery

## Milestone 4 — Interoperability
- [ ] MCP server transport for scoped workspace tools
- [ ] MCP client integration
- [ ] Relay design: phone-initiated outbound connection, scoped expiring tokens
- [ ] Patch/task bundle exchange for clients without MCP support
- [ ] Agent connection dashboard, permissions, audit log, revocation

## Milestone 5 — Engineering workflows
- [ ] Git status/diff/branch awareness
- [ ] Supported test/build execution with explicit sandbox limits
- [ ] Diagnostics, bounded repair attempts, verified outcomes
- [ ] Accessibility, keyboard navigation, tablet layouts, UI tests

## Current constraints
Workspace tool descriptors are MCP-style, but no MCP network transport or external AI connection is implemented yet. File access is read-only. Workspace content is not automatically sent to the model. Build verification is delegated to CI; report actual workflow status before claiming success.

Architecture decisions and security gates for external agent connections are documented in [AGENT_CONNECTION_ARCHITECTURE.md](AGENT_CONNECTION_ARCHITECTURE.md).
