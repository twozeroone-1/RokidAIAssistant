# Gemini Multi-Key Pool Design

**Date:** 2026-04-02

## Goal

Allow the phone app to accept multiple Gemini API keys in Settings, automatically rotate/fail over between them at runtime, and expose the latest approved Gemini models in the model picker so users can keep using Gemini when one key hits quota or becomes temporarily unavailable.

## Current State

- The app stores Gemini access in a single string field: `ApiSettings.geminiApiKey`.
- `SettingsRepository` persists a single encrypted string under `gemini_api_key`.
- `GeminiService` accepts exactly one key and appends it directly to the request URL.
- The Settings UI exposes Gemini input as a single text field.
- The Gemini model catalog in `AvailableModels` is only partially current: it already includes `gemini-3-flash-preview`, but it does not include `gemini-3.1-flash-lite-preview`, and the Gemini Live entry is still outdated.

This makes Gemini brittle: one exhausted or invalid key blocks the whole provider.

## Requirements

- Users can paste multiple Gemini API keys directly into the phone app Settings UI.
- Existing single-key users must continue to work without migration friction.
- Runtime Gemini requests should automatically switch keys on quota and temporary availability failures.
- Invalid or forbidden keys should be temporarily excluded so they are not retried on every request.
- The change must be limited to Gemini and Gemini Live related Gemini-key usage.
- Secrets must stay inside existing app-side secure storage patterns.
- The model picker must expose the latest approved Gemini text/vision and Live-capable model IDs we intend to support.

## Non-Goals

- Generalizing multi-key pools to all providers.
- Importing keys from desktop files or external sync sources.
- Building a complex analytics dashboard for key health.
- Permanent server-side key validation or remote secret storage.
- Automatically syncing model catalogs from Google at runtime.

## Recommended Approach

Use a multiline Gemini key pool stored in the existing `geminiApiKey` string field, parsed into a normalized list at runtime, with a small in-memory `GeminiKeyPool` coordinator that selects keys, applies failover, and tracks cooldown state.

This keeps persistence and migration simple while isolating failover behavior from UI and network code.

Update the static Gemini catalog in `AvailableModels` at the same time so the Settings model picker reflects the latest approved Gemini entries.

## Data Model

### Stored Value

Keep `ApiSettings.geminiApiKey: String`, but reinterpret it as raw Gemini key input:

- one line = one key
- blank lines ignored
- surrounding whitespace trimmed
- duplicate keys removed while preserving first-seen order

### Derived Helpers

Add helper methods on `ApiSettings`:

- `getGeminiApiKeys(): List<String>`
- `hasGeminiKeyPool(): Boolean`
- `primaryGeminiApiKeyOrBlank(): String`

Behavior:

- a single key remains valid input
- multiline input becomes a parsed pool
- all existing code that still references `geminiApiKey` directly should be moved to helper access where Gemini rotation matters

## Runtime Architecture

### New Component

Add a focused class such as `GeminiKeyPool` under `phone-app/src/main/java/com/example/rokidphone/service/ai/`.

Responsibilities:

- hold normalized Gemini key list
- remember next starting index for round-robin selection
- mark keys temporarily unavailable
- classify failures for retry/failover decisions
- provide the next candidate key for one request attempt

It should remain in-memory only. Persistence of per-key health is unnecessary for this feature and would complicate stale-state recovery.

### Service Integration

`GeminiService` should stop behaving like a permanently single-key client. Instead, each request should:

1. Ask `GeminiKeyPool` for a starting key.
2. Attempt the Gemini request with that key.
3. On success, mark the key successful and return.
4. On retryable quota/availability failure, mark the key cooling down and continue with the next available key.
5. On invalid/forbidden key errors, exclude that key for a longer cooldown and continue.
6. If all keys are exhausted, return the last meaningful user-facing error.

The key pool should be reused within the service instance so repeated calls can round-robin instead of always starting at key 0.

## Failover Policy

### Retry/Fallback Errors

