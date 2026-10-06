# KMP + Compose Multiplatform 전환 작업 계획

기준: `native-mvp-2026-09-23` (`66ee73e`), 작업 브랜치 `migration/kmp-compose`.
Drive `14_아키텍처 전환 및 Codex 재개 지시서`의 단계별 게이트를 따른다.

## 안전 기준점

- 중단된 21개 파일 변경은 `recovery/codex-interrupted-2026-09-23` (`9da9ae5`)과 `~/.codex/handoffs/KKKeyboard-recovery-2026-09-23/`에 보존했다. 승인 출처와 Android 사전 회귀 테스트 실패를 해결하기 전에는 이 콘텐츠를 전환 브랜치에 합치지 않는다.
- `main`과 원격은 `66ee73e`로 일치한다. 마지막 앱 배포는 `b72b0a4`의 Play internal / TestFlight 9.1이다. 전환 브랜치에서 같은 배포를 반복하지 않는다.

## 파일·모듈 전환 순서

| 단계 | 원본 및 새 위치 | 통과 조건 |
| --- | --- | --- |
| M1 core POC | `android/settings.gradle.kts`에서 `../sharedCore` 연결. `sharedCore/src/commonMain`, `commonTest`를 추가하고 Android 앱에 의존성만 연결. `ios/project.yml`과 iOS CI에서 App/Extension 둘 다 같은 작은 정적 framework를 링크. | Android 공통 테스트와 기존 앱 테스트·APK, iOS 앱/Extension 시뮬레이터 빌드와 unsigned archive. Extension의 `RequestsOpenAccess=false` 유지. |
| M2 순수 로직 | `android/app/.../HangulComposer.kt`, `CandidateInputBuffer.kt`, `JapaneseTransliterator.kt`, `LearningContent.kt`, `PracticeSession.kt`, `RainGame.kt`, `CafeGame.kt`에서 플랫폼 의존 없는 부분을 한 클래스씩 `sharedCore`로 이동. Swift의 `ios/Shared`, `ios/Learning` 구현은 공통 fixture parity 이후에만 제거. | 클래스별 `commonTest`, Android/iOS fixture 결과 일치, 회귀 테스트. |
| M3 공통 본체 | `sharedUI/src/commonMain`에 온보딩, 홈, 설정, 키보드 테스트, 기본/단문연습, Rain, Cafe, 점수/XP 화면을 구현. Android `MainActivity.kt`는 공통 UI 호스트로, iOS `ViewController.swift`는 Compose 호스트로 단계별 교체. OS 설정 이동·권한 상태만 얇은 bridge. | 같은 화면 코드가 양 플랫폼에서 실행되고 화면별 UI·동작을 실기기에서 확인. |
| M4 네이티브 키보드 | `android/app/.../KoreanKeyboardService.kt`와 `ios/KeyboardExtension/KeyboardViewController.swift`는 OS shell로 유지. 같은 sharedCore 입력 계약, 키 배열·후보·툴바·설정 의미를 공통 명세로 맞춘다. iOS 앱↔Extension 설정·상용구는 App Group 저장소로 공유. | 입력·삭제·후보·Space/Enter·설정·상용구 parity와 실제 Extension latency/메모리 검증. |
| M5 단문연습 | `shared/content/learning_items.json`의 같은 의미 문장을 `japaneseText`, `japaneseHangulPronunciation`, `koreanText`로 묶고 목표별 `copyText`/`expectedAnswer`를 둔다. | 세 줄 항상 표시, 일본어/한국어 목표 무작위 선택, 한글 음차 변환 경로 확인. |
| M6/M7 | 아래 기능표가 양 플랫폼 실기기에서 PASS한 뒤 중복 Android UI·Swift core 정리 및 `main` 통합. | Android/iPhone 실기기, Android 빌드, iOS archive, TestFlight 설치까지 확인. |

## iOS Keyboard Extension small POC 측정

1. XcodeGen의 App과 Extension이 `SharedCore` 정적 framework를 각각 링크하고 작은 한글 음절 조합 함수를 호출한다. 이 단계에서 기존 키보드 처리 경로를 대체하지 않는다.
2. framework 추가 전후 App/Extension 실행 파일 크기, archive 크기, Extension cold start와 입력 처리 시간, 피크 메모리를 같은 iPhone 조건에서 기록한다. CI의 크기와 archive 검사는 가능하지만 시작 시간·메모리는 실기기가 필요하다.
3. `RequestsOpenAccess=false`, 앱과 Extension의 서명·프로비저닝, unsigned archive를 확인한다. TestFlight 업로드는 명확한 QA checkpoint에서만 실행한다.
4. Extension이 메모리·시작 지연 조건을 충족하지 못하면 본체 앱의 KMP/Compose 결정과 분리해 네이티브 shell + 공유 fixture/spec로 남긴다.

