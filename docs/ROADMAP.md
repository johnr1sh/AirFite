# AirFite Engineering Roadmap

Updated: 2026-11-22

## Milestone 1 — Scoped workspace foundation
- [x] Android project at repository root
- [x] SAF folder selection and persisted grant
- [x] Workspace file listing and text preview
- [x] Transport-neutral read-only tool registry with MCP-style schemas
- [x] Path validation, file/depth/read-size limits
- [x] Skip hidden and common generated/dependency folders
- [ ] Add unit tests for path validation and tool dispatch

## Milestone 2 — Reliable provider and sessions
- [ ] Keystore-backed encrypted API-key storage
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
