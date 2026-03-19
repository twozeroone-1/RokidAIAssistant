# Docs Strict Fallback Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Docs mode fast by returning docs answers immediately when they are good enough, and perform a single General AI fallback only on clear docs misses.

**Architecture:** Add a shared docs fallback evaluator in the RAG layer, then wire it into the text and voice entry points that already call `AnythingLlmRagService`. Reuse existing route badge metadata so the UI can show a small `Docs -> General` marker without new screen-specific state.

**Tech Stack:** Kotlin, Android ViewModel/Service classes, existing RAG service layer, JUnit/Robolectric tests

---

## Chunk 1: Shared Fallback Policy

### Task 1: Add evaluator tests

**Files:**
- Create: `phone-app/src/test/java/com/example/rokidphone/service/rag/DocsFallbackEvaluatorTest.kt`
- Modify: `phone-app/src/test/java/com/example/rokidphone/service/rag/RouteResolverTest.kt`

- [ ] **Step 1: Write the failing evaluator tests**

Add tests for:
- docs answer with sources does not fallback
- docs answer with zero sources and explicit miss phrase does fallback
- docs answer with zero sources and a substantive answer does not fallback
- docs failure maps to fallback with a reason

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.rag.DocsFallbackEvaluatorTest" --tests "com.example.rokidphone.service.rag.RouteResolverTest"`

Expected: missing evaluator symbols or assertion failures for the new badge label

- [ ] **Step 3: Implement the evaluator**

**Files:**
- Create: `phone-app/src/main/java/com/example/rokidphone/service/rag/DocsFallbackEvaluator.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/rag/RouteResolver.kt`

Add:
- strict miss heuristics
- reason strings for metadata
- explicit `Docs -> General` badge label

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.rag.DocsFallbackEvaluatorTest" --tests "com.example.rokidphone.service.rag.RouteResolverTest"`

Expected: PASS

## Chunk 2: Text and Voice Integration

### Task 2: Add failing integration-adjacent tests where practical

**Files:**
- Modify: `phone-app/src/test/java/com/example/rokidphone/service/rag/AnythingLlmRagServiceTest.kt`
- Modify: `phone-app/src/test/java/com/example/rokidphone/data/db/ConversationMetadataTest.kt`

- [ ] **Step 1: Extend tests for fallback-facing metadata expectations**

Cover:
- fallback route metadata still round-trips
- badge label expectation is explicit enough for user visibility

- [ ] **Step 2: Run tests to verify they fail or expose missing behavior**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.db.ConversationMetadataTest" --tests "com.example.rokidphone.service.rag.AnythingLlmRagServiceTest"`

- [ ] **Step 3: Implement wiring**

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/viewmodel/ConversationViewModel.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/EnhancedAIService.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`

Apply the policy:
- docs first
- single fallback on strict miss
- preserve fallback route metadata and reason

- [ ] **Step 4: Run targeted tests**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.db.ConversationMetadataTest" --tests "com.example.rokidphone.service.rag.AnythingLlmRagServiceTest" --tests "com.example.rokidphone.service.rag.DocsFallbackEvaluatorTest" --tests "com.example.rokidphone.service.rag.RouteResolverTest"`

Expected: PASS

## Chunk 3: Verification

### Task 3: Verify no regression in touched paths

**Files:**
- Verify touched Kotlin files and tests above

- [ ] **Step 1: Run the focused unit suite**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.rag.*" --tests "com.example.rokidphone.data.db.ConversationMetadataTest"`

- [ ] **Step 2: Inspect failures and fix minimally if needed**

- [ ] **Step 3: Re-run the same focused suite**

Expected: PASS with fresh output
