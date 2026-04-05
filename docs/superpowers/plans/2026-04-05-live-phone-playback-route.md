# Live Phone Playback Route Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a phone-only live playback sub-option that can force phone speaker output while falling back safely to the system route.

**Architecture:** Keep the existing `liveOutputTarget` pipeline selection, add a separate `phonePlaybackRoute` setting for the `PHONE` path only, and apply the route inside `LiveAudioManager` as best-effort communication-device routing. Ignore the route for `AUTO`, `GLASSES`, and `BOTH`.

**Tech Stack:** Kotlin, Android AudioManager, Compose, Robolectric, JUnit

---

### Task 1: Lock in settings and coordinator behavior with failing tests

**Files:**
- Modify: `phone-app/src/test/java/com/example/rokidphone/data/ApiSettingsTest.kt`
- Modify: `phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt`
- Modify: `phone-app/src/test/java/com/example/rokidphone/service/ai/LiveSessionCoordinatorTest.kt`

- [ ] **Step 1: Add a failing default-value test for `phonePlaybackRoute`**
- [ ] **Step 2: Add a failing persistence test for `phonePlaybackRoute`**
- [ ] **Step 3: Add a failing coordinator test proving only `PHONE` forwards the route**
- [ ] **Step 4: Run the targeted tests and verify they fail for the expected missing setting/config fields**

### Task 2: Add settings model and persistence

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/DocsAssistantModels.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`

- [ ] **Step 1: Add `PhonePlaybackRoute` enum**
- [ ] **Step 2: Add `phonePlaybackRoute` to `ApiSettings` with default `SYSTEM_DEFAULT`**
- [ ] **Step 3: Add SharedPreferences load/save support**
- [ ] **Step 4: Keep unset fallback values stable for existing installs**

### Task 3: Expose the option in settings UI

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayout.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
- Modify: `phone-app/src/main/res/values/strings.xml`
- Modify: `phone-app/src/main/res/values-ko/strings.xml`

- [ ] **Step 1: Add a new realtime settings item key for the phone playback route row**
- [ ] **Step 2: Show the row only when `liveOutputTarget == PHONE`**
- [ ] **Step 3: Add the selection dialog with `SYSTEM_DEFAULT` and `PHONE_SPEAKER`**
- [ ] **Step 4: Update labels so output target and route semantics stay clear**

### Task 4: Propagate and apply the route

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ai/LiveSessionCoordinator.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ai/GeminiLiveSession.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ai/LiveAudioManager.kt`

- [ ] **Step 1: Add `phonePlaybackRoute` to live session config models**
- [ ] **Step 2: Normalize the route so only `PHONE` passes through**
- [ ] **Step 3: Pass the route into `LiveAudioManager`**
- [ ] **Step 4: Apply best-effort speaker forcing with automatic fallback**
- [ ] **Step 5: Clear forced routing on playback stop/release**

### Task 5: Verify and package

**Files:**
- Verify: `phone-app/src/test/java/com/example/rokidphone/data/ApiSettingsTest.kt`
- Verify: `phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt`
- Verify: `phone-app/src/test/java/com/example/rokidphone/service/ai/LiveSessionCoordinatorTest.kt`

- [ ] **Step 1: Run the targeted phone tests**
- [ ] **Step 2: Build `:phone-app:assembleDebug`**
- [ ] **Step 3: Install the updated debug APK on the connected S23 if verification passes**
