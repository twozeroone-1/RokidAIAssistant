# Live Phone Playback Route Design

**Context**

사용자는 `Realtime conversation`의 `휴대폰에서 재생` 경로를 쓸 때, 시스템 라우팅에만 맡기지 않고 휴대폰 스피커 강제를 선택할 수 있길 원한다. 현재 앱은 `실시간 출력 장치`로 파이프라인만 고르고, 폰 재생 시 실제 물리 출력은 안드로이드가 결정한다.

**Goal**

`실시간 출력 장치 = 휴대폰에서 재생`일 때만 보이는 하위 옵션을 추가한다. 첫 단계는 `시스템 기본`과 `휴대폰 스피커로 강제` 두 값만 지원한다. 스피커 강제는 best-effort로 적용하고 실패하면 자동으로 시스템 기본으로 폴백한다.

## UX

### Settings

- 위치: 폰 `Settings`의 `Realtime conversation`
- 상위 항목: `실시간 출력 장치`
  - `자동`
  - `안경 앱 재생`
  - `휴대폰에서 재생(시스템 출력 사용)`
  - `휴대폰 앱 + 안경`
- 하위 항목: `휴대폰 재생 경로`
  - 노출 조건: `liveOutputTarget == PHONE`
  - 선택값:
    - `시스템 기본`
    - `휴대폰 스피커로 강제 (실험적)`

### Behavior

- 상위 출력 장치가 `PHONE`이 아니면 하위 경로 설정은 UI에 숨긴다.
- `PHONE + 시스템 기본`은 현재 동작과 동일하다.
- `PHONE + 휴대폰 스피커로 강제`는 live 재생 시작 시 라우팅을 시도한다.
- 강제가 실패하거나 기기에서 지원하지 않으면 재생은 계속하고, 물리 출력만 시스템 기본으로 폴백한다.
- `GLASSES`, `AUTO`, `BOTH`에서는 하위 경로를 적용하지 않는다.

## Architecture

### Settings/Data

- `PhonePlaybackRoute` enum 추가
  - `SYSTEM_DEFAULT`
  - `PHONE_SPEAKER`
- `ApiSettings`에 `phonePlaybackRoute` 필드 추가
  - 기본값: `SYSTEM_DEFAULT`
- `SettingsRepository`에 저장/로드 키 추가
- 기존에 저장된 설정이 없으면:
  - `liveOutputTarget`: `PHONE`
  - `phonePlaybackRoute`: `SYSTEM_DEFAULT`

### UI

- `SettingsScreen`에 하위 row와 선택 dialog 추가
- `RealtimeConversationSettingsLayout`에 새 key 추가
- 노출 조건은 `settings.liveOutputTarget == LiveOutputTarget.PHONE`

### Live Session

- `LiveSessionConfig`와 `LiveSessionBaseConfig`에 `phonePlaybackRoute` 추가
- `LiveSessionCoordinator.sync()`는 출력 장치가 `PHONE`일 때만 설정값을 전달하고, 아니면 `SYSTEM_DEFAULT`로 정규화한다
- `GeminiLiveSession`은 값을 `LiveAudioManager`에 전달한다

### Audio Routing

- `LiveAudioManager`에 phone playback route 적용 로직 추가
- `PHONE_SPEAKER`일 때:
  - Android 12+에서는 `setCommunicationDevice(TYPE_BUILTIN_SPEAKER)`를 best-effort 시도
  - 그 외 버전에서는 `speakerphoneOn = true`를 best-effort 시도
- playback 종료 시:
  - 적용했던 라우팅을 해제하고, 더 이상 recording/playback이 없으면 audio mode를 `MODE_NORMAL`로 복원한다

## Why This Shape

- 사용자가 헷갈리던 `안경 앱 재생`과 `휴대폰에서 재생`의 차이는 유지하면서, 폰 재생 경로만 더 세밀하게 고를 수 있다.
- `안경 강제`까지 넣으면 `안경 앱 재생`과 의미가 겹쳐 UI가 더 혼란스러워진다.
- 라우팅 강제를 `PHONE`일 때만 적용하면 영향 범위를 최소화할 수 있다.

## Fallback Rules

- speaker 강제 실패는 live 세션 실패로 취급하지 않는다.
- 강제 실패 시 로그만 남기고 시스템 기본 재생을 계속한다.
- 상위 출력 장치를 바꾸면 하위 route는 저장은 유지하되 현재 세션 적용값은 재정규화한다.

## Tests

- `ApiSettings` 기본값 테스트
- `SettingsRepository` 저장/로드 및 미설정 fallback 테스트
- `LiveSessionCoordinator`가 `PHONE`일 때만 `phonePlaybackRoute`를 session config로 전달하는지 테스트
- `LiveAudioManager` 라우팅 정규화/적용 여부는 작은 정책 단위로 테스트

## Non-Goals

- `안경 강제` 라우팅 옵션 추가
- `BOTH`에서 phone route 세부 제어 노출
- 안경 앱 재생 파이프라인 변경
