# 2026-10-06 최신 카페 난이도 수정 스토어 테스트 배포 완료

카페 게임의 모든 난이도가 보통으로 표시되고 보통에서 긴 문항이 출제되던 결함을 수정한 최신 APK302 소스를 Android와 iOS에 배포했다. 두 바이너리의 정확한 소스는 `a3f58e87da44fd0b0bba2347805ee2f98a09568a`이며 Android APK302와 같다. 새로운 제품 코드 수정이나 APK 중복 빌드는 하지 않았다.

사용자 “찐막 가자 찐막 스토어에 싹 올리자” 지시의 직접 배포 승인을 기록했다. `release_approved=true`, `apk_test_complete=false`; 휴대폰 APK 테스트 완료를 추정하지 않는다. 기존 규칙에 따라 Play 비공개 테스트 및 외부 TestFlight 배포이며, production/App Store 정식 공개 전환은 하지 않았다.

| 대상 | 버전 | 확인된 서비스 상태 | 실행 |
|---|---|---|---|
| Android `com.ghtnql.kkkeyboard` | 0.1.0 / 302 | 기존 비공개 트랙 `ㅋㅋ키보드`, `completed` | [Play 업로드](https://github.com/ghtnql/KKKeyboard/actions/runs/37450192908) |
| iOS 앱·키보드 확장 | 1.0 / 23 | Apple `VALID`, 외부 `공개 테스트` 연결, `IN_BETA_TESTING` | [TestFlight 배포](https://github.com/ghtnql/KKKeyboard-build/actions/runs/37449944861) |

Android는 Linux 로컬 `bundleRelease`로 빌드했다. APK302와 동일한 깨끗한 소스 작업트리를 사용했다. ZIP CRC, bundletool, jarsigner, 등록 upload 인증서 연속성, 실제 DEX `DEBUG=false`/`MOCK_ADS=false`/운영 광고 ID, 사전·학습·상용구·테마·이미지·음원 등 소스 자산38개 바이트 일치를 확인했다. Google Actions는 검증한 AAB 전송과 Play API 업로드만 수행했으며 Android 빌드를 하지 않았다. 한국어·일본어 카페 수정 릴리스 안내를 읽어 확인했다. 업로드 후 새 API edit에서302 completed를 확인했고 [독립 전체 트랙 재조회](https://github.com/ghtnql/KKKeyboard/actions/runs/37450360015)에서도 같은 상태와 production 빈 상태/internal277 보존을 확인했다. 해당 작업에서 만든 임시 private AAB 전송 Release/tag는 성공 후 정리했다.

- 공유 AAB: `/mnt/shared-hdd/공유폴더/ㅋㅋ키보드/스토어 배포/2026-10-06_KKKeyboard-v302-a3f58e8.aab`
- 크기: `32976713` bytes
- SHA-256: `6c5a2f6741aa2a247e19226e66a0e7a3ae273412672de66e927de8a1f90cf093`
- 업로드 인증서 SHA-256: `be76fbc0b3f9877826edd8fbcd7ff1d14600ad30ba195b492685ea1f15868e46`
- 공유 원자 복사 후 전체 파일 읽기 크기·해시 일치 확인, 최종 배포 완료 후 전체 읽기와 옆 `.aab.release.json` 재확인.

iOS는 같은 소스의 표준 `tools/release-ios-public-build.sh`를 수동1회 실행했다. snapshot `aeb6f8a383f4356d966261b5933185973c62b801`. Apple 전체15개 빌드 이력에서23을 선택했다. iPhone17Pro/iOS26.4.1 시뮬레이터 native72/72 PASS, 실패·skip0. 앱·확장 AppGroup 서명 archive/export 검증, Compose 필수 플래그 양쪽true, 운영 광고 ID와 `test_ads=false`, 소스 자산26개 일치 확인 후 업로드했다. 산출물의 천지인 플러스 및 플릭 렌더링 캡처도 직접 확인했다. Apple `processingState=VALID`, 기존 외부 그룹 연결 readback, 베타 심사 제출 및 최종 GET의 `externalBuildState=IN_BETA_TESTING`을 확인했다. 최초 심사 제출 응답과 최종 외부 이용 상태는 로그에 따로 보존했다. 원본·빌드 GitHub 저장소 모두 `private=true`를 독립 조회했으며 helper는 exit0으로 종료했다. Linux 로컬 IPA는 따로 보관하지 않았다.

검사 근거: 기존 카페 공통 모델6/세션14/실제 Compose UI3 총23개 PASS를 유지하고, 이번 실제 iOS72개와 서명 빌드 결과를 추가했다. 기획이나 공통 코드 사용만으로 전체 기능 동등성을 완료 처리하지 않는다. 새로운 휴대폰 설치·테스트, 실제 운영 광고 노출/보상, App Group 실기기 앱↔확장 사용, Android API26~28 팝업 위치 및 전체 iPhone 기능 동등성은 미검증 사항으로 유지한다. 현재 Playwright 브라우저는 Google 로그인 화면으로 새 Console UI는 확인하지 못했으며 Play 서비스 API 등록 결과와 휴대폰 제공 여부를 구분한다.

공유 기록: `스토어 배포/2026-10-06_iOS23-a3f58e8.release.json`, `APK 배포/2026-10-06_KKKeyboard-v302-a3f58e8-release.apk.delivery.json`, `13_개발 진행 현황.md`. 증거 `/home/ghtnql/.codex/handoffs/KKKeyboard-final-store-2026-10-06`. 원래 사용자 미커밋5파일 해시를 보존했다. 새 개발 위임 없이 총괄이 배포·최종 검증을 직접 수행했다. 기존 카페 수정의 실제 Muse 작업은 이전 기록에 보존돼 있다. 실제 iOS job 경과는 resolve7초/archive-upload1146초/status130초이며 청구 시간·비용·절감량으로 추정하지 않는다.
