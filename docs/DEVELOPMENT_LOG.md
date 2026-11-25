## 2026-11-25 (restore compatible Gradle CI baseline)
- Investigated failed GitHub Actions run [45524521954](https://github.com/johnr1sh/AirFite/actions/runs/45524521954). The failure occurs while applying AGP 8.13.2: its application plugin references Gradle's internal `InternalProblems` API, removed in Gradle 9.6. This is a toolchain compatibility failure, not an app-source compilation failure.
- Reverted the CI-installed Gradle version to 8.14.8. Android's AGP compatibility table lists 8.13 as requiring Gradle 8.13 minimum, so 8.14.8 satisfies the published minimum. The previous 8.14.8 workflow passed `testDebugUnitTest`, `lintDebug`, and `assembleDebug` on commit `28c2192f0a02ebb1e554b0b4ec3948ec33003aa8` (run [45524244164](https://github.com/johnr1sh/AirFite/actions/runs/45524244164)).
- Do not retry Gradle 9.6+ with AGP 8.13.2. Revisit only alongside an AGP release whose compatibility notes explicitly support that Gradle line.
- Research: [Android Gradle Plugin compatibility table](https://developer.android.com/build/releases/about-agp) and [AGP 8.13 release notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes).
- Verification for this rollback commit: pending its GitHub Actions run; the known-good prior run is recorded above.

## 2026-11-25 (CI runner warning cleanup and Gradle 9.9.2 trial)
- Set Git's default branch to main before checkout to remove the runner's init.defaultBranch hint.
- Upgrade the CI-installed Gradle from 8.14.8 to 9.9.2, the current supported release, to remove the setup action's out-of-date notice.
- AGP 8.13.2 requires Gradle 8.13 or newer; the new version satisfies the published minimum. The complete Android build is the compatibility gate, so this migration remains pending until CI passes.

## 2026-11-25 (encrypted, bounded chat history)
- Encrypt persisted chat history with the existing Android Keystore AES-GCM facility instead of writing prompts and responses as plaintext SharedPreferences values.
- Migrate the previous chatHistory preference to encryptedChatHistory on load; retain the legacy value only if encryption is temporarily unavailable and show an in-app warning.
- Bound in-memory and persisted history to the newest 40 messages and 128 Ki UTF-16 code units, marking a truncated message and avoiding broken UTF-16 surrogate pairs.
- Clear both encrypted and legacy history on New chat. Added pure-Kotlin policy tests for message/character caps, newest-message priority, truncation notice, and surrogate safety.
- Android CI is the integration verification source; local validation covers the Android-free history policy only.

## 2026-11-24 (background HTTP cancellation and transient retry policy)
- Replaced the background worker's blocking OkHttp execution with a cancellable callback adapter using `suspendCancellableCoroutine`; WorkManager cancellation now cancels the underlying call and closes a response if callback delivery races cancellation.
- Retry network I/O failures and HTTP 408, 425, 429, and 5xx through the existing bounded exponential WorkManager backoff; permanent HTTP errors fail immediately.
- Added pure-Kotlin retry-policy tests for attempt bounds and transient/permanent HTTP status classification.
- Added a root `.gitignore` for Gradle/Android build outputs, local configuration, signing material, and environment credentials.
- Local validation: retry policy compiled and its boundary/status cases passed in a Kotlin harness. Android build/lint/worker integration tests are not available in this environment; CI verification is pending.

## 2026-11-23 (workspace ignore rules and CI warning cleanup)
- Workspace listing and bounded search now honor the selected root folder's `.gitignore`, capped at 32 KB; unreadable, oversized, or binary ignore files fall back to no user rules rather than hiding files.
- Added pure-Kotlin matcher tests for root anchoring, nested directory rules, glob patterns, negation, escaped comment/negation markers, and blank lines.
- CI uses Gradle 8.14.8, explicitly preserves the AndroidX Graphics Path native library that AGP could not strip, uses the auto-mirrored chat arrow, and cancels stale same-ref runs. The build command uses `--warning-mode=all`.
- Kotlin matcher harness passed 29 assertions. CI run 185 (`a913266`) passed unit tests, lint, and APK assembly; run 186 (`4d2df25`) also passed.
- Remaining Gradle deprecation warnings originate from AGP internals (`isCrunchPngs`, `isUseProguard`, `isWearAppUnbundled`, and null attribute lookup). Gradle 8.14.8 is the latest patched 8.x line; its “out of date” notice is informational. A coordinated AGP/Kotlin/Gradle upgrade is the next compatibility cleanup.

# Development log

Newest first.

## 2026-11-24 (workspace credential read safeguards)
- Applied sensitive-path checks before opening files and credential-content checks after bounded reads, covering both workspace previews and the agent's `workspace.read_file` tool.
- Expanded path detection for dotenv variants, SSH private-key names, common credential stores, API-key names, and keystore formats. Conventional `.env.example`, `.env.sample`, and `.env.template` files remain readable, but their contents are still scanned.
- Added best-effort detection for AWS, Google, OpenAI/Anthropic, GitHub, Slack, GitLab, Stripe, JWT-like, and common credential-assignment patterns, with common placeholders excluded.
- Sensitive-looking paths are omitted from workspace search; search query length is capped at 256 characters and declared in the tool schema.
- Added regression tests for path variants, provider tokens, assignment patterns, placeholders, and the read-sharing policy.
- Local Kotlin harness: all 11 credential-guard test methods passed. Full Android Gradle tests, lint, and APK assembly are pending GitHub Actions for the pushed commit.

## 2026-11-23 (background task persistence and cancellation hardening)
- Added a pure Kotlin task-record retention policy and six tests covering active-status counting, upsert, oldest-terminal eviction, active-task preservation, and invalid limits.
- Background task record/input writes now share synchronized, single-editor updates. The store caps active queued/running/retrying tasks at 10, retains at most 20 records when terminal records can be pruned, and cleans encrypted inputs for evicted/completed/cancelled tasks.
- Retry admission is atomic with the state transition, preventing concurrent retries from exceeding the active-task cap. Cancelling persists a terminal state before WorkManager cancellation; late worker callbacks cannot overwrite cancellation or recreate deleted records.
- Added cancellation checks before/after provider requests to avoid publishing results after cancellation. WorkManager may still finish an already-running HTTP call because cancellation does not forcibly terminate the synchronous OkHttp call.
- Verification: pending CI for these changes. No local Android SDK/build is available in this run.


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
