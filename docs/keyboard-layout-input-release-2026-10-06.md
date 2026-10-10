# 2026-10-06 키보드 배열·언어 선택·커서 반복 수정

사용자 최종 지정: 천지인 플러스 셋째 행 왼쪽에 `!`, 하단 왼쪽부터 `[?][?123][한/日]`. 기존 Android 버튼 폭을 유지한다. 언어 키를 길게 눌러 한국어/일본어/영어 선택 후 손을 떼면 직접 전환하고, 방향키는 350ms 이후 50ms 간격으로 반복한다.

Android IME와 iOS 확장을 함께 수정했다. iOS 플릭은 Android의 4×5 배열과 폭 비율, 플러스는 4행·8칸 비율로 맞췄다. iOS 영어 QWERTY 및 두 기호 페이지(각 27키)도 Android와 같은 키 순서·비율로 구성했다. iOS의 다른 키보드·닫기 경로는 상단에 유지했다. 언어 선택은 저장되며 영어 사용 후에도 한글 자판 선호 설정을 유지한다. 반복은 손 떼기, 영역 이탈, 취소, 화면 종료와 자판 전환 시 중단하며 이전 손가락 이벤트가 새 반복을 취소하지 않는다.

## 현재 검증

Android 집중 테스트 81개 모두 통과(KeyboardView69, CheonjiinPlus4, HangulFlick5, KeyboardLanguage3). 실제 터치 언어 전환·반복 중지·저장·키 좌표 검사를 포함한다. Muse 실제 부분 편집과 좁힌 후속 작업을 통합했고 최종 정적 검토를 완료했다. 모델은 Meta `muse-spark-1.3-contributor`; 사용량 정보는 제공되지 않았다. iOS 실행37410275976에서 iPhone17Pro/iOS26.4.1 시뮬레이터 네이티브63개 모두 통과/실패·skip0. 플러스·플릭 렌더링 캡처를 직접 확인했다. 새 cursor/symbol 검사도 workflow 선택 목록에 포함했다.

기존 테스트 캔버스의 상단 키보드 그림은 실제 시스템 IME 위치 검증 근거가 아니다. API36 실제 에뮬레이터 입력 화면에서 바닥 배치, 297→298→299 업데이트, 한국어/일본어/영어 팝업 표시·실제 영어 전환·한국어 복귀 저장, 방향키 반복 후 중지를 확인했다. 휴대폰 실기기 검증은 아직 미완료다.

## 배포 범위

사용자가 수정 후 바로 스토어 배포를 명시 승인했다. 새 APK는 `apk_test_complete=false`, `release_approved=true`로 기록하고 로컬 APK 검증·공유 후 기존 Play 비공개 테스트 및 iOS 외부 TestFlight로 진행한다. 정식 프로덕션/App Store 출시는 범위에 없다. 공유폴더가 현재 기준 원본이다. 실제 버전·소스 SHA·해시·서비스 상태는 공유폴더 `13_개발 진행 현황.md`와 후속 완료 기록에 남긴다.

## 실제 화면 검증 후 보완

