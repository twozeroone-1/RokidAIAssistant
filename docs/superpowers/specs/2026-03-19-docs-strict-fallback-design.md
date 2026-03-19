# Docs Strict Fallback Design

## Goal

Keep Docs mode fast for document-grounded answers while falling back to General AI only on clear docs misses, and make that fallback visible to the user with a small badge.

## Current Context

- `AnythingLlmRagService` sends text queries directly to `workspace/{slug}/chat`.
- `ConversationViewModel`, `EnhancedAIService`, and `PhoneAIService` each call the docs service directly for text/voice flows.
- `GENERAL_AI_FALLBACK` already exists, but today it is used only for photo routing.
- Chat and history UIs already render `routeBadge` values from message and conversation metadata.

## Problem

Docs mode is optimized for workspace retrieval, but when the workspace does not contain the answer the current behavior still returns the docs result or a hard failure. That leaves two gaps:

1. The user does not get a helpful answer when docs clearly miss.
2. The user cannot easily tell when the assistant had to leave the docs path.

## Design

### Routing Principle

- Keep `Docs` as the primary and fastest path.
- Do not pre-emptively call General AI.
- Trigger one General AI fallback only on a strict miss:
  - AnythingLLM request failure
  - timeout
  - empty docs answer
  - docs answer with zero sources plus either a very short reply or a known "not found" style phrase

### Fallback Evaluation

Introduce a small shared evaluator in the docs/rag layer:

- Input: `RagAnswer` or docs failure
- Output: whether to fallback and the reason string to store in metadata

The evaluator should be conservative:

- Do not fallback when docs returned sources.
- Do not fallback just because sources are empty if the reply is still substantive.
- Match a small, explicit set of miss phrases such as `not found`, `no relevant information`, `I don't know`, `don't have enough information`.

### Response Handling

- `ConversationViewModel` text chat:
  - try docs first
  - if evaluator says fallback, call the active General AI provider once
  - persist the result with `GENERAL_AI_FALLBACK`
- `PhoneAIService` voice/text flow:
  - same behavior for transcript-based assistant answers
- `EnhancedAIService`:
  - align `sendMessage`, `sendMessageStream`, and `quickChat` with the same policy where feasible for text flows

### User Visibility

- Reuse the existing badge mechanism.
- Change the fallback badge label from generic `Fallback` to explicit `Docs -> General`.
- Preserve `fallbackReason` in message/conversation metadata.

## Non-Goals

- No speculative parallel fallback requests.
- No workspace/thread redesign in this change.
- No photo-routing redesign beyond preserving existing behavior.
- No multi-turn docs memory feature.

## Testing

- Unit test the docs miss evaluator with clear fallback and no-fallback cases.
- Extend route/metadata coverage for the new fallback badge label.
- Run targeted `phone-app` unit tests covering the new evaluator and existing AnythingLLM behavior.
