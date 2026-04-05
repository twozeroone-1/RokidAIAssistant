# Realtime Settings Order Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorder the existing `Realtime conversation` settings rows so the most commonly used controls appear first without changing behavior or adding new UI patterns.

**Architecture:** Keep the Compose screen and dialogs intact, but move row ordering into a small helper that returns the visible row keys for a given `ApiSettings` state. The screen will continue using existing components, and tests will verify the ordering logic independently from Compose rendering.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, JUnit4, Truth, Gradle

---

## Chunk 1: Testable ordering model

### Task 1: Add a failing unit test for the default live order

**Files:**
- Create: `phone-app/src/test/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayoutTest.kt`
- Create: `phone-app/src/main/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayout.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun defaultLiveOrder_placesSessionRoutingAndVoiceControlsFirst() {
    val items = realtimeConversationSettingItems(ApiSettings()).map { it.key }

    assertThat(items).containsExactly(
        RealtimeConversationSettingKey.LIVE_RAG,
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.ui.RealtimeConversationSettingsLayoutTest"`
Expected: FAIL because the helper does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```kotlin
enum class RealtimeConversationSettingKey { ... }

data class RealtimeConversationSettingItem(
    val key: RealtimeConversationSettingKey,
)

fun realtimeConversationSettingItems(settings: ApiSettings): List<RealtimeConversationSettingItem> = ...
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.ui.RealtimeConversationSettingsLayoutTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add phone-app/src/test/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayoutTest.kt phone-app/src/main/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayout.kt
git commit -m "test: add realtime settings ordering coverage"
```

### Task 2: Add a failing test for conditional rows

**Files:**
- Modify: `phone-app/src/test/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayoutTest.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayout.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun conditionalRows_showMicProfileAndCameraIntervalOnlyWhenEnabled() {
    val settings = ApiSettings(
        experimentalLiveMicTuningEnabled = true,
        liveCameraMode = LiveCameraMode.INTERVAL,
    )

    val items = realtimeConversationSettingItems(settings).map { it.key }

    assertThat(items).contains(RealtimeConversationSettingKey.EXPERIMENTAL_LIVE_MIC_PROFILE)
    assertThat(items).contains(RealtimeConversationSettingKey.LIVE_CAMERA_INTERVAL)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.ui.RealtimeConversationSettingsLayoutTest.conditionalRows_showMicProfileAndCameraIntervalOnlyWhenEnabled"`
Expected: FAIL because conditional ordering has not been implemented.

- [ ] **Step 3: Write minimal implementation**

```kotlin
if (settings.experimentalLiveMicTuningEnabled) { ... }
if (settings.liveCameraMode == LiveCameraMode.INTERVAL) { ... }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.ui.RealtimeConversationSettingsLayoutTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add phone-app/src/test/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayoutTest.kt phone-app/src/main/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayout.kt
git commit -m "test: cover conditional realtime settings rows"
```

## Chunk 2: Apply the order in Compose

### Task 3: Rewire `SettingsScreen` to use the ordered helper

**Files:**
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayout.kt`
- Test: `phone-app/src/test/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayoutTest.kt`

- [ ] **Step 1: Update the screen to render rows in helper order**

Use the helper keys to drive rendering while keeping each row's current component, label, dialog trigger, and enabled/visibility logic.

- [ ] **Step 2: Keep existing behavior intact**

Specifically preserve:
- Output target disabled state when live answer audio is off
- Live RAG sub-controls only when Live RAG is on
- Realtime warning text for realtime camera mode

- [ ] **Step 3: Run targeted tests**

Run: `.\gradlew :phone-app:testDebugUnitTest --tests "com.example.rokidphone.ui.RealtimeConversationSettingsLayoutTest"`
Expected: PASS

- [ ] **Step 4: Run build verification**

Run: `.\gradlew :phone-app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt phone-app/src/main/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayout.kt phone-app/src/test/java/com/example/rokidphone/ui/RealtimeConversationSettingsLayoutTest.kt
git commit -m "refactor: reorder realtime settings section"
```
