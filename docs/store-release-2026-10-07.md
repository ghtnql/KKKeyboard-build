# 2026-10-07 최신 소스 스토어 테스트 배포

일본어 후보 개인화·가나 조합 안내, 기호 자판 3페이지와 화살표 순서, 한글학습 안내·사운드를 포함한 최신 소스 `829096e9dcac6e74d2bfe6b0dfe651d5db8c18d3`를 Android AAB 307과 iOS 24로 배포했다. 사용자의 직접 빌드·업로드 승인으로 `release_approved=true`, `apk_test_complete=false`를 유지했다. 대상은 기존 Play 비공개 테스트 `ㅋㅋ키보드`와 외부 TestFlight `공개 테스트`다.

## Android

Linux의 고정 소스 작업 트리에서 `:sharedUI:testDebugUnitTest :app:bundleRelease`를 실행했다. 빌드는 3분 33초에 성공했다. 앱 349개 JUnit은 동일 소스에서 이미 완료된 결과를 확인했고, 이번 공통 UI 61개 검사는 새로 통과했다. 두 집계 모두 실패·오류·skip 0이다. 기존 Kotlin lint의 메타데이터 버전 호환 진단은 비치명적으로 남아 있으며 lint 무진단 상태로 보고하지 않는다.

- 패키지 `com.ghtnql.kkkeyboard`, 버전 `0.1.0` / `307`
- AAB 32988403 bytes, SHA-256 `33d5af893de3cc5ad5ae1df8028f2f3b46760e3d99add3a58ba5a5f1af7fff4d`
- 등록 업로드 인증서 SHA-256 `be76fbc0b3f9877826edd8fbcd7ff1d14600ad30ba195b492685ea1f15868e46`
- bundletool, JAR 서명, ZIP CRC, manifest 및 실제 DEX `DEBUG=false`/`MOCK_ADS=false`, 운영 광고 설정, 소스 자산 38개 검증
- 공유 파일 `/mnt/shared-hdd/공유폴더/ㅋㅋ키보드/스토어 배포/2026-10-07_KKKeyboard-v307-829096e.aab`: 원자적 복사 후 전체 파일 크기·SHA-256·원본 바이트 일치 확인
- 업로드 전 조회 [37485901004](https://github.com/ghtnql/KKKeyboard/actions/runs/37485901004), 업로드 [37486914530](https://github.com/ghtnql/KKKeyboard/actions/runs/37486914530), 전체 트랙 재조회 [37487260903](https://github.com/ghtnql/KKKeyboard/actions/runs/37487260903)
- 비공개 트랙307 `completed` 및 새 한국어·일본어 설명을 fresh API edit와 별도 전체 트랙 조회로 확인. 프로덕션은 비어 있고 기존 내부277은 유지됨
- 업로드 전용 main workflow 설명 갱신 커밋 `84440584e59aa2cf1449b7738886908f213704a2`; 임시 전송 Release/tag 정리 완료. Android Actions 빌드는 사용하지 않음

현재 브라우저는 Google 로그인 화면이므로 이번 Play Console 화면 재확인은 미완료다. 실제 서비스 API 등록 확인과 구분한다.

## iOS

동일한 소스의 canonical helper를 깨끗한 작업 트리에서 한 번 실행했다. snapshot `1e8cdcdddef1c6258716482ce3d8d55435373274`, [실행37486779544](https://github.com/ghtnql/KKKeyboard-build/actions/runs/37486779544) 성공. Apple 전체 빌드16개 조회 후 기존 최댓값보다 큰24를 선택했다.

- 앱/확장 버전 `1.0` / 빌드24, 식별자 `com.ghtnql.kkkeyboard` / `com.ghtnql.kkkeyboard.keyboard`
- 실제 네이티브 검사 73개 성공, 실패 0 / skip 0
- iPhone 17 Pro 시뮬레이터의 천지인 Plus·설정 닫기 후 플릭 자판 캡처를 총괄이 실제 이미지로 확인. Plus 개별 자음/된소리 힌트와 플릭 방향 힌트·상단 닫기 경로가 렌더링됨. 실기기 입력/사운드 검증과 구분
- Release archive, 내보낸 서명 IPA, 앱/확장의 App Group·Compose frame duration, 운영 광고 `test_ads=false`, 소스 자산 26개 검증
- Apple 처리 `VALID`, 기존 외부 그룹 `공개 테스트` 연결 재조회, 최종 `externalBuildState=IN_BETA_TESTING`: 외부 테스터 이용 가능
- 표준 helper 종료 후 원본과 빌드 저장소 `private=true`를 별도 재조회
- 실제 job 경과 시간: Resolve iOS build number 7초, Archive and upload 1436초, Check TestFlight build status 613초. 과금 사용량·비용으로 환산하지 않음
- IPA는 macOS runner에서 서명·검증·업로드됐고 Linux에 따로 보관하지 않음. 진단 manifest·검사 요약·캡처는 로컬 증거 폴더에 보관

## 검토와 검증 범위

Muse `muse-spark-1.3-contributor`에 읽기 전용 검토를 실제 2회 맡겼다. 첫 범위는 네이티브 관련8파일, 두 번째는 공급자·저장 경로와 실제 사전 데이터 사실로 좁혔다. 총괄이 실제 출력과 소스를 대조했고 이번 신규 변경에서 확정된 배포 차단 문제는 찾지 못했다. 중복 입력이 있을 때 iOS Dictionary 생성 예외 가능성은 조건부 방어 개선사항이며 실제 생성 후보의 중복은 입증되지 않았다. 실제 사전의 공백 표면형은 없고 exact 별칭의 고유 후보 최대4개를 확인했다. 제품 코드 수정과 GPT 추가 위임은 없었다. 측정 usage는 제공되지 않아 비용 절감 수치를 주장하지 않는다.

기존 기능 차이는 남아 있다. iOS Space/Return은 읽기와 공백/줄바꿈을 확정하는 반면 Android는 순위 exact 후보를 확정한다. 이번 배포에서 무관한 입력 정책을 바꾸지 않았으며 전체 기능 동등성 완료로 보고하지 않는다. 기기에서 실제 사운드, 운영 광고 노출·보상, 앱/키보드 확장 간 App Group 사용과 전체 기능 동등성은 별도 확인이 필요하다. 컴파일·자동 검사·시뮬레이터 캡처는 사용자 휴대폰 설치/테스트 완료가 아니다.

기존 APK307과 이 AAB/iOS는 같은 소스다. APK는 공유 `APK 배포/2026-10-06_KKKeyboard-v307-829096e-jamo-audio-debug.apk`, SHA-256 `95e40ea0383326e76b551ca89fe8e8d61f2ead7269aa6eda0edc0659f2c7420d`이며 사용자 테스트 상태는 대기다. 새 APK는 만들지 않았다.

## 기록

공유폴더 `13_개발 진행 현황.md`에 빌드 전/Android 완료/최종 결과를 이력을 보존해 기록하고 전체 읽기로 확인했다. AAB와 iOS의 `.release.json`, APK의 `.delivery.json`, 공유 `개발_인계/store-release-2026-10-07.md` 및 로컬 인계를 갱신했다. 시작할 때 존재한 AGENTS/문서5파일의 바이트 해시를 보존했다. 실제 빌드 소스는 위829096e이며 결과 보고서 커밋과 구분한다.

증거 경로: `/home/ghtnql/.codex/handoffs/KKKeyboard-store-2026-10-07`. 이번 작업은 스토어 테스트 배포이며 정식 Play/App Store 공개 출시는 포함하지 않는다.
