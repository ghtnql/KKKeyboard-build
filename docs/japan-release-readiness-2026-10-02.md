# 한국·일본 출시 준비 — 2026-10-02

## 2026-10-02 최신 결정: 광고 전용 출시

이번 출시는 본체 앱/게임 광고만 제공한다. 인앱결제/유료 상품은 제외하고 구매·가격·복원·환불 UI 및 판매 문구를 제거한다. 아래 구매 구현/가격/SKU 준비는 이전 작업 이력이며 현재 출시의 필수 항목이 아니다. 앱의 광고 개인정보 동의 및 선택은 유지하고 키보드 확장에는 광고 SDK·표시·구매/광고 시청 유도 UI를 두지 않는다. 코드 검증과 실제 새 바이너리 배포는 구분한다. Play278/TestFlight17/APK279는 수정 전 배포본이다.

현재 진행 중. 사업자등록·통신판매업 준비 외의 코드·자료·검증을 정비하는 작업이며, 스토어 심사·실기기·일본 사용자 검증을 완료한 것으로 표시하지 않는다.

## 요청과 완료 기준

- LOC-JP-002: 일본어 시스템 언어 첫 키보드 사용은 일본어 변환; 사용자의 이후 입력 모드 선택은 재시작 후 유지. 양 플랫폼 회귀 근거 필요.
- iPhone 플릭: 공유 설정에서 한글 플릭 선택 → 저장 → 설정 닫기 → 실제 플릭 배열 렌더 및 입력 → 확장 재시작에도 선택 유지. 소스 연결, 자동 렌더, 실제 iPhone을 구분한다.
- LOC-JP-001: 공통 본체/확장 설정과 네이티브 자판의 UI 일본어 제공. 한글 학습 내용·발음·한국어 언어명·사용자 상용구는 학습/사용자 데이터로 유지한다.
- 구매: 본체 앱의 1회 비소모성 광고 제거, 한국 기준 예정가 3,900원. 실제 가격은 스토어 조회값 사용. 구매 보류·취소·오류에서 권한을 부여하지 않음. 구매 복원, 재실행 재조회, 환불/취소 후 권한 회수. 키보드 확장에는 결제 SDK/구매 버튼 없음.
- 광고: 본체 전면/보상 광고 모두 구매 권한으로 생략; 관련 테마는 광고 없이 이용. UMP 동의 확인과 광고 개인정보 선택 진입점. 확장에는 광고 SDK/네트워크 없음.
- 출시 자료: 한국/일본 스토어 설명·구매/환불 안내·개인정보·데이터 공개를 실제 SDK와 일치하도록 작성. 언어별 스크린샷은 실제 렌더 사용.
- 콘텐츠: 기존 563개/단문 211항목 보존. review_required를 사용자/원어민 검수 완료로 바꾸지 않음. 저작권 위험과 원어민 검수 기록을 구분.
- REL-JP-001: 한국/일본 같은 최초 출시 버전으로 availability/스토어 metadata 확인. 실제 스토어 조회·등록·심사 여부를 별도 기록.

## 현재 확인

- 소스 시작점 migration/kmp-compose 63f9532. 9월 QA 체크리스트에 LOC-JP-001/002, STORE-JP-001 등이 TODO인 것을 확인했다.
- 일본어 기기 기본 입력과 이후 선택 저장을 Android/iOS 모두 수정. Android 실제 서비스 회귀 7건 통과, iOS Swift 회귀는 배포 workflow에서 실행 예정.
- iOS 한글 플릭은 공통 설정 enum, setInputLayout, KeyboardLayoutSettings의 hangul_flick, reloadInputLayout의 flickRows 경로가 있다. 이번 실기기 실행은 미검증.
- 브라우저 Play Console 접근은 로그인된 Console 대신 안내 사이트로 이동했다. 이 브라우저로 현재 판매자 인증/상품/스토어 공개 상태를 확인한 상태가 아니다.
- 최신 전달 APK v22 사용자 테스트 대기. 사용자가 2026-10-02 “그냥 스토어에 바로 올려라”로 이번 배포의 APK 대기 순서를 명시적으로 변경. 테스트 완료로 표시하지 않고 현재 소스를 Play 기존 비공개 트랙/iOS 외부 TestFlight에 직접 올린다.

