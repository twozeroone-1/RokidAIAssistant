# Glasses Focus Recovery Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore tap and enter-key handling by automatically reclaiming focus when the glasses app loses it to the Rokid launcher.

**Architecture:** Extract a small pure-Kotlin focus recovery policy that decides when a recovery launch is allowed. Wire `MainActivity` to use that policy from window-focus lifecycle events and relaunch itself with a dedicated recovery intent when needed.

**Tech Stack:** Kotlin, Android activity lifecycle, JUnit4, adb real-device verification

---

### Task 1: Add recovery policy tests

**Files:**
- Create: `glasses-app/src/test/java/com/example/rokidglasses/focus/FocusRecoveryPolicyTest.kt`
- Create: `glasses-app/src/main/java/com/example/rokidglasses/focus/FocusRecoveryPolicy.kt`

- [ ] **Step 1: Write the failing test**
- [ ] **Step 2: Run the test to verify it fails**
- [ ] **Step 3: Write the minimal recovery policy**
- [ ] **Step 4: Run the test to verify it passes**

### Task 2: Wire recovery into MainActivity

**Files:**
- Modify: `glasses-app/src/main/java/com/example/rokidglasses/MainActivity.kt`

- [ ] **Step 1: Connect `MainActivity` lifecycle and focus callbacks to the policy**
- [ ] **Step 2: Add a guarded relaunch path using a dedicated recovery extra**
- [ ] **Step 3: Cancel pending recovery when focus returns**
- [ ] **Step 4: Compile the glasses app**

### Task 3: Verify on device

**Files:**
- None

- [ ] **Step 1: Install and launch the updated glasses app**
- [ ] **Step 2: Confirm focus returns to `com.example.rokidglasses/.MainActivity` after loss**
- [ ] **Step 3: Confirm touchpad tap or enter key reaches `MainActivity` again**
