# AirFite Engineering Roadmap

Updated: 2026-11-24

## Active focus — product foundation and agent connectivity
- [x] Establish secure external-agent connection architecture and threat model (design baseline)
- [x] Navigation shell and redesigned UI: Chat, Files, Agents, Settings; theme, logo, adaptive launcher icon (dark only)
- [ ] Projects, activity log and approval-queue destinations (need the database and approval flow first)
- [ ] Light theme and tablet layouts
- [ ] UI tests (Compose) for navigation, settings and error states
- [x] Persist bounded recent chat history and provide clear/new-chat action
- [x] Add provider connectivity diagnostic against OpenAI-compatible `/models`
- [x] Preserve encrypted API key when saving settings with key field blank
- [x] Add workspace refresh and bounded search scan
- [x] Add connection/session state model and tests (`agent/ConnectionSession`; pure Kotlin, not yet wired to a transport or UI)
- [x] Central deny-by-default tool policy with capability tiers (`agent/ToolPolicy`; not yet wired to a transport or UI)
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
- [x] Honor bounded root `.gitignore` rules during workspace listing and search (comments, negation, anchoring, directory-only, `*`, `?`, `**`)
- [x] Explicit, bounded single-file context attachment to chat
- [x] Sensitive filename and common credential-pattern attachment checks (dotenv variants, credential config files, SSH key names, GitLab/Google/Stripe/JWT patterns, and assignment-style secrets)
- [x] Treat attached file content as untrusted input in model instructions
- [x] Unit tests for path validation (`workspace/WorkspacePath`) and secret/sensitive-file guard
- [ ] Unit tests for tool dispatch (blocked on an Android-free workspace abstraction)

## Milestone 2 — Reliable provider and sessions
- [x] Keystore-backed encrypted API-key storage
- [x] HTTPS endpoint and model validation at settings save
- [x] Multi-turn chat context (bounded to last 12 messages)
- [x] Keep failed prompts available for retry
- [x] Shared HTTPS provider endpoint validation (rejects credentials, query/fragment, malformed hosts, and invalid ports), enforced on chat requests as well as settings
- [ ] Provider presets
- [x] Bounded provider response bodies and explicit connection/read/call timeouts
- [ ] Streaming, cancellation, retry/backoff, request limits
- [x] Persistent recent conversation (40-message cap)
- [x] Encrypted WorkManager background prompt tasks with bounded history (20 records), active queue cap (10), retry, cancel, and cancellation-safe result persistence
- [ ] Cancel in-flight background HTTP calls with WorkManager; retry transient provider HTTP statuses through bounded exponential backoff (implemented; CI verification pending)
- [ ] Multi-session conversation management and interrupted-task recovery
- [ ] Provider abstraction

## Milestone 3 — Safe agent editing
- [ ] Planner/executor/reviewer cycle with bounded iterations
- [ ] Patch proposals, diff preview, backups, undo
- [ ] Explicit approval for write/delete/command tools
- [x] Root `.gitignore` filtering for workspace listing/search (ignore matcher tests); [ ] deeper context indexing with secret exclusion
- [x] Validate provider tool-call envelopes against registered schemas (IDs, duplicate IDs, JSON object arguments, required fields, types, numeric/string/array bounds, array item schemas, and bounded argument size)
- [x] Keep oversized tool results valid JSON with explicit partial-preview metadata
- [x] Unit tests for tool-call envelope/schema validation and result bounds
- [ ] Retry/recover malformed provider responses without dispatching invalid calls

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
Background prompt runs are single provider requests, not autonomous multi-step agents. Task history is capped at 20 records; up to 10 tasks may be queued/running/retrying, active tasks are not evicted, and cancellation is persisted so late worker updates cannot overwrite it. WorkManager cancellation now cancels the in-flight OkHttp call; network failures and HTTP 408/425/429/5xx use bounded exponential retries. Workspace tool descriptors are MCP-style, but no MCP network transport or external AI connection is implemented yet. File access is read-only. Provider tool-call envelopes are validated before dispatch; malformed calls fail closed, and oversized tool results are returned as bounded valid JSON with partial-preview metadata. Workspace content is not automatically sent to the model. Build verification is delegated to CI; report actual workflow status before claiming success.

Architecture decisions and security gates for external agent connections are documented in [AGENT_CONNECTION_ARCHITECTURE.md](AGENT_CONNECTION_ARCHITECTURE.md).
