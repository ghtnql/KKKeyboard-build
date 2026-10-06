# 출시 빌드 순서

## 2026-10-04 운영 광고 빌드 배포 완료

실제 빌드 소스는 `2a241fff7024ffba66699b59a7c8e38bbf486133`이다. Android AAB 285를 Linux에서 빌드하고 서명·ZIP·bundletool·실제 DEX의 운영 광고 ID와 `MOCK_ADS=false`·소스 리소스 38개를 검증한 뒤 기존 Play 비공개 트랙에 업로드했다. SHA-256은 `653d15548165937a41659745f16431270709459a74e2bbe035d432e20da3dbb0`이다. 16:14 KST 실제 트랙 화면에서 `v285 operating ads release`, 버전 코드 285, `선택한 테스터에게 제공됩니다.`, 게시일 `10월 4일 오후 2:03`을 확인했다. 기존 국가 178개 및 테스터 14명은 유지했다. 정식 공개 출시는 하지 않았다.

iOS는 표준 helper의 실행 `37176641806`으로 동일 소스의 빌드 19를 서명·검증·TestFlight에 업로드했다. 네이티브 키보드/설정 검사 42개 성공, 실패/skip 0이며 캡처 2장을 확인했다. 실제 IPA 검증 manifest에서 운영 광고 ID 세 개, `test_ads=false`, 소스 리소스 26개를 확인했다. Apple API가 기존 외부 그룹 `공개 테스트` 연결 및 최종 `externalBuildState=IN_BETA_TESTING`을 반환했다. helper 종료 뒤 원본/빌드 저장소 모두 `private=true`를 별도로 확인했다. Linux 로컬 IPA는 보관하지 않았다.

두 Google 계정의 브라우저 인증은 완료했으나 AdMob 승인과 스토어 연결/광고 게재 제한은 남아 있다. 운영 광고 설정의 바이너리 검증과 실제 운영 광고 노출·보상 실기기 검증을 구분한다. iOS 실기기 기능 동등성 및 기존 플릭 힌트 경계도 미검증이다. 사용자 직접 배포 승인은 `release_approved=true`, `apk_test_complete=false`로 유지했다. 기존 Drive `13_개발 진행 현황.md`에 빌드 전 실제 소스/검사를 기록하고 정확히 읽어 확인했으며 배포 결과도 같은 문서에 갱신한다. 아래 진행 중/보류 기록은 당시의 이력이며 이 완료 기록이 최신 상태다.

## 2026-10-04 운영 광고 설정 확보와 배포 진행

총괄 브라우저에서 두 Google 계정을 확인했다. `ghtnqlxx`의 기존 KKKeyboard Android/iOS 앱에 보상형·전면 광고 단위를 각 1개씩 만들고 AdMob 화면에서 다시 확인했다. Android 앱/보상형/전면 ID는 `ca-app-pub-2254079335610888~1612197588`, `ca-app-pub-2254079335610888/5359870908`, `ca-app-pub-2254079335610888/7524008976`이다. iOS는 `ca-app-pub-2254079335610888~5939464564`, `ca-app-pub-2254079335610888/4478209089`, `ca-app-pub-2254079335610888/6129278663`이다. 보상은 서울 테마 24시간 이용권이며 실제 SDK 보상 콜백 이후 기존 권한 저장 경로를 사용한다.

iOS 실제 ID 세 개는 빌드 저장소의 `testflight` 변수에 등록하고 읽기 확인했다. Android 실제 ID의 Release 설정 사전 검사가 통과했다. 내보낸 iOS 앱 검사에도 실제 ID 세 개의 정확한 일치·샘플/공백 거부·`KKUseTestAds` boolean false 확인을 추가했다. 실제 ID 정상 및 변조 설정 17개 검사를 통과했다. Muse의 제한된 런타임 검토와 검사기 편집 결과를 총괄이 검증했으며 런타임 컨트롤러는 변경하지 않았다.

두 앱을 대상으로 기존 개인정보처리방침을 연결한 GDPR 동의 메시지를 게시하고 게시 상태를 읽어 확인했다. 영어 기본/일본어 추가, 동의·거부·옵션 관리, EEA/영국/스위스 대상이다. AdMob이 제공한 두 기존 게시자 코드로 `https://ghtnql.github.io/app-ads.txt`를 게시하고 HTTP 응답 내용도 확인했다. 기존 프로젝트 정책 사이트와 공유 설정은 유지했다.