Immediately try the next key when Gemini returns or implies:

- HTTP `429`
- HTTP `503`
- `RESOURCE_EXHAUSTED`
- quota exceeded
- rate limit exceeded
- overload / temporarily unavailable

These should put the failing key into a short cooldown, for example 5 to 15 minutes.

### Long Cooldown Errors

Temporarily exclude the key for a longer period, for example 1 hour, when Gemini returns or implies:

- HTTP `401`
- HTTP `403`
- API key invalid
- permission denied
- key not authorized

### Non-Failover Errors

Do not rotate through the entire pool when the problem is likely request-shaped rather than key-shaped:

- malformed request
- unsupported model / invalid generation config
- local serialization bug

In these cases, fail fast and surface the error.

### Network Errors

For timeouts or transient IO failures:

- retry once on the same key
- then advance to next key if the retry still fails

## UI Design

Update the Gemini section in Settings from a single-line field to a multiline secret input.

Expected UX:

- label communicates that multiple keys are allowed
- helper text explains: one key per line
- the field still accepts a single key without any extra mode switching
- optional status text can show parsed count, for example: `13 keys loaded`

No per-key list editing UI is required. Simple multiline paste is enough and matches the user’s workflow.

## Model Catalog Updates

As of 2026-04-02, the app-side Gemini model list should be refreshed to include the latest approved IDs we want users to pick in-app:

- Keep: `gemini-3.1-pro-preview`
- Keep: `gemini-3-flash-preview`
- Add: `gemini-3.1-flash-lite-preview`

For Gemini Live, replace the outdated entry with the current approved Live/native-audio model:

- Replace outdated Live entry with: `gemini-2.5-flash-native-audio-preview-09-2025`

Do not add an unverified `Gemini 3 Flash Live` model label or ID unless Google publishes a current documented model ID for it. The app should only ship model IDs we can trace to official documentation.

## Compatibility

- Existing stored single Gemini keys continue to load and function.
- `BuildConfig.GEMINI_API_KEY` fallback remains acceptable as a final development fallback, but if runtime key pool exists it should take precedence.
- Gemini Live and any Gemini-backed STT path should read from the same normalized key pool source where practical.
- Existing saved Gemini model selections should still load. If a saved Gemini model ID is no longer supported in the refreshed catalog, validation should fall back to a safe supported model instead of leaving the provider misconfigured.

## Testing Strategy

### Unit Tests

Add focused tests for:

- multiline parsing
- whitespace trimming
- duplicate removal
- single-key backward compatibility
- round-robin selection
- short cooldown after quota failure
- long cooldown after invalid-key failure
- all-keys-exhausted behavior

### Service Tests

Cover request execution scenarios such as:

- first key succeeds
- first key gets `429`, second key succeeds
- first key invalid, second key succeeds
- request-shape error does not rotate through all keys

### UI Tests

Keep UI verification lightweight:

- multiline Gemini field accepts line breaks
- helper text is shown
- saved settings round-trip without data loss

## File Impact

Expected primary files:

- Modify: `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ai/GeminiService.kt`
- Modify: `phone-app/src/main/java/com/example/rokidphone/service/ai/AiServiceFactory.kt`
- Create: `phone-app/src/main/java/com/example/rokidphone/service/ai/GeminiKeyPool.kt`
- Create/Modify tests under `phone-app/src/test/java/com/example/rokidphone/`
- Review Gemini model validation and catalog definitions in `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`

## Risks

- If rotation is added too deep in `GeminiService` without separating concerns, the file will become harder to maintain.
- If request-shape errors are misclassified as key failures, the app will burn through all keys and hide the real bug.
- If cooldown is too aggressive, temporary failures may unnecessarily remove usable keys.

## Recommendation

Implement the smallest useful version:

- multiline key input
- normalized key parsing
- in-memory round-robin selection
- failover for quota/availability/auth errors
- targeted unit tests

Do not add metrics, import/export, or generalized multi-provider pooling in this iteration.
