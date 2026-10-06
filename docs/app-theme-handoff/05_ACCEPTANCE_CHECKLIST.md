# APK 기준 완료 체크리스트

문서/목업 완성이 아니라 실제 앱 결과가 기준이다.

## 빌드
- [ ] Linux 로컬 `debug APK` 성공
- [ ] Linux 로컬 `feedback APK` 성공
- [ ] 관련 unit/common UI tests 성공
- [ ] lint 오류 0
- [ ] Android GitHub Actions로 APK/AAB 반복 빌드하지 않음

## 구조 보존
- [ ] 기존 `Page` 전부 접근 가능
- [ ] HOME 버튼/페이지 이동 의미 변경 없음
- [ ] 설정 항목 누락 없음
- [ ] 상용구 CRUD 동작 유지
- [ ] 키보드 테스트 동작 유지
- [ ] BASIC/SENTENCE/CONVERT/Rain/Cafe 진입 가능
- [ ] 결과/XP/기록 화면 동작 유지
- [ ] 광고 privacy UI 유지
- [ ] 서울 테마 광고 unlock/24시간 상태 유지

## 앱 전체 테마
각각 실제 APK에서 확인:

### 일반 다크
- [ ] HOME 전체가 다크
- [ ] SETTINGS 전체가 다크
- [ ] THEMES 전체가 다크
- [ ] PHRASES/EDITOR/TEST/SELECT/PRACTICE/RESULT/PROGRESS 전체가 다크
- [ ] Rain/Cafe의 chrome/HUD가 다크, 게임 아트는 유지
- [ ] 시스템 키보드가 같은 `basic_dark` 테마

### 서울 낮
- [ ] HOME에 서울 낮 분위기 반영
- [ ] SETTINGS/THEMES/학습 화면도 색/카드/입력/버튼이 서울 낮 스킨
- [ ] 서울 이미지는 장식으로만 사용되고 글자 가독성 유지
- [ ] 시스템 키보드가 같은 `seoul_day`
- [ ] 24시간 unlock gate 유지

### 서울 밤
- [ ] HOME에 서울 밤 분위기 반영
- [ ] SETTINGS/THEMES/학습 화면도 서울 밤 스킨
- [ ] 장문 설정 화면에 neon glow 남발 없음
- [ ] 입력 필드/카드 가독성 유지
- [ ] 시스템 키보드가 같은 `seoul_night`
- [ ] 24시간 unlock gate 유지

## 상태 변경
- [ ] 테마 적용 즉시 Compose 본체 앱 색상도 변경
- [ ] 앱 재시작 후 선택 유지
- [ ] Android 키보드 재오픈 후 같은 테마 유지
- [ ] iOS Compose 앱에서 같은 테마 토큰 사용
- [ ] iOS Keyboard Extension은 공유 테마 설정을 읽음

## 가독성
- [ ] 밝은 배경에서 흰 글자가 날아가지 않음
- [ ] 어두운 배경에서 muted text가 지나치게 흐리지 않음
- [ ] 모든 주요 CTA 대비 충분
- [ ] OutlinedTextField 커서/텍스트/label 식별 가능
- [ ] disabled/selected/focused 상태가 구별됨

## 성능
- [ ] 서울 배경 이미지 때문에 첫 화면이 눈에 띄게 느려지지 않음
- [ ] 스크롤 중 이미지 재디코딩/깜빡임 없음
- [ ] image unavailable 시 solid fallback 정상
- [ ] 키보드 입력 지연에 영향 없음

## 완료 보고 시 첨부
- 실제 Android APK 경로/파일명
- 3개 테마 각각 HOME/SETTINGS/THEMES/PRACTICE 실제 캡처
- 시스템 키보드 실제 캡처
- 테스트 결과 요약
- 남은 실기기/iOS blocker
