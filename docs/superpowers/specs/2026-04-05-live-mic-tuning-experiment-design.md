# Live Mic Tuning Experiment Design

**Context**

사용자는 `Gemini Live`에서 안경 입력을 사용할 때 작은 목소리도 더 잘 인식되길 원한다. Rokid 문서에는 `changeAudioSceneId(audioSceneId, callback)`가 있고 값은 `0=근거리`, `1=원거리`, `2=파노라마`로 설명된다. 하지만 현재 프로젝트의 live 입력은 CXR 오디오 스트림이 아니라 안경 앱의 `AudioRecord(MIC)` 기반 커스텀 캡처를 사용한다.

**Goal**

사용자가 폰 설정에서 실험 기능을 켜고 `0/1/2` 프로파일을 선택할 수 있게 한다. 이 설정은 `Gemini Live + 안경 입력`일 때만 반영한다. 작동하지 않거나 SDK 호출이 실패해도 현재 live 경로가 깨지지 않도록 폴백한다.

## UX

### Settings

- 위치: 폰 `Settings`의 `Realtime conversation` 섹션
- 항목 1: `Experimental live mic tuning`
  - off: 현재 동작 유지
  - on: 아래 프로파일 선택 항목 노출
- 항목 2: `Live mic profile`
  - `0 - Near field`
  - `1 - Far field`
  - `2 - Panorama`
- 적용 범위:
  - `liveModeEnabled == true`
  - 실제 live 입력 소스가 `GLASSES`일 때만 의미가 있다

### Behavior

- 실험 기능이 꺼져 있으면 현재 코드 경로를 그대로 유지한다.
- 실험 기능이 켜져 있으면 phone side에서 live 세션 시작/동기화 시 선택한 프로파일을 안경으로 전달한다.
- glasses side live 캡처는 선택된 프로파일에 맞는 실험용 오디오 소스를 먼저 시도한다.
- 초기화 실패 또는 녹음 실패 시 즉시 기존 `MIC` 경로로 폴백한다.
- 일반 녹음과 웨이크워드는 이번 변경 범위에서 제외한다.

## Architecture

### Phone

- `ApiSettings`에 아래 필드를 추가한다.
  - `experimentalLiveMicTuningEnabled: Boolean = false`
  - `experimentalLiveMicProfile: Int = 0`
- `SettingsRepository`에 저장/로드 키를 추가한다.
- `SettingsScreen`에 토글과 프로파일 선택 UI를 추가한다.
- `LiveSessionControlPayload`에 두 필드를 추가해 live 세션 상태와 함께 안경으로 전달한다.

### Common

- `LiveSessionControlPayload`는 구버전 payload와 호환되도록 새 필드가 없으면 기본값으로 파싱한다.

### Glasses

- `GlassesViewModel`의 live session state에 실험 플래그와 프로파일을 저장한다.
- `startLiveInputCapture()`에서 다음 우선순위를 사용한다.
  - 실험 off: 기존 `MIC`
  - 실험 on + profile 0/1/2: 프로파일별 실험 소스 시도
  - 실패 시 기존 `MIC`
- 이번 단계에서는 `changeAudioSceneId`가 실제 시스템 입력에 영향을 주는지 보장할 수 없으므로, live 캡처 측에만 한정된 best-effort 실험으로 구현한다.

## Why This Shape

- 현재 프로젝트는 이미 `Gemini Live` 설정 동기화 경로와 `LiveSessionControlPayload`를 갖고 있어 UI 값 전달 자체는 작게 끝낼 수 있다.
- 리스크가 큰 부분은 오디오 캡처다. 그래서 일반 녹음/웨이크워드/CXR full pipeline 이전은 제외하고, `Gemini Live` live 캡처 한 지점만 바꾼다.
- 사용자가 쉽게 끌 수 있도록 토글을 기본 off로 둔다.

## Fallback Rules

- settings payload에 새 필드가 없어도 glasses는 기본값으로 처리한다.
- 실험용 AudioRecord 초기화가 실패하면 로그를 남기고 기존 `MIC`로 다시 시도한다.
- capture 시작 후 read loop에서 예외가 나면 기존 종료 처리와 동일하게 정리한다.
- 실험 기능이 켜져 있어도 live 입력 소스가 `PHONE`이면 아무 일도 하지 않는다.

## Tests

### Common

- `LiveSessionControlPayload` round-trip에 실험 플래그와 프로파일이 포함되는지 확인
- 구버전 payload fallback 확인

### Phone

- `ApiSettings` 기본값/범위 보정 확인
- `SettingsRepository` 저장/로드 round-trip 확인

### Glasses

- payload 적용 시 실험 상태가 UI state에 반영되는지 확인
- 실험 off/on에 따라 live capture source resolution helper가 올바른 값을 주는지 확인

## Non-Goals

- 웨이크워드 민감도 개선
- 일반 녹음 경로 변경
- CXR `openAudioRecord + setAudioStreamListener`로의 정식 이전
- Rokid SDK 오디오 씬 호출의 효과를 이번 단계에서 강제 보장하기
