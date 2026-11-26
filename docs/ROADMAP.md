# AirFite Engineering Roadmap

Updated: 2026-11-26

## Active focus — product foundation and agent connectivity
- [x] Establish secure external-agent connection architecture and threat model (design baseline)
- [x] Navigation shell and redesigned UI: Chat, Files, Agents, Settings; theme, logo, adaptive launcher icon (dark only)
- [ ] Projects, activity log and approval-queue destinations (need the database and approval flow first)
- [ ] Light theme and tablet layouts
- [ ] UI tests (Compose) for navigation, settings and error states
- [x] Persist bounded recent chat history and provide clear/new-chat action
- [x] Encrypt saved chat history with Android Keystore AES-GCM; migrate legacy plaintext history and cap it at 40 messages / 128 Ki UTF-16 code units
- [x] Add provider connectivity diagnostic against OpenAI-compatible `/models`
- [x] Centralize AGP, Kotlin, and Compose compiler plugin versions in a Gradle version catalog; Kotlin and Compose compiler plugins now share Kotlin 2.4.21
- [x] Configure weekly Dependabot version updates for Gradle dependencies and GitHub Actions
- [x] Refresh validated dependencies: DocumentFile 1.1.0, WorkManager 2.12.0, kotlinx-coroutines 1.11.0, and org.json 20260814 (test scope)
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
- [x] Sensitive filename and credential-pattern safeguards for explicit attachments, workspace previews, and agent file reads (dotenv variants except conventional templates, SSH/private-key files, credential stores, provider tokens, JWT-like values, and assignment-style secrets)
- [x] Treat attached file content as untrusted input in model instructions
- [x] Unit tests for path validation (`workspace/WorkspacePath`) and secret/sensitive-file guard
- [x] Extract Android-free workspace tool dispatch policy behind a narrow SAF data-source port; cover listing, exact-path reads, traversal rejection, duplicate paths, argument validation, and bounded search with JVM unit tests
- [x] Run SAF workspace enumeration and preview reads on `Dispatchers.IO`; cancel superseded jobs, reject stale results after workspace/file changes, and expose loading states with operation-gate tests
- [x] Run SAF workspace listing and preview reads on `Dispatchers.IO`; cancel superseded requests and prevent stale results from replacing the currently selected workspace or file

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
- [x] Cancel in-flight background HTTP calls with WorkManager; retry transient provider HTTP statuses through bounded exponential backoff
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
- [ ] Upgrade to Gradle 9 only alongside an Android Gradle Plugin release that explicitly supports it
- [ ] Git status/diff/branch awareness
- [ ] Supported test/build execution with explicit sandbox limits
- [ ] Diagnostics, bounded repair attempts, verified outcomes
- [ ] Accessibility, keyboard navigation, tablet layouts, UI tests

## Current constraints
CI is pinned to Gradle 8.14.8 with AGP 8.13.2 and Kotlin/Compose compiler plugins 2.4.21. This combination is within Kotlin 2.4.21's published full-support matrix. A separate AGP 9.5.0 / Gradle 9.9.2 trial passed CI, but is not promoted because Kotlin 2.4.21's published full-support matrix currently caps AGP at 9.3.1 and Gradle at 9.7.0. The setup action's Gradle out-of-date notice is informational; do not suppress it or adopt an unsupported pair merely to hide it. Revisit with a fully supported AGP/Gradle/Kotlin combination.
The Compose compiler Gradle plugin must match the Kotlin Gradle plugin; both are now versioned from `gradle/libs.versions.toml`. CI's Gradle 8.14.8 out-of-date notice remains informational until a separately validated AGP 9.x / Gradle 9.x migration.
Chat history is encrypted with the Android Keystore; legacy plaintext history is migrated on load when encryption succeeds, and saved history is capped at 40 messages / 128 Ki UTF-16 code units. Background prompt runs are single provider requests, not autonomous multi-step agents. Task history is capped at 20 records; up to 10 tasks may be queued/running/retrying, active tasks are not evicted, and cancellation is persisted so late worker updates cannot overwrite it. WorkManager cancellation now cancels the in-flight OkHttp call; network failures and HTTP 408/425/429/5xx use bounded exponential retries. Workspace tool descriptors are MCP-style, but no MCP network transport or external AI connection is implemented yet. File access is read-only. Provider tool-call envelopes are validated before dispatch; malformed calls fail closed, and oversized tool results are returned as bounded valid JSON with partial-preview metadata. Workspace content is not automatically sent to the model. Likely credential paths and recognized credential-bearing content are blocked from workspace previews and agent reads; this is heuristic protection, not a guarantee that all secrets are detected. Agent search queries are capped at 256 characters. Build verification is delegated to CI; report actual workflow status before claiming success.

Architecture decisions and security gates for external agent connections are documented in [AGENT_CONNECTION_ARCHITECTURE.md](AGENT_CONNECTION_ARCHITECTURE.md).
