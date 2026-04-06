# Docs RAG Fresh Session Default Design

## Goal

Make AnythingLLM-backed RAG requests start in a fresh server-side session by default so previous docs conversations do not influence later answers.

## Current Context

- `AnythingLlmRagService` sends docs queries directly to `api/v1/workspace/{slug}/chat`.
- The Rokid app already has `alwaysStartNewAiSession`, but that only creates a new local conversation record and clears local AI provider history.
- AnythingLLM partitions chat history by `thread_id` and `api_session_id`.
- When neither `thread_id` nor `api_session_id` is set, workspace chat history accumulates in the default workspace bucket.
- `ConversationViewModel`, `EnhancedAIService`, `PhoneAIService`, and `LiveRagToolAdapter` all route docs lookups through `RagService.answer`.
- The glasses app does not call AnythingLLM directly. It only renders results produced by the phone app.

## Problem

The current docs integration reuses the default AnythingLLM workspace chat bucket. That means:

1. Earlier docs questions and answers can bleed into later RAG prompts.
2. Backfilled citations from previous turns can affect current retrieval quality.
3. The user experiences RAG as if it has unwanted memory even when the app starts a fresh local conversation.

## Design

### Session Isolation Principle

- Every AnythingLLM RAG request should carry a fresh `sessionId` by default.
- The new `sessionId` applies only to docs/RAG requests.
- General AI chat, local conversation history, and existing `alwaysStartNewAiSession` behavior stay unchanged.

### Why `sessionId` Instead Of New Thread Per Request

Recommended approach:

- Reuse the existing `workspace/{slug}/chat` endpoint.
- Add `sessionId` to the request body.
- Generate a new UUID per docs request when `alwaysNewSession` is enabled.

Reasons:

- No server changes are required because AnythingLLM already supports `sessionId`.
- All existing RAG entry points benefit automatically because they already depend on `RagService.answer`.
- It avoids extra round trips for `thread/new`.
- It keeps the change focused on server-side docs context isolation instead of introducing thread lifecycle management.

### RAG Settings Policy

- `AnythingLlmSettings.alwaysNewSession` becomes the single docs-side switch for this behavior.
- Default value remains `true`.
- `ApiSettings.toAnythingLlmSettings()` should set it explicitly so the default is obvious at the conversion boundary.
- No new user-facing toggle is added in this change.

### Network Contract

Extend the AnythingLLM chat request model to include:

- `sessionId: String?`

Behavior:

- if `alwaysNewSession == true`, send a fresh UUID on each `answer()` call
- if `alwaysNewSession == false`, omit `sessionId` and preserve current AnythingLLM behavior

### Affected Call Paths

No call-site-specific branching is needed once `AnythingLlmRagService` owns session creation.

Covered automatically:

- text docs chat via `ConversationViewModel`
- voice docs routing via `PhoneAIService`
- photo-context docs lookups via `EnhancedAIService` and `PhoneAIService`
- live RAG tool calls via `LiveRagToolAdapter`

## Non-Goals

- No new UI setting for docs session isolation
- No change to app-side conversation creation rules
- No migration from workspace chat to thread chat
- No cleanup policy for AnythingLLM threads because no threads are created
- No change to general AI provider history behavior

## Error Handling

- If UUID generation fails, treat it as a normal request failure and keep existing error handling.
- If AnythingLLM ignores or rejects `sessionId`, surface the API error through the existing RAG failure path.
- Do not silently fall back to shared workspace history when a fresh-session request was intended.

## Testing

- Verify `AnythingLlmRagService` sends `sessionId` when `alwaysNewSession == true`.
- Verify consecutive RAG calls send different `sessionId` values.
- Verify `sessionId` is omitted when `alwaysNewSession == false`.
- Verify existing answer/source parsing still works unchanged.
- Verify one representative higher-level path still succeeds with the updated `RagService` contract.
