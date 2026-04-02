# New Conversation Toggle Design

**Date:** 2026-04-02

## Goal

Add a user-facing toggle that keeps conversation records but forces every AI request to run as a fresh session when enabled.

## Requirements

- Default behavior stays unchanged.
- A new setting enables per-request isolation for all AI requests.
- When enabled, each request creates a new conversation record instead of reusing the current one.
- When enabled, provider-side conversation history must be cleared before the request so prior context is not sent.
- Existing upstream `main` behavior should remain intact when the toggle is off.

## Scope

Included:
- Chat screen text requests
- Phone service voice requests
- Photo analysis requests
- Recording analysis requests
- Image analysis repository requests

Deferred:
- Deep refactors of provider caching
- Glasses input changes
- Broad UI redesign

## Architecture

The feature adds one new persisted setting, `alwaysStartNewAiSession`, and a small session-policy helper package that centralizes two decisions:

1. Should the current request start a new conversation?
2. Should the current provider history be cleared before the request?

Large existing files keep their current responsibilities. They only ask the new helper whether to create a fresh conversation and whether to clear service-side history.

## Data Flow

### Toggle Off

- Existing conversation reuse remains unchanged.
- Existing provider history behavior remains unchanged.

### Toggle On

- The request entry point creates a new conversation id for that request.
- The entry point clears relevant AI service history before the request.
- User and assistant messages are stored in the new conversation.
- History remains visible in the app, but no previous conversation context is sent.

## File Strategy

Modify minimally:
- `phone-app/src/main/java/com/example/rokidphone/data/ApiSettings.kt`
- `phone-app/src/main/java/com/example/rokidphone/data/SettingsRepository.kt`
- `phone-app/src/main/java/com/example/rokidphone/ui/SettingsScreen.kt`
- `phone-app/src/main/java/com/example/rokidphone/viewmodel/ConversationViewModel.kt`
- `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`
- `phone-app/src/main/java/com/example/rokidphone/service/EnhancedAIService.kt`
- `phone-app/src/main/java/com/example/rokidphone/data/AiRepository.kt`

Additive helpers:
- `phone-app/src/main/java/com/example/rokidphone/service/session/AiRequestSessionSupport.kt`

## Error Handling

- If conversation creation fails, keep existing error behavior.
- If clearing provider history fails, log and continue only if the provider is unavailable for clearing; do not hide primary request failures.
- The toggle must not break provider selection or docs assistant routing.

## Testing

- Persist/load the new setting.
- Verify session helper behavior with toggle on and off.
- Verify representative request entry points create fresh conversations and clear history only when enabled.
