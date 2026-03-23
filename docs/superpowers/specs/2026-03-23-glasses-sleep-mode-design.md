# Glasses Sleep Mode Design

Date: 2026-03-23

## Goal

Add a phone-controlled "sleep mode" display option for the glasses app that reduces screen usage during idle and in-progress workflows.

When enabled:

- the glasses keep the screen on
- idle, capture, send, and analyze states show a compact five-dot progress indicator
- final output keeps the existing text-first display behavior
- the same display model works for both voice and camera workflows

## Current Project Constraints

- The glasses UI is driven by `GlassesUiState` in `glasses-app` and rendered from `MainActivity`.
- The current glasses UI does not have a single workflow enum. It uses booleans and message callbacks such as:
  - `isListening`
  - `isProcessing`
  - `isCapturingPhoto`
  - `MessageType.USER_TRANSCRIPT`
  - `MessageType.AI_RESPONSE_TEXT`
  - `MessageType.PHOTO_ANALYSIS_RESULT`
- The phone already owns persistent runtime settings through `ApiSettings` and `SettingsRepository`.
- The phone and glasses already exchange runtime commands over the existing Bluetooth SPP `Message` / `MessageType` channel.

## Recommended Architecture

Use a phone-owned toggle plus a glasses-owned display-stage mapper.

### Phone side

- Add a persisted `glassesSleepModeEnabled` setting to the phone app settings model.
- Expose the toggle in the existing recording / glasses settings section.
- Sync the boolean to the glasses over the existing Bluetooth message channel:
  - when the setting changes
  - when the phone service starts
  - when the glasses reconnect

### Glasses side

- Add a small display-mode flag to the glasses runtime state.
- Add a focused workflow-stage enum for rendering only.
- Derive the rendering stage from current glasses state plus incoming phone messages.
- Keep the existing workflow logic for recording, photo capture, pagination, and result handling.
- Only change how the UI is rendered when sleep mode is enabled.

This keeps business logic stable and limits the change to settings sync plus conditional rendering.

## Shared Workflow Model

Sleep mode uses a common five-stage model for both voice and camera flows.

1. `IDLE`
   - default standby state
   - disconnected state is intentionally hidden here when sleep mode is on
2. `CAPTURING_INPUT`
   - voice: recording
   - camera: photo capture in progress
3. `SENDING`
   - voice: audio is being sent to the phone
   - camera: photo is being compressed and transferred
4. `ANALYZING`
   - voice: phone is recognizing / thinking
   - camera: photo is sent and phone AI analysis is pending
5. `OUTPUT`
   - final assistant answer or photo analysis result is visible

This stage model is rendering-specific. It does not replace the existing low-level state fields.

## UI Design

The glasses UI keeps the current black background and overall layout structure.

### Normal mode

- No behavior change.
- Existing status indicator, text, hints, pagination, and result display remain as-is.

### Sleep mode

- Replace the current top-right status indicator with five horizontally aligned dots.
- Exactly one dot is active at a time.
- Use the existing centered display area, but hide most text for the first four stages.

Sleep mode rendering rules:

- `IDLE`: only dot 1 active, no connection warning text
- `CAPTURING_INPUT`: only dot 2 active, no large text
- `SENDING`: only dot 3 active, no large text
- `ANALYZING`: only dot 4 active, no large text
- `OUTPUT`: only dot 5 active, existing result text remains visible

### Output dismissal

- The final output remains visible until the user taps to dismiss it.
- Dismissing output clears the result / pagination state and returns the display to `IDLE`.

## Phone Settings UX

Add a single switch in the existing phone settings screen near the other glasses-related toggles.

Suggested behavior:

- label: `Sleep mode on glasses`
- subtitle: explain that the glasses replace most standby / progress text with a compact progress indicator and only show full text for final output

This setting is phone-owned and should persist across app restarts.

## Protocol Changes

Reuse the existing Bluetooth SPP runtime message channel.

Add one message type dedicated to sleep-mode configuration sync.

- phone -> glasses: sleep mode enabled / disabled

The payload can be a simple string boolean or a tiny encoded value. It does not need a new transport or a complex settings bundle.

## Behavior Rules

### Voice flow mapping

- `startRecording()` -> `CAPTURING_INPUT`
- `stopRecording()` local send path -> `SENDING`
- `MessageType.AI_PROCESSING` and `MessageType.USER_TRANSCRIPT` -> `ANALYZING`
- `MessageType.AI_RESPONSE_TEXT` -> `OUTPUT`

### Camera flow mapping

- `captureAndSendPhoto()` capture step -> `CAPTURING_INPUT`
- compression / transfer step -> `SENDING`
- `photo_sent_waiting_ai` and remote processing wait -> `ANALYZING`
- `MessageType.PHOTO_ANALYSIS_RESULT` -> `OUTPUT`

### Error handling

Errors should still be visible.

Recommended rule:

- if an error occurs, temporarily show the error text even in sleep mode
- after the user dismisses it or a new interaction starts, return to the mapped stage

This preserves debuggability without abandoning the low-power display behavior.

## File Impact

Expected primary files:

- `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
- `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`
- `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
- `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`
- `phone-app/src/main/res/values/strings.xml`
- `phone-app/src/main/res/values-ko/strings.xml`
- `common/src/main/java/com/example/rokidcommon/protocol/MessageType.kt`
- `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt`
- `glasses-app/src/main/java/com/example/rokidglasses/MainActivity.kt`
- optional focused UI helper file under `glasses-app/src/main/java/com/example/rokidglasses/ui/`

## Testing Strategy

Add narrow tests around the new behavior instead of broad refactors.

- settings persistence test for the new phone toggle
- glasses stage-mapping tests for voice states
- glasses stage-mapping tests for camera states
- compile/build verification for both apps

Manual verification should cover:

- sleep mode off: current UI unchanged
- sleep mode on, voice flow: idle -> recording -> send -> analyze -> output
- sleep mode on, camera flow: idle -> capture -> send -> analyze -> output
- output stays visible until tap
- disconnect state stays visually hidden in sleep mode

## Scope Limits

- No attempt to reduce actual CPU wakefulness or change screen power APIs in v1
- No redesign of the normal-mode glasses UI
- No new standalone settings screen
- No replacement of the existing recording or photo pipeline
- No generalized multi-setting sync framework beyond the single new toggle

## Implementation Summary

Build sleep mode as a rendering-layer feature, not a workflow rewrite. The phone stores and syncs one boolean setting, and the glasses map existing workflow signals into a shared five-stage progress model for voice and camera. This keeps the change incremental, low-risk, and aligned with the current architecture.
