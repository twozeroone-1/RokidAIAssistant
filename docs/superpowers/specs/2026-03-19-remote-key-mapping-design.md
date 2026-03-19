# Remote Key Mapping Design

Date: 2026-03-19

## Goal

Add user-configurable Bluetooth remote key mappings so a user can:

- press a learned key to toggle glasses recording
- press a learned key to trigger glasses photo capture and AI analysis
- configure those mappings from the main phone settings screen

The existing hardcoded key behavior must remain as a fallback when no custom key has been learned.

## Current Project Constraints

- The settings UI lives in the phone app and already uses `ApiSettings` plus `SettingsRepository`.
- Physical key events are currently handled locally in the glasses app `MainActivity`.
- Phone and glasses already communicate over the existing Bluetooth SPP message channel using `Message` and `MessageType`.
- The current repo does not have a shared runtime settings bridge like the separate remote-key-mapping worktree used; that worktree solved cross-device sync differently.

## Recommended Architecture

Use a phone-owned settings model, an SPP-based learning/sync protocol, and glasses-side key resolution.

### Phone side

- Add remote input settings fields to `ApiSettings`.
- Persist them in `SettingsRepository`.
- Add a `Remote Key Mapping` section to the main `SettingsScreen`.
- Allow the user to start learning either the recording key or the camera key.
- Show the current learned key names in human-readable form.

### Glasses side

- Add a small resolver that maps a `keyCode` to a semantic action:
  - toggle recording
  - capture photo
  - no custom action
- Keep existing hardcoded key handling as fallback behavior.
- When the phone requests learning mode, the glasses capture the next allowed physical key and send it back to the phone.

### Sync channel

Reuse the existing SPP `Message` / `MessageType` channel.

New message types will cover:

- phone -> glasses: start learning record key
- phone -> glasses: start learning camera key
- phone -> glasses: cancel learning
- phone -> glasses: push current remote key settings
- glasses -> phone: learned key result

This keeps the design aligned with the current repo architecture and avoids inventing a second transport.

## UI Design

The new section belongs in the main phone settings screen, not in the docs settings screen.

The section includes:

- learned record key row
- learned camera key row
- learn record key action
- learn camera key action
- clear mappings action
- status text for:
  - waiting for next key on glasses
  - saved successfully
  - blocked key
  - duplicate key rejection

The UI remains phone-centric, but the actual key capture happens on the glasses so the learned input reflects real hardware behavior.

## Behavior Rules

### Learning

- User taps `Learn Recording Key` or `Learn Camera Key` in phone settings.
- Phone stores the current learning target and sends a learning command to the glasses.
- Glasses capture the next non-blocked key event and send the selected key code back.
- Phone validates the learned key and saves it.

### Validation

- Record key and camera key must be different.
- Disallow blocked keys such as:
  - unknown
  - home
  - back
  - app switch
  - volume keys
- If validation fails, keep old saved values and show an error message.

### Runtime handling

- If a saved custom record key matches the pressed key, it toggles recording.
- If a saved custom camera key matches the pressed key, it captures a photo and starts AI analysis.
- If no custom key matches, existing hardcoded behavior still applies.

## Data Model

Add the following settings to `ApiSettings`:

- `remoteRecordKeyCode: Int?`
- `remoteCameraKeyCode: Int?`
- `remoteKeyLearningTarget: RemoteKeyLearningTarget?`
- `remoteKeyLearningStatusMessage: String`

The persisted model needs a validation helper to reject duplicate mappings.

Define an enum similar to:

- `RECORD`
- `CAMERA`

The learning target can stay phone-owned. The glasses only need enough information to know whether they are currently learning and which action the next key belongs to.

## Error Handling

- If the service is not connected when learning starts, show a status message instead of silently failing.
- If the glasses do not receive the command, the phone remains in a waiting state until cancelled or replaced.
- If the glasses send a blocked or duplicate key, the phone rejects it and updates the status message.
- If the user starts a new learning action while another is active, the new one replaces the old one.

## Testing Strategy

Add focused tests before implementation:

- `ApiSettings` validation rejects duplicate record/camera keys.
- A key formatting helper renders stable labels for saved keys.
- A glasses-side resolver prefers custom mappings and falls back otherwise.
- Repository persistence stores and restores remote key settings.

Integration tests can remain narrow. The first goal is confidence in the model, persistence, and resolution logic.

## Scope Limits

- No new standalone settings screen.
- No migration to a new transport layer.
- No removal of existing hardcoded key behavior.
- No attempt to map every physical key action in v1.

## Implementation Summary

This feature should be built by extending the current phone settings model, adding a small SPP message protocol for learning/sync, and layering a custom-key resolver on top of the existing glasses key handling. The design intentionally keeps changes local and incremental so the new feature can ship without destabilizing current recording and photo flows.