두 AdMob 계정은 Google 계정 승인 대기이며 KKKeyboard 앱은 스토어 미연결로 광고 게재 제한 상태다. 운영 ID 및 실제 SDK 경로 준비와 운영 광고 송출 승인/실제 노출 검증을 구분한다. 비공개 Play/TestFlight 앱을 정식 공개 앱으로 임의 전환하지 않는다. `ghtnqlaa`의 별도 기존 Play 개발자 계정은 인증 기한 미준수로 게시가 막혀 있고, 키보드는 정상인 `ghtnqlxx`의 기존 Play 앱과 비공개 트랙에 배포한다. 이 상태는 위의 운영 ID 부재에 따른 이전 보류를 대체하며 Google 심사 대기를 없앤다는 뜻은 아니다.

## 2026-10-04 광고 사전 점검과 스토어 배포 대기

사용자가 최신 APK 소스의 AAB·IPA 빌드와 기존 Play 비공개 테스트/외부 TestFlight 업로드를 직접 승인했다. 이 승인은 `release_approved=true`, `apk_test_complete=false`로 기록하며 실제 APK 사용자 테스트 완료를 추정하지 않는다. 추가 조건인 **빌드 전 광고 점검**을 통과해야 진행한다.

점검 결과 Android 운영 광고 ID가 없고 기존 TestFlight 절차는 Google 샘플 광고를 강제로 사용하고 있었다. 실제 광고 경로를 유지한 것과 운영 광고 설정/노출이 검증된 것은 다르다. Android `validateReleaseAds`를 `preReleaseBuild`에 연결해 누락·형식 오류·샘플 발행자·서로 다른 발행자·앞뒤 공백을 거부한다. 개발 debug/feedback에는 이 배포 게이트를 적용하지 않는다.

Android 로컬 빌드에는 `KK_ADMOB_ANDROID_APP_ID`, `KK_ADMOB_ANDROID_REWARDED_ID`, `KK_ADMOB_ANDROID_INTERSTITIAL_ID` 환경 변수가 필요하다. iOS 빌드 저장소의 `testflight` 환경/저장소 변수에는 각각 `KK_ADMOB_IOS_APP_ID`, `KK_ADMOB_IOS_REWARDED_ID`, `KK_ADMOB_IOS_INTERSTITIAL_ID`를 설정한다. iOS도 빌드 도구 실행 전 같은 검사를 거치며 샘플 강제 설정을 제거하고 `KKUseTestAds=false` 및 운영 앱/보상형/전면 ID를 아카이브에 전달한다. XcodeGen에 보상형 광고 Info.plist 속성도 포함한다. Debug 광고 생략, 패키지·서명·App Group·수동 배포 게이트는 유지한다.

검사에 쓰는 합성 ID는 형식 검사 fixture일 뿐 운영 ID가 아니다. 운영 ID 입력, AdMob 앱/광고 단위와 광고 동의 설정 확인, SDK 광고 로드·보상·닫기 동작 검증이 남아 있다. 현재 AAB·IPA 생성/업로드는 보류 중이며 Google Play 심사·게시와 AdMob 광고 제공 승인은 별도 상태로 기록한다. iOS 설정 정적 검사만으로 Xcode 빌드/실기기 검증 완료를 선언하지 않는다.

## 2026-10-04 개발 테스트판 광고 생략

Android `debug`는 컴파일 상수 `MOCK_ADS=true`로 광고 버튼을 유지하면서 광고 SDK/동의 창을 호출하지 않는다. 서울 테마 체험 버튼은 기존 24시간 권한과 테마 선택을 저장하고, 게임 종료는 광고 대기 없이 결과로 넘어간다. `release`와 `feedback`은 항상 `MOCK_ADS=false`이며 기존 동의·로드·표시·실제 보상 콜백을 사용한다. 저장소 이름이나 런타임 설정으로 스토어 광고를 끄지 않는다. 일반 테스트 APK는 `-PKK_PARALLEL_DEBUG=true`로 Play판과 분리한다.

