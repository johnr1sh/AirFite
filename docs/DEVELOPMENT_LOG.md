## 2026-11-23 (workspace ignore rules and CI warning cleanup)
- Workspace listing and bounded search now honor the selected root folder's `.gitignore`, capped at 32 KB; unsupported/invalid or unreadable ignore files fall back to no user rules rather than hiding files.
- Added pure-Kotlin matcher tests for root anchoring, nested directory rules, glob patterns, negation, escaped comment/negation markers, and blank lines.
- CI now uses Gradle 8.14.8 (latest patched 8.x at this update); explicitly keeps the AndroidX Graphics Path native library that AGP could not strip; switched the deprecated chat arrow to its auto-mirrored icon.
- CI result for the pre-change head `9c80ff3`: testDebugUnitTest, lintDebug, assembleDebug all passed (run 158). Changes in this entry require a new CI run; no build is claimed yet.

# Development log

Newest first.

## 2026-11-23 (capabilities expansion)
- Added guided provider setup with endpoint/key/model steps, validation, save-and-test flow, and model discovery.
- Added recent-workspace switching, file-search empty states, and safer workspace change confirmation.
- Added tablet navigation rail, centered wide-screen content, and animated destination transitions.
- Added a Jetpack Glance home-screen widget with Chat and Files shortcuts.
- Added WorkManager background prompt runs, network constraints, retries, encrypted task input/output, task history, retry/cancel/delete actions, and start/completion notifications.
- Background runs are currently single provider requests; they do not yet use workspace tools or autonomous multi-step agent loops.
- Build verification is pending for the latest head; intermediate CI runs exposed source-formatting issues that have been repaired and requeued.


## 2026-11-23 (guided UI refinement)
- Added provider setup progress guidance for endpoint, API key, and model; connection testing and model discovery now have a dedicated status dialog.
- Added a confirmation dialog before switching an existing workspace and an empty-search state in the Files browser.
- Refined navigation and screen cards. Build verification is pending for the latest commits; earlier UI commits had compilation failures that are being corrected rather than treated as successful.

## 2026-11-23 (chat UX refinement)
- Reworked the empty-chat experience into a more focused welcome, readable quick-start cards, and an explicit workspace-context affordance.
- Refined the message composer into a rounded, bordered container with better attachment/send affordances and clearer placeholder copy.
- Softened the app-wide shape system to give cards and fields more modern, comfortable corner radii.
- UI changes are not device-rendered in this environment; Android CI is the verification source. Entries state only what was verified; see ROADMAP.md for what remains.

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
