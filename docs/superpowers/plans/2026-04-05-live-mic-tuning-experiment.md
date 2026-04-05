# Live Mic Tuning Experiment Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in experimental live mic tuning toggle and 0/1/2 profile selection for Gemini Live glasses input, with safe fallback to the current MIC path.

**Architecture:** Persist the opt-in settings in phone configuration, sync them to glasses through `LiveSessionControlPayload`, and keep the experiment scoped to `GlassesViewModel.startLiveInputCapture()`. If the experiment path fails, reuse the current `MIC` capture path unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, SharedPreferences, Bluetooth SPP payload sync, Android `AudioRecord`.

---

## Chunk 1: Settings And Payload

### Task 1: Add persisted settings

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write failing tests for new settings defaults and persistence**
- [ ] **Step 2: Run targeted tests to confirm failure**
- [ ] **Step 3: Add settings fields, normalization, and persistence keys**
- [ ] **Step 4: Re-run targeted tests and make them pass**
- [ ] **Step 5: Commit**

### Task 2: Extend live payload

**Files:**
- Modify: `common/src/main/java/com/example/rokidcommon/protocol/LiveSessionControlPayload.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`
- Test: `common/src/test/java/com/example/rokidcommon/protocol/LiveSessionControlPayloadTest.kt`

- [ ] **Step 1: Write failing payload round-trip and fallback tests**
- [ ] **Step 2: Run tests to confirm failure**
- [ ] **Step 3: Add payload fields and phone sync wiring**
- [ ] **Step 4: Re-run tests and verify pass**
- [ ] **Step 5: Commit**

## Chunk 2: Settings UI

### Task 3: Add experimental toggle and profile picker

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
- Modify: `phone-app/src/main/res/values/strings.xml`
- Modify: `phone-app/src/main/res/values-ko/strings.xml`

- [ ] **Step 1: Add user-facing strings for the experiment**
- [ ] **Step 2: Add toggle row in live settings**
- [ ] **Step 3: Add profile selector shown only when the experiment is enabled**
- [ ] **Step 4: Build `:phone-app:assembleDebug`**
- [ ] **Step 5: Commit**

## Chunk 3: Glasses Live Capture

### Task 4: Sync experiment state into glasses live session state

**Files:**
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt`
- Test: `glasses-app/src/test/java/com/example/rokidglasses/viewmodel/GlassesViewModelTest.kt`

- [ ] **Step 1: Write failing tests for payload application and source resolution**
- [ ] **Step 2: Run tests to confirm failure**
- [ ] **Step 3: Add new synced state fields and helper functions**
- [ ] **Step 4: Re-run tests and verify pass**
- [ ] **Step 5: Commit**

### Task 5: Implement scoped live capture experiment with fallback

**Files:**
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt`

- [ ] **Step 1: Introduce a helper that resolves the preferred audio source for live capture**
- [ ] **Step 2: Try the experimental source only for live glasses input**
- [ ] **Step 3: Fallback to the current `MIC` source if initialization fails**
- [ ] **Step 4: Keep cleanup and stop behavior unchanged**
- [ ] **Step 5: Build `:glasses-app:assembleDebug` and commit**

## Chunk 4: Verification

### Task 6: Run focused verification

**Files:**
- Modify: none

- [ ] **Step 1: Run `:common:testDebugUnitTest` for payload tests**
- [ ] **Step 2: Run targeted `:phone-app:testDebugUnitTest` and `:glasses-app:testDebugUnitTest`**
- [ ] **Step 3: Run `:phone-app:assembleDebug :glasses-app:assembleDebug`**
- [ ] **Step 4: Verify the experiment can be disabled without changing current live behavior**
- [ ] **Step 5: Verify profile changes are synced to glasses live state**