## 실제 사람이 확인해야 하는 항목

- iPhone/Android 실제 입력·플릭·연속 터치·햅틱·오디오 및 기기 변경 구매 복원.
- 일본 사용자 소수 베타 및 원어민 최종 문구/발음 검수: REL-004, REL-008을 근거 없이 PASS로 바꾸지 않음.
- 판매자 인증, 운영 광고 ID·AdMob 메시지 구성, 스토어 상품 활성화/지역별 가격, 개인정보 공개 응답과 스토어 심사. 실제 확인 전 완료로 말하지 않음.

## 통합 검증과 운영 설정 상태

- Android/공통 자동 검사 329건(공통 Core13, UI31, 본체285) 실패/오류/건너뜀 0. 콘텐츠 변환 회귀8건 통과. Kotlin iOS SimulatorArm64 컴파일 통과. Swift native 테스트와 배포 검증은 별도다.
- 실제 일본어 공통 화면과 광고 제거 구매 보류/복원 화면의 Android 렌더를 확인. 결제 구매·복원 UI, Play Billing9.1.0, StoreKit2, UMP consent 및 구매자 광고 생략을 양 플랫폼에 연결. 실결제·환불 미검증.
- 정책 전용 공개 저장소 ghtnql/KKKeyboard-policy에 한국/일본 개인정보·환불 페이지 배포. HTTP200 및 저장소 정책파일과 응답 전체 바이트 일치 확인. 앱 링크 교체.
- Play API 실제 조회: 기존 비공개 트랙 ㅋㅋ키보드 v277 completed, 최대277, 한국어 listing만, oneTimeProducts0개. Android 검증용 공개 RSA 키 미설정. 따라서 출시 코드에 상품 조회가 실패하면 구매 버튼을 활성화하지 않는다. 3,900원 상품이 실제 판매 중인 상태가 아니다. 운영 광고 ID/스토어 개인정보 설문/판매자 확인도 별도다.
- iOS bundle 검증기에서 학습 JSON, 사전 JSON, 9음원까지 정확한 소스 hash 검사 추가. 정상 샘플26자산 통과, 학습/사전/음원 누락 및 변조 각각 거부 확인. 실 export bundle 검증은 macOS 배포 시 실행한다.

## 실제 배포 결과 — 2026-10-02 10:19KST

- Google Play 기존 비공개 트랙 ㅋㅋ키보드278 completed, KO/JA설명과 실제JA화면3장 등록·readback확인. sourceb0ec590193577fbd8b763b4e2fbf4513f08d6a1d. 프로덕션공개출시 아님.
- iOS firstattempt Swift콜백타입fail을 실제generated SharedUI.h void (^)(void) 근거로 Muse수정. source876e17502c0a2b074029b1bc141dd7b9c9a7c28a(snapshot92bf92f9b5a1f8e920be6c996d96c0cf77881ac8), TestFlight1.0(17). 두Swift파일외 Android/common/assets불변.
- iPhone17Pro Simulator(iOS26.4.1) nativeXCTest41 PASS/실패0/skip0. Flick3검사 실제adapter선택·닫기·재진입·재생성/12키배열 및 실제PNG확인. physicaliPhone진동/타감/오디오검증별도.
- signed app/extensionbuild17/package/AppGroup, exported26assets hash exact source검사 PASS. Uploadsuccess; AppleVALID expiredfalse. 기존외부그룹 공개 테스트 연결readback 및 beta submission 수행후 최종externalBuildState IN_BETA_TESTING. betaReviewState submissionresponseWAITING_FOR_REVIEW는이력이며 별도로승인완료라고표시하지않음.
- finalrun https://github.com/ghtnql/KKKeyboard-build/actions/runs/36947925389 success. source/빌드repoPRIVATE독립확인. 일회성Playworkflow/ephemeralrunner제거확인. APKtested=false(사용자직접배포예외).
- 아직 SKU/Androidpurchasepublickey/productionAdMob설정 및 Console판매자/개인정보/국가확인, 실제결제/환불, 기존paritygap과원어민콘텐츠검수는미완료. 테스트배포를사업자서류외모든출시요건완료로해석하지않는다.
