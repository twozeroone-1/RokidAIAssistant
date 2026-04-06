# Docs RAG Fresh Session Default Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make AnythingLLM-backed docs requests use a fresh server-side session by default so previous RAG turns cannot contaminate later answers.

**Architecture:** Keep the existing `workspace/{slug}/chat` integration, extend the request DTO with optional `sessionId`, and let `AnythingLlmRagService` generate a new UUID per request when docs settings require isolation. This keeps the change inside the shared RAG layer so text chat, voice docs, photo-context docs, and live RAG all inherit the behavior without separate flow-specific logic.

**Tech Stack:** Kotlin, Retrofit, Android service/viewmodel code, JUnit/Robolectric, MockWebServer

---

## Chunk 1: Shared Docs Session Policy

### Task 1: Lock down the docs-session setting behavior

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/DocsAssistantModels.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/data/DocsAssistantSettingsTest.kt`

- [ ] **Step 1: Write the failing settings tests**

Add tests that verify:
- `AnythingLlmSettings.alwaysNewSession` defaults to `true`
- `ApiSettings.toAnythingLlmSettings()` produces `alwaysNewSession == true` for normal docs usage
- a copied `AnythingLlmSettings(alwaysNewSession = false)` preserves the opt-out value for future internal use

- [ ] **Step 2: Run the focused tests and confirm they fail for the missing conversion assertion**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.DocsAssistantSettingsTest"`

Expected: FAIL because the conversion does not currently assert or wire the docs session flag explicitly

- [ ] **Step 3: Implement the minimal settings wiring**

Update `toAnythingLlmSettings()` so the docs-side session policy is explicit:

```kotlin
return AnythingLlmSettings(
    serverUrl = normalizeAnythingLlmServerUrl(anythingLlmServerUrl),
    apiKey = normalizeAnythingLlmApiKey(anythingLlmApiKey),
    workspaceSlug = normalizeAnythingLlmWorkspaceSlug(anythingLlmWorkspaceSlug),
    runtimeEnabled = anythingLlmRuntimeEnabled,
    queryMode = anythingLlmQueryMode,
    alwaysNewSession = true,
    lastHealthStatus = anythingLlmLastHealthStatus,
    lastHealthMessage = anythingLlmLastHealthMessage,
    recentFailureCount = anythingLlmRecentFailureCount,
)
```

- [ ] **Step 4: Re-run the focused tests**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.DocsAssistantSettingsTest"`

Expected: PASS

- [ ] **Step 5: Commit**

Run:

```bash
git add phone-app/src/main/java/com/example/rokidphone/data/DocsAssistantModels.kt phone-app/src/test/java/com/example/rokidphone/data/DocsAssistantSettingsTest.kt
git commit -m "fix: default docs rag to fresh anythingllm sessions"
```

## Chunk 2: AnythingLLM Request Contract

### Task 2: Extend the request DTO and API contract

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/rag/network/AnythingLlmDtos.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/rag/network/AnythingLlmApi.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/service/rag/AnythingLlmRagServiceTest.kt`

- [ ] **Step 1: Write the failing API-shape tests**

Add tests that inspect the outgoing request body through `MockWebServer`:
- one docs request includes `message`, `mode`, and non-empty `sessionId`
- two consecutive docs requests produce different `sessionId` values
- opt-out mode omits `sessionId`

- [ ] **Step 2: Run the focused tests and confirm they fail**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.rag.AnythingLlmRagServiceTest"`

Expected: FAIL because the request body currently has no `sessionId`

- [ ] **Step 3: Extend the DTOs and request model**

Update the request model to carry the optional field:

```kotlin
data class AnythingLlmChatRequest(
    @SerializedName("message")
    val message: String,
    @SerializedName("mode")
    val mode: String = "query",
    @SerializedName("sessionId")
    val sessionId: String? = null,
)
```

Keep the Retrofit endpoint path unchanged.

- [ ] **Step 4: Re-run the focused tests**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.rag.AnythingLlmRagServiceTest"`

Expected: still FAIL until the service generates the new session id

- [ ] **Step 5: Commit**

Run:

```bash
git add phone-app/src/main/java/com/example/rokidphone/service/rag/network/AnythingLlmDtos.kt phone-app/src/main/java/com/example/rokidphone/service/rag/network/AnythingLlmApi.kt phone-app/src/test/java/com/example/rokidphone/service/rag/AnythingLlmRagServiceTest.kt
git commit -m "fix: add docs rag session id to anythingllm requests"
```

