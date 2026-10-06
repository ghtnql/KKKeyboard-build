# Codex 실행 순서

1. 이 폴더의 `00~05`를 모두 읽는다.
2. `migration/kmp-compose`의 현재 dirty/로컬 상태를 먼저 확인하고 보존한다.
3. 현재 실제 UI가 `KKKeyboardApp.kt`와 다르면 **현재 로컬 코드가 우선**이며, 문서의 Page 이름/요소 매핑만 재조정한다.
4. 공통 `AppThemeSpec`/CompositionLocal 또는 동등 구조를 추가한다.
5. hard-coded `Home*` / `AppColors`를 dynamic theme로 교체한다.
6. `Header`, `Panel`, `HomeButton`, `NavButton`, input/chip/switch/slider부터 공통 skin을 적용한다.
7. HOME -> SETTINGS -> THEMES -> TEST/PHRASES -> SELECT/PRACTICE -> RESULT/PROGRESS -> game chrome 순서로 적용한다.
8. 서울 이미지 자산을 sharedUI에서 사용할 수 있게 연결한다. 새 도시 그림을 생성하지 말고 기존 승인 asset을 재사용한다.
9. 테마 선택 후 본체 앱과 키보드가 같은 선택값을 사용하게 한다.
10. Linux에서 tests + lint + APK를 빌드한다.
11. 실제 APK에서 screenshot 가능한 emulator/device 검증을 진행한다. 실기기가 없으면 가능한 범위의 screenshot 테스트를 남기고 blocker를 명시한다.
12. Drive `13_개발 진행 현황.md`에 적용 결과/빌드 결과/남은 blocker를 업데이트한다.

## 금지
- 가상의 bottom navigation 추가
- 앱을 새 정보 구조로 재설계
- 테마 작업과 동시에 입력 엔진 리팩터링
- Rain/Cafe 게임 로직 수정
- GitHub Actions Ubuntu로 반복 APK/AAB 생성
- 서울 테마 unlock BM 삭제/우회
- iOS Keyboard Extension에 Full Access를 테마 때문에 추가
