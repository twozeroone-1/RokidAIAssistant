# Live Session Auto Resume Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically resume Gemini Live sessions across the 10-minute connection lifetime and `1008 / The operation was aborted.` close path without depending on the existing Long session toggle.

**Architecture:** Keep the existing resumption-handle and API key rotation design, but split “resumable connection lifetime close” from “quota/invalid key” failures. Request `sessionResumption` for all sessions, keep `contextWindowCompression` behind the existing Long session toggle, and recover using the saved handle on `goAway` and on terminal `1008 abort` while preserving current key rotation behavior for quota and invalid credentials.

**Tech Stack:** Kotlin, coroutines, StateFlow/SharedFlow, OkHttp WebSocket, JUnit.

---

## Chunk 1: Failure Classification

### Task 1: Add resumable-close classification tests

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ai/LiveSessionFailurePolicy.kt`
- Create or Modify: `phone-app/src/test/java/com/example/rokidphone/service/ai/LiveSessionFailurePolicyTest.kt`

- [ ] **Step 1: Write a failing test showing `The operation was aborted.` is not classified as `QUOTA` or `INVALID_KEY`**
- [ ] **Step 2: Run the targeted test and confirm failure**
- [ ] **Step 3: Add a helper for resumable connection-lifetime close reasons**
- [ ] **Step 4: Re-run the targeted test and make it pass**
- [ ] **Step 5: Commit**

## Chunk 2: Resume Behavior

### Task 2: Cover resume behavior when long-session toggle is off

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ai/LiveSessionCoordinator.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/service/ai/LiveSessionCoordinatorTest.kt`

- [ ] **Step 1: Write a failing test showing a saved handle resumes on `goAway` even when `liveLongSessionEnabled` is `false`**
- [ ] **Step 2: Run the targeted test and confirm failure**
- [ ] **Step 3: Relax the long-session gate for handle-based recovery**
- [ ] **Step 4: Re-run the targeted test and make it pass**
- [ ] **Step 5: Commit**

### Task 3: Recover on terminal `1008 abort`

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ai/LiveSessionCoordinator.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/service/ai/LiveSessionCoordinatorTest.kt`

- [ ] **Step 1: Write a failing test showing `The operation was aborted.` with a saved handle triggers resume instead of terminal error**
- [ ] **Step 2: Run the targeted test and confirm failure**
- [ ] **Step 3: Wire resumable-close detection into `onSessionFailure()`**
- [ ] **Step 4: Re-run the targeted test and make it pass**
- [ ] **Step 5: Commit**

### Task 4: Preserve key-rotation behavior

**Files:**
- Modify: `phone-app/src/test/java/com/example/rokidphone/service/ai/LiveSessionCoordinatorTest.kt`

- [ ] **Step 1: Write or extend a test proving quota failures still rotate keys instead of using resumption**
- [ ] **Step 2: Run the targeted test and confirm the current behavior**
- [ ] **Step 3: Adjust implementation only if the new resume logic regressed quota handling**
- [ ] **Step 4: Re-run the targeted test and verify pass**
- [ ] **Step 5: Commit**

## Chunk 3: Verification

### Task 5: Run focused verification

**Files:**
- Modify: none

- [ ] **Step 1: Run `:phone-app:testDebugUnitTest --tests \"com.example.rokidphone.service.ai.LiveSessionFailurePolicyTest\"`**
- [ ] **Step 2: Run `:phone-app:testDebugUnitTest --tests \"com.example.rokidphone.service.ai.LiveSessionCoordinatorTest\"`**
- [ ] **Step 3: Run `:phone-app:assembleDebug`**
- [ ] **Step 4: Review logs to confirm `goAway`/`1008 abort` now produce reconnect attempts**
- [ ] **Step 5: Commit**
