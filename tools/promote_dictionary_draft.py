#!/usr/bin/env python3
"""Add the tracked Drive dictionary draft to the bundled vocabulary.

Legacy entries and priorities stay intact. New priorities start above the
legacy range so overlapping aliases retain their existing candidate order.
The original review status and source metadata travel with each new entry.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DRAFT = ROOT / "shared/drafts/ja_dictionary_draft.json"
RELEASE = ROOT / "shared/dictionaries/ja_common.json"
PRIORITY_BASE = {"manual_high": 200, "manual_medium": 1000, "manual_low": 2000}
MAX_ALIAS_LENGTH = 64


def split(value: str, *, remove_spaces: bool = False) -> list[str]:
    values = [part.strip() for part in value.split("|") if part.strip()]
    if remove_spaces:
        values = [part.replace(" ", "") for part in values]
    return list(dict.fromkeys(values))


def converted(row: dict, index: int) -> dict:
    aliases = split(row["hangul_aliases"], remove_spaces=True)
    surfaces = split(row["surfaces"])
    assert aliases and surfaces, row["id"]
    assert all(
        1 <= len(alias) <= MAX_ALIAS_LENGTH and all("가" <= ch <= "힣" for ch in alias)
        for alias in aliases
    ), row["id"]
    assert row["review_status"] == "review_required", row["id"]
    return {
        "id": row["id"],
        "priority": PRIORITY_BASE[row["priority"]] + index,
        "aliases": aliases,
        "surfaces": surfaces,
        "reviewStatus": row["review_status"],
        "metadata": {
            key: row.get(key)
            for key in (
                "kana_reading", "part_of_speech", "homophone_group",
                "usage_domain", "politeness", "source", "notes",
            )
        },
    }


def main() -> None:
    draft = json.loads(DRAFT.read_text(encoding="utf-8"))
    release = json.loads(RELEASE.read_text(encoding="utf-8"))
    assert draft["releaseEligible"] is False
    entries = release["entries"]
    assert len({entry["id"] for entry in entries}) == len(entries)
    originals = [entry for entry in entries if not entry["id"].startswith("dict_ja_")]
    assert len(originals) == 100, "Unexpected legacy dictionary size"
    assert max(entry["priority"] for entry in originals) < min(PRIORITY_BASE.values())
    assert len(draft["rows"]) == 200
    incoming = [converted(row, index) for index, row in enumerate(draft["rows"], 1)]
    assert len({entry["id"] for entry in incoming}) == len(incoming)
    assert not ({entry["id"] for entry in originals} & {entry["id"] for entry in incoming})
    # Replacing only the generated entries makes reruns deterministic.
    release["entries"] = originals + incoming
    release["reviewStatus"] = "mixed_machine_checked_and_review_required"
    RELEASE.write_text(json.dumps(release, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(originals)} legacy entries preserved; {len(incoming)} draft entries added; {len(release['entries'])} total")


if __name__ == "__main__":
    main()
