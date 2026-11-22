# Pocket Engineer — Android starter

A lightweight Android-first AI coding companion foundation.

## Current MVP
- Jetpack Compose UI
- Provider configuration (OpenAI-compatible endpoint, API key, model)
- Chat interface
- Local provider settings persistence
- Workspace architecture placeholder

## Build
Open this directory in Android Studio, sync Gradle, and run on an Android device.

Requirements:
- Android Studio
- JDK 17
- Android SDK 35

## Configure
In the app, open Settings and enter:
- Base URL (example: https://api.openai.com/v1)
- API key
- Model name (example: gpt-4o-mini or a compatible provider model)

Never commit API keys.

## Roadmap
1. Workspace picker using Android Storage Access Framework
2. File tree and editor
3. Agent planner + tool calls (read/write/list/search)
4. Sandboxed command execution
5. Git diff and commit
6. Build/test loop
7. Streaming output
8. Provider presets (Cerebras, Groq, OpenRouter, Gemini-compatible gateways)

This is a starter source project, not a prebuilt APK.
