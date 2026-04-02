# New Conversation Toggle Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a toggle that records every AI request in history but forces each request to run as a fresh session when enabled.

**Architecture:** Add one persisted setting and one small session-policy helper package. Existing request entry points keep their current flow and only call the helper to decide whether to create a fresh conversation and clear provider history.

**Tech Stack:** Kotlin, Android, Jetpack Compose, Room, Gradle

---

## Chunk 1: Settings And Shared Policy

### Task 1: Persist the new toggle

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/data/ApiSettingsTest.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt`

- [ ] Write failing tests for the new setting default and persistence.
- [ ] Run the focused tests and confirm they fail for the missing setting.
- [ ] Add `alwaysStartNewAiSession` to settings and repository persistence.
- [ ] Re-run the focused tests and confirm they pass.
- [ ] Commit the settings change.

### Task 2: Add a shared request-session helper

**Files:**
- Create: `phone-app/src/main/java/com/example/rokidphone/service/session/AiRequestSessionSupport.kt`
- Create: `phone-app/src/test/java/com/example/rokidphone/service/session/AiRequestSessionSupportTest.kt`

- [ ] Write failing tests for toggle-gated history clearing and helper decisions.
- [ ] Run the focused helper tests and confirm they fail.
- [ ] Implement the minimal helper.
- [ ] Re-run the focused helper tests and confirm they pass.
- [ ] Commit the helper change.

## Chunk 2: UI Toggle

### Task 3: Expose the toggle in Settings

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
- Modify: `phone-app/src/main/res/values/strings.xml`
- Modify: `phone-app/src/main/res/values-ko/strings.xml`

- [ ] Add a failing UI-oriented assertion if an existing test pattern exists; otherwise validate by build.
- [ ] Add the new switch row and localized labels.
- [ ] Run a focused compile/build check.
- [ ] Commit the UI change.

## Chunk 3: Request Entry Points

### Task 4: Apply the policy to chat requests

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/viewmodel/ConversationViewModel.kt`

- [ ] Write the failing test if a suitable test harness exists; otherwise add helper-level coverage and verify by targeted behavior checks.
- [ ] Make chat sends create a fresh conversation and clear service history when the toggle is on.
- [ ] Verify the relevant tests/build still pass.
- [ ] Commit the chat integration.

### Task 5: Apply the policy to phone-service requests

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`

- [ ] Add failing coverage for request-level session reset where practical.
- [ ] Update voice, photo, and recording paths to use fresh conversations and cleared history when enabled.
- [ ] Verify focused tests/build.
- [ ] Commit the phone-service integration.

### Task 6: Apply the policy to repository/service utility paths

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/EnhancedAIService.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/AiRepository.kt`

- [ ] Add failing coverage for utility-path session behavior where practical.
- [ ] Update the utility paths to honor the toggle.
- [ ] Verify focused tests/build.
- [ ] Commit the utility-path integration.

## Chunk 4: Final Verification

### Task 7: Run focused verification

**Files:**
- No code changes expected

- [ ] Run targeted `phone-app` unit tests for settings and session helpers.
- [ ] Run `:phone-app:assembleDebug`.
- [ ] Review the diff for unrelated changes.
- [ ] Summarize behavior and remaining risks.
