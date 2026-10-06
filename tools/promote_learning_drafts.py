#!/usr/bin/env python3
"""Include the user's Drive learning draft in the bundled game content."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DRAFT = ROOT / "shared/drafts/learning_items_draft.json"
RELEASE = ROOT / "shared/content/learning_items.json"
JA_MODE = "ja_to_hangul_pronunciation"
KO_MODE = "ko_same_hangul"
GAME_TYPES = {"typing", "rain", "convert", "sentence", "cafe"}
DRAFT_ROWS = 540
BASELINE_ROWS = 23

_HINT_PREFIX = re.compile(r"^\s*일본어\s*:\s*", flags=0)
# Normalization allowed ONLY for trailing Japanese sentence punctuation.
_TRAILING_JA_PUNCT = "。．！？!?."


def split(value):
    return list(dict.fromkeys(part.strip() for part in value.split("|") if part.strip()))


def _clean_ko_hint(hint):
    """Remove leading '일본어:' prefix and trim; preserve source spelling otherwise."""
    return _HINT_PREFIX.sub("", hint.strip(), count=1).strip()


def _norm_ja_prompt(value):
    return value.strip().rstrip(_TRAILING_JA_PUNCT).strip()


def _draft_korean_text(row):
    for key in ("meaning_ko", "meaningKo", "koreanText", "korean_text"):
        value = row.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def build_learning_items(draft, release, expected_draft_rows=DRAFT_ROWS,
                         expected_baseline_rows=BASELINE_ROWS):
    assert len(draft) == expected_draft_rows and \
        len({row["id"] for row in draft}) == expected_draft_rows
    draft_ids = {row["id"] for row in draft}
    original = [row for row in release if row["id"] not in draft_ids]
    assert len(original) == expected_baseline_rows, "Bundled baseline changed; review the merge"
    # JA sentence lookup: exact Japanese prompt -> primary_answer.
    ja_exact = {}
    ja_norm = {}
    for row in draft:
        if row.get("language_mode") == JA_MODE and row.get("game_type") == "sentence":
            key = row["prompt"].strip()
            ja_exact.setdefault(key, []).append(row["primary_answer"].strip())
            ja_norm.setdefault(_norm_ja_prompt(key), []).append(row["primary_answer"].strip())
    promoted = []
    for row in draft:
        assert row["language_mode"] in (JA_MODE, KO_MODE) and row["game_type"] in GAME_TYPES
        assert row["review_status"] == "review_required"
        answers = split(row["accepted_answers"])
        assert row["primary_answer"] in answers and row["prompt"] and answers
        lang = "ja" if row["language_mode"].startswith("ja_") else "ko"
        game_types = [row["game_type"]]
        # Every Japanese draft is also a Cafe order. Its original game type remains playable.
        if lang == "ja" and "cafe" not in game_types:
            game_types.append("cafe")
        item = {
            "id": row["id"],
            "category": row["topic"],
            "difficulty": {"beginner": 1, "intermediate": 2}[row["difficulty"]],
            "sourceLanguage": lang,
            "sourceText": row["prompt"],
            "targetLanguage": lang,
            "acceptedAnswers": answers,
            "meaningHint": row.get("hint"),
            "enabledModes": [row["language_mode"]],
            "gameTypes": game_types,
            "source": row["source"],
            "reviewStatus": row["review_status"],
            "copyrightStatus": row["copyright_status"],
        }
        if row["game_type"] == "sentence":
            if lang == "ja":
                japanese_text = row["prompt"].strip()
                pronunciation = row["primary_answer"].strip()
                korean_text = _draft_korean_text(row)
            else:
                hint = (row.get("hint") or "").strip()
                assert hint, f"KO sentence {row['id']} missing Japanese hint"
                japanese_text = _clean_ko_hint(hint)
                assert japanese_text, f"KO sentence {row['id']} missing japaneseText after hint cleanup"
                candidates = ja_exact.get(japanese_text)
                if candidates is None:
                    candidates = ja_norm.get(_norm_ja_prompt(japanese_text), [])
                unique = list(dict.fromkeys(c.strip() for c in candidates if c.strip()))
                assert unique, f"KO sentence {row['id']} has no matching JA draft for {japanese_text!r}"
                assert len(unique) == 1, f"KO sentence {row['id']} ambiguous pronunciation for {japanese_text!r}"
                pronunciation = unique[0]
                korean_text = row["prompt"].strip()
            assert japanese_text and pronunciation and korean_text, \
                f"Sentence {row['id']} missing one of japaneseText/japaneseHangulPronunciation/koreanText"
            item["japaneseText"] = japanese_text
            item["japaneseHangulPronunciation"] = pronunciation
            item["koreanText"] = korean_text
        promoted.append(item)
    rows = original + promoted
    assert len(rows) == expected_baseline_rows + expected_draft_rows
    assert len({row["id"] for row in rows}) == expected_baseline_rows + expected_draft_rows
    return rows


def main():
    draft = json.loads(DRAFT.read_text(encoding="utf-8"))["rows"]
    release = json.loads(RELEASE.read_text(encoding="utf-8"))
    rows = build_learning_items(draft, release)
    RELEASE.write_text("[\n" + ",\n".join("  " + json.dumps(row, ensure_ascii=False, separators=(",", ":")) for row in rows) + "\n]\n", encoding="utf-8")
    print(f"Bundled {len(rows) - len(draft)} existing and {len(draft)} Drive draft learning items")


if __name__ == "__main__":
    main()
