# 실제 앱 화면별 테마 적용 맵

대상: `sharedUI/src/commonMain/kotlin/com/ghtnql/kkkeyboard/sharedui/KKKeyboardApp.kt`

## 공통 화면 골격
현재 구조를 유지한다.

- 루트 `Surface`: 선택 테마의 `appBackground`
- 일반 페이지 외곽 여백: 기존 20dp 유지
- 게임(RAIN/CAFE) 외곽 여백: 기존 12dp/4dp 유지
- 일반 페이지 요소 간격: 기존 16dp 유지
- 게임 화면 요소 간격: 기존 4dp 유지
- 상단 뒤로가기/타이틀 Row: 구조 및 높이 유지, 색상만 테마화
- `WindowInsets.safeDrawing` 유지

서울 테마는 앱 전체 배경에 사진을 과도하게 깔지 않는다. 읽기 쉬운 실사용 UI가 우선이다.

### 앱 전체 배경 표현
- 일반 다크: 단색 + 미세한 surface 계층만 사용.
- 서울 낮: `appBackground`를 기본으로 하고, 페이지 상단 140~180dp에 서울 낮 이미지를 12~18% 정도의 낮은 시각 강도로 사용. 텍스트 뒤에는 반드시 solid/gradient 보호층 사용.
- 서울 밤: `appBackground`를 기본으로 하고, 페이지 상단 또는 빈 영역에 서울 밤 이미지를 18~24% 정도 사용. 본문 카드 뒤에는 이미지가 직접 비치지 않게 한다.
- 게임 자체의 `Rain`/`Cafe` 배경 아트는 교체하지 않는다. 게임 UI chrome만 테마화한다.

## ONBOARDING
현재 요소:
- Header
- 안내 Panel
- StatusLine
- 시스템 키보드 설정 버튼
- 키보드 선택 버튼
- 상태 새로고침 버튼
- 기본 연습 진입 버튼

적용:
- Header에 테마별 미세한 hero treatment 허용.
- 모든 안내/상태는 `Panel` 토큰 사용.
- primary CTA 한 개만 강한 accent 사용.
- 서울 배경이 안내문 가독성을 침해하면 이미지 강도 즉시 낮춘다.

## HOME
현재 요소를 그대로 유지:
- 홈 타이틀 + ⚙
- 키보드 준비/미준비 상태
- 시스템 키보드 설정/선택
- 키보드 테스트 섹션
- 타이핑 연습 섹션
- BASIC / SENTENCE / CONVERT / Rain / Cafe 버튼
- 연습 기록 요약 + 기록 보기

적용:
- 현재 세로 스크롤 구조 유지. 가상의 카드 그리드/하단 탭바 추가 금지.
- 섹션 제목은 `textPrimary`.
- 상태 문구는 success/warning semantic color 사용.
- `HomeButton`은 primary/secondary 두 상태만 유지하되 테마별 surface와 accent를 적용.
- 서울 테마에서는 맨 위 홈 타이틀 주변에만 테마 아트 느낌을 주고, 긴 버튼 목록은 solid surface로 유지.

## SETTINGS
현재 요소:
- UI 언어 FilterChip
- 키보드 상태
- 시스템 키보드 설정/전환/새로고침
- 테마 화면 진입
- 게임 BGM/SFX
- 햅틱
- 한글 레이아웃 Dropdown
- flick 거리 Slider
- 천지인 cycle timeout Slider
- 세로/가로 높이 FilterChip
- 세로/가로 숫자행 Switch
- 고급 입력
- 상용구
- 광고 개인정보

적용:
- 설정별 기능은 하나도 이동하지 않는다.
- `Panel`을 theme surface로 통일하고, Panel 간 대비는 border/shadow로만 준다.
- FilterChip selected = accentContainer/onAccentContainer.
- Switch/Slider active track = accent.
- Dropdown/OutlinedButton/OutlinedTextField border = outline.
- 서울 밤은 네온을 장식 요소에만 사용하고 장문의 설정 화면에는 glow 남발 금지.

## THEMES
현재 로직 유지:
- `platform.readThemes()` 목록
- `ThemePreview(theme)`
- system/basic_light/basic_dark/seoul_day/seoul_night 등
- 서울 낮/밤 광고 24시간 해금
- 적용 중/광고 CTA/적용 버튼

UI 개선 허용 범위:
- 세로 Panel 나열 구조는 유지해도 되고, 동일 기능/스크롤 의미를 유지하는 범위에서 theme card 형태로 정돈 가능.
- 단, 광고 잠금 상태/24시간 남은 시간/현재 활성 상태가 항상 명확해야 한다.
- 서울 낮/밤 선택 즉시 본체 앱 시각 테마도 갱신한다.
- locked Seoul은 미리보기 가능, 실제 적용은 기존 광고 unlock gate를 통과해야 한다.

## ADVANCED_SETTINGS
플랫폼별 기존 content를 감싸는 shared container 색상만 테마화한다. 기능 구조 변경 금지.

## PHRASES / PHRASE_EDITOR
- 목록/편집 기능 구조 유지.
- phrase card = surfaceElevated.
- 편집 OutlinedTextField의 focus/accent/border만 테마화.
- 삭제/오류는 danger semantic color 유지.

## TEST
- 다중행 입력 필드와 글자수, 초기화 버튼 구조 유지.
- 테마 배경이 실제 키보드 입력 테스트 가독성을 방해하지 않게 입력 영역은 불투명 surface 사용.

## SELECT
- BASIC/SENTENCE/CONVERT/RAIN/CAFE 선택 후 모드/난이도 FilterChip 및 시작 버튼 구조 유지.
- 선택된 chip = accentContainer.
- 시작 버튼 = accent.

## PRACTICE
- Header, Prompt Panel, 3줄 단문 표시, AnswerField, 확인, 피드백, 종료 구조 유지.
- 일본어/한글발음/한국어 3줄의 정보 계층을 색으로만 과하게 구분하지 말고 typography + spacing 우선.
- AnswerField는 가장 높은 대비를 보장.

## RAIN
- `rain_city_alley.png` 게임 아트 유지.
- 테마는 상단 header, HUD panel, 버튼, 입력 필드/결과 overlay에만 적용.
- 게임 target 카드 위치/물리/타이밍 변경 금지.

## CAFE
- `cafe_background.png`, `cafe_customers.png` 유지.
- 테마는 header/HUD/입력/결과/설정 overlay에만 적용.
- 게임 난이도/주문 로직 변경 금지.

## FINISHING / RESULT
- 광고 대기/결과 화면의 카드와 버튼만 테마화.
- 성적 수치 자체의 색상은 semantic success/warning/danger를 사용.

## PROGRESS
- 세션 수/정확도/XP 기록을 surface card로 표시.
- 서울 테마 이미지는 기록 숫자 뒤에 사용하지 않는다.

## 시스템 키보드 본체
앱 시각 테마와 테마 ID를 공유하되 기존 네이티브 키보드 구조 유지.
- Android: `KoreanKeyboardService.kt`
- iOS: `KeyboardExtension/KeyboardViewController.swift`
- 키 배열/후보/툴바/빠른 설정 구조를 이번 앱 스킨 작업 때문에 변경하지 않는다.
- 기존 `shared/themes/themes.json` 팔레트는 키보드의 canonical palette로 계속 사용.