iOS도 `DEBUG` 조건의 개발 빌드에서만 같은 동작을 제공한다. TestFlight/App Store의 `Release` 아카이브는 실제 광고 경로를 유지한다. iOS 네이티브 컴파일·실기기와 스토어의 실제 광고 노출은 별도 검증이며 Android mock 테스트로 완료를 추정하지 않는다. APK는 설치를 위해 debug 서명을 유지하고 스토어 서명/제품 ID를 교체하지 않는다.

## 2026-10-04 Play판을 보존하는 별도 로컬 테스트 APK

일반 프로필에서 앱을 삭제했더라도 투폰 등 다른 프로필에 Play판이 남아 있으면 같은 패키지의 debug APK는 서명 충돌로 설치되지 않는다. 모든 프로필의 설치 상태를 확인하고, 테스터용 Play판과 데이터를 무단 삭제하지 않는다.

공존이 필요할 때만 `:app:assembleDebug -PKK_PARALLEL_DEBUG=true`를 사용한다. 이 옵션은 debug의 패키지만 `com.ghtnql.kkkeyboard.dev`, 앱/키보드 이름을 `ㅋㅋ키보드 테스트`로 바꾼다. 기본 debug, release, feedback의 패키지·서명·기존 번역은 그대로 유지한다. 검증기에 해당 테스트 패키지를 명시하며, 기존 `com.ghtnql.kkkeyboard` APK를 업데이트 비교 대상으로 넣지 않는다. 별도 앱은 설정·저장 공간이 분리되며 Play판의 데이터 이전이나 스토어 동작 검증을 대신하지 않는다.

무선 ADB 설치는 `adb -s DEVICE install --user 0 -r -t APK`처럼 대상 프로필을 명시하고, 설치 후 기존 Play판의 버전·서명·user 10 설치 상태를 다시 확인한다. APK 전달/Drive 선행 기록/사용자 테스트/AAB·iOS 게이트는 동일하다. 이 옵션은 Android 테스트 배포 충돌만 해결하며 공통 기능이나 iOS 제품 식별자를 바꾸지 않는다.

## 2026-10-02 광고 전용 소스 직접 배포 승인과 결과

사용자 ‘올리자’ 지시로 광고 전용 `086d6ac5e988e07fcbee57a5cbb3188a9758ad6c` 소스를 APK 테스트 대기 없이 기존 Play 비공개280 및 외부 TestFlight18에 배포했다. `release_approved=true`, `apk_test_complete=false`를 유지했다. Play completed/APIreadback, iOS native42검사·export서명/26자산·AppleVALID 및 최종 IN_BETA_TESTING, 빌드 저장소 PRIVATE 복원을 확인했다. 이것은 해당 소스에 한한 예외이며 새로운 수정의 기본 APK 우선 절차는 유지한다. 프로덕션 공개 출시는 포함하지 않는다.

## 2026-10-02 출시 수익 모델 확정

본체 앱/게임 광고만 제공한다. 광고 제거 3,900원·프리미엄·기타 유료 상품/인앱결제는 이번 출시에서 제외한다. 구매·가격·복원·환불 UI/결제 조회와 스토어 구매 제공 문구를 노출하지 않는다. 개인정보처리방침 및 광고 동의 변경은 유지한다. 키보드 확장에는 광고 SDK/표시·구매 또는 광고 시청 유도 UI를 두지 않는다. 추후 유료 기능은 별도 사용자 지시와 실제 상품/구매 검증 후 변경한다. 과거 결제 계획보다 이 결정이 우선한다.

## 2026-10-02 이번 배포의 명시적 예외

사용자가 “그냥 스토어에 바로 올려라”라고 지시했다. 이번 출시 준비 소스는 APK 사용자 테스트 대기를 우회해, 통합 검증 후 Google Play 기존 비공개 테스트와 iOS 외부 TestFlight 배포를 진행한다. APK 테스트 완료로 기록하지 않으며 iOS는 `release_approved=true`, `apk_test_complete=false`로 이 승인을 기록한다. Android 빌드는 계속 로컬에서 수행하고 기존 등록 서명·실제 트랙을 확인한다. 업로드·심사 대기·외부 이용 가능·프로덕션 공개를 구분한다. 기본 향후 APK 우선 규칙은 유지한다.