API36 실제 입력창에서 천지인/플러스가 화면 하단에 표시되고 지정된 !/? 배치를 확인했다. 길게 누르기 팝업은 기존 48dp 폭 때문에 언어 이름이 잘리는 문제를 발견했다. Android/iOS 모두 글자 폭+여백으로 팝업 폭을 계산해 한 줄로 표시하고 좁은 창에서는 글자 크기를 줄인다. 자판 버튼 크기와 단일 자모 팝업 글꼴은 유지한다. API29 이상은 화면 좌표 기반 팝업 배치를 명시한다([Android 공식 문서](https://developer.android.com/reference/android/widget/PopupWindow#setIsLaidOutInScreen(boolean))). API26~28 위치 검증은 미완료다.

첫 iOS 검사 실행37406188544는 테스트용 Probe.state 읽기 전용 재정의가 Xcode26.6의 읽기·쓰기 속성과 충돌해 업로드 전에 실패했다. Muse에 1파일만 맡겨 getter/setter 전달로 고쳤으며 실제 iOS 동작 실패와 구분한다. 팝업은 Muse 2파일 편집+Android 좌표 후속 편집을 통합했고, Kotlin에서 Java setter를 명시 호출하도록 총괄이 수정했다. 새 SHA로 필요한 네이티브 검사를 다시 실행한다.

Android298은 실제 비공개 트랙 completed 등록(실행37406637833)을 확인했다. 최종 팝업 수정 Android299도 실행37407722065에서 비공개 트랙 completed 등록 후 새 API edit으로 재확인했다. 298과 정확한 소스·해시를 구분한다. iOS21은 실행37410275976에서 서명 archive/export·운영 광고·리소스26개 검증 후 업로드 완료했다. Apple processingState=VALID, 기존 공개 테스트 그룹 연결 후 externalBuildState=IN_BETA_TESTING을 확인했다. 두 GitHub 저장소 private 복원도 확인했다.

## iOS 네이티브 검사 환경 진단 정정

실행37407408948은61개 중60개 통과하고 버튼 활성화1개가 실패했다. 최초에는 custom tapEvent 전달 문제로 해석하여 Muse의 명시 dispatch 변경을 a469b92에 통합했다. 후속 실행37408656899의 실제 진단은63개 중59개 통과/4개 실패였다. 버튼3개 실패 로그에 `UIApp is nil`이 명시되어 있었고, 다른1개는 Compose의 주 앱 Info.plist `CADisableMinimumFrameDurationOnPhone` 누락으로 비동기 충돌했다. 앞선 결과만으로 제품 버튼 버그를 확정한 판단을 정정한다.

검사가 앱 호스트 없이 실행되어 UIKit의 target-action 전달과 Compose의 앱 설정 검사 조건을 충족하지 못했다. 제품 버튼의 불필요한 명시 dispatch 변경은 UIKit 기본 전달로 복원하고, disabled custom tap 차단만 유지한다. 원래 실패 테스트와 추가된2개 테스트의 기대는 유지한다. Muse 최초2파일 작업은 변경 없이 실패했고 좁힌 project.yml 후속 작업은 성공했다. SharedUI가 static framework라는 검토 결과를 반영하여 전체 앱 대신 UIKit 전용 테스트 호스트로 후속 보완한다. 테스트 호스트에는 실제 UIApplication 진입과 필요한 plist 키를 제공하고 Kotlin/광고/AppGroup은 중복 적재하지 않는다. XCTest target의 호스트 의존성과 TEST_HOST/BUNDLE_LOADER를 연결한다([XcodeGen 프로젝트 설정](https://github.com/yonaskolb/XcodeGen/blob/master/Docs/ProjectSpec.md)). 새 네이티브 검사 통과 여부는 실행 결과로 확인한다.

이 후속 변경은 iOS 및 문서에만 해당한다. 완료 Android299의 실제 소스3ac466edb1de54e7ef73765d1811bc26e9b916a6와 후속 iOS 소스는 구분하고 Android/shared diff가 없는 것을 검증한다. Android299를 다시 업로드하지 않는다. 앞선 모든 iOS 실패는 archive/upload 이전이며 이전 TestFlight20을 새21로 보고하지 않는다.

## 최종 빌드·검증 근거

Android299 실제 바이너리 소스3ac466edb1de54e7ef73765d1811bc26e9b916a6, iOS21 실제 앱 소스d0d5726deab46c4b63985871c4028e63d8ef6ddd(snapshot e366df16b709b5064680fc0e6a7256894e2d8c28). 후속 iOS만 변경하여 Android/shared diff 없음. Android 전체 집중81개 및 팝업 최종69개 통과, iOS63개 통과. iOS 앱·확장 버전1.0/21 일치, AppGroup 포함 서명 검사 및 source 일치 리소스26개·test_ads=false 확인. 테스트 호스트는 배포 앱에 포함하지 않는다.

Android APK/AAB는 공유폴더 고정 이름299-3ac466e로 보관하고 전체 readback 크기·SHA-256 검증했다. APK signer093b3e4d55d2d87f49d979e1de764eeb60783fc903de0fac5b94b061e0a2e5c3, AAB upload certificate be76fbc0b3f9877826edd8fbcd7ff1d14600ad30ba195b492685ea1f15868e46 연속성 유지. iOS IPA는 표준 helper 원격 서명·검증·업로드로 처리했고 Linux 별도 IPA 보관은 하지 않았다.

확인 한계: 새299/21의 휴대폰 실기기 설치·터치·AppGroup 연결·실제 광고 보상은 이번에 검증하지 않았다. iOS 렌더링 캡처는 테스트 자판 자체이며 전체 시스템 IME 하단 위치 확인으로 대신하지 않는다. iOS 상단은 다른 키보드/닫기 경로를 유지하므로 Android와 전체 화면 픽셀 동일성을 주장하지 않는다. API26~28 Android 팝업 위치도 미검증이다.

## 배포 완료 상태

- Android299: Play 비공개 트랙 `ㅋㅋ키보드`, `v299 keyboard input release`, `completed`, [실행37407722065](https://github.com/ghtnql/KKKeyboard/actions/runs/37407722065). APK `APK 배포/2026-10-06_KKKeyboard-v299-3ac466e-debug.apk` 38,732,891bytes SHA-256 `6ce08b3b66b7b594c1c7bddc7e18e027d9ee5dca896ac0a0a0278044878fee03`. AAB `스토어 배포/2026-10-06_KKKeyboard-v299-3ac466e.aab` 32,972,300bytes SHA-256 `1ebd7788b434fc85e3ba4fbf3b29db017d347b46f1731067860a2826d8f7a6ab`.
- iOS1.0/21: [실행37410275976](https://github.com/ghtnql/KKKeyboard-build/actions/runs/37410275976) success, 업로드 후 Apple `VALID`, 기존 외부 `공개 테스트` 연결, 마지막 독립 GET 결과 `IN_BETA_TESTING`. 제출 응답의 최초 WAITING_FOR_REVIEW와 최종 외부 상태를 구분했다. 메타데이터 `스토어 배포/2026-10-06_iOS21-d0d5726.release.json`.
- 원본/빌드 GitHub 저장소 `private=true` 재확인. 출시 채널은 기존 테스트 채널이며 production/App Store 정식 출시를 진행한 기록이 아니다. 새 휴대폰 테스트 완료는 주장하지 않는다.
- 공유폴더 진척/선별 검증 JSON/실제 Android 시스템 캡처/iOS 테스트 자판 캡처를 전체 readback 검증했다. iOS 네이티브 테스트 호스트 수정은 Muse의 실제 첫 실패·범위 축소 성공·static runtime 분리 후속 성공을 검토·통합했다. 사용량/비용은 측정되지 않았다.
