# Glasses Response Font Scale Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a phone-side slider setting that controls glasses AI response body font size and applies immediately to connected glasses.

**Architecture:** Persist `responseFontScalePercent` in the phone app settings, send it to glasses through a small shared protocol message, and apply it only to glasses response-body rendering and pagination. The phone remains the source of truth, and glasses update immediately when a synced value arrives.

**Tech Stack:** Kotlin, SharedPreferences, Jetpack Compose, StateFlow, existing phone↔glasses message protocol in `common`, `PhoneAIService`, `GlassesViewModel`.

---

## File Map

- Modify: `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
  - Add `responseFontScalePercent`
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`
  - Persist and clamp the value
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
  - Add slider UI
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`
  - Send initial and changed values to glasses
- Modify: `common/src/main/java/com/example/rokidcommon/protocol/MessageType.kt`
  - Add a message type for font scale sync
- Modify: `common` protocol helper(s) used to build and parse messages
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt`
  - Receive sync message, store scale in state, repaginate
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/MainActivity.kt`
  - Apply scale to AI response body text rendering
- Test: `phone-app/src/test/java/com/example/rokidphone/data/ApiSettingsTest.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/service/PhoneAIServiceTest.kt`
- Test: `common/src/test/java/com/example/rokidcommon/protocol/MessageTypeTest.kt`
- Test: `glasses-app/src/test/java/com/example/rokidglasses/viewmodel/GlassesResponseFontScaleTest.kt`

## Chunk 1: Phone Setting Model

### Task 1: Add and persist `responseFontScalePercent`

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/data/ApiSettingsTest.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write the failing tests**

Cover:
- default value is `100`
- values are clamped to `80..140`
- save/load persists non-default value

- [ ] **Step 2: Run tests to verify they fail**

Run:
`.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.ApiSettingsTest" --tests "com.example.rokidphone.data.SettingsRepositoryTest"`

Expected: FAIL because the field and persistence do not exist yet

- [ ] **Step 3: Write minimal implementation**

Add:
- `responseFontScalePercent: Int = 100`
- clamp helper in the repository
- persistence key and save/load wiring

- [ ] **Step 4: Run tests to verify they pass**

Run:
`.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.ApiSettingsTest" --tests "com.example.rokidphone.data.SettingsRepositoryTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt phone-app/src/test/java/com/example/rokidphone/data/ApiSettingsTest.kt phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt
git commit -m "feat: persist glasses response font scale"
```

## Chunk 2: Phone Settings UI

### Task 2: Add slider to phone settings

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`

- [ ] **Step 1: Add slider UI**

Add a labeled slider section such as:
- label: `안경 응답 글자 크기`
- range: `80..140`
- default marker or reset affordance to `100%`

- [ ] **Step 2: Wire slider to settings state**

Update `onSettingsChange(settings.copy(responseFontScalePercent = ...))`

- [ ] **Step 3: Build verify**

Run:
`.\gradlew.bat :phone-app:compileDebugKotlin`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt
git commit -m "feat: add glasses response font scale slider"
```

## Chunk 3: Shared Protocol

### Task 3: Add a phone→glasses font scale sync message

**Files:**
- Modify: `common/src/main/java/com/example/rokidcommon/protocol/MessageType.kt`
- Modify: other protocol builder/parser helpers in `common`
- Test: `common/src/test/java/com/example/rokidcommon/protocol/MessageTypeTest.kt`

- [ ] **Step 1: Write the failing protocol test**

Cover:
- new message type exists
- payload round-trips correctly

- [ ] **Step 2: Run test to verify it fails**

Run:
`.\gradlew.bat :common:testDebugUnitTest --tests "com.example.rokidcommon.protocol.MessageTypeTest"`

Expected: FAIL because the new message type is missing

- [ ] **Step 3: Add the minimal protocol implementation**

Add:
- new message type, e.g. `SET_RESPONSE_FONT_SCALE`
- helper to build the message with one integer payload

- [ ] **Step 4: Run test to verify it passes**

