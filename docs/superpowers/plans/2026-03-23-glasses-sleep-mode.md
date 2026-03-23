# Glasses Sleep Mode Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a phone-controlled sleep mode for the glasses app that replaces most standby and in-progress text with a shared five-dot progress indicator for both voice and camera workflows, while keeping final output text visible until the user dismisses it.

**Architecture:** Persist a `glassesSleepModeEnabled` flag in the phone app and sync it to the glasses over the existing Bluetooth message channel. On the glasses, derive a shared display stage from current workflow state and render either the existing UI or a sleep-mode variant without rewriting the underlying recording or photo pipelines.

**Tech Stack:** Kotlin, Android SDK, Jetpack Compose, SharedPreferences via `SettingsRepository`, existing Bluetooth SPP `Message` protocol, JUnit

---

## File Structure

- Modify: `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
  Add the persisted sleep-mode setting.
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`
  Load and save the new setting.
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
  Add the sleep-mode toggle near other glasses settings.
- Modify: `phone-app/src/main/res/values/strings.xml`
  Add English UI strings for the new toggle.
- Modify: `phone-app/src/main/res/values-ko/strings.xml`
  Add Korean UI strings for the new toggle.
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`
  Push the setting to the glasses on startup, reconnect, and setting changes.
- Modify: `common/src/main/java/com/example/rokidcommon/protocol/MessageType.kt`
  Add the new protocol message type for sleep-mode sync.
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt`
  Add sleep-mode state, shared stage mapping, and output-dismiss behavior.
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/MainActivity.kt`
  Render the sleep-mode variant and wire tap dismissal behavior.
- Create: `glasses-app/src/main/java/com/example/rokidglasses/ui/SleepModeUi.kt`
  Keep the five-dot indicator and compact rendering helpers isolated.
- Test: `phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt`
  Cover persistence of the new toggle.
- Test: `glasses-app/src/test/java/com/example/rokidglasses/viewmodel/GlassesDisplayStageTest.kt`
  Cover voice and camera stage mapping.

## Chunk 1: Phone Setting Model and UI

### Task 1: Add the persisted phone setting

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
- Modify: `phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write a failing persistence test**

Add a test that saves settings with `glassesSleepModeEnabled = true` and verifies the repository restores the same value.

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.SettingsRepositoryTest"`

Expected: FAIL because the new field is not defined or persisted yet.

- [ ] **Step 3: Add the setting to `ApiSettings`**

Add:
- `glassesSleepModeEnabled: Boolean = false`

Place it alongside other glasses-related recording / display settings.

- [ ] **Step 4: Persist the setting in `SettingsRepository`**

Add:
- new preference key
- load path in `loadSettings()`
- save path in `saveSettings()`

- [ ] **Step 5: Run the test to verify it passes**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.SettingsRepositoryTest"`

Expected: PASS

### Task 2: Add the phone settings toggle

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
- Modify: `phone-app/src/main/res/values/strings.xml`
- Modify: `phone-app/src/main/res/values-ko/strings.xml`

- [ ] **Step 1: Add strings for the new toggle**

Add:
- title string
- subtitle / description string

- [ ] **Step 2: Add the switch row to the existing recording / glasses section**

Place it near:
- `push_chat_to_glasses`
- `push_recording_to_glasses`

Wire it through `onSettingsChange(settings.copy(glassesSleepModeEnabled = it))`.

- [ ] **Step 3: Compile the phone app**

Run: `.\gradlew :phone-app:compileDebugKotlin`

Expected: SUCCESS

## Chunk 2: Protocol and Phone Service Sync

### Task 3: Extend the shared protocol

**Files:**
- Modify: `common/src/main/java/com/example/rokidcommon/protocol/MessageType.kt`

- [ ] **Step 1: Add a new message type for sleep-mode sync**

Add one message type for:
- phone -> glasses sleep mode enabled / disabled

- [ ] **Step 2: Build dependent modules**

Run: `.\gradlew :common:compileDebugKotlin :phone-app:compileDebugKotlin :glasses-app:compileDebugKotlin`

Expected: SUCCESS

### Task 4: Sync the setting from `PhoneAIService`

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`

- [ ] **Step 1: Find the existing reconnect / startup sync points**

Use the current remote-key and glasses-message patterns as references if present in the branch.

- [ ] **Step 2: Add a small helper to send sleep-mode config**

Create a helper that sends the new message type with the current `glassesSleepModeEnabled` value.

- [ ] **Step 3: Call the helper at runtime sync points**

Trigger it:
- on service startup after settings are available
- when the glasses reconnect
- when the setting changes while the phone app is running

- [ ] **Step 4: Compile the phone app**

Run: `.\gradlew :phone-app:compileDebugKotlin`

Expected: SUCCESS

## Chunk 3: Glasses Display Model

### Task 5: Add a display-stage model with tests

**Files:**
- Create: `glasses-app/src/test/java/com/example/rokidglasses/viewmodel/GlassesDisplayStageTest.kt`
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt`

