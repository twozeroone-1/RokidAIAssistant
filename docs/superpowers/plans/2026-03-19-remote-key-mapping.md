# Remote Key Mapping Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add user-configurable remote key mappings in the phone settings screen so the glasses can learn custom keys for recording and camera actions while preserving existing hardcoded fallback behavior.

**Architecture:** Persist remote key settings in the phone app `ApiSettings` and sync learning/config state to the glasses through the existing Bluetooth SPP `Message` protocol. On the glasses, add a focused resolver that checks custom mappings first and falls back to existing hardcoded key behavior when no custom mapping applies.

**Tech Stack:** Kotlin, Android SDK, Jetpack Compose, SharedPreferences via `SettingsRepository`, existing Bluetooth SPP message protocol, JUnit

---

## File Structure

- Modify: `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
  Adds persisted remote key fields, learning target enum, and validation helpers.
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`
  Loads and saves remote key settings and learning status.
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
  Adds the main settings UI section for learned keys, learn actions, and reset.
- Create: `phone-app/src/main/java/com/example/rokidphone/input/RemoteKeySupport.kt`
  Formats key labels and blocks unsafe key codes.
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ServiceBridge.kt`
  Exposes flows for learn-record, learn-camera, cancel-learning, and clear-mappings requests.
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`
  Sends learning/config messages to glasses and handles learned-key responses.
- Modify: `common/src/main/java/com/example/rokidcommon/protocol/MessageType.kt`
  Adds new protocol message types for remote key learning and config sync.
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt`
  Stores synced remote settings, handles learning mode, and resolves custom key actions.
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/MainActivity.kt`
  Routes physical keys through the custom resolver before legacy handling.
- Create: `glasses-app/src/main/java/com/example/rokidglasses/input/RemoteKeyActionResolver.kt`
  Small mapping layer for glasses-side custom key behavior.
- Test: `phone-app/src/test/java/com/example/rokidphone/data/ApiSettingsTest.kt`
  Adds validation coverage for duplicate remote keys.
- Test: `phone-app/src/test/java/com/example/rokidphone/input/RemoteKeySupportTest.kt`
  Covers blocked key detection and key label formatting.
- Test: `phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt`
  Covers persistence of remote key settings.
- Test: `glasses-app/src/test/java/com/example/rokidglasses/input/RemoteKeyActionResolverTest.kt`
  Covers custom-key priority and fallback behavior.

## Chunk 1: Phone Model and Utility Layer

### Task 1: Extend `ApiSettings` with remote key settings

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
- Modify: `phone-app/src/test/java/com/example/rokidphone/data/ApiSettingsTest.kt`

- [ ] **Step 1: Write a failing test for duplicate remote key validation**

Add a test that creates settings with the same `remoteRecordKeyCode` and `remoteCameraKeyCode` and expects a validation error.

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.ApiSettingsTest"`

Expected: FAIL because the new validation behavior does not exist yet.

- [ ] **Step 3: Add remote key fields and validation helper to `ApiSettings`**

Add:
- `remoteRecordKeyCode: Int? = null`
- `remoteCameraKeyCode: Int? = null`
- `remoteKeyLearningTarget: RemoteKeyLearningTarget? = null`
- `remoteKeyLearningStatusMessage: String = ""`

Add a helper that rejects duplicate record/camera mappings.

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.ApiSettingsTest"`

Expected: PASS

## Task 2: Add key formatting and blocked-key helpers

**Files:**
- Create: `phone-app/src/main/java/com/example/rokidphone/input/RemoteKeySupport.kt`
- Create: `phone-app/src/test/java/com/example/rokidphone/input/RemoteKeySupportTest.kt`

- [ ] **Step 1: Write failing tests for blocked keys and key label formatting**

Add tests for:
- `null` -> `"Not set"`
- a known key code formats into a readable label
- blocked keys such as `KEYCODE_BACK` are rejected

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.input.RemoteKeySupportTest"`

Expected: FAIL because the helper file does not exist yet.

- [ ] **Step 3: Implement `RemoteKeySupport.kt`**

Add:
- blocked key set
- `isBlockedRemoteKeyCode(keyCode: Int): Boolean`
- `formatRemoteKeyCode(keyCode: Int?): String`

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.input.RemoteKeySupportTest"`

Expected: PASS

## Chunk 2: Phone Persistence and UI

### Task 3: Persist remote key settings in `SettingsRepository`

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`
- Modify: `phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write a failing persistence test for remote key settings**

Cover saving and reloading:
- record key code
- camera key code
- learning target
- learning status message

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.SettingsRepositoryTest"`

Expected: FAIL because the keys are not persisted yet.

- [ ] **Step 3: Add repository load/save support**

Add preference keys and wire them through `loadSettings()` and `saveSettings()`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.SettingsRepositoryTest"`

Expected: PASS

### Task 4: Add the main settings UI section

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
- Modify: `phone-app/src/main/res/values/strings.xml`
- Modify: `phone-app/src/main/res/values-ko/strings.xml`

- [ ] **Step 1: Add strings for the remote key mapping section**

Add labels for:
- section title
- learned record key
- learned camera key
- learn record key
- learn camera key
- clear mappings
- waiting / success / error text

- [ ] **Step 2: Add a settings section with action rows**

Show:
- current record key
- current camera key
- learn record button/row
- learn camera button/row
- clear mappings row
- current learning status text