## 기능 parity 게이트

| 기능 | Android | iOS | 완료 판정 |
| --- | --- | --- | --- |
| 앱 시작·온보딩·키보드 활성화 상태 | 공통 Compose 화면 연결 | 공통 Compose 화면·시뮬레이터 UI 테스트 통과 | 실기기 확인 대기 |
| 키보드 테스트, 한국어 직접 입력, 한글 음차→일본어 후보·확정 | 기준 구현 | 핵심 경로 구현 | 실기기 재검증 대기 |
| 숫자·기호·Space·Enter·Backspace, 상용구, 설정 | 키보드 기준 구현·공통 상용구 앱 UI 연결 | 키보드 기준 구현·공통 상용구 앱 UI 연결, App Group 읽기 경로 | 실기기 동작·설정 parity 대기 |
| 기본·단문연습, Rain, Cafe, 점수·XP | 기존 구현·공통 Compose 화면 연결 | 공통 Compose 화면·시뮬레이터 기본 경로 연결 | 전체 흐름·실기기 parity 대기 |
| 공통 데이터·fixture | JSON 공유·일부 core KMP | JSON 공유·일부 core KMP | 전체 엔진 parity 대기 |
| Android/iPhone 실기기, Android 빌드, iOS archive, TestFlight 설치 | 이전 checkpoint만 | 이전 checkpoint만 | migration 검증 대기 |

## 빌드·배포 운영

- Android APK/AAB·테스트·lint는 Linux 로컬에서 실행한다. 기존 `android.yml`은 `main` push에서 Play internal 업로드까지 이어지므로, migration을 합치기 전에 수동 배포로 분리한다.
- iOS/macOS 작업은 변경된 checkpoint에서만 CI로 실행한다. `ios.yml`은 migration PR 및 수동 실행에서 앱·확장 빌드와 unsigned archive/XCTest를 검증하며 배포하지 않는다. 단순 문서 수정은 macOS 빌드를 유발하지 않는다.
- 전체 전환은 위 기능표가 양 플랫폼 PASS하기 전 완료로 표기하지 않는다.

## 첫 M1 checkpoint 현황