## Chunk 3: Shared RAG Service Behavior

### Task 3: Generate a fresh session id per docs request

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/rag/AnythingLlmRagService.kt`
- Modify: `phone-app/src/test/java/com/example/rokidphone/service/rag/AnythingLlmRagServiceTest.kt`

- [ ] **Step 1: Add the final failing assertions**

Ensure tests cover:
- `sessionId` generation happens inside `answer()`
- generated ids differ across consecutive calls
- response parsing remains unchanged after adding the new field

- [ ] **Step 2: Run tests and confirm the service is still missing the behavior**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.rag.AnythingLlmRagServiceTest"`

Expected: FAIL with missing `sessionId` assertions

- [ ] **Step 3: Implement the minimal service change**

Generate one UUID per request when the docs policy is enabled:

```kotlin
val requestSessionId = if (settings.alwaysNewSession) UUID.randomUUID().toString() else null

val response = api.chat(
    slug = settings.workspaceSlug,
    request = AnythingLlmChatRequest(
        message = normalizedQuestion,
        mode = settings.queryMode.wireValue,
        sessionId = requestSessionId,
    ),
)
```

Do not add extra request-state caching.

- [ ] **Step 4: Re-run the focused tests**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.rag.AnythingLlmRagServiceTest"`

Expected: PASS

- [ ] **Step 5: Commit**

Run:

```bash
git add phone-app/src/main/java/com/example/rokidphone/service/rag/AnythingLlmRagService.kt phone-app/src/test/java/com/example/rokidphone/service/rag/AnythingLlmRagServiceTest.kt
git commit -m "fix: isolate docs rag requests with fresh anythingllm sessions"
```

## Chunk 4: Representative Integration Coverage

### Task 4: Verify one higher-level caller still works unchanged

**Files:**
- Modify: `phone-app/src/test/java/com/example/rokidphone/service/ai/LiveRagToolAdapterTest.kt`

- [ ] **Step 1: Add a small representative integration-adjacent assertion**

Extend the fake settings or fake service coverage only if needed to prove that the new docs-session field does not break live RAG callers.

- [ ] **Step 2: Run the focused caller test**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.ai.LiveRagToolAdapterTest"`

Expected: PASS after any necessary test fixture updates

- [ ] **Step 3: Commit**

Run:

```bash
git add phone-app/src/test/java/com/example/rokidphone/service/ai/LiveRagToolAdapterTest.kt
git commit -m "test: cover live rag with fresh docs session defaults"
```

## Chunk 5: Final Verification

### Task 5: Run focused verification and inspect regressions

**Files:**
- Verify touched Kotlin and test files only

- [ ] **Step 1: Run the focused docs and live RAG unit suite**

Run: `./gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.rag.AnythingLlmRagServiceTest" --tests "com.example.rokidphone.data.DocsAssistantSettingsTest" --tests "com.example.rokidphone.service.ai.LiveRagToolAdapterTest"`

Expected: PASS

- [ ] **Step 2: Run a compile-level safety check**

Run: `./gradlew :phone-app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Review the diff for scope control**

Run: `git diff -- docs/superpowers/specs/2026-04-06-docs-rag-fresh-session-default-design.md docs/superpowers/plans/2026-04-06-docs-rag-fresh-session-default.md phone-app/src/main/java/com/example/rokidphone/data/DocsAssistantModels.kt phone-app/src/main/java/com/example/rokidphone/service/rag/network/AnythingLlmDtos.kt phone-app/src/main/java/com/example/rokidphone/service/rag/network/AnythingLlmApi.kt phone-app/src/main/java/com/example/rokidphone/service/rag/AnythingLlmRagService.kt phone-app/src/test/java/com/example/rokidphone/data/DocsAssistantSettingsTest.kt phone-app/src/test/java/com/example/rokidphone/service/rag/AnythingLlmRagServiceTest.kt phone-app/src/test/java/com/example/rokidphone/service/ai/LiveRagToolAdapterTest.kt`

Expected: only docs-session-isolation changes appear

- [ ] **Step 4: Summarize residual risks**

Capture:
- AnythingLLM server versions that may ignore unknown `sessionId`
- potential analytics/history growth on the server due to one session bucket per request
- future option to migrate to threads only if product requirements expand beyond isolation