## 2026-10-02 선행 조건: Google Drive 진척부터 기록

APK 빌드 전에 기존 Google Drive [13_개발 진행 현황.md](https://drive.google.com/file/d/1H3StHsJ2wX7Tr8QliJVNejrU6c5hA-n0/view)를 갱신하고 읽기 확인을 완료한다. 실제 구현·검증·미완료·Android/iOS 차이·소스 커밋을 적는다. 제안 가격이나 기획은 구현 완료와 구분한다. 기록 실패 시 APK 빌드를 보류하고 알린다. 기존 파일 ID·이력·공유 설정을 보존한다.

APK 전달이 검증되면 같은 문서에 실제 Drive 링크·버전·해시·사용자 테스트 상태를 갱신한다. 아래 APK → 사용자 테스트 → AAB/iOS 게이트는 그대로 적용한다. 이 문서의 과거 운영 상태는 해당 날짜의 이력이며 최신 상태는 Drive 진척과 로컬 인계·실제 저장소를 대조한다.

1. 기능을 완성하고 대상 커밋을 고정한다. Android 테스트, 디버그/피드백 APK 생성과 검증은 로컬 PC에서 수행한다. GitHub Actions에서는 Android 빌드를 실행하지 않는다.
2. 해당 커밋의 APK를 사용자에게 전달한다. 사용자가 APK 테스트 완료를 명시하기 전에는 AAB나 iOS 빌드를 시작하지 않는다.
3. 승인된 **동일한 커밋**에서 Android 서명 AAB를 로컬 PC에서 생성한다. Google Play의 기본 사전 배포 대상은 **비공개 테스트(closed testing)** 이며, 현재 비공개 트랙의 실제 ID/name을 확인해 그 트랙으로 배포한다. `internal`은 사용자가 명시적으로 요청한 smoke test에만 사용한다. Play Console 업로드와 트랙 상태를 확인하고, 비공개 릴리스가 실제로 생성/검토 상태인지와 기존 테스터 그룹 연결을 기록한다.
4. iOS 검증이 필요하면 `iOS CI`를 수동 실행한다. `apk_test_complete=true`와 APK를 테스트한 커밋의 전체 40자리 SHA를 `tested_commit`에 입력해야 macOS 작업이 시작된다. 선택한 워크플로 커밋과 다르면 작업은 건너뛴다.
5. APK 테스트 완료 후 같은 입력으로 `iOS TestFlight`를 수동 실행한다. 이 워크플로는 해당 커밋을 서명·업로드하고, 처리 완료를 기다린 다음 기존 `공개 테스트` 그룹에 연결하고 외부 베타 심사를 제출한다. 그룹 연결과 심사 제출은 멱등적으로 확인한다. **심사 제출은 외부 테스터 공개 완료가 아니다.** 결과에는 Apple의 `externalBuildState`를 표시하며 `IN_BETA_TESTING`일 때만 외부 테스트 가능으로 보고한다. 빌드 번호는 macOS 시작 전에 App Store Connect의 전체 빌드 이력을 모든 페이지에서 조회해 기존 최댓값보다 큰 정수로 정한다. GitHub 실행 번호와 저장소 변경에 의존하지 않는다. 앱과 확장, 업로드 후 상태 조회는 이 번호를 함께 사용한다. 이미 업로드된 실행을 재실행하지 말고 `status_only`에 실제 Apple 빌드 번호를 입력해 상태를 확인한다.
6. `status_only=true`는 기존 `status_build_number`를 조회한다. 읽기만 하는 조회에는 APK 승인 입력이 필요 없다. `assign_external_preview` 또는 `submit_external_review`를 켜는 조회에는 동일한 APK 승인과 커밋 일치 조건이 적용된다.

기존 Android Actions 빌드 절차는 checkout → JDK 17/Android SDK 36/Gradle 8.13 설정 → `testDebugUnitTest` → `assembleDebug` → 로컬 업로드 키로 `bundleRelease`였다. 이 절차는 로컬 PC에서 필요한 단계만 수행한다. 기존 Android Actions 워크플로는 제거했다.

현재 원격의 기존 iOS 워크플로는 비활성화되어 있다. APK 테스트 승인 전에는 다시 활성화하지 않는다. 나중에 재활성화할 때는 먼저 이 수동 게이트가 포함된 커밋이 기본 브랜치에 반영되었는지 확인한다. 구형 기본 브랜치 워크플로를 활성화하면 과거 자동 트리거가 다시 실행될 수 있다.

`preview_without_app_group`은 App Group 동기화가 없는 미리보기 빌드에만 사용한다. 앱과 확장의 프로비저닝 프로파일 및 TestFlight 자격 증명은 `testflight` 환경의 기존 설정을 사용한다. iOS 워크플로 수동 실행 자체가 TestFlight 업로드를 뜻하지 않는다.

정식 TestFlight 배포 전에는 두 타깃의 App Group 프로비저닝과 네이티브 상용구 테스트 등 남은 iOS 검증을 완료한다. 서명 오류가 나도 `preview_without_app_group`을 자동으로 켜지 않는다. 미리보기는 해당 제한을 알고 별도로 선택했을 때만 사용한다.


## APK 전달과 승인 기록

`/home/ghtnql/.codex/skills/apk-delivery/SKILL.md`를 사용한다. Gradle 출력은 빌드 커밋이 포함된 고정 파일명으로 복사한 뒤 아래 검증기로 확인한다.

```bash
python3 /home/ghtnql/.codex/skills/apk-delivery/scripts/verify_apk.py \
  --apk /absolute/path/to/immutable.apk \
  --source-commit BUILD_COMMIT \
  --expected-package com.ghtnql.kkkeyboard \
  --previous-apk /absolute/path/to/previous.apk
```

위 검증기는 APK 무결성만 확인하며 다운로드 링크를 출력하지 않는다. 2026-09-27 휴대폰에서 로컬 APK 링크의 `fs/readFile` 30초 시간초과가 확인됐다. 로컬 검사 성공을 전달 성공으로 보고하지 않는다.

기본 전달 경로는 Google Drive의 `ㅋㅋ키보드 / APK 배포` 폴더(`11IT2YuNW-jojs3oANWM1dVD9AV2h-fth`)다. 기존 APK를 업로드한 후 파일 ID·크기·MIME·부모 폴더·다운로드 권한을 읽어 확인하고, 전체 파일을 다시 내려받아 원본 크기/SHA-256과 비교한다. `apk-delivery/scripts/verify_drive_delivery.py`가 검증 후 실제 Drive 파일 링크를 출력한다. 임시 서명 다운로드 URL은 사용자에게 보내지 않는다. 기존 공유 설정을 유지하며 현재 폴더는 소유자 계정으로 접근한다.

APK 옆 `.delivery.json`에 로컬 검증, Drive 파일 ID/폴더/정식 URL, 실제 다운로드 해시, 사용자 테스트 상태를 분리해 기록한다. 파일 전송 실패는 앱 재빌드나 AAB/iOS 실행 사유가 아니다. 사용자 테스트 완료 후 실제 대상 APK와 소스 커밋을 기록하며 과거 APK의 커밋을 현재 HEAD로 바꾸지 않는다.

## 로컬 Android 명령

PC의 JDK 17/Android SDK 36을 사용하고 `android/`에서 실행한다. 기능별 필요한 테스트를 먼저 선택한 뒤 APK를 한 번 만든다.

```bash
./gradlew :sharedCore:testDebugUnitTest :sharedUI:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

일반 게임 포함 검토는 `debug`, 외부 키보드 사용성 테스터 전용은 `feedback`이며 혼용하지 않는다. 승인 후 AAB는 `:app:bundleRelease`로 생성한다. `ANDROID_UPLOAD_KEYSTORE_PATH`, `ANDROID_UPLOAD_STORE_PASSWORD`, `ANDROID_UPLOAD_KEY_ALIAS`, `ANDROID_UPLOAD_KEY_PASSWORD`는 기존 로컬 비밀 저장소에서 프로세스 환경으로만 공급한다. `ANDROID_VERSION_CODE`는 Play Console의 기존 최대값보다 커야 하며 GitHub 실행 횟수와 연결하지 않는다. AAB 서명 확인 후 브라우저로 정확한 앱·트랙·버전과 업로드 처리 결과를 확인한다. 자격 증명 값을 문서/명령 출력에 기록하지 않는다.

### Google Play 트랙 안전장치

- 패키지는 항상 `com.ghtnql.kkkeyboard`인지 확인한다.
- "Play에 올려라", "AAB 올려라", "테스트 배포"는 별도 지시가 없으면 비공개 테스트를 뜻한다.
- 배포 자동화에서 `internal` 하드코딩을 금지한다. Play API가 반환하는 현재 비공개 트랙 ID/name을 사용한다.
- 내부 트랙에 이미 존재하는 번들을 비공개 트랙으로 옮길 때는 새 번들을 무조건 재업로드하지 말고, 기존 versionCode의 재사용/승격 가능 여부를 먼저 확인한다.
- draft 앱에서는 비공개 트랙 릴리스가 `draft` 상태만 허용될 수 있으므로 API 오류 본문을 확인하고 Play Console의 필수 앱 설정을 완료한 뒤 최종 제출한다.

## 공통 엔진의 검증 범위

공통 로직/데이터/화면/게임 자산은 sharedCore/sharedUI 한곳에 유지한다. APK 확인으로 공통 동작을 검증하고 승인 후 iOS에서는 Swift 연결, 리소스 번들, 키보드 확장, App Group, safe area 등 플랫폼 경계를 추가 확인한다. 같은 기능을 각 플랫폼에 다시 구현해 시각·동작 차이를 만들지 않는다.

## 운영 상태 (2026-09-27)

저장소는 사용자의 명시적 지시에 따라 public으로 전환했고 익명 HTTP 200을 확인했다. 원격 Android/iOS CI/TestFlight는 비활성 상태다. main과 migration/kmp-compose 모두 수동 승인 조건을 적용한다. main은 아직 구형 네이티브 앱이므로 배포 대상이 아니다. 현재 공통 엔진과 공개 TestFlight 후속 절차는 migration/kmp-compose에서 검증·통합한다. 현재 그래픽 APK c504f94는 사용자 테스트 대기 중이다. 이번 운영 정비는 앱 빌드나 스토어 배포가 아니다.

main에는 자동 실행 방지 조건과 Android workflow 제거만 선반영했다. 공통 엔진 기능은 아직 main에 병합하지 않았다. APK 승인 후 정확한 소스와 전체 TestFlight 절차를 통합·검증한 뒤 워크플로를 활성화한다.

## 2026-09-30 명시적 테스트 배포 승인

사용자가 기본 상용구10개 추가 후 AAB·iOS 빌드/업로드를 직접 요청했다. 이번 소스는 APK 테스트 완료로 표시하지 않고 사용자의 명시적 테스트 배포 승인으로 진행한다. 수정 APK도 제공하고 동일한 고정 커밋에서 AAB·iOS를 만든다. TestFlight 입력 `release_approved=true`, `apk_test_complete=false`는 이 예외를 기록한다. 기본 APK 우선 절차와 정확한 SHA 조건은 유지한다. Play 배포 대상은 기존 내부 테스트 트랙이며 프로덕션 출시는 포함하지 않는다.

iOS 공통 스크립트는 `KK_RELEASE_APPROVED=true tools/release-ios-public-build.sh`로 실행한다. 낡은 gh 호환을 위해 visibility 변경은 `gh api PATCH`로 수행하며 EXIT trap으로 성공/실패/명시적 종료 모두 빌드 저장소를 private으로 복원한다.

## 2026-10-01 TestFlight 번호 역행 수정

사용자 아이폰에는 구형13.1이 설치되어 있고, 새 빌드 저장소에서 최신 코드가7로 업로드되어 업데이트 순서가 뒤집혔다. 이번 사용자 지시로 배포 번호/자산 검증만 수정하고 동일한 앱 기능 소스를 재배포한다. APK 테스트 완료로 기록하지 않는다. 번호 조회 실패나 잘못된 이력은 빌드를 중단한다. 내보낸 IPA의 앱/확장 번호·패키지·App Group·게임/테마/상용구 자산 해시와 소스 커밋을 검사하고 ios-release-manifest.json에 기록한다. 업로드 후 실제 높은 번호와 공개 테스트 그룹 연결, Apple 외부 테스트 가능 상태를 확인한다. 휴대폰 업데이트/실사용 완료는 별도 확인한다.
