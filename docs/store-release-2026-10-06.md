# 2026-10-06 APK297 승인 후 Play / TestFlight 배포 완료

사용자가 APK297을 실기기에서 확인하고 `딱좋구나 이대로 스토어에 올리자`라고 승인한 배포를 이어받았다. 이전 GPT 작업에서 이미 업로드가 끝나 있었다. 이번 이어받기에서는 완료 로그와 실제 서비스 상태를 다시 확인하고, iOS 수정 두 건을 본 저장소에 통합하고, 공유폴더에 결과를 기록했다. 신규 앱 빌드·APK 생성·스토어 중복 업로드는 하지 않았다.

## 실제 배포 상태

| 대상 | 결과 | 바이너리의 정확한 소스 |
| --- | --- | --- |
| Android | 기존 Play 비공개 트랙 `ㅋㅋ키보드`, `v297 keyboard input release`, versionCode **297**, `completed` | `49b7b5810108ecf760cc2c0228e13786e450ad65` |
| iOS | 버전 **1.0**, 빌드 **20**, `processingState=VALID`, 기존 외부 그룹 `공개 테스트`에 연결, `externalBuildState=IN_BETA_TESTING` | `82243f8ad7ad6960ae11029e80f517812f3da72b` |

- Android 업로드는 [실행 37389273718](https://github.com/ghtnql/KKKeyboard/actions/runs/37389273718)에서 08:35 KST에 완료됐다. 11:04 KST의 [읽기 전용 조회 37402334759](https://github.com/ghtnql/KKKeyboard/actions/runs/37402334759)도 트랙·버전·완료 상태를 반환했다. 기존 internal 트랙은 277이며 production 릴리스는 비어 있다.
- iOS는 [실행 37398274055](https://github.com/ghtnql/KKKeyboard-build/actions/runs/37398274055)에서 10:44 KST에 완료됐다. 11:01 KST의 [상태 전용 조회 37402067940](https://github.com/ghtnql/KKKeyboard-build/actions/runs/37402067940)에서 처리 완료와 외부 테스트 상태를 다시 확인했다. 이 조회는 아카이브·업로드·외부 심사 제출을 수행하지 않았다. 빌드 저장소 snapshot은 `f2b50f0362ea80fdcac1c5467decc920ddebf33a`이고, `BUILD_SOURCE.txt`의 실제 앱 소스는 위의 `82243f8…`이다.
- 두 GitHub 저장소의 `private=true`를 확인했다. 이번 배포 범위는 기존 비공개 Play 테스트와 외부 TestFlight다. 정식 Play production / App Store 출시는 수행하지 않았다.

## 바이너리와 검증

Android AAB는 Linux 로컬 빌드이며 GitHub 실행은 이미 만든 파일을 업로드했다. 공유폴더의 파일 전체를 다시 읽어 업로드 로그·로컬 원본과 크기/SHA-256이 일치함을 확인했다.

- 파일: `/mnt/shared-hdd/공유폴더/ㅋㅋ키보드/스토어 배포/2026-10-06_297.aab`
- 메타데이터: 같은 폴더의 `2026-10-06_297.release.json`
- 패키지 / 버전: `com.ghtnql.kkkeyboard`, versionCode `297`, versionName `0.1.0`
- 크기: **32,971,264 bytes**
- SHA-256: `7c2b26688b85a2551d6e183dfd5209b1ea9dd7ce8f8a53dd9e5e9a96cf762cd4`
- 업로드 인증서 SHA-256: `be76fbc0b3f9877826edd8fbcd7ff1d14600ad30ba195b492685ea1f15868e46` — 이전 배포의 인증서와 동일하다.

ZIP CRC와 bundletool validate가 성공했고, ASCII 경로의 jarsigner 검증이 `jar verified`를 반환했다. jarsigner에는 자체 서명·타임스탬프 및 manifest 엔트리 경고가 있으며 경고 없는 검증으로 기록하지 않는다. 실제 DEX의 `BuildConfig`에서 `MOCK_ADS=false`, `DEBUG=false`, 운영 보상형·전면 광고 ID를 확인했다. 학습 데이터·사전·문구·테마와 이미지 등 확인 대상 14개 리소스가 소스와 바이트 단위로 일치했다.

iOS 완료 로그에서 네이티브 검사 **44개 성공 / 실패 0**, 서명된 archive/export 검사 및 리소스 26개 검증을 확인했다. 앱과 키보드 확장의 빌드 번호는 모두 20이며 Release manifest의 `test_ads=false`를 확인했다. 이 실행의 서명된 IPA 파일을 Linux에 별도로 보관한 것은 아니다.

## iOS 수정 통합

이전 GPT가 실패한 iOS 빌드를 수정한 두 커밋을 Muse `muse-spark-1.3-contributor`에 세 파일 범위로 검토시켰다. 실제 결과와 diff를 확인한 뒤 `migration/kmp-compose`에 fast-forward 통합하고 원격에 push했다. 사용자의 기존 미커밋 AGENTS·문서 파일은 바이트 그대로 보존했다.

- `4bc3a9e`: `LongPressKeyButton`의 상태 이름 `held` / `selected`를 `holdActivated` / `selectedChoiceIndex`로 바꿔 UIKit 속성 충돌을 해결했다. 제스처·타이머·콜백 로직은 동일하다.
- `82243f8`: 천지인 플러스 렌더링 검사가 중복 키 제목을 전체 화면에서 찾던 대신 실제 하단 행에서 찾도록 수정했다. 하단 9개 버튼과 순서·좌표 검사는 유지한다.

Android 소스 `49b7b58…`와 iOS 소스 `82243f8…`의 차이는 이 두 iOS 수정뿐이다. Android/shared 데이터 변경은 없다. 통합 후 `git diff --check`가 통과했다. Muse는 정적 검토를 수행했으며 44개 실행 테스트의 근거는 실제 iOS CI 로그다. 사용량 수치는 반환되지 않았다.

Play 읽기 전용 workflow는 릴리스 이름·버전·상태까지 출력하도록 `9b43bddd6b277e3990618a72c7d05de014ccfe77`에서 보완했다. 이 커밋은 업로드용 legacy `main`의 workflow 변경이며 앱 바이너리 소스가 아니다. 조회의 임시 edit는 종료 시 삭제하며 릴리스 변경이나 Android 빌드를 수행하지 않는다.

## 확인 범위와 남은 사용자 확인

APK297 사용자 실기기 테스트와 배포 승인은 기존 진척 기록에서 확인했다. 이번 API 재조회는 Play 등록 완료와 TestFlight 외부 테스트 가능 상태를 입증한다. 로그인되지 않은 Play Console 화면을 새로 확인하거나 테스터 휴대폰에서 업데이트를 설치한 것은 아니다.

iOS 시뮬레이터 검사 44개와 표준 천지인·플릭 캡처를 확인했다. 천지인 플러스 캡처 한 장은 검은 화면이어서 해당 화면의 시각 검증 근거로 쓰지 않는다. 플러스 좌표 검사는 통과했지만 iPhone 실기기의 전체 기능 동등성, App Group 앱/확장 연결 및 실제 운영 광고 노출·보상은 이번 작업에서 검증하지 않았다. CI 성공을 실기기 동등성 완료로 표시하지 않는다.

기준 진척 문서는 `/mnt/shared-hdd/공유폴더/ㅋㅋ키보드/13_개발 진행 현황.md`이고, 이번 문서의 공유 사본은 `개발_인계/store-release-2026-10-06.md`다. 조회 로그·검증 JSON·Muse 결과는 `/home/ghtnql/.codex/handoffs/KKKeyboard-store-resume-2026-10-06`에 보관한다.
