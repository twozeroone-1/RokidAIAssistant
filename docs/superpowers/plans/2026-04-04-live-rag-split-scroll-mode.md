# Live RAG Split Scroll Mode Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add split-live-only scroll settings so users can choose automatic or manual scrolling for the right-side RAG panel, with adjustable auto-scroll speed.

**Architecture:** Persist the new settings in phone `ApiSettings`/`SettingsRepository`, send them to glasses through `LiveSessionControlPayload`, and route glasses split-panel behavior from synced state. Keep pagination behavior unchanged and scope the new input handling to split live manual mode only.

**Tech Stack:** Kotlin, Jetpack Compose, Android SharedPreferences, Bluetooth SPP message sync.

---

## Chunk 1: Settings Model And Sync

### Task 1: Add persisted settings model

**Files:**
- Create: `common/src/main/java/com/example/rokidcommon/protocol/LiveRagSplitScrollMode.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/data/ApiSettingsTest.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write the failing tests**
- [ ] **Step 2: Run the targeted tests to confirm failure**
- [ ] **Step 3: Add enum, defaults, clamp helpers, and repository persistence**
- [ ] **Step 4: Re-run targeted tests and make them pass**
- [ ] **Step 5: Commit**

### Task 2: Extend live payload

**Files:**
- Modify: `common/src/main/java/com/example/rokidcommon/protocol/LiveSessionControlPayload.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`
- Test: `common/src/test/java/com/example/rokidcommon/protocol/LiveSessionControlPayloadTest.kt`

- [ ] **Step 1: Write failing payload round-trip/fallback tests**
- [ ] **Step 2: Run test to confirm failure**
- [ ] **Step 3: Add new payload fields and sync wiring**
- [ ] **Step 4: Re-run tests and verify pass**
- [ ] **Step 5: Commit**

## Chunk 2: Phone Settings UI

### Task 3: Add split-only settings section

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
- Modify: `phone-app/src/main/res/values/strings.xml`
- Modify: `phone-app/src/main/res/values-ko/strings.xml`

- [ ] **Step 1: Add/adjust strings for scroll mode and speed labels**
- [ ] **Step 2: Add split-mode-only radio/toggle section in settings UI**
- [ ] **Step 3: Add auto-speed slider shown only in AUTO mode**
- [ ] **Step 4: Build `:phone-app:assembleDebug`**
- [ ] **Step 5: Commit**

## Chunk 3: Glasses Behavior

### Task 4: Sync new state into glasses UI model

**Files:**
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt`
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesLivePanelContent.kt`
- Test: `glasses-app/src/test/java/com/example/rokidglasses/viewmodel/GlassesLivePanelContentTest.kt`

- [ ] **Step 1: Write failing tests for AUTO/MANUAL panel flags**
- [ ] **Step 2: Run tests to confirm failure**
- [ ] **Step 3: Add synced state and helper logic**
- [ ] **Step 4: Re-run tests and verify pass**
- [ ] **Step 5: Commit**

### Task 5: Implement manual split scrolling and speed-aware auto scroll

**Files:**
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/MainActivity.kt`
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/viewmodel/GlassesViewModel.kt`

- [ ] **Step 1: Add helper logic so split-manual intercepts `DPAD_UP/DOWN` only when active**
- [ ] **Step 2: Add speed-aware auto-scroll duration mapping for right panel only**
- [ ] **Step 3: Ensure next turn / clear / session reset returns scroll to top**
- [ ] **Step 4: Build `:glasses-app:assembleDebug` and run targeted tests**
- [ ] **Step 5: Commit**

## Chunk 4: Verification

### Task 6: End-to-end verification

**Files:**
- Modify: none

- [ ] **Step 1: Run `:common:testDebugUnitTest` targeted live payload tests**
- [ ] **Step 2: Run `:phone-app:assembleDebug :glasses-app:assembleDebug`**
- [ ] **Step 3: Reinstall debug APKs on phone and glasses**
- [ ] **Step 4: Verify AUTO mode final RAG scroll speed changes with slider**
- [ ] **Step 5: Verify MANUAL mode uses `DPAD_UP/DOWN` for right panel and keeps pagination intact**
