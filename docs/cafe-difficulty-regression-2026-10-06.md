# 2026-10-06 카페 난이도 표시·장문 출제 수정

실제 CafeDifficulty RELAXED/NORMAL/RUSH를 UiStrings가 EASY/HARD 문자열로 판단해 세 버튼이 모두 보통으로 표시됐다. 시간/숨김 외 문항 난이도나 길이는 고려하지 않고 전체 cafe 목록을 섞어 출제해 difficulty1로 저장된 장문도 보통에 나왔다.

실제 enum 식별자와 저장된 기록 키를 유지하고 한국어 쉬움/보통/어려움, 일본어/영어 대응 표시를 수정했다. 공통 forCafe는 기존 모드/gameType 조건에 콘텐츠 난이도와 길이 제한을 적용한다. 쉬움은 level1/4자, 보통은 level1~2/8자, 어려움은 level1~3/12자까지다. NFC 정규화 뒤 공백 제외 길이를 원문·일본어·한글 발음·한국어 표기·모든 정답 별칭에 적용한다. 뜻 설명은 입력 대상이 아니므로 별도 계산하지 않는다. 문장을 자르거나 정답을 바꾸지 않는다. 후보가 없다고 전체 장문 목록으로 돌아가지 않는다. CafeSession도 입력 목록을 방어적으로 같은 필터로 검사한다.

선택 화면의 개수와 start 출제가 같은 selectedItems/forCafe 경로를 쓴다. 선택한 난이도의 글자 제한·첫 주문 시간을 표시하고 카페 문항은 ‘주문’으로 안내한다. 누적 후보 방식으로 짧은 주문은 더 높은 난이도에도 등장할 수 있다. 기존 20/13/9초·후속 주문 시간 감소·어려움2.5초 뒤 숨김·매칭·점수는 유지한다. 긴 문장 콘텐츠는 단문 연습 등에 남긴다.

실제 번들 검사: 한국어 쉬움26/보통63/어려움63, 일본어74/177/220. 공통 모델6+기존 학습 세션14+실제 Compose UI3=23개 PASS/실패·skip0. 난이도1 장문, 번역 줄/별칭 초과, 빈 후보, NFC 한글, 표시 언어3종, 실제 버튼/개수 변경, 장문+짧은 단어 fixture에서 보통 start를 검사했다. 일본어 보통 선택/짧은 실제 게임 캡처를 직접 확인했다. 테스트 첫 컴파일은 Android legacy 동명 enum과의 import 충돌로 실패했으며 shared enum alias로 고쳐 전체23개 통과했다.

Android 실제 launcher ComposeMainActivity와 IME 설정/테마 진입은 sharedUI를 사용하며, iOS AppDelegate도 같은 SharedUI MainViewController를 사용한다. 양쪽 learningItems 어댑터가 같은 JSON의 difficulty·원문·정답·triad를 전달하는 것을 대조했다. iOS 카페 로직/화면은 이번 공통 변경에 함께 반영되지만 새 Swift/Xcode archive·실기기 검증·TestFlight 배포는 아직 하지 않았다. 기존 Play300/TestFlight22는 이번 난이도 수정 포함 빌드가 아니다. 정식 APK302 먼저 전달해 사용자 검사 후 후속 릴리스 gate를 따른다.

Muse Meta muse-spark-1.3-contributor 최초2파일 작업 실패/부분 diff없음 → 번역1파일 축소 성공 → 모델1파일 후속 성공. 실제 diff를 총괄 검토·통합하고 UI/23개 검사·렌더링을 검증했다. GPT 대체 위임 없음, 사용량/비용 데이터 없음. 원래 사용자 미커밋5파일 유지.

APK302 실제 커밋·해시·기존 서명/301 업데이트·공유 전체 읽기·Drive 전체 다운로드 검사는 완료 후 공유 진척 문서와 배포 JSON에 기록한다.

최종 정식 APK302 source a3f58e87da44fd0b0bba2347805ee2f98a09568a 전달 완료. 34,068,570bytes SHA05ff346bcb58e2edb876cef3b358fd52f61f01125a695e493089440de18175e2. release 운영 광고/기존 upload 서명/CRC 검증, API36 301→302 업데이트와 새 설치·실행 통과. 공유 전체 파일과 Drive 전체 다운로드 해시 일치, 원래 APK 배포 폴더 최종302 하나. Drive301은 fresh metadata에서 외부 피드백 폴더로 이동돼 있어 수정하지 않고 새302를 원래 폴더에 업로드했다. 새 iOS Xcode/TestFlight 미실행·사용자302 테스트 대기.
