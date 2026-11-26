## 2026-11-26 (recursive provider tool-schema validation)
- Fixed a validation gap where nested JSON objects were type-checked but their required fields, property types, and `additionalProperties` rules were not recursively enforced. Array item schemas now recursively validate object contents as well.
- Added hard bounds for schema nesting depth, object property count, and array item count so provider-controlled nested arguments cannot drive unbounded recursive validation or oversized container traversal.
- Added regression tests for valid nested objects, missing nested required fields, unexpected nested properties, wrong nested types, object items inside arrays, and excessive nesting depth.
- The validator remains deny-by-default: the entire provider tool-call batch is validated before any call is dispatched. No workspace write or execution capability was added.

## 2026-11-26 (validated dependency refresh)
- Merged Dependabot PRs [#5](https://github.com/johnr1sh/AirFite/pull/5), [#6](https://github.com/johnr1sh/AirFite/pull/6), [#7](https://github.com/johnr1sh/AirFite/pull/7), and [#8](https://github.com/johnr1sh/AirFite/pull/8). Updated DocumentFile 1.1.0, WorkManager 2.12.0, kotlinx-coroutines-android 1.11.0, and the test-only org.json dependency to 20260814.
- Each dependency PR had an individual successful GitHub Actions run for unit tests, lint, and debug APK assembly before merge. The combined main-head workflow after all dependency merges and documentation updates is the final integration gate.
- No dependency changes introduce broader storage permissions or workspace write/execute capabilities.

## 2026-11-26 (SAF responsiveness and Kotlin toolchain maintenance)
- Merged PR [#9](https://github.com/johnr1sh/AirFite/pull/9) as commit [46f39d5](https://github.com/johnr1sh/AirFite/commit/46f39d53f40f6dc55fc85a816ae4e449d6faff56). SAF folder enumeration and file preview reads now run on `Dispatchers.IO`; superseded jobs are cancelled, operation-generation tokens prevent stale results from publishing after a workspace/file switch, and the Files UI shows loading states. Added four JVM tests for the operation gate.
- PR validation run [45739503664](https://github.com/johnr1sh/AirFite/actions/runs/45739503664) passed unit tests, lint, and debug APK assembly. The first post-merge main run was cancelled by the next main push, so the current main-head run remains the integration gate.
- Merged Dependabot PR [#4](https://github.com/johnr1sh/AirFite/pull/4) as commit [771ae3d](https://github.com/johnr1sh/AirFite/commit/771ae3d3b002626f5a9b1a6eb59211d8f8914a6c), aligning Kotlin Gradle and Compose compiler plugins on 2.4.21.
- Research: [Android data-layer guidance](https://developer.android.com/topic/architecture/data-layer) recommends keeping external I/O behind data-source/repository boundaries and testing business logic with fakes; [SAF documentation](https://developer.android.com/training/data-storage/shared/documents-files) keeps access scoped to user-selected URIs; [Kotlin Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html) lists the fully supported AGP/Gradle ranges for Kotlin 2.4.21.
- A separate AGP 9.5.0 / Gradle 9.9.2 trial passed unit tests, lint, and APK assembly, but was not promoted: the published Kotlin 2.4.21 full-support matrix currently tops out at AGP 9.3.1 and Gradle 9.7.0. Keep the supported AGP 8.13.2 / Gradle 8.14.8 baseline rather than trading compatibility for a clean version notice.
- Main-head verification after the Kotlin update is pending; no full Android build success is claimed yet.

## 2026-11-26 (keep SAF workspace I/O off the UI thread)
- Moved selected-folder enumeration and file preview reads from synchronous ViewModel callbacks to `Dispatchers.IO`. This avoids blocking Compose interactions while Storage Access Framework providers enumerate remote or slow document trees.
- Added independent latest-request tokens for refresh and file-read operations. Switching workspaces invalidates both; opening a newer file invalidates an older preview; refreshing the list does not cancel a valid in-flight preview. Superseded jobs are cancelled and stale completions cannot overwrite current state.
- Removed the remaining synchronous `DocumentFile.fromTreeUri(...).name` lookup from workspace selection; the actual folder name is resolved with the asynchronous refresh.
- Added four pure Kotlin tests for latest-refresh wins, latest-file-read wins, workspace-switch invalidation, and independence between refresh and preview operations.
- Research: Android's [threading guidance](https://developer.android.com/topic/performance/threads) explains that blocking the main thread with I/O can stall rendering and input and lead to ANRs; the [coroutines guidance](https://developer.android.com/kotlin/coroutines/coroutines-adv) recommends `Dispatchers.IO` for disk and network I/O while updating UI state on the main dispatcher.
- Validation: the operation gate and all four test methods compiled with the local Kotlin compiler against a minimal JUnit-compatible harness; all 4 test methods passed. Android Gradle unit tests, lint, and APK assembly are not available locally, and no GitHub commit status/check run has been surfaced yet.

## 2026-11-26 (Android-free workspace tool service)
- Extracted read-only workspace tool dispatch into a pure Kotlin service behind a small data-source port. Tool argument validation, exact path membership, duplicate-path rejection, and search-query bounds no longer require Android classes.
- Kept Storage Access Framework authorization at the Android adapter boundary and re-resolve the selected path immediately before reading; the extraction does not widen the user-granted tree or add write/execute tools.
- Added JVM tests for list/read/search dispatch, traversal and missing-path rejection, directories and ambiguous duplicate entries, wrong/missing/unexpected arguments, unknown tools, and query limits.
- Research: Android's official [data layer guidance](https://developer.android.com/topic/architecture/data-layer) recommends interfaces/fakes around external data sources so business rules can be unit-tested; [SAF documentation](https://developer.android.com/training/data-storage/shared/documents-files) keeps file access tied to user-selected document/tree URIs. AirFite follows that boundary rather than converting SAF URIs to raw filesystem paths.
- Validation: the Android-free service and its eight JVM test methods compiled with the local Kotlin compiler and passed in a small JUnit-compatible harness. This validates the pure dispatch policy only; the Android Gradle unit-test, lint, and APK workflow for the new commits is still pending.

## 2026-11-26 (centralize Kotlin and Compose plugin versions)
- Moved Android Gradle Plugin, Kotlin Gradle Plugin, and Compose compiler plugin versions into the standard Gradle version catalog. Both Kotlin plugins now reference the single Kotlin version (2.3.21), correcting the prior mismatch where the Compose plugin was 2.0.21.
- Added weekly Dependabot version updates for Gradle manifests and GitHub Actions.
- Expanded Android CI path filters so version-catalog and Dependabot configuration changes run unit tests, lint, and APK assembly.
- Baseline verification before this change: main run [45552442418](https://github.com/johnr1sh/AirFite/actions/runs/45552442418) passed unit tests, lint, and debug APK assembly. This commit still requires its own workflow result.
- Gradle 8.14.8's setup-action out-of-date notice remains. A Gradle 9.9.2 trial failed during AGP 8.13.2 plugin application; upgrade AGP and migrate to built-in Kotlin only as a separately tested compatibility change.

## 2026-11-25 (enforce actual workspace search I/O budget)
- Changed workspace search to account for bytes actually read instead of trusting Storage Access Framework file-size metadata, which may be stale or under-reported.
- Each search read now receives only the remaining aggregate byte allowance and reads at most that many bytes. Failed/binary reads conservatively consume their reservation so repeated failures cannot evade the total I/O budget.
- Added a pure-Kotlin `WorkspaceSearchBudget` policy and five unit tests for aggregate bytes, per-file/file-count caps, invalid accounting, failed reads, and sequential reservations.
- Local Kotlin harness: five budget scenarios passed. Full Android unit tests, lint, and APK assembly remain subject to the current GitHub Actions run.

## 2026-11-25 (restore compatible Gradle CI baseline)
- Investigated failed main workflow run [45524521954](https://github.com/johnr1sh/AirFite/actions/runs/45524521954). AGP 8.13.2 fails during plugin application with Gradle 9.9.2 because it references Gradle's internal `InternalProblems` API, removed in Gradle 9.6. This occurs before app source compilation.
- Restored the CI-installed Gradle version to 8.14.8. Main run [45524244164](https://github.com/johnr1sh/AirFite/actions/runs/45524244164) previously passed unit tests, lint, and debug APK assembly with the same app toolchain and Gradle 8.14.8.
- Keep Gradle 9 migration blocked until the Android Gradle Plugin is upgraded to a version compatible with that Gradle line. The new main commit's full workflow is the current verification gate.

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
