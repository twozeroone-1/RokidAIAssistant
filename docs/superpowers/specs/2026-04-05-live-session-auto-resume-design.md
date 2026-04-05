# Live Session Auto Resume Design

**Context**

현재 `Gemini Live` 세션은 약 10분 전후에 서버가 `goAway`를 보내고, 이후 `1008 / The operation was aborted.`로 WebSocket을 종료할 수 있다. 코드베이스에는 이미 `sessionResumptionUpdate` 수신, resumption handle 저장, `goAway` 알림, API 키 회전 로직이 존재한다. 하지만 자동 복구는 `liveLongSessionEnabled == true`일 때만 동작하며, 기본값이 `false`라서 실제 사용자 환경에서는 복구가 비활성화되기 쉽다.

**Goal**

`goAway -> 1008 abort` 흐름을 사용자가 별도 설정을 켜지 않아도 자동 복구한다. 이 변경은 기존의 `quota` / `invalid key` 분류 및 다중 API 키 fallback 동작을 깨지 않아야 한다.

## Desired Behavior

- 세션이 약 10분 수명 제한에 도달하면 `goAway`를 받는 즉시 저장된 resumption handle로 재연결을 시도한다.
- 만약 `goAway` 처리 타이밍을 놓치더라도, 직후 `1008 / The operation was aborted.`로 종료되면 같은 handle로 한 번 더 복구를 시도한다.
- 위 복구는 사용자 `Long session` 토글과 무관하게 동작한다.
- `quota`, `429`, `503`, `resource_exhausted`, `invalid key`는 기존처럼 session resume이 아니라 기존 failure policy를 따른다.
- 복구가 성공하면 `RECONNECTING -> ACTIVE`로 다시 진입하고, 안경 세션은 끊지 않는다.
- 복구가 실패하면 현재처럼 `ERROR`로 노출한다.

## Approach

### 1. Introduce a dedicated resumable-close classifier

- `The operation was aborted.`와 같은 서버 연결 수명 종료 계열 close reason을 별도 helper로 식별한다.
- 이 helper는 quota/invalid-key 분류와 분리한다.

### 2. Decouple session resumption from the UI long-session toggle

- 현재 `resumeSessionWithSavedHandle()`는 `liveLongSessionEnabled`가 꺼져 있으면 즉시 `false`를 반환한다.
- 이 조건을 제거하거나, 최소한 `goAway` 및 resumable close 경로에는 적용하지 않는다.
- 결과적으로 “자동 복구 capability”는 서버가 제공하는 handle이 있는지 여부에 의해 결정되고, 토글은 더 이상 필수 gate가 아니다.
- WebSocket setup에서는 `sessionResumption`을 기본으로 요청하고, `contextWindowCompression`만 기존 `Long session` 토글에 유지한다.

### 3. Recover on both warning and terminal close

- `goAway` 수신 시 기존처럼 resumption handle 기반 복구를 시도한다.
- 추가로, `onSessionFailure()`에서도 `1008 abort` 계열이면 저장된 handle로 복구를 시도한다.
- 이미 같은 handle로 재시도한 경우는 `lastAttemptedResumptionHandle`로 중복 방지한다.

### 4. Preserve key rotation semantics

- API 키 회전은 여전히 `INVALID_KEY` 또는 `QUOTA`일 때만 수행한다.
- `1008 abort`는 connection lifetime 계열로 보고 key rotation을 하지 않는다.

## Files

- `phone-app/src/main/java/com/example/rokidphone/service/ai/LiveSessionFailurePolicy.kt`
  - resumable-close classifier 추가
- `phone-app/src/main/java/com/example/rokidphone/service/ai/LiveSessionCoordinator.kt`
  - `goAway`/`1008 abort` 복구 정책 보강
- `phone-app/src/test/java/com/example/rokidphone/service/ai/`
  - coordinator/failure policy 테스트 추가 또는 확장

## Tests

- `The operation was aborted.`는 `quota`나 `invalid key`로 분류되지 않는다.
- `goAway`가 왔고 handle이 있을 때 `liveLongSessionEnabled = false`여도 resume이 시도된다.
- `1008 abort`가 발생하고 handle이 있을 때 resume이 시도된다.
- `quota` 에러는 여전히 key rotation 경로를 탄다.
- 같은 handle에 대해 중복 resume이 반복되지 않는다.

## Non-Goals

- UI에 새 토글 추가
- API 키 회전 정책 재설계
- 안경 쪽 live capture lifecycle 수정
- Live API provider 교체
