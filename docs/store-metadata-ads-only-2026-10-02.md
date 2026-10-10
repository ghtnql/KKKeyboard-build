# 광고 전용 출시 설명 반영 — 2026-10-02

## 광고 전용 새 바이너리 실제 배포 완료 — 2026-10-02 15:05KST

- 사용자 ‘올리자’ 직접 배포 승인으로 APK 테스트 대기를 우회했다. APK 테스트 완료로 표시하지 않는다. 동일 소스 `086d6ac5e988e07fcbee57a5cbb3188a9758ad6c`에서 Android/iOS를 배포했다.
- Play 비공개 `ㅋㅋ키보드` 280 completed, 업로드 SHA-256 및 트랙/기존 테스터/KOJA 설명 재조회 확인. [실제 업로드 실행](https://github.com/ghtnql/KKKeyboard/actions/runs/36971052661). AAB 로컬 빌드 32,545,493 bytes, SHA256 a4dcc101523e85f002cfc3390b392fefaea09f044bbddb659e206dc75df3b59c, 기존 등록 서명 유지, 28개 자산 소스 일치.
- TestFlight1.0(18) 업로드 및 AppleVALID/expiredfalse, 기존 외부 `공개 테스트` 그룹 연결과 베타 제출, 최종 `IN_BETA_TESTING` 확인. [실제 실행](https://github.com/ghtnql/KKKeyboard-build/actions/runs/36970647336). snapshot78e5a2fbce178f684872644ee7c5ac6001504058. 베타 제출 응답 WAITING_FOR_REVIEW를 App Store 심사 승인으로 표시하지 않는다.
- iPhone17Pro/iOS26.4.1 Simulator nativeXCTest42건 통과, 실패/건너뜀0. App/extension18·서명·AppGroup 및 exported26자산 소스 hash 검증 성공. 최신 nativeFlick12키 실제PNG 확인. 물리 기기 검증 및 전체 플랫폼 동등성 검수는 별도다.
- 원본/빌드 저장소 PRIVATE 독립 확인, Play 일회성 workflow·ephemeral runner·일시 인증정보 정리 완료. 새 APK는 이번 요청에서 만들지 않았으며 기존 APK279는 수정 전 소스다.
- 아래는 바이너리 업로드 전 설명 정리 당시의 이력이다. 이번 배포는 스토어 테스트 배포이며 프로덕션 공개 출시/실운영 광고 활성화 완료가 아니다. 운영 AdMob ID/동의 설정은 여전히 남아 있다.

이번 출시는 본체 앱/게임 광고만 제공하고 유료 상품과 인앱결제를 제외한다. 구매 UI·가격·복원·환불 및 결제 연결을 양 플랫폼에서 제거했다. 광고 동의 변경과 개인정보처리방침 링크는 유지한다. 키보드 확장에는 광고 SDK/표시 또는 광고 시청 유도 UI를 제공하지 않는다.

## 실제 스토어 설명 상태

- Google Play 한국어/일본어 전체 설명 및 기존 비공개 278 릴리스 안내의 구매 문구를 제거하고 API 재조회 값의 일치를 확인했다. [메타데이터 전용 실행](https://github.com/ghtnql/KKKeyboard/actions/runs/36969108208). 모든 트랙의 버전·상태와 기존 테스터 연결을 유지했다. AAB 업로드 없음.
- Apple 현재 등록된 한국어 App Store 1.0 초안(PREPARE_FOR_SUBMISSION) 및 한국어 TestFlight 설명을 광고 전용으로 갱신하고 API 재조회 값 일치를 확인했다. [메타데이터 전용 실행](https://github.com/ghtnql/KKKeyboard-build/actions/runs/36969113489). 일본어 App Store 설명은 로컬 문서 초안이며 실제 등록을 완료했다고 표시하지 않는다. 바이너리 업로드/심사 제출 없음.
- 두 일회성 메타데이터 워크플로는 성공 후 삭제했다. 원본 저장소와 빌드 저장소는 PRIVATE 유지.
- 정책 저장소 `ghtnql/KKKeyboard-policy` 커밋 `659231f`에서 한국어/일본어 개인정보처리방침과 기존 구매·환불 URL을 현재 유료 상품 없음 안내로 갱신했다. 5개 HTML 페이지 HTTP200 및 소스와 전체 바이트 일치 확인. 앱 안에는 구매·환불 URL 진입이 없다.

## 코드 검증과 배포 경계

- Android UI6/게임 종료 광고13/테마4 총23 검사 통과, 실패·오류·건너뜀0. Kotlin iOS simulator 컴파일 성공. 실제 KO/JA 개인정보 패널 및 확장 테마 안내 PNG를 확인했다.
- Swift/Xcode 및 물리 기기 검증은 이번 작업에서 실행하지 않았다. iOS 테마의 오래된 유료 권한 무시 회귀 테스트는 소스에 추가됐지만 실행하지 않았다.
- 기존 Play278/TestFlight17/APK279는 구매 UI가 남은 수정 전 바이너리다. 이번 코드 변경의 새 APK/AAB/iOS 빌드 및 업로드는 아직 없다. 이전 APK의 링크/해시/사용자 테스트 상태를 새 코드로 바꾸지 않는다.
- 실운영 광고 ID/동의 구성, 판매자·개인정보·국가 설정, 기존 플랫폼 동등성 및 기기/원어민 검수는 남아 있다. 유료 상품/SKU/가격/결제키/실결제·환불 검사는 이번 광고 전용 출시의 필수 항목에서 제외한다.
