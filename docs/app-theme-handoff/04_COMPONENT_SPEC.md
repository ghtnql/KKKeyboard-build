# 공통 Compose 컴포넌트 적용 명세

## 1. AppThemeSpec
`KeyboardThemeChoice` 또는 현재 저장된 theme ID를 입력으로 받아 공통 앱 시각 토큰을 반환한다.

권장 역할:
- `colorScheme`
- `backgroundArtwork`
- `artworkHeaderAlpha`
- semantic colors(success/warning/danger)
- surface hierarchy

`system`은 OS dark/light에 따라 basic_light/basic_dark 앱 스펙을 선택한다.
`basic_light`는 기존 현재 Light UI를 최대한 유지한다.
`basic_dark`, `seoul_day`, `seoul_night`는 `02_APP_THEME_TOKENS.json`을 따른다.

## 2. ThemeBackdrop
루트 배경용 공통 composable.

- `basic_dark`: artwork 없음.
- Seoul: canonical 서울 이미지 + theme background + gradient protection.
- 긴 설정/학습 화면은 이미지보다 surface가 우선.
- 이미지가 로드되지 않아도 solid color fallback으로 모든 기능 사용 가능해야 한다.

## 3. Header
현재 `Header(title, subtitle)`의 구조 유지.
- title: `textPrimary`
- subtitle: `textSecondary`
- 서울 테마에서 배경 아트가 겹치면 자동으로 surface scrim 또는 gradient 아래에 표시.

## 4. Panel
현재 `Card(fillMaxWidth) + padding(18dp) + gap(10dp)` 유지.
- radius 16dp
- background `surface` 또는 `surfaceElevated`
- border 1dp `outline`의 낮은 alpha
- 일반 다크: 그림자 최소
- 서울 낮: 아주 약한 shadow
- 서울 밤: glow 대신 얕은 cool shadow/border 사용. 설정 화면 전체가 번쩍이지 않게 한다.

## 5. HomeButton
현재 높이 52dp 유지.

primary=true:
- container = accent
- content = onAccent

primary=false:
- container = surfaceMuted
- content = textPrimary
- border = outline 1dp 허용

shape = 10dp radius.

## 6. NavButton / OutlinedButton
- background는 투명 또는 surface
- outline = theme outline
- title = textPrimary
- subtitle = textSecondary
- focus/selected 시 accent border 2dp

## 7. FilterChip
- selected background = accentContainer
- selected text = onAccentContainer
- unselected background = surface
- border = outline
- 서울 밤에서도 neon full glow 금지.

## 8. Switch / Slider
- active track/thumb = accent
- inactive = surfaceMuted / outline
- accessible contrast 유지.

## 9. OutlinedTextField
- background = surfaceElevated (가능하면 불투명)
- normal border = outline
- focused border = accent 2dp
- text = textPrimary
- placeholder/label = textSecondary
- 연습/테스트 화면에서 배경 이미지가 글자 뒤로 직접 비치지 않음.

## 10. StatusLine
- ACTIVE/ENABLED: success
- not ready/disabled: warning
- 오류: danger
- 상태를 색 하나로만 전달하지 말고 현재 문구 유지.

## 11. 상단 back/title row
현재 페이지 그래프와 버튼 위치 유지.
- back/title = textPrimary
- sound action = accent 또는 textPrimary
- 하단 구분선 필요 시 outline 1dp.

## 12. ThemePreview
현재 preview asset과 실제 keyboard theme가 불일치하지 않게 유지.
- 서울 낮/밤 preview는 기존 승인 asset 사용.
- app-wide 적용 후 preview 아래 설명에 “앱 + 키보드” 적용 의미가 드러나도록 로컬라이즈 문구 조정 가능.
- BM lock/24h remaining/current active 표시 유지.

## 13. 게임 화면
`RainGame`/`CafeGame`의 아트와 gameplay 영역은 테마 토큰으로 덮지 않는다.
테마 적용 범위는 chrome/HUD/input/dialog/result만.

## 14. iOS Keyboard Extension quick settings
앱 전체 테마 작업과 별개로 네이티브 Extension 내부 구조를 새로 디자인하지 않는다.
현재 공유 테마 ID를 읽어 palette/background만 반영한다.
