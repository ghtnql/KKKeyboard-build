# App Store 개인정보 답변 — iOS build 27

답변 자료 준비 완료. App Store Connect 웹에서 `게시`한 상태와 구분한다. 현재 관리자 브라우저 로그인이 없어 아직 게시하지 못했다.

## 근거

- 실제 build27 패키지: GoogleMobileAds13.11.0, GoogleUserMessagingPlatform3.1.0. 업로드 실행37717181583의 resolved package 로그 확인.
- Google SDK13.11.0 원본 ZIP SHA256: `310516d18f0d600c9e45ed42b955a6b8ec52108d380f7cd8ed1e424e9d3fec22`. Google Swift Package checksum과 일치.
- Google SDK/UMP의 실제 `PrivacyInfo.xcprivacy` 수집 유형·Linked·Tracking·Purpose 선언을 합친 아래 표를 사용한다. 동일 항목의 목적은 합집합, Linked/Tracking은 어느 SDK든 true이면 true로 기재한다.
- 앱은 운영 광고를 제공하고 UMP 동의 후 광고 요청을 한다. IDFA를 읽는 자체 코드나 ATT 요청은 없으며 이를 근거로 광고 SDK의 추적 선언을 임의 삭제하지 않는다. SDK 선언대로 `기기 ID` 추적을 공개한다. 광고 동의와 ATT 허용은 서로 다르다.
- 본체/확장은 사용자 입력·학습 답안·클립보드 텍스트를 자체 서버에 보내지 않는다. UserDefaults/App Group의 로컬 저장은 수집 신고가 아니다. 키보드 확장에는 광고 SDK가 없다.
- 공식 설명: https://developers.google.com/admob/ios/privacy/data-disclosure
- Apple 게시 절차: https://developer.apple.com/help/app-store-connect/manage-app-information/manage-app-privacy

## 관리자 입력 순서

App Store Connect → ㅋㅋ키보드 → 앱 개인정보 → 시작/편집 → 개발자 또는 타사 파트너가 데이터를 수집합니까? **예**.

데이터 유형은 아래 7개다. 표의 사용 목적·사용자 연결·추적을 각 항목 상세 화면에 입력한다. 아래에 없는 개인정보 유형은 앱 자체 수집 기능이 없으므로 선택하지 않는다.

| 데이터 유형 | 사용 목적 | 사용자와 연결 | 추적에 사용 |
|---|---|---|---|
| 위치 → 대략적인 위치 | 타사 광고, 개발자 광고 또는 마케팅, 분석, 앱 기능 | 예 | 아니요 |
| 식별자 → 기기 ID | 타사 광고, 개발자 광고 또는 마케팅, 분석 | 예 | 예 |
| 사용 데이터 → 제품 상호 작용 | 타사 광고, 개발자 광고 또는 마케팅, 분석, 앱 기능 | 예 | 아니요 |
| 사용 데이터 → 광고 데이터 | 타사 광고, 개발자 광고 또는 마케팅, 분석 | 예 | 아니요 |
| 진단 → 충돌 데이터 | 분석 | 아니요 | 아니요 |
| 진단 → 성능 데이터 | 타사 광고, 개발자 광고 또는 마케팅, 분석, 앱 기능 | 아니요 | 아니요 |
| 진단 → 기타 진단 데이터 | 타사 광고, 개발자 광고 또는 마케팅, 분석 | 아니요 | 아니요 |

개인정보처리방침 URL: https://ghtnql.github.io/KKKeyboard-policy/privacy-policy.html

각 항목 저장 후 **게시**까지 실행해야 한다. 표나 정책 URL만 저장한 것은 개인정보 답변 게시 완료가 아니다. 완료 기준은 실제 웹 게시 결과와 정식 심사 API의 `You must have published answers to your app's data usages` 오류 해소다.
