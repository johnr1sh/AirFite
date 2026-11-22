# Development log

Newest first. Entries state only what was verified; see ROADMAP.md for what remains.

## 2026-11-22
- Audit of `main` (17 files, no database, no navigation, no MCP, single-file chat/workspace UI).
- Fixed: chat requests used the raw settings text as the API base URL, bypassing HTTPS/credential/query validation, so the key could be sent to a URL settings would have rejected. Requests now use `ProviderEndpoint.normalize`.
- Fixed: attachment secret scan missed hyphenated keys (`sk-proj-…`, `sk-ant-…`); added GitHub/Slack token patterns. Logic moved to `workspace/SensitiveFileGuard`.
- Added `workspace/WorkspacePath` (relative-path validation) used by `workspace.read_file`.
- Added `agent/ConnectionSession` (state machine where `CONNECTED` requires a completed handshake with peer identity) and `agent/ToolPolicy` (deny-by-default capability gate).
- Verification: the Android-free sources and their 50 JUnit tests were compiled with Kotlin 2.0.21 and run on the JVM locally. The Android build, lint and instrumented behavior were not run locally (no Android SDK or Maven access in this environment); they are covered only by the GitHub Actions workflow.
