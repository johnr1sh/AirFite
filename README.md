# AirFite

AirFite is an Android-first coding assistant with a user-authorized local workspace.

## Current capabilities
- Jetpack Compose Android app and OpenAI-compatible provider settings
- Android Storage Access Framework workspace folder selection
- Scoped file listing, text preview, and search
- Explicit single-file context attachment to chat (64 KB limit; sensitive-name and common secret-pattern checks)
- Transport-neutral workspace tool registry with MCP-style descriptors
- Read-only tools: `workspace.list_files`, `workspace.read_file`, and `workspace.search_files`
- File count, depth, and read-size limits; hidden and build/dependency folders excluded
- GitHub Actions debug APK build

## Credential security
Provider keys are encrypted using a key stored in Android Keystore. Legacy plaintext preferences are migrated on first load where possible. The key field is masked in the UI.

## Workspace security
AirFite only accesses the folder selected through Android's document picker. The current milestone exposes read-only tools; it does not advertise write, delete, or shell tools. Workspace content is never auto-sent. Users can explicitly attach a selected file to the next message; file context is capped at 64 KB and common secret patterns are blocked. Attached content is treated as untrusted data.

## Build
Open the repository root in Android Studio with JDK 17 and Android SDK 35, or run `gradle assembleDebug`. The CI workflow builds and uploads a debug APK artifact.

## Roadmap
See [docs/ROADMAP.md](docs/ROADMAP.md) for planned agent interoperability, safe editing, MCP transport, relay, session recovery, Git, and test execution.
