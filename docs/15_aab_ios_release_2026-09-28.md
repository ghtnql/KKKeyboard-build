# 2026-09-28 AAB + iOS TestFlight 결과 (소스 f902d00)

## Android (완료)
- AAB: `build/KKKeyboard-drive-content-f902d001-v5-release.aab` (18,097,810 bytes,
  SHA-256 `5cbe4384148ee1a95851b5c1b4d2db85518960b16244b730a99d5980255d0b03`)
- 서명: 등록된 업로드 키 (SHA1 `0D:6E:79…`), versionCode 5, 패키지 `com.ghtnql.kkkeyboard`
- Play Console **내부 테스트 트랙에 업로드 완료** (실행 36365706804)
- provenance: `build/KKKeyboard-drive-content-f902d001-v5-release.aab.release.json`

## iOS (업로드 완료, Apple 심사 대기)
- 방식: `tools/release-ios-public-build.sh` → 스냅샷 → `ghtnql/KKKeyboard-build`(private) →
  실행 중에만 public → 정식 빌드 → private 복원 확인
- 실행: [36377597828](https://github.com/ghtnql/KKKeyboard-build/actions/runs/36377597828),
  소스 `f902d00` (사전 300·학습 103 포함), 스냅샷 `d49bf52e`
- 빌드 4: App Group 포함 정식 서명. 내부 테스트 `READY_FOR_BETA_TESTING`,
  `공개 테스트` 그룹 연결됨, 외부 베타 심사 `WAITING_FOR_REVIEW`
- 외부 테스터에게는 Apple 승인 후에만 보임. 승인되면 TestFlight에서 확인

## 이후 수정 반영 (공통 스크립트)
- `release-ios-public-build.sh`에 스냅샷 압축 후 `gradlew` 실행권한 복원 추가
  (zip 추출로 권한이 날아가 Xcode가 `Permission denied`로 떨어졌음).
  `.bat` 원본에도 같은 수정 필요.
- 스크립트 버그 2건 발견 (Drive 원본 반영 필요):
  1. 낡은 `gh`는 `--accept-visibility-change-consequences`를 모름
  2. watch 실패 후 `exit 1`이 private 복원 trap을 건너뛰어 repo가 public으로 남음
     (2회 모두 수동으로 private 복원 확인)
