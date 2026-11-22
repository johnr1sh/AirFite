# Development log

Newest first. Entries state only what was verified; see ROADMAP.md for what remains.

## 2026-11-22 (UI redesign)
- Replaced the single-file UI with a design system and four screens: `ui/theme`, `ui/components` (logo mark, notices, empty states), `ui/chat`, `ui/workspace`, `ui/agents`, `ui/settings`, shell in `ui/AirFiteApp.kt`.
- Added the AirFite logo (Compose Canvas mark, adaptive launcher icon incl. monochrome layer, `docs/assets/airfite-logo.svg`) and a dark framework theme with edge-to-edge system bars.
- Every value shown is real state: provider-ready dot reflects whether a key is set; Agents screen states that no connections exist and that the MCP transport is not built; tool list comes from the live manifest; tier statuses mirror what is implemented.
- Not verified locally: no emulator or Android SDK here, so the Compose UI was only built by CI, never rendered on a device. The logo SVG was rendered and inspected.

## 2026-11-22
- Audit of `main` (17 files, no database, no navigation, no MCP, single-file chat/workspace UI).
- Fixed: chat requests used the raw settings text as the API base URL, bypassing HTTPS/credential/query validation, so the key could be sent to a URL settings would have rejected. Requests now use `ProviderEndpoint.normalize`.
- Fixed: attachment secret scan missed hyphenated keys (`sk-proj-…`, `sk-ant-…`); added GitHub/Slack token patterns. Logic moved to `workspace/SensitiveFileGuard`.
- Added `workspace/WorkspacePath` (relative-path validation) used by `workspace.read_file`.
- Added `agent/ConnectionSession` (state machine where `CONNECTED` requires a completed handshake with peer identity) and `agent/ToolPolicy` (deny-by-default capability gate).
- Verification: the Android-free sources and their 50 JUnit tests were compiled with Kotlin 2.0.21 and run on the JVM locally. The Android build, lint and instrumented behavior were not run locally (no Android SDK or Maven access in this environment); they are covered only by the GitHub Actions workflow.
