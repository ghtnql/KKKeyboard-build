# ㅋㅋ키보드 앱 전체 테마 적용 — Codex 개발 전달서 v1

기준 브랜치: `migration/kmp-compose`
기준 확인 SHA: `6924e30833726f942ce2c9ab2531a9c9887986e7`
기준 UI 파일: `sharedUI/src/commonMain/kotlin/com/ghtnql/kkkeyboard/sharedui/KKKeyboardApp.kt`

## 목적
현재 테마 선택은 사실상 시스템 키보드의 스킨에 집중되어 있다. 이를 **앱 전체 시각 테마**로 확장한다.

출시 우선 적용 대상은 다음 3종이다.

1. `basic_dark` — 일반 다크모드
2. `seoul_day` — 서울 낮
3. `seoul_night` — 서울 밤

테마 선택 시 **공통 Compose 본체 앱 전체 + Android/iOS 시스템 키보드가 같은 테마 ID를 공유**해야 한다.

## 절대 규칙

- 현재 앱의 실제 화면 구조, 기능, 버튼 의미, 페이지 이동, 게임 로직을 바꾸지 않는다.
- 디자인 참고 이미지에 존재하는 가상의 하단 탭바, 가상의 채팅 화면, 신규 기능을 구현하지 않는다.
- 현재 `Page` 구조를 그대로 유지한다:
  `ONBOARDING, HOME, SETTINGS, THEMES, ADVANCED_SETTINGS, PHRASES, PHRASE_EDITOR, TEST, SELECT, PRACTICE, RAIN, CAFE, FINISHING, RESULT, PROGRESS`.
- `KKKeyboardApp.kt`의 실제 콘텐츠를 테마 스킨으로 감싼다. 화면을 새로 만드는 작업이 아니다.
- 서울 테마의 기존 광고 24시간 해금/적용 흐름을 깨지 않는다.
- 키보드 레이아웃, 키 높이, 후보, 상용구, 입력 엔진 로직은 시각 테마 작업 때문에 변경하지 않는다.
- iOS는 공통 Compose 본체 앱의 테마를 Android와 동일하게 사용한다. iOS Keyboard Extension은 기존 네이티브 shell을 유지하면서 동일 테마 ID/팔레트를 읽는다.
- 테마를 적용한 뒤 Android와 iOS가 외관상 같은 제품으로 느껴져야 한다.

## 현재 코드에서 반드시 제거할 하드코딩
`KKKeyboardApp.kt` 상단의 다음 고정 Light 색상 체계를 앱 전체 테마 스펙으로 교체한다.

- `HomeBackground`
- `HomeText`
- `HomeMuted`
- `HomeAccent`
- `HomeNeutral`
- 고정 `AppColors = lightColorScheme(...)`

테마 선택값에서 `AppThemeSpec`을 만들고 `MaterialTheme(colorScheme = ...)`와 공통 배경/카드/버튼/텍스트가 이를 사용하도록 한다.

## 구현 기준
`02_APP_THEME_TOKENS.json`이 숫자 기준(source of truth)이다.
`01_SCREEN_STYLE_MAP.md`가 실제 화면별 적용 범위다.
`03_ASSET_MANIFEST.json`이 기존 이미지 자산 사용 위치다.
`04_COMPONENT_SPEC.md`가 공통 Compose 컴포넌트 규칙이다.
`05_ACCEPTANCE_CHECKLIST.md`를 모두 통과하기 전 완료로 보고하지 않는다.

## 기존 시안 폴더와의 관계
이전에 생성된 `테마_디자인_개발전달_초안` 이미지는 **분위기/색감 참고용**이다.
해당 이미지에 그려진 화면 구조를 복제하지 않는다.
현재 실제 Compose 화면 구조가 항상 우선한다.

## 결과물
Codex가 이 명세를 적용한 뒤 사용자는 문서나 목업이 아니라 **Linux에서 빌드한 실제 Android APK**를 보고 판단한다.
따라서 디자인 완료 판단은 이미지 제작이 아니라 실제 앱 렌더링 결과로 한다.
