# Drive content drafts

이 디렉터리는 Google Drive `10_콘텐츠 마스터`의 `review_required` 스냅샷이다. 앱 빌드 입력이 아니며 Android/iOS 리소스로 패키징하지 않는다.

검증과 비출시 후보 생성:

```bash
python3 tools/validate_content_drafts.py
```

결과는 Git에서 제외된 `build/content-staging/`에 생성된다.

- `ja_dictionary_candidates.json`: 자동 변환 가능한 신규 후보와 수동 병합 필요 후보
- `learning_item_candidates.json`: 현재 앱 스키마로 변환한 게임 후보
- `validation_report.json`: 오류, 경고와 행 수

`review_required` 행은 이 디렉터리나 staging 결과에서 출시용 `shared/dictionaries/ja_common.json` 또는 `shared/content/learning_items.json`으로 자동 복사하지 않는다.

2026-09-28 사용자가 초안을 앱에 포함하고 직접 검수하겠다고 명시했다. 이 작업에서 `tools/promote_dictionary_draft.py`와 `tools/promote_learning_drafts.py`를 실행해 200개 사전·80개 학습 항목을 출시 자산에 반영했다. 원본의 `review_required` 및 저작권 검토 상태는 데이터에 남겨 두며, 검수 완료로 표시하지 않는다.
