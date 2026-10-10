import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from promote_learning_drafts import build_learning_items


def ja_sentence(i, **kw):
    row = {
        "id": f"ja{i}", "language_mode": "ja_to_hangul_pronunciation",
        "game_type": "sentence", "topic": "t", "difficulty": "beginner",
        "prompt": f"日本語{i}です", "primary_answer": f"한굴{i}",
        "accepted_answers": f"한굴{i}", "hint": "ko hint",
        "meaning_ko": f"한국어{i}", "source": "drive",
        "review_status": "review_required", "copyright_status": "ok",
    }
    row.update(kw)
    return row


def ko_sentence(i, ja_prompt, prefixed=True, **kw):
    hint = f"일본어: {ja_prompt}" if prefixed else ja_prompt
    row = {
        "id": f"ko{i}", "language_mode": "ko_same_hangul",
        "game_type": "sentence", "topic": "t", "difficulty": "beginner",
        "prompt": f"한국어{i}", "primary_answer": f"한국어{i}",
        "accepted_answers": f"한국어{i}|한국어{i} ",
        "hint": hint, "source": "drive",
        "review_status": "review_required", "copyright_status": "ok",
    }
    row.update(kw)
    return row


def typing_row(i, lang="ja"):
    mode = "ja_to_hangul_pronunciation" if lang == "ja" else "ko_same_hangul"
    return {
        "id": f"w{i}", "language_mode": mode, "game_type": "typing",
        "topic": "t", "difficulty": "beginner", "prompt": f"p{i}",
        "primary_answer": f"a{i}", "accepted_answers": f"a{i}",
        "hint": "h", "source": "drive",
        "review_status": "review_required", "copyright_status": "ok",
    }


class PromoteTest(unittest.TestCase):
    def test_baseline_extra_preserved(self):
        base = {"id": "b1", "custom": {"x": 1}, "extra": [1]}
        draft = [typing_row(1)]
        rows = build_learning_items(draft, [dict(base)], 1, 1)
        self.assertEqual(rows[0], base)

    def test_ja_sentence_fields(self):
        draft = [ja_sentence(1)]
        rows = build_learning_items(draft, [], 1, 0)
        item = rows[0]
        self.assertEqual(item["japaneseText"], "日本語1です")
        self.assertEqual(item["japaneseHangulPronunciation"], "한굴1")
        self.assertEqual(item["koreanText"], "한국어1")
        self.assertEqual(item["acceptedAnswers"], ["한굴1"])
        self.assertEqual(item["enabledModes"], ["ja_to_hangul_pronunciation"])
        self.assertEqual(item["reviewStatus"], "review_required")

    def test_ko_sentence_prefixed_and_raw_hint(self):
        jp = "日本語9です"
        ja = ja_sentence(9, prompt=jp)
        ko_p = ko_sentence(1, jp, prefixed=True, prompt="한국어9",
                         primary_answer="한국어9",
                         accepted_answers="한국어9|한국어9")
        ko_r = ko_sentence(2, jp, prefixed=False, id="ko2", prompt="한국어9",
                         primary_answer="한국어9",
                         accepted_answers="한국어9|한국어9")
        rows = build_learning_items([ja, ko_p, ko_r], [], 3, 0)
        by_id = {r["id"]: r for r in rows}
        for kid in ("ko1", "ko2"):
            self.assertEqual(by_id[kid]["japaneseText"], jp)
            self.assertEqual(by_id[kid]["japaneseHangulPronunciation"], "한굴9")
            self.assertEqual(by_id[kid]["koreanText"], "한국어9")
            self.assertEqual(by_id[kid]["acceptedAnswers"], ["한국어9"])

    def test_ko_missing_pair_fails(self):
        with self.assertRaises(AssertionError):
            build_learning_items([ko_sentence(1, "存在しない文")], [], 1, 0)

    def test_ko_ambiguous_pronunciation_fails(self):
        jp = "同じ文です"
        a = ja_sentence(1, prompt=jp, primary_answer="하",
                        accepted_answers="하", meaning_ko="m1")
        b = ja_sentence(2, id="ja2", prompt=jp, primary_answer="히",
                        accepted_answers="히", meaning_ko="m2")
        with self.assertRaises(AssertionError):
            build_learning_items([a, b, ko_sentence(1, jp)], [], 3, 0)

    def test_trailing_ja_punct_fallback_retains_spelling(self):
        base = "日本語9です"
        punctuated = base + "。"
        ja = ja_sentence(9, prompt=base)
        ko = ko_sentence(1, punctuated, prefixed=True, prompt="한국어9",
                         primary_answer="한국어9",
                         accepted_answers="한국어9|한국어9")
        rows = build_learning_items([ja, ko], [], 2, 0)
        by_id = {r["id"]: r for r in rows}
        self.assertEqual(by_id["ko1"]["japaneseText"], punctuated)
        self.assertEqual(by_id["ko1"]["japaneseHangulPronunciation"], "한굴9")
        self.assertEqual(by_id["ko1"]["koreanText"], "한국어9")

    def test_missing_ja_meaning_ko_fails(self):
        jp = "日本語9です"
        ja = ja_sentence(9, prompt=jp)
        del ja["meaning_ko"]
        ko = ko_sentence(1, jp, prefixed=True, prompt="한국어9",
                         primary_answer="한국어9",
                         accepted_answers="한국어9|한국어9")
        with self.assertRaises(AssertionError):
            build_learning_items([ja, ko], [], 2, 0)

    def test_duplicate_ids_and_review_status_not_autoapproved(self):
        with self.assertRaises(AssertionError):
            build_learning_items([typing_row(1), typing_row(1, lang="ko")],
                                 [], 2, 0)
        bad = typing_row(2)
        bad["id"] = "w9"
        bad["review_status"] = "approved"
        with self.assertRaises(AssertionError):
            build_learning_items([bad], [], 1, 0)


if __name__ == "__main__":
    unittest.main()
