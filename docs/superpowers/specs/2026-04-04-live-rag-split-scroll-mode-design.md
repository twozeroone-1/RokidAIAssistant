# Live RAG Split Scroll Mode Design

**Context**

`Live RAG display mode = 실시간 답변 + RAG 결과 분할 표시`에서 오른쪽 `RAG 결과` 패널은 문서 길이에 따라 overflow가 자주 발생한다. 기존 auto-scroll은 속도 제어가 없고, 사용자가 직접 읽기 속도를 맞출 방법도 없다. 반면 기존 non-live pagination은 `DPAD up/down` 기반 수동 탐색 UX가 이미 익숙하다.

**Goal**

분할 표시 모드에서만 보이는 전용 설정을 추가해 사용자가 `자동 스크롤`과 `수동 스크롤` 중 하나를 선택할 수 있게 한다. 자동 모드에서는 final RAG 결과에만 느린 auto-scroll이 적용되고, 속도는 슬라이더로 조정한다. 수동 모드에서는 split live 상태에서 `DPAD up/down`이 오른쪽 `RAG 결과` 패널만 위아래로 움직인다.

## UX

### Settings

- 위치: 폰 `Settings`의 Live RAG 섹션
- 노출 조건: `liveRagEnabled == true` 이고 `liveRagDisplayMode == SPLIT_LIVE_AND_RAG`
- 항목 1: `RAG 결과 스크롤 방식`
  - `자동 스크롤`
  - `수동 스크롤`
- 항목 2: `자동 스크롤 속도`
  - `자동 스크롤` 선택 시에만 표시
  - 5단계 고정
  - `매우 느림 / 느림 / 보통 / 빠름 / 매우 빠름`

### Glasses Behavior

- 자동 모드:
  - 오른쪽 `RAG 결과` 패널만 auto-scroll 대상이다.
  - `LiveTranscriptPayload(role = RAG, isFinal = true)`를 받은 뒤에만 스크롤을 시작한다.
  - overflow가 없으면 스크롤은 동작하지 않는다.
  - 새 user turn, `DISPLAY_CLEAR`, live 종료, reconnect 초기화 시 스크롤 상태는 리셋된다.
- 수동 모드:
  - split live 화면에서만 `DPAD_UP / DPAD_DOWN`이 오른쪽 패널 스크롤에 우선 사용된다.
  - 기존 pagination 화면에서는 지금처럼 `previousPage()/nextPage()`를 유지한다.
  - left `실시간 답변` 패널은 여전히 고정이다.

## Data Model

`ApiSettings`에 아래 두 필드를 추가한다.

- `liveRagSplitScrollMode: LiveRagSplitScrollMode = AUTO`
- `liveRagAutoScrollSpeedLevel: Int = 2`

새 enum:

- `AUTO`
- `MANUAL`

속도 레벨은 정수 0..4로 저장한다. UI에서는 텍스트 라벨로 변환한다.

## Sync Strategy

폰이 안경에 이미 live 설정을 `LiveSessionControlPayload`로 보내고 있으므로, 이 payload에 아래 필드를 확장한다.

- `splitScrollMode`
- `autoScrollSpeedLevel`

폰은 아래 시점마다 동기화한다.

- live session start/end
- live 관련 설정 변경
- SPP reconnect 직후

안경은 payload 수신 시 UI state를 갱신해 split panel 동작에 즉시 반영한다.

## Glasses Implementation

### State

`GlassesUiState`에 아래 상태를 추가한다.

- `liveRagSplitScrollMode`
- `liveRagAutoScrollSpeedLevel`
- 기존 `liveRagIsFinal`
- 수동 스크롤용 현재 위치 상태는 Compose `ScrollState`로 유지하고 ViewModel에 영속화하지 않는다.

### Input Routing

`MainActivity.onKeyDown()`의 기존 우선순위는 아래처럼 수정한다.

1. paginated output이면 기존 `previousPage()/nextPage()`
2. split live + manual mode + right panel visible이면 right panel scroll
3. 그 외 기존 처리

이렇게 하면 기존 non-live 및 paginated UX를 유지하면서 split-manual만 추가된다.

### Auto Scroll

`SplitPanel`에 `autoScroll`과 `autoScrollSpeedLevel`을 주입한다.

- 오른쪽 패널만 활성화
- `withFrameNanos` 후 `scrollState.maxValue`를 읽는다
- `speedLevel -> durationMillis` 매핑으로 느린/빠른 정도를 제어한다
- recommended baseline:
  - 0: 18000ms
  - 1: 13000ms
  - 2: 9000ms
  - 3: 6500ms
  - 4: 4500ms
- 실제 duration은 `maxValue`도 반영해 너무 짧거나 너무 길지 않게 clamp한다

## Error Handling

- 구형 payload에서 새 필드가 없으면 `AUTO` + 기본 속도 레벨로 fallback
- manual mode인데 split panel이 비어 있으면 key input을 가로채지 않는다
- reconnect 중 state reset이 있어도 폰이 payload를 재전송하므로 최종 상태는 복원된다

## Tests

### Common

- `LiveSessionControlPayload` round-trip에 `splitScrollMode`, `autoScrollSpeedLevel` 포함
- 구형 payload fallback test

### Phone

- settings 저장/로드 round-trip
- split mode 관련 설정이 바뀌면 live payload 생성에 반영되는지 test

### Glasses

- split mode일 때만 panel content가 `autoScrollRightPanel`을 활성화
- `rag final + AUTO`일 때만 auto-scroll 활성
- `MANUAL`이면 final이어도 auto-scroll 비활성
- manual mode input routing이 paginated mode보다 뒤에 오고, split live에서만 켜지는지 test

## Non-Goals

- left `실시간 답변` 패널 auto-scroll
- split live 내부의 양쪽 동시 스크롤
- user-visible per-device separate settings
- 자유도 높은 arbitrary speed slider
