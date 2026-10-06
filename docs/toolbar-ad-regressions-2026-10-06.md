# 2026-10-06 상단 버튼·광고 회귀 수정

사용자 iPhone 상단 버튼에서 다른 키보드로 전환되는 증상·광고 누락, Android Chrome 입력창에서 상용구/자판 메뉴가 간헐적으로 키보드를 닫는 증상을 함께 처리한다.

Android 제공 영상의 고해상도 시간 분할에서 상용구 팝업을 열자 입력창이 확대되고 키보드가 내려가는 장면을 확인했다. 포커스 가능한 PopupMenu를 포커스를 가져가지 않는 KeyboardToolbarMenu로 교체했다. 메뉴는 IME 영역에 고정하며 웹 입력창의 anchor scroll 요청을 피한다. 상용구는 선택 시 최신 저장 내용을 ID로 다시 읽고, 자판 선택은 기존 저장·조합 경로를 유지한다. 취소·뒤로가기·다른 메뉴 열기·자판 재생성·입력 종료에서 메뉴를 정리한다. 키 배열/크기는 바꾸지 않는다.

iOS 상단 버튼의 exclusive touch 보호를 복원하되 문자 입력의 기존 다중 터치는 유지한다. 설정/테마는 키보드 확장 안에서 Compose 화면을 열지만 실제 확장 Info.plist에 필수 CADisableMinimumFrameDurationOnPhone가 없었다. 앱과 테스트 호스트에만 있던 값을 확장에도 추가하고, 실제 export 앱/확장 각각의 값이 정확한 boolean true인지 배포 검사에 추가했다. 이 누락은 확장 종료와 OS 대체 키보드 전환을 일으킬 수 있는 코드 결함이며, 사용자 휴대폰 crash 로그로 원인이 확정된 것은 아니다.

iOS 앱은 AppDelegate.window를 사용하는데 광고 동의/표시 컨트롤러는 scene 창만 찾았다. UIKit 공통 해석기를 추가해 활성 scene 창 우선, 실제 legacy 창 대체 경로를 연결한다. 비활성/숨겨진/분리된 창·전환 중 화면을 거부한다. 동의 SDK 갱신은 실행당 1회, 비동기 완료 뒤 현재 창을 다시 찾고 필요한 동의 양식만 재시도한다. 동의 후 게임 광고를 준비하며, 오래된 로딩 콜백이 새 요청 상태를 변경하지 않게 유지한다. 운영 광고 ID, SDK 동의 판정, 보상과 빈도 정책은 유지하고 확장에 광고 SDK를 넣지 않는다. RequestsOpenAccess=false 유지.

검사: Android KeyboardViewTest 71개 통과(실패/오류/skip 0). 상용구 삽입·조합·자판 저장·비포커스 메뉴 설정·뒤로가기·화면 종료 정리를 검사했다. export 검사 Python 3개 통과: 양쪽 플래그 정상 허용, 확장 누락/false/문자열/정수 거부, 앱 누락 거부. iOS UIKit 9개 새 검사(상단 버튼 4, 앱 창 5)를 포함한 최종 72개가 정식 macOS/iPhone17Pro iOS26.4.1 시뮬레이터에서 전부 통과했다. Android 실제 Chrome IME 검증도 완료했다. 실제 휴대폰 광고 노출로 보고하지 않는다.

위임: Meta muse-spark-1.3-contributor 광고 검토 성공, 편집 범위를 helper/동의/광고 컨트롤러로 줄인 3회에서 실제 부분 작업을 유지하고 최종 컨트롤러 편집 성공. Android 검토 실패 후 한 파일 메뉴 편집 성공. iOS 상단 버튼 3회는 보호 설정 한 줄만 남겼고 새 검사는 완성하지 못해 해당 테스트 파일만 gpt-6.1-sol로 넘겨 통합했다. 총괄이 실제 diff·수정·검사를 통합했다. 모델 사용량/비용 데이터 없음.

새 APK/테스트 채널 버전·소스·해시·실제 결과는 확인 후 공유 진척 문서와 배포 메타데이터에 기록한다. 사용자 휴대폰 재검사, 실제 iOS 광고/보상, API26~28 팝업 위치는 아직 확인하지 않았다.

## 배포 검증 결과

Android300 b2064f6b9336f50e0a61031e73c55661027098f8: 로컬 APK/AAB 패키지·기존 서명·bundletool·CRC·DEX 운영 광고·소스 자산·공유 전체 읽기 검사 완료. 실제 Chrome textarea에서 상용구 메뉴·문구 こんにちは 삽입·Plus→Flick 선택 후 같은 입력창과 키보드 표시 유지 확인(제공 영상의 경우와 같은 메뉴 진입). Play 기존 비공개 ㅋㅋ키보드 실행37433537986 success, 새 API edit 읽기 versionCode300 completed. APK 공유 `APK 배포/2026-10-06_KKKeyboard-v300-b2064f6-debug.apk`, SHA4e97bcbc7725b6656b14398337cdf0823c859dcebe2bf4224c947113bd87d06e. AAB 공유 `스토어 배포/2026-10-06_KKKeyboard-v300-b2064f6.aab`, SHA5cf1aae211dbfad2313b1e4b959e865a097c120eaacb1b57d1391ea9c794b45a.

첫 Android 빌드는 parallel-debug 옵션 누락을 패키지 검사가 거부해 배포하지 않았고 .dev 옵션으로 재빌드했다. 공유검증 전에 조기 시작된 iOS37413956686을 setup 중 취소·private 복원했다. 이후 올바른 APK 공유를 확인한 iOS37414132513은 실제72개 중70통과/2실패로 archive/upload 없이 종료됐다. 실패 두 개는 새 UIKit 테스트의 .allEvents 이벤트 합집합 조회와 visible UIWindow 자동 scene 연결 가정이었다. 제품 변경 없이 테스트 두 파일만 c890e86에서 실제 등록 이벤트/legacy fixture로 바로잡았다. 생산 코드는 b206과 동일해 Android 재빌드하지 않았다. 표준 clean-worktree helper iOS37433647654에서 재검증을 통과하고 아래와 같이 배포를 완료했다. 실패 이력과 전체 native summary는 공유 기록에 보존한다.

최종 iOS37433647654 source c890e867a9af595e071d7c0d2bf180af3a63f580 / snapshot eaf58e16b73f09b5480646650558ef22e182819e: native72/72 PASS, 실패/skip0. 앱·확장1.0/22 일치, 실제 export 양쪽 Compose 필수 플래그 true, 서명·AppGroup 검사 및 26개 자산·운영 광고(test_ads=false) 검사 후 TestFlight 업로드 성공. 최종 Plus/Flick 시뮬레이터 캡처 직접 확인. Apple processingState=VALID, 기존 외부 공개 테스트 그룹 연결과 재조회 확인. 첫 베타 심사 제출 WAITING_FOR_REVIEW 응답 뒤 마지막 GET externalBuildState=IN_BETA_TESTING으로 외부 테스터 이용 가능 확인. 원본·빌드 저장소 모두 private=true 복원 확인. Linux IPA 별도 보관은 하지 않았다.

최종 공유 APK/AAB 전체 파일을 다시 읽어 크기·SHA-256 일치 확인, 원래 사용자 미커밋 5개 파일 해시 유지. 새 휴대폰 설치·실제 iOS 광고/보상·AppGroup 실사용 연결, Android API26~28 팝업 위치는 미검증이다. Android 테스트와 iOS 시뮬레이터 테스트는 각각의 근거이며 전체 기능/픽셀 동등성이나 실기기 광고 노출 완료를 선언하지 않는다.