- [ ] **Step 1: Write failing tests for stage mapping**

Cover:
- idle and disconnected -> `IDLE`
- listening -> `CAPTURING_INPUT`
- local send / transfer -> `SENDING`
- transcript-ready / analysis wait -> `ANALYZING`
- ai response / photo result -> `OUTPUT`

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew :glasses-app:testDebugUnitTest --tests "com.example.rokidglasses.viewmodel.GlassesDisplayStageTest"`

Expected: FAIL because the stage model does not exist yet.

- [ ] **Step 3: Add the rendering-stage enum and derived-state helpers**

In `GlassesViewModel`, add:
- sleep-mode enabled flag
- display-stage enum
- helper(s) that derive stage from current state and message events

Keep this focused on rendering state, not pipeline control.

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew :glasses-app:testDebugUnitTest --tests "com.example.rokidglasses.viewmodel.GlassesDisplayStageTest"`

Expected: PASS

### Task 6: Preserve and dismiss output state correctly

**Files:**
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt`

- [ ] **Step 1: Add a dedicated output-dismiss path**

Add a method that:
- clears result text / pagination
- clears transcript-derived output state if needed
- returns the renderer to `IDLE`

- [ ] **Step 2: Update voice result handling**

Make sure:
- `MessageType.AI_RESPONSE_TEXT` enters `OUTPUT`
- `MessageType.USER_TRANSCRIPT` enters `ANALYZING`

- [ ] **Step 3: Update camera result handling**

Make sure:
- capture starts in `CAPTURING_INPUT`
- compress / transfer maps to `SENDING`
- `photo_sent_waiting_ai` maps to `ANALYZING`
- `MessageType.PHOTO_ANALYSIS_RESULT` enters `OUTPUT`

- [ ] **Step 4: Compile the glasses app**

Run: `.\gradlew :glasses-app:compileDebugKotlin`

Expected: SUCCESS

## Chunk 4: Glasses UI Rendering

### Task 7: Isolate the sleep-mode UI helpers

**Files:**
- Create: `glasses-app/src/main/java/com/example/rokidglasses/ui/SleepModeUi.kt`

- [ ] **Step 1: Add a five-dot indicator composable**

Implement a helper that takes:
- current stage
- color / sizing inputs if needed

Render exactly five dots with one active dot at a time.

- [ ] **Step 2: Add a compact display content helper**

Render rules:
- no large text for `IDLE`, `CAPTURING_INPUT`, `SENDING`, `ANALYZING`
- final output text only for `OUTPUT`

- [ ] **Step 3: Compile the glasses app**

Run: `.\gradlew :glasses-app:compileDebugKotlin`

Expected: SUCCESS

### Task 8: Integrate sleep-mode rendering into `MainActivity`

**Files:**
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/MainActivity.kt`

- [ ] **Step 1: Branch rendering between normal mode and sleep mode**

Use the synced flag from `GlassesViewModel` to decide whether to show:
- existing `StatusIndicator` + normal layout
- sleep-mode indicator + compact layout

- [ ] **Step 2: Keep existing output behavior where required**

When in `OUTPUT` during sleep mode:
- show result text
- keep page indicator if paginated
- allow existing paging controls to continue working

- [ ] **Step 3: Route tap dismissal in sleep mode**

If the display is currently in `OUTPUT`, a tap should dismiss the output and return to `IDLE`.

- [ ] **Step 4: Compile the glasses app**

Run: `.\gradlew :glasses-app:compileDebugKotlin`

Expected: SUCCESS

## Chunk 5: End-to-End Verification

### Task 9: Verify both apps build with the new setting and protocol

**Files:**
- No additional file changes expected

- [ ] **Step 1: Build both target modules**

Run: `.\gradlew :phone-app:assembleDebug :glasses-app:assembleDebug`

Expected: SUCCESS

- [ ] **Step 2: Install and run the glasses app on the emulator**

Run:
- `adb -s emulator-5554 install -r .\glasses-app\build\outputs\apk\debug\glasses-app-debug.apk`
- `adb -s emulator-5554 shell am start -W -n com.example.rokidglasses/.MainActivity`

Expected: app launches and stays in foreground.

- [ ] **Step 3: Manual verification for sleep mode off**

Confirm the current UI is unchanged when the toggle is off.

- [ ] **Step 4: Manual verification for voice flow in sleep mode**

Confirm:
- idle dot 1
- recording dot 2
- sending dot 3
- analyzing dot 4
- final output dot 5 with text
- tap to dismiss returns to dot 1

- [ ] **Step 5: Manual verification for camera flow in sleep mode**

Confirm:
- idle dot 1
- capture dot 2
- transfer dot 3
- analysis wait dot 4
- final output dot 5 with text
- tap to dismiss returns to dot 1

- [ ] **Step 6: Manual verification for disconnected state**

Confirm that disconnected sleep mode still renders as `IDLE` without the large connection warning text.

