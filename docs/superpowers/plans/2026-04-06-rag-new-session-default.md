# RAG New Session Default Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every AnythingLLM-backed RAG request start a fresh session by default for both Docs mode and Live RAG, without adding a user-facing toggle.

**Architecture:** Keep the current app-to-AnythingLLM integration, but move session isolation into `AnythingLlmRagService` so all RAG entrypoints share one policy. Use AnythingLLM's existing `sessionId` and `reset` request fields instead of changing the server fork.

**Tech Stack:** Kotlin, Retrofit, Gson, Robolectric, JUnit, MockWebServer

---

## Chunk 1: Lock the default policy in app settings

### Task 1: Make the RAG settings mapping explicit

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/DocsAssistantModels.kt`
- Create: `phone-app/src/test/java/com/example/rokidphone/data/DocsAssistantModelsTest.kt`

- [ ] **Step 1: Write the failing mapping test**

```kotlin
@Test
fun `toAnythingLlmSettings keeps always new session enabled for docs requests`() {
    val settings = ApiSettings(
        answerMode = AnswerMode.DOCS,
        anythingLlmServerUrl = "https://docs.local",
        anythingLlmApiKey = "secret",
        anythingLlmWorkspaceSlug = "manuals",
    )

    val result = settings.toAnythingLlmSettings()

    assertThat(result.alwaysNewSession).isTrue()
}
```

- [ ] **Step 2: Run the new test to verify it fails or is missing**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.DocsAssistantModelsTest"`
Expected: missing test file or failing assertion before implementation.

- [ ] **Step 3: Update the mapping to set the intent explicitly**

```kotlin
fun ApiSettings.toAnythingLlmSettings(): AnythingLlmSettings {
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
}
```

- [ ] **Step 4: Re-run the targeted test**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.DocsAssistantModelsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/data/DocsAssistantModels.kt phone-app/src/test/java/com/example/rokidphone/data/DocsAssistantModelsTest.kt
git commit -m "test: pin docs rag new-session default"
```

---

## Chunk 2: Send a fresh AnythingLLM session on every RAG request

### Task 2: Extend the request DTO for session isolation

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/rag/network/AnythingLlmDtos.kt`

- [ ] **Step 1: Add request fields for `sessionId` and `reset`**

```kotlin
data class AnythingLlmChatRequest(
    @SerializedName("message")
    val message: String,
    @SerializedName("mode")
    val mode: String = "query",
    @SerializedName("sessionId")
    val sessionId: String? = null,
    @SerializedName("reset")
    val reset: Boolean = false,
)
```

- [ ] **Step 2: Do not touch response DTOs or API interface signatures unless compilation requires it**

Run: no command yet
Expected: request model now supports the server contract used by `/v1/workspace/:slug/chat`.

### Task 3: Add failing service tests for fresh-session behavior

**Files:**
- Modify: `phone-app/src/test/java/com/example/rokidphone/service/rag/AnythingLlmRagServiceTest.kt`

- [ ] **Step 1: Add a test that verifies `sessionId` and `reset` are sent**

```kotlin
@Test
fun `answer includes session id and reset when always new session is enabled`() = runTest {
    serverRule.server.enqueue(jsonResponse("""{"id":"chat-1","textResponse":"ok","sources":[],"close":true}"""))

    val service = AnythingLlmRagService(sessionIdFactory = { "rag-session-1" })
    service.answer(createSettings(), "How do I deploy?").getOrThrow()

    val request = serverRule.server.takeRequest()
    assertThat(request.body.readUtf8()).contains("\"sessionId\":\"rag-session-1\"")
    assertThat(request.body.readUtf8()).contains("\"reset\":true")
}
```

- [ ] **Step 2: Add a test that verifies two consecutive calls use different session IDs**

```kotlin
@Test
fun `answer generates a fresh session id per request`() = runTest {
    serverRule.server.enqueue(jsonResponse("""{"id":"chat-1","textResponse":"one","sources":[],"close":true}"""))
    serverRule.server.enqueue(jsonResponse("""{"id":"chat-2","textResponse":"two","sources":[],"close":true}"""))

    val ids = ArrayDeque(listOf("rag-session-1", "rag-session-2"))
    val service = AnythingLlmRagService(sessionIdFactory = { ids.removeFirst() })

    service.answer(createSettings(), "first").getOrThrow()
    service.answer(createSettings(), "second").getOrThrow()

    val firstBody = serverRule.server.takeRequest().body.readUtf8()
    val secondBody = serverRule.server.takeRequest().body.readUtf8()
    assertThat(firstBody).contains("\"sessionId\":\"rag-session-1\"")
    assertThat(secondBody).contains("\"sessionId\":\"rag-session-2\"")
}
```

