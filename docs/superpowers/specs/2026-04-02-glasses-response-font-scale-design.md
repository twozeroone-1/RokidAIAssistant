# Glasses Response Font Scale Design

## Goal

Add a slider in the phone app settings that controls the glasses AI response body font size. When glasses are connected, the new value should apply immediately on the glasses display. The change should affect only the response body, not the surrounding UI labels, hints, or indicators.

## Scope

- Include:
  - Phone-side persisted setting for glasses response font scale
  - Slider UI in the phone settings screen
  - Small protocol addition to sync the setting to glasses
  - Immediate application on the glasses when connected
  - Response body rendering and pagination changes on the glasses
- Exclude:
  - Global typography changes across the glasses UI
  - A separate glasses-only settings screen
  - Bluetooth/input/camera changes

## Recommended Approach

Use a phone-side persisted `responseFontScalePercent` setting and sync it to glasses via the existing phone↔glasses message channel.

- Default: `100`
- Min: `80`
- Max: `140`

This matches the user's control model:
- configure in the phone app
- persist in the phone app
- push to glasses immediately when connected
- reapply automatically when glasses reconnect

## Architecture

### 1. Phone-side persisted setting

Add `responseFontScalePercent: Int` to the phone app settings model.

Files affected:
- `phone-app/.../data/ApiSettings.kt`
- `phone-app/.../data/SettingsRepository.kt`
- `phone-app/.../ui/SettingsScreen.kt`

Responsibilities:
- Persist the slider value in phone settings
- Clamp values to `80..140`
- Default to `100`

### 2. Protocol sync to glasses

Add a small phone→glasses settings message to the shared protocol.

Files affected:
- `common/.../protocol/MessageType.kt`
- shared message helpers as needed

Payload:
- `responseFontScalePercent`

Behavior:
- `PhoneAIService` sends the current value when glasses connect
- `PhoneAIService` also sends updates when the slider changes

This keeps the sync one-way and simple.

### 3. Phone runtime propagation

`PhoneAIService` should observe settings changes and push the new value to glasses only when it changes.

Requirements:
- Do not spam the transport on every recomposition
- Use a narrow observer on the relevant setting field
- When glasses reconnect, resend the current persisted value

Immediate UX is preferred, but the value is still small enough that the transport cost is negligible.

### 4. Glasses-side application

`GlassesViewModel` should store the current response font scale in `uiState` and update it when a sync message arrives.

Requirements:
- Recompute visible pagination when the scale changes and a response is already on screen
- Keep the setting in memory on glasses; the phone remains the source of truth

### 5. Response rendering

The current response body rendering in `MainActivity` uses fixed sizes:
- paginated: `20.sp` / `28.sp`
- non-paginated: `24.sp` / `32.sp`

Change only the AI response body to:
- multiply base size and line height by `responseFontScalePercent / 100f`

Do not change:
- hint text
- page indicator
- connection or setup labels

### 6. Pagination behavior

The current pagination logic in `GlassesViewModel.paginateText()` uses fixed limits:
- `MAX_CHARS_PER_PAGE = 120`
- `MAX_LINES_PER_PAGE = 4`

To avoid heavy layout measurement, keep the character-budget approach but scale it using the font percent.

Recommended heuristic:
- `effectiveMaxCharsPerPage = round(BASE_MAX_CHARS_PER_PAGE * 100f / responseFontScalePercent)`

This preserves current behavior at `100%`, increases pages for larger text, and reduces pages for smaller text.

## Error Handling

- Clamp all stored and received values to `80..140`
- Fall back to `100` if the setting is missing or malformed
- If glasses receive an invalid sync payload, ignore it and keep the current value
- If repagination fails, keep the last visible response and recover on the next incoming update

## Performance Considerations

Immediate sync does not meaningfully increase runtime load:
- the payload is a single integer
- updates are user-driven and rare

The only practical concern is jitter while dragging the slider.

Recommended mitigation:
- send updates immediately, but only on actual value changes
- if needed during implementation, debounce to a short interval so drag remains smooth

## Testing

### Phone tests

- `ApiSettings` default and clamp behavior
- `SettingsRepository` save/load for `responseFontScalePercent`
- `PhoneAIService` emits sync messages when the value changes
- `PhoneAIService` resends the current value on glasses reconnection

### Common/protocol tests

- New message type serializes and deserializes correctly

### Glasses tests

- Incoming sync message updates `uiState.responseFontScalePercent`
- Larger scale increases page count for the same response
- Smaller scale reduces page count for the same response
- Existing visible response repaginates immediately after receiving a new scale

### Manual verification

- Move slider in phone settings while glasses are connected
- Confirm response body size changes immediately
- Confirm hints and indicators remain unchanged
- Restart/reconnect glasses and confirm the last value is reapplied

## Upstream Compatibility

Keep this change narrow to reduce future conflicts:
- add one new phone setting field
- add one small protocol message
- modify `PhoneAIService` only where connection/setup messages already flow
- modify glasses rendering and pagination only where AI responses are already handled
- avoid changes to camera, key handling, Bluetooth behavior, and unrelated UI