- `sharedCore`는 Kotlin 2.4.20의 Android/iOS 정적 framework 목표로 생성했고, 공통 한글 음절 조합 함수 하나를 Android와 iOS 코드에 연결했다. Android 공통 테스트 1개와 앱 테스트 178개, debug APK와 lint가 통과했다.
- `ios/project.yml`은 App/Keyboard Extension/Tests에 동일한 framework 검색 경로와 Xcode 실행 스크립트를 둔다. [iOS CI #35821870724](https://github.com/ghtnql/KKKeyboard/actions/runs/35821870724)에서 XcodeGen, App/Extension 시뮬레이터 빌드, unsigned device archive, `RequestsOpenAccess=false`, Swift↔KMP XCTest를 포함한 48개 테스트가 통과했다. 시작 지연·메모리·크기 비교, 서명 archive와 iPhone 실기기 확인은 남아 있다.
- Linux에서 feedback 테스트는 실패 0(의도된 8개 제외), `assembleFeedback`은 성공했다. R8의 Kotlin 2.4 메타데이터 파싱 경고가 있어 테스터 APK 배포 전 축소 빌드를 기기에서 검증한다.
- `android.yml`의 자동 push/PR 트리거를 없애 Play 업로드가 migration PR에서 실행되지 않게 했다. `ios.yml`은 macOS 검증을 위해 PR 및 수동 트리거를 유지한다. TestFlight 업로드는 실행하지 않는다.

기술 선택 근거: [KMP 호환성](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html), [iOS 직접 통합](https://kotlinlang.org/docs/multiplatform-direct-integration.html), [Compose 호환성](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html).

## M3 공통 UI 진행 현황 (2026-09-23)

- `sharedUI/src/commonMain`에 온보딩·홈·설정·키보드 테스트·기본/단문연습·Rain·Cafe·결과/XP 화면과 공통 세션 상태를 구현했다. Android는 `ComposeMainActivity`를 런처로, iOS 앱은 `MainViewController` Compose 호스트로 연결했다. 기존 Android View 화면은 롤백 참고용으로 보존하고 iOS Keyboard Extension은 네이티브 shell과 `SharedCore`를 유지한다.
- Android와 iOS 앱은 출시용 `shared/content/learning_items.json`의 23개 항목을 각각 기존 파서로 읽어 공통 UI 모델에 전달한다. Android 학습 진행은 기존 `learning_progress` SharedPreferences 키를 재사용하고 iOS는 앱 `UserDefaults`에 저장한다.
- Compose Multiplatform 1.9.3, Kotlin 2.4.20, AGP 8.13.2, Gradle 8.13을 사용한다. 1.12.1은 Android SDK 37/AGP 9.1 이상을 요구해 현재 앱과 맞지 않았다. `sharedUI`는 iPhone 기기와 Apple Silicon/Intel 시뮬레이터 대상을 포함한다.
- 공통 세션 테스트 5개와 iOS 두 시뮬레이터 대상 KMP 컴파일이 통과했다. Android debug 테스트는 Compose 런처 smoke를 포함해 179/179, feedback 테스트는 실패 0/의도된 제외 8, 두 APK 생성, lint 오류 0/경고 34를 확인했다. 초기 macOS CI의 Compose 리소스 동기화와 `iosX64` 대상 문제를 수정한 [iOS CI #35868758900](https://github.com/ghtnql/KKKeyboard/actions/runs/35868758900)에서 App/Extension 시뮬레이터 빌드, unsigned device archive, `RequestsOpenAccess=false`, XCTest 48/48이 통과했다. [iOS CI #35870038266](https://github.com/ghtnql/KKKeyboard/actions/runs/35870038266)에서는 XCTest 48/48과 Compose 온보딩→홈 시뮬레이터 UI 테스트 1/1이 통과했다. 첨부 화면에서 상태 표시줄과 상단 제목이 겹치는 문제가 보여 수정 중이다.
- 실기기 UI·입력·접근성, iOS 키보드 확장 지연/메모리, 비한글 NFC 정규화, 화면 언어 설정, Android 기존 상세 설정과의 완전한 parity, M2 공통 엔진 이전, M4/M5, 서명 archive/TestFlight 설치가 남아 있다. M3 및 전체 전환은 아직 완료가 아니다.

## M2/M5 추가 진행 현황 (2026-09-27)

- `sharedCore`의 `CandidateInputTokenBuffer`가 Android/iOS 후보 입력 토큰 상태를 공통으로 관리한다. 플랫폼 adapter는 기존 호출 API를 유지하고 iOS의 빈 조합 문자열에서 이전 후보 접두를 버리는 안전 동작도 유지한다. `64dc502`에서는 두 플랫폼의 한글→가나 기본 변환 표를 `HangulKanaTransliterator`로 합쳤다. `f70ce75`에서는 공통 Compose 연습 세션의 문제별 정답 판정, 오답 거리, 콤보·점수·결과 계산을 `PracticeEngine`으로 옮겼다. Android 통합 검증에서 Core 11/11, 공통 UI 8/8, 앱 debug 180/180, APK 생성이 통과했다. [iOS CI #36274169963](https://github.com/ghtnql/KKKeyboard/actions/runs/36274169963)에서는 앱/확장 빌드, unsigned archive, XCTest 54/54, UI 3/3과 `sharedCore`/`sharedUI`의 iOS 시뮬레이터 Kotlin 테스트 태스크가 성공했다. 사전 후보 순위, 네이티브 게임 엔진 전체의 M2 이전은 남아 있다.
- 단문연습은 `japaneseText`, `japaneseHangulPronunciation`, `koreanText` 세 줄이 있는 항목만 공통 UI에서 사용한다. 현재 검증된 `こんにちは / 곤니치와 / 안녕하세요` 한 묶음에 세 줄을 추가했고, 매번 일본어 또는 한국어 복사 목표를 무작위로 선택한다. 기존 한국어 원문 10개는 일본어 문장/발음 검수 전까지 이전 흐름의 기준 데이터로 보존하며 공통 세 줄 단문연습에 넣지 않는다. 따라서 M5 콘텐츠 확장은 미완료다.
- 화면 캡처에서 확인된 iOS 상태 표시줄 겹침에 `WindowInsets.safeDrawing` 여백을 적용하고 Rain 화면 UI 테스트를 추가했다. Android 통합 검증은 Core 6/6, 공통 UI 7/7, 앱 debug 180/180, feedback 180개 중 의도된 8개 제외·실패 0, debug/feedback APK 생성, lint 종료 코드 0(오류 0, 경고 35, 힌트 1)이다. Lint 내부에서 Kotlin 2.4 메타데이터를 2.2로 읽으려는 진단 메시지가 출력되어 해당 검사의 신뢰도는 추가 확인이 필요하다.

## M4 상용구·설정 공유 진행 현황 (2026-09-27)

- 공통 Compose 설정 화면에 상용구 목록·추가·수정·삭제를 연결했다. Android는 기존 `UserPhraseStore`를 재사용한다. iOS 앱의 저장소 코드는 App Group에 상용구와 배치 설정을 쓸 수 있고 Keyboard Extension은 기존 로컬 항목을 보존하며 App Group 데이터를 읽는다. 동일 ID에서는 확장 로컬 수정이 우선한다. 앱의 기존 로컬 값은 공유 저장소에 한 번만 옮기고 최신 공유 값을 덮어쓰지 않는다. `abf0424`에서 공통 설정 화면의 가로·세로 높이와 숫자 행을 양 플랫폼 저장소에 연결했다. Android는 공통 UI 9/9, 앱 debug 180/180과 APK 생성이 통과했다. [iOS CI #36287066747](https://github.com/ghtnql/KKKeyboard/actions/runs/36287066747)에서 앱/확장 빌드, unsigned archive, XCTest 54/54, UI 3/3, 공통 Kotlin iOS 시뮬레이터 테스트가 통과했다. 설정 화면 캡처에서 두 방향의 높이/숫자 행 UI를 확인했다. 플랫폼별 추가 옵션과 실기기 설정 적용은 남아 있다.
- Apple의 [Custom Keyboard 접근 권한 문서](https://developer.apple.com/documentation/uikit/configuring-open-access-for-a-custom-keyboard)는 `RequestsOpenAccess=false`일 때 공유 컨테이너 읽기만 허용하고 쓰기를 금지한다. 따라서 확장에서 수정한 상용구/설정을 앱으로 양방향 동기화하는 것은 이 개인정보 조건 아래 불가능하다. App Group `group.com.ghtnql.kkkeyboard`를 두 App ID에 등록하고 두 배포 프로비저닝 프로파일을 갱신해야 서명된 빌드를 검증할 수 있다. 이 작업 전에는 App Group의 실기기 동작을 완료로 표시하지 않는다.
- 앞선 [iOS CI #36271698941](https://github.com/ghtnql/KKKeyboard/actions/runs/36271698941)은 Swift `map` 클로저의 빠진 `return` 때문에 앱 빌드에서 실패했다. 수정과 공통 상용구 화면을 포함한 [iOS CI #36272086669](https://github.com/ghtnql/KKKeyboard/actions/runs/36272086669)는 앱/확장 빌드, unsigned archive, UI 테스트 3/3이 통과했으나 새 설정 이전 XCTest가 실제 최소 표시 높이 238 대신 요청 높이 220을 기대해 전체 54개 중 1개가 실패했다. 기대값과 다음 높이 순서를 수정한 [후속 iOS CI #36272665475](https://github.com/ghtnql/KKKeyboard/actions/runs/36272665475)는 앱/확장 빌드, unsigned archive, XCTest 54/54, UI 테스트 3/3이 통과했다. Android 공통 UI 테스트와 컴파일, 상용구 저장소·Compose 런처 집중 테스트도 통과했다.
- 해당 시뮬레이터 캡처에서 상단 상태 표시줄 겹침이 사라진 것을 확인했다. Rain 단어 카드가 오른쪽 밖으로 잘리는 문제는 카드 실제 폭을 반영해 위치를 계산하도록 수정했다. Android 공통 UI 테스트와 앱 컴파일이 통과했다. [최신 iOS CI #36272866201](https://github.com/ghtnql/KKKeyboard/actions/runs/36272866201)에서도 앱/확장 빌드, unsigned archive, XCTest 54/54, UI 테스트 3/3이 성공했고 Rain 캡처에서 카드가 경기장 안에 표시됨을 확인했다. 이 결과는 시뮬레이터 검증이며 실기기 게이트를 대신하지 않는다.
- TestFlight 워크플로에 배포 전 두 프로비저닝 프로파일의 UUID, 팀, App ID, App Group 포함 여부를 확인하는 단계를 추가했다. 기존 프로파일은 App Group 도입 전 생성됐으므로 외부 등록과 갱신 여부를 확인하기 전에는 업로드 워크플로를 실행하지 않는다. 이 변경은 배포를 실행하지 않았다.
