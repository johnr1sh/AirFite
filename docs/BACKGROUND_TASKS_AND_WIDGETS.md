# Background tasks and home widget

## Quick widget
AirFite provides a resizable home-screen widget with direct shortcuts to Chat and Files. Add it from the Android launcher widget picker. The widget is a shortcut surface; it does not make API calls or run an agent itself.

## Background tasks
The Agents screen can queue a prompt using WorkManager. Work is persisted by Android and constrained to an available network connection. The worker retries transient I/O failures with exponential backoff, then reports success or failure through a notification when notification permission is granted.

- Prompt limit: 6,000 characters.
- Provider responses are bounded to 2 MB.
- At most 20 task-history records are retained.
- Provider key, prompt, task title, and successful response are encrypted with the app's Android Keystore-backed AES-GCM key.
- Background tasks do not automatically read workspace files or invoke workspace tools.
- The worker currently performs one OpenAI-compatible chat-completions request. It is not yet an autonomous multi-step agent or a foreground service.
- Android and WorkManager may defer execution to preserve battery and system health. A queued task is not a guarantee of immediate execution.
- Failed tasks retain their encrypted input for an explicit Retry action. Successful tasks remove their encrypted input while retaining the encrypted result.

## Notifications
AirFite requests notification permission when a user queues their first background task on Android 13 or newer. Tasks still appear in the in-app history if permission is declined. Notifications contain generic task status text rather than prompt or response content.

## Privacy
Background prompts and responses are stored encrypted in app-private preferences. Task inputs are not sent to an AirFite server; the request is sent directly to the configured provider. App backup is disabled in the current manifest.
