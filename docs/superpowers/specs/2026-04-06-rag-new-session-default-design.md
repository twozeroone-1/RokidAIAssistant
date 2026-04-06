# RAG New Session Default Design

## Goal

`Docs` 모드 텍스트 질의와 `Live RAG` 툴 호출이 모두 AnythingLLM에 질의할 때마다 새 세션으로 시작하게 바꾼다. 사용자는 같은 세션에 묶인 이전 RAG 답변 문맥 때문에 다음 답변이 오염되지 않아야 한다.

## Current Problem

현재 앱의 RAG 경로는 `phone-app`에서 AnythingLLM `POST /v1/workspace/:slug/chat`를 호출하지만, 요청에 `sessionId`를 넣지 않는다. 결과적으로 AnythingLLM API 세션 경계를 앱이 직접 제어하지 못하고, 사용자가 연속 질의를 하면 이전 RAG 응답의 문맥이 다음 응답에 영향을 줄 수 있다.

문제는 두 경로에 모두 걸린다.

- `Docs` 모드 텍스트 질의
- `Live RAG`의 `search_docs` 툴 호출

두 경로 모두 결국 `AnythingLlmRagService.answer()`를 통해 AnythingLLM로 들어가므로, 세션 정책을 이 서비스 계층에서 통합하는 것이 가장 안전하다.

## Desired Behavior

- `Docs` 모드에서 질문할 때마다 새 AnythingLLM 세션으로 시작한다.
- `Live RAG`가 툴 호출을 수행할 때마다 새 AnythingLLM 세션으로 시작한다.
- 별도 토글은 두지 않는다.
- 기본 정책은 항상 새 세션이다.
- 일반 AI 대화 세션 동작은 바꾸지 않는다.
- `AnythingLLM` 서버 쪽 문서/임베딩/워크스페이스 데이터는 건드리지 않는다.

## Chosen Approach

추천 구현은 앱이 RAG 요청마다 새 `sessionId`를 생성해서 AnythingLLM API에 보내는 방식이다.

- `AnythingLlmSettings.alwaysNewSession`를 실제 요청 작성에 연결한다.
- `AnythingLlmChatRequest`에 `sessionId`와 `reset` 필드를 추가한다.
- `AnythingLlmRagService.answer()`가 `alwaysNewSession == true`일 때 매 호출마다 고유 `sessionId`를 생성한다.
- `ApiSettings.toAnythingLlmSettings()`에서 이 정책을 명시적으로 `true`로 고정한다.
- `ConversationViewModel`, `PhoneAIService`, `LiveRagToolAdapter`는 기존처럼 `toAnythingLlmSettings()`와 `AnythingLlmRagService`만 사용하게 두고, 세션 정책은 공통 서비스 계층에서 해결한다.

이 접근의 장점은 다음과 같다.

- 앱만 수정하면 된다.
- 이미 서버가 지원하는 `sessionId`/`reset` 계약을 활용한다.
- `Docs`와 `Live RAG`가 자동으로 같은 정책을 따른다.
- 이후 `twozeroone-1/anything-llm` 업데이트와 충돌할 가능성이 낮다.

## Rejected Alternatives

### 1. 같은 `sessionId`를 계속 쓰고 매번 `reset=true`만 보낸다

가능은 하지만 “새 세션으로 시작”이라는 요구를 표현하기에 덜 명확하다. 클라이언트가 reset 의미에 더 강하게 결합된다.

### 2. AnythingLLM 서버 fork를 수정해서 workspace chat을 강제로 무상태로 만든다

앱은 단순해질 수 있지만, 서버 커스텀이 늘고 이후 서버 업데이트 때 충돌이 커진다. 이번 요구는 앱 계층에서 충분히 해결 가능하므로 과하다.

## Implementation Shape

- 수정 중심 파일:
  - `phone-app/src/main/java/com/example/rokidphone/service/rag/AnythingLlmRagService.kt`
  - `phone-app/src/main/java/com/example/rokidphone/service/rag/network/AnythingLlmDtos.kt`
  - `phone-app/src/main/java/com/example/rokidphone/data/DocsAssistantModels.kt`
- 영향 확인 파일:
  - `phone-app/src/main/java/com/example/rokidphone/service/ai/LiveRagToolAdapter.kt`
  - `phone-app/src/main/java/com/example/rokidphone/service/PhoneAIService.kt`
  - `phone-app/src/main/java/com/example/rokidphone/viewmodel/ConversationViewModel.kt`
- 테스트 파일:
  - `phone-app/src/test/java/com/example/rokidphone/service/rag/AnythingLlmRagServiceTest.kt`
  - `phone-app/src/test/java/com/example/rokidphone/service/ai/LiveRagToolAdapterTest.kt`
  - 필요 시 새 매핑 테스트 파일 추가

## Testing

- `AnythingLlmRagServiceTest`에서 요청 body에 `sessionId`와 `reset`이 들어가는지 검증한다.
- `AnythingLlmRagServiceTest`에서 연속 두 번 `answer()` 호출 시 서로 다른 `sessionId`가 사용되는지 검증한다.
- `DocsAssistantModels` 매핑 테스트로 `toAnythingLlmSettings()`가 `alwaysNewSession = true`를 유지하는지 검증한다.
- 기존 `LiveRagToolAdapterTest`와 함께 RAG 서비스 계약이 깨지지 않았는지 확인한다.
- 수동 검증:
  - `Docs` 모드에서 연속 두 질문
  - `Live RAG`에서 연속 두 질문
  - 두 번째 답변이 첫 번째 답변 문맥 없이 독립적으로 나오는지 확인

## Risks

- 문서형 질의에서 “같은 흐름을 이어서 묻기”는 더 약해진다. 이번 요구에서는 의도된 변화다.
- AnythingLLM 쪽이 `sessionId` 없이도 내부적으로 어느 정도 격리해 주는 경우가 있을 수 있지만, 현재 사용자 체감 문제를 기준으로는 클라이언트가 세션을 명시적으로 분리하는 편이 더 안전하다.