- [ ] **Step 3: Wire rows to callbacks instead of direct repository writes**

The UI should request actions from the service bridge and update settings through `onSettingsChange`.

- [ ] **Step 4: Build the phone app to verify the UI compiles**

Run: `.\gradlew :phone-app:compileDebugKotlin`

Expected: SUCCESS

## Chunk 3: Protocol and Service Bridge

### Task 5: Extend the shared message protocol

**Files:**
- Modify: `common/src/main/java/com/example/rokidcommon/protocol/MessageType.kt`

- [ ] **Step 1: Add new message types for remote key learning and config sync**

Add codes for:
- remote config sync
- learn record key
- learn camera key
- cancel learning
- learned key result

- [ ] **Step 2: Build common-dependent modules to verify enum changes compile**

Run: `.\gradlew :common:compileDebugKotlin :phone-app:compileDebugKotlin :glasses-app:compileDebugKotlin`

Expected: SUCCESS

### Task 6: Add service bridge flows for remote key operations

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ServiceBridge.kt`

- [ ] **Step 1: Add flows for**

- start learn record key
- start learn camera key
- cancel learning
- clear mappings

- [ ] **Step 2: Add helper methods to emit those requests**

- [ ] **Step 3: Compile phone app to verify bridge changes**

Run: `.\gradlew :phone-app:compileDebugKotlin`

Expected: SUCCESS

### Task 7: Handle sync and learning in `PhoneAIService`

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`

- [ ] **Step 1: Subscribe to new `ServiceBridge` flows**

On learn actions:
- update repository learning state
- send learning command to glasses

On clear action:
- reset saved mappings
- send remote config sync to glasses

- [ ] **Step 2: Handle learned-key result messages from glasses**

When a learned key arrives:
- reject blocked keys
- reject duplicates
- save valid mapping
- clear learning target
- update status message
- push refreshed config to glasses

- [ ] **Step 3: Send remote config to glasses on connection or service startup**

This ensures the glasses receive saved mappings after reconnect.

- [ ] **Step 4: Compile phone app to verify service changes**

Run: `.\gradlew :phone-app:compileDebugKotlin`

Expected: SUCCESS

## Chunk 4: Glasses Resolver and Learning Flow

### Task 8: Add glasses-side key resolver with tests

**Files:**
- Create: `glasses-app/src/main/java/com/example/rokidglasses/input/RemoteKeyActionResolver.kt`
- Create: `glasses-app/src/test/java/com/example/rokidglasses/input/RemoteKeyActionResolverTest.kt`

- [ ] **Step 1: Write failing resolver tests**

Cover:
- custom record key -> record action
- custom camera key -> camera action
- unmatched key -> no custom action

- [ ] **Step 2: Run the test to verify it fails**

Run: `.\gradlew :glasses-app:testDebugUnitTest --tests "com.example.rokidglasses.input.RemoteKeyActionResolverTest"`

Expected: FAIL because the resolver does not exist yet.

- [ ] **Step 3: Implement the resolver**

Add a compact data model and mapping function that only resolves custom actions.

- [ ] **Step 4: Run the test to verify it passes**

Run: `.\gradlew :glasses-app:testDebugUnitTest --tests "com.example.rokidglasses.input.RemoteKeyActionResolverTest"`

Expected: PASS

### Task 9: Wire learning and synced settings into `GlassesViewModel`

**Files:**
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt`

- [ ] **Step 1: Add state for synced remote key config and learning target**

- [ ] **Step 2: Handle new incoming phone messages**

Process:
- config sync
- learn record key
- learn camera key
- cancel learning

- [ ] **Step 3: Add an outgoing learned-key result message**

When learning mode is active and a valid physical key is pressed, send the key code back to the phone and exit learning mode.

- [ ] **Step 4: Compile glasses app to verify changes**

Run: `.\gradlew :glasses-app:compileDebugKotlin`

Expected: SUCCESS

### Task 10: Update `MainActivity` to prefer custom mappings over legacy keys

**Files:**
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/MainActivity.kt`

- [ ] **Step 1: Route `onKeyDown` and `onKeyUp` through the new resolver**

Custom actions should run first. Only fall back to the current hardcoded `when` block if no custom mapping applies.

- [ ] **Step 2: Ensure learning mode captures the next key before legacy handling**

This avoids accidentally triggering recording or photo while learning.

- [ ] **Step 3: Compile glasses app to verify activity changes**

Run: `.\gradlew :glasses-app:compileDebugKotlin`

Expected: SUCCESS

## Chunk 5: Verification

### Task 11: Run focused verification

**Files:**
- No code changes required

- [ ] **Step 1: Run phone unit tests**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.ApiSettingsTest" --tests "com.example.rokidphone.input.RemoteKeySupportTest" --tests "com.example.rokidphone.data.SettingsRepositoryTest"`

Expected: PASS

- [ ] **Step 2: Run glasses unit tests**

Run: `.\gradlew :glasses-app:testDebugUnitTest --tests "com.example.rokidglasses.input.RemoteKeyActionResolverTest"`

Expected: PASS

- [ ] **Step 3: Run module compile checks**

Run: `.\gradlew :common:compileDebugKotlin :phone-app:compileDebugKotlin :glasses-app:compileDebugKotlin`

Expected: SUCCESS

- [ ] **Step 4: Document any gaps**

If no hardware verification is run in this session, explicitly note that learning over a real remote still needs device validation.