- [ ] **Step 3: Run the targeted test class to verify the new tests fail**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.rag.AnythingLlmRagServiceTest"`
Expected: FAIL because the request body does not yet contain `sessionId` and `reset`.

### Task 4: Update `AnythingLlmRagService` to own session creation

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/rag/AnythingLlmRagService.kt`

- [ ] **Step 1: Add an injectable session ID factory**

```kotlin
class AnythingLlmRagService(
    private val apiFactory: (String, String) -> AnythingLlmApi = { serverUrl, apiKey ->
        AnythingLlmApiFactory.create(serverUrl, apiKey)
    },
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
) : RagService
```

- [ ] **Step 2: Build the request with a fresh session when `alwaysNewSession` is enabled**

```kotlin
val request = AnythingLlmChatRequest(
    message = normalizedQuestion,
    mode = settings.queryMode.wireValue,
    sessionId = if (settings.alwaysNewSession) sessionIdFactory() else null,
    reset = settings.alwaysNewSession,
)
```

- [ ] **Step 3: Keep health checks and response mapping unchanged**

Run: no command yet
Expected: only request composition changes; response parsing and source mapping stay intact.

- [ ] **Step 4: Re-run the targeted service tests**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.rag.AnythingLlmRagServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/service/rag/AnythingLlmRagService.kt phone-app/src/main/java/com/example/rokidphone/service/rag/network/AnythingLlmDtos.kt phone-app/src/test/java/com/example/rokidphone/service/rag/AnythingLlmRagServiceTest.kt
git commit -m "feat: isolate anythingllm rag requests"
```

---

## Chunk 3: Verify Docs mode and Live RAG both inherit the new policy

### Task 5: Confirm existing call sites stay on the shared service path

**Files:**
- Verify: `phone-app/src/main/java/com/example/rokidphone/viewmodel/ConversationViewModel.kt`
- Verify: `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`
- Verify: `phone-app/src/main/java/com/example/rokidphone/service/ai/LiveRagToolAdapter.kt`
- Verify: `phone-app/src/test/java/com/example/rokidphone/service/ai/LiveRagToolAdapterTest.kt`

- [ ] **Step 1: Confirm Docs mode still calls `resolveDocsTextQuery()` / `AnythingLlmRagService.answer()` through `toAnythingLlmSettings()`**

Run: `rg -n "toAnythingLlmSettings\\(|resolveDocsTextQuery\\(|ragService.answer\\(" phone-app/src/main/java`
Expected: `ConversationViewModel` and `PhoneAIService` still reach RAG through the shared settings mapping and service.

- [ ] **Step 2: Confirm Live RAG still uses `LiveRagToolAdapter` with `settings.toAnythingLlmSettings()`**

Run: `rg -n "LiveRagToolAdapter|toAnythingLlmSettings\\(" phone-app/src/main/java/com/example/rokidphone/service`
Expected: `PhoneAIService` builds the live adapter and passes `settings.toAnythingLlmSettings()` into tool execution.

- [ ] **Step 3: Run the adapter test class to guard the live tool contract**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.ai.LiveRagToolAdapterTest"`
Expected: PASS

- [ ] **Step 4: Run the combined targeted suite**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.rag.AnythingLlmRagServiceTest" --tests "com.example.rokidphone.service.ai.LiveRagToolAdapterTest" --tests "com.example.rokidphone.data.DocsAssistantModelsTest"`
Expected: PASS

- [ ] **Step 5: Manual verification on device**

1. Set `Answer mode = Docs`.
2. Ask a first docs question with a specific answer.
3. Ask a second related docs question that should fail if prior context leaks.
4. Enable `Live RAG` and repeat with two consecutive live tool-triggering prompts.
5. Confirm each result is grounded only in docs retrieval and not in the prior RAG answer wording.

- [ ] **Step 6: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/viewmodel/ConversationViewModel.kt phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt phone-app/src/main/java/com/example/rokidphone/service/ai/LiveRagToolAdapter.kt phone-app/src/test/java/com/example/rokidphone/service/ai/LiveRagToolAdapterTest.kt
git commit -m "test: verify rag session isolation entrypoints"
```

---

## Final Verification

- [ ] **Step 1: Run the phone app unit test slice for docs/live RAG**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.rag.AnythingLlmRagServiceTest" --tests "com.example.rokidphone.service.ai.LiveRagToolAdapterTest" --tests "com.example.rokidphone.data.DocsAssistantModelsTest"`
Expected: PASS

- [ ] **Step 2: Build the phone debug APK**

Run: `.\gradlew :phone-app:assembleDebug`
Expected: BUILD SUCCESSFUL