Run:
`.\gradlew.bat :common:testDebugUnitTest --tests "com.example.rokidcommon.protocol.MessageTypeTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add common/src/main/java/com/example/rokidcommon/protocol/MessageType.kt common/src/test/java/com/example/rokidcommon/protocol/MessageTypeTest.kt
git commit -m "feat: add glasses font scale sync message"
```

## Chunk 4: Phone Runtime Sync

### Task 4: Push current and changed values from phone to glasses

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/service/PhoneAIServiceTest.kt`

- [ ] **Step 1: Write the failing test**

Cover:
- current setting is sent when glasses connect
- changed setting is resent when the slider value changes

- [ ] **Step 2: Run test to verify it fails**

Run:
`.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.PhoneAIServiceTest"`

Expected: FAIL because sync behavior is missing

- [ ] **Step 3: Write minimal implementation**

Implement:
- one narrow observer for `responseFontScalePercent`
- send only when value changes
- resend current value on glasses connection/setup path

- [ ] **Step 4: Run test to verify it passes**

Run:
`.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.PhoneAIServiceTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt phone-app/src/test/java/com/example/rokidphone/service/PhoneAIServiceTest.kt
git commit -m "feat: sync glasses response font scale from phone"
```

## Chunk 5: Glasses Runtime Application

### Task 5: Receive and apply synced font scale on glasses

**Files:**
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt`
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/MainActivity.kt`
- Test: `glasses-app/src/test/java/com/example/rokidglasses/viewmodel/GlassesResponseFontScaleTest.kt`

- [ ] **Step 1: Write the failing glasses test**

Cover:
- incoming font scale message updates `uiState`
- larger scale increases pages for long text
- smaller scale reduces pages for the same text

- [ ] **Step 2: Run test to verify it fails**

Run:
`.\gradlew.bat :glasses-app:testDebugUnitTest --tests "com.example.rokidglasses.viewmodel.GlassesResponseFontScaleTest"`

Expected: FAIL because scale state and sync handling are missing

- [ ] **Step 3: Add scale-aware state and repagination**

In `GlassesViewModel`:
- add `responseFontScalePercent` to `GlassesUiState`
- handle incoming sync message
- rerun pagination when the value changes and a response is visible
- use scale-aware char budget for pagination

- [ ] **Step 4: Apply scale to response rendering**

In `MainActivity`:
- scale only the AI response body font size and line height
- keep hint text and page indicator unchanged

- [ ] **Step 5: Run tests to verify they pass**

Run:
`.\gradlew.bat :glasses-app:testDebugUnitTest --tests "com.example.rokidglasses.viewmodel.GlassesResponseFontScaleTest" :glasses-app:compileDebugKotlin`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt glasses-app/src/main/java/com/example/rokidglasses/MainActivity.kt glasses-app/src/test/java/com/example/rokidglasses/viewmodel/GlassesResponseFontScaleTest.kt
git commit -m "feat: apply synced glasses response font scale"
```

## Chunk 6: Device Verification

### Task 6: Verify immediate sync on real devices

**Files:**
- No code changes unless issues are found

- [ ] **Step 1: Build and install both apps**

Run:
```bash
.\gradlew.bat :phone-app:assembleDebug :glasses-app:assembleDebug
adb -s ce0918298aba802904 install -r phone-app\\build\\outputs\\apk\\debug\\phone-app-debug.apk
adb -s 1901092553806330 install -r glasses-app\\build\\outputs\\apk\\debug\\glasses-app-debug.apk
```

- [ ] **Step 2: Verify live sync**

Manual checks:
- open phone settings
- move slider while glasses are connected
- confirm current visible AI response text changes immediately

- [ ] **Step 3: Verify pagination**

Manual checks:
- use a long AI response
- compare page count at `80%`, `100%`, and `140%`

- [ ] **Step 4: Verify reconnect behavior**

Manual checks:
- disconnect/reconnect glasses
- confirm last slider value is reapplied without re-entering settings

- [ ] **Step 5: Final commit if verification fixes are needed**

```bash
git add <fixed-files>
git commit -m "fix: polish synced glasses font scale behavior"
```
