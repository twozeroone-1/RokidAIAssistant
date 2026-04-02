# Gemini Multi-Key Pool Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add multiline Gemini API key input to the phone app and use the parsed key pool for round-robin selection and automatic failover on quota and auth-related failures.

**Architecture:** Persist Gemini keys in the existing encrypted settings string, parse them into a normalized list through `ApiSettings`, and route Gemini requests through a small in-memory `GeminiKeyPool` helper. Keep the implementation Gemini-specific and avoid broad provider abstractions in this iteration.

**Tech Stack:** Kotlin, Android SharedPreferences/EncryptedSharedPreferences, Jetpack Compose, OkHttp, Robolectric/JUnit, Gradle.

---

## Chunk 1: Settings Model And Parsing

### Task 1: Add Gemini key-pool parsing helpers to ApiSettings

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/data/ApiSettingsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `getGeminiApiKeys parses multiline input and removes duplicates`() {
    val settings = ApiSettings(
        geminiApiKey = " key-a \n\nkey-b\nkey-a\nkey-c "
    )

    assertThat(settings.getGeminiApiKeys()).containsExactly(
        "key-a", "key-b", "key-c"
    ).inOrder()
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.ApiSettingsTest"`
Expected: FAIL because `getGeminiApiKeys()` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
fun getGeminiApiKeys(): List<String> {
    return geminiApiKey
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
}
```

- [ ] **Step 4: Add single-key compatibility test**

```kotlin
@Test
fun `getGeminiApiKeys keeps single key input compatible`() {
    val settings = ApiSettings(geminiApiKey = "single-key")
    assertThat(settings.getGeminiApiKeys()).containsExactly("single-key")
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.ApiSettingsTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt phone-app/src/test/java/com/example/rokidphone/data/ApiSettingsTest.kt
git commit -m "feat: add Gemini key pool parsing helpers"
```

### Task 2: Keep settings persistence compatible with multiline Gemini input

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write the failing persistence round-trip test**

```kotlin
@Test
fun `saveSettings preserves multiline Gemini key pool`() {
    val original = repository.getSettings().copy(
        geminiApiKey = "key-a\nkey-b\nkey-c"
    )

    repository.saveSettings(original)

    assertThat(repository.getSettings().geminiApiKey).isEqualTo("key-a\nkey-b\nkey-c")
}
```

- [ ] **Step 2: Run test to verify current behavior**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.SettingsRepositoryTest"`
Expected: either PASS already or FAIL due to missing coverage setup.

- [ ] **Step 3: Add minimal repository normalization only if needed**

```kotlin
private fun normalizeGeminiKeyInput(raw: String): String =
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString("\n")
```

- [ ] **Step 4: Save normalized Gemini input before persisting**

```kotlin
putString(KEY_GEMINI_API_KEY, normalizeGeminiKeyInput(settings.geminiApiKey))
```

- [ ] **Step 5: Run tests to verify pass**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.SettingsRepositoryTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt phone-app/src/test/java/com/example/rokidphone/data/SettingsRepositoryTest.kt
git commit -m "feat: normalize persisted Gemini key pools"
```

## Chunk 2: Key Pool Runtime

### Task 3: Create GeminiKeyPool with round-robin and cooldown support

**Files:**
- Create: `phone-app/src/main/java/com/example/rokidphone/service/ai/GeminiKeyPool.kt`
- Create: `phone-app/src/test/java/com/example/rokidphone/service/ai/GeminiKeyPoolTest.kt`

- [ ] **Step 1: Write the failing round-robin test**

```kotlin
@Test
fun `nextCandidate rotates through healthy keys`() {
    val pool = GeminiKeyPool(listOf("k1", "k2", "k3"))

    assertThat(pool.nextCandidate()).isEqualTo("k1")
    pool.markSuccess("k1")
    assertThat(pool.nextCandidate()).isEqualTo("k2")
}
```

- [ ] **Step 2: Write the failing cooldown exclusion test**

```kotlin
@Test
fun `quota failure cools key down and advances`() {
    val pool = GeminiKeyPool(listOf("k1", "k2"), clock = fakeClock)

    assertThat(pool.nextCandidate()).isEqualTo("k1")
    pool.markQuotaFailure("k1")

    assertThat(pool.nextCandidate()).isEqualTo("k2")
}
```

- [ ] **Step 3: Run tests to verify failure**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.ai.GeminiKeyPoolTest"`
Expected: FAIL because class does not exist.

- [ ] **Step 4: Write minimal implementation**

```kotlin
class GeminiKeyPool(
    private val keys: List<String>,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val cooldownUntil = mutableMapOf<String, Long>()
    private var nextIndex = 0

    fun nextCandidate(): String? { /* round-robin healthy selection */ }
    fun markSuccess(key: String) { /* keep healthy */ }
    fun markQuotaFailure(key: String) { /* short cooldown */ }
    fun markInvalidKey(key: String) { /* long cooldown */ }
}
```

- [ ] **Step 5: Add all-keys-exhausted test**

```kotlin
@Test
fun `nextCandidate returns null when all keys are cooling down`() { /* ... */ }
```

- [ ] **Step 6: Run tests to verify pass**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.ai.GeminiKeyPoolTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/service/ai/GeminiKeyPool.kt phone-app/src/test/java/com/example/rokidphone/service/ai/GeminiKeyPoolTest.kt
git commit -m "feat: add Gemini key pool failover manager"
```

### Task 4: Add Gemini error classification helpers

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ai/GeminiKeyPool.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/service/ai/GeminiKeyPoolTest.kt`

- [ ] **Step 1: Write failing classification tests**

```kotlin
@Test
fun `classify treats 429 as retryable quota failure`() { /* ... */ }

@Test
fun `classify treats invalid key as long cooldown failure`() { /* ... */ }

@Test
fun `classify treats malformed request as non failover`() { /* ... */ }
```

- [ ] **Step 2: Run tests to verify failure**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.ai.GeminiKeyPoolTest"`
Expected: FAIL on missing classification behavior.

- [ ] **Step 3: Implement minimal classifier**

```kotlin
enum class GeminiKeyFailure { QUOTA, INVALID, NON_FAILOVER, NETWORK }
```

- [ ] **Step 4: Run tests to verify pass**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.ai.GeminiKeyPoolTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/service/ai/GeminiKeyPool.kt phone-app/src/test/java/com/example/rokidphone/service/ai/GeminiKeyPoolTest.kt
git commit -m "feat: classify Gemini key failover conditions"
```

## Chunk 3: Gemini Service Integration

### Task 5: Inject parsed Gemini keys into Gemini service construction

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ai/AiServiceFactory.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ai/GeminiService.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/service/ai/GeminiServiceTest.kt`

- [ ] **Step 1: Write the failing constructor-level test**

```kotlin
@Test
fun `Gemini service uses parsed key pool when multiple keys are configured`() { /* ... */ }
```

- [ ] **Step 2: Run the targeted test**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.ai.GeminiServiceTest"`
Expected: FAIL for missing multi-key support.

- [ ] **Step 3: Extend service constructor minimally**

```kotlin
class GeminiService(
    apiKey: String,
    private val keyPool: GeminiKeyPool? = null,
    ...
)
```

- [ ] **Step 4: Update factory to pass parsed keys**

```kotlin
val geminiKeys = settings.getGeminiApiKeys()
val keyPool = GeminiKeyPool(geminiKeys)
```

- [ ] **Step 5: Run targeted tests**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.ai.GeminiServiceTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/service/ai/AiServiceFactory.kt phone-app/src/main/java/com/example/rokidphone/service/ai/GeminiService.kt phone-app/src/test/java/com/example/rokidphone/service/ai/GeminiServiceTest.kt
git commit -m "feat: wire Gemini key pools into service creation"
```

### Task 6: Apply failover inside Gemini chat and image requests

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ai/GeminiService.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/service/ai/GeminiServiceTest.kt`

- [ ] **Step 1: Write failing chat failover test**

```kotlin
@Test
fun `chat retries with next key after quota failure`() { /* first key 429, second key 200 */ }
```

- [ ] **Step 2: Write failing invalid-key exclusion test**

```kotlin
@Test
fun `chat skips invalid key after auth failure`() { /* first key 403, second key succeeds */ }
```

- [ ] **Step 3: Run tests to verify failure**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.ai.GeminiServiceTest"`
Expected: FAIL due to single-key execution path.

- [ ] **Step 4: Implement minimal per-request candidate loop**

```kotlin
val attemptKeys = keyPool?.candidateSequence().orSingle(apiKey)
for (key in attemptKeys) {
    val result = executeRequestWith(key)
    if (result.isSuccess) return result.value
}
```

- [ ] **Step 5: Reuse the same policy for `transcribe`, `transcribeAudioFile`, `chat`, and `analyzeImage`**

Only rotate on classified key-related failures. Keep malformed-request failures immediate.

- [ ] **Step 6: Run targeted tests**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.ai.GeminiServiceTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/service/ai/GeminiService.kt phone-app/src/test/java/com/example/rokidphone/service/ai/GeminiServiceTest.kt
git commit -m "feat: add Gemini key failover to runtime requests"
```

## Chunk 4: Settings UI

### Task 7: Convert Gemini key field to multiline pool input

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
- Modify: `phone-app/src/main/res/values/strings.xml`
- Modify: `phone-app/src/main/res/values-ko/strings.xml`

- [ ] **Step 1: Write down the expected UI text changes**

Add strings such as:

```xml
<string name="gemini_api_keys_label">Gemini API Keys</string>
<string name="gemini_api_keys_hint">Enter one Gemini API key per line.</string>
<string name="gemini_api_keys_count">%1$d Gemini keys loaded</string>
```

- [ ] **Step 2: Update the Compose input to multiline**

Use a text field that supports multi-line secret entry and keeps the existing `onSettingsChange(settings.copy(geminiApiKey = it))` flow.

- [ ] **Step 3: Show parsed count from `settings.getGeminiApiKeys().size`**

- [ ] **Step 4: Compile to verify**

Run: `.\gradlew.bat :phone-app:compileDebugKotlin`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt phone-app/src/main/res/values/strings.xml phone-app/src/main/res/values-ko/strings.xml
git commit -m "feat: add multiline Gemini key pool settings UI"
```

## Chunk 5: Regression Verification

### Task 8: Verify merged behavior and guard existing flows

**Files:**
- Modify as needed: test files only if regressions are exposed

- [ ] **Step 1: Run Gemini-specific test suites**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.data.ApiSettingsTest" --tests "com.example.rokidphone.data.SettingsRepositoryTest" --tests "com.example.rokidphone.service.ai.GeminiServiceTest" --tests "com.example.rokidphone.service.ai.GeminiKeyPoolTest"`
Expected: PASS

- [ ] **Step 2: Run broader AI factory coverage**

Run: `.\gradlew.bat :phone-app:testDebugUnitTest --tests "com.example.rokidphone.service.ai.AiServiceFactoryTest"`
Expected: PASS

- [ ] **Step 3: Run compile verification**

Run: `.\gradlew.bat :phone-app:compileDebugKotlin`
Expected: PASS

- [ ] **Step 4: Run final combined verification**

Run: `.\gradlew.bat :common:testDebugUnitTest :phone-app:testDebugUnitTest`
Expected: PASS

- [ ] **Step 5: Commit final adjustments**

```bash
git add .
git commit -m "test: verify Gemini multi-key pool integration"
```
