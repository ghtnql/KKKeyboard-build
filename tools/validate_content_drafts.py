#!/usr/bin/env python3
"""Validate Drive content snapshots and build non-release staging candidates."""

from __future__ import annotations

import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DICT_DRAFT = ROOT / "shared/drafts/ja_dictionary_draft.json"
GAME_DRAFT = ROOT / "shared/drafts/learning_items_draft.json"
RELEASE_DICT = ROOT / "shared/dictionaries/ja_common.json"
RELEASE_GAMES = ROOT / "shared/content/learning_items.json"
OUTPUT = ROOT / "build/content-staging"

PRIORITY_BASE = {"manual_high": 200, "manual_medium": 1000, "manual_low": 2000}
DIFFICULTY = {"beginner": 1, "intermediate": 2, "advanced": 3}
PLAYABLE_GAME_TYPES = {"typing", "rain", "cafe", "sentence", "convert"}


def read_json(path: Path):
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def split(value: str | None) -> list[str]:
    return [part.strip() for part in (value or "").split("|") if part.strip()]


def unique(values: list[str]) -> list[str]:
    return list(dict.fromkeys(values))


def main() -> int:
    dictionary_draft = read_json(DICT_DRAFT)
    game_draft = read_json(GAME_DRAFT)
    release_dictionary = read_json(RELEASE_DICT)["entries"]
    release_games = read_json(RELEASE_GAMES)

    errors: list[str] = []
    warnings: list[str] = []
    if dictionary_draft.get("releaseEligible") is not False:
        errors.append("dictionary draft must remain releaseEligible=false")
    if game_draft.get("releaseEligible") is not False:
        errors.append("game draft must remain releaseEligible=false")

    # Compare against the original vocabulary, even after the user's draft has
    # been bundled, so rerunning this audit keeps the overlap counts meaningful.
    draft_ids = {row["id"] for row in dictionary_draft["rows"]}
    baseline_dictionary = [row for row in release_dictionary if row["id"] not in draft_ids]
    existing_ids = {row["id"] for row in baseline_dictionary}
    existing_aliases = {alias for row in baseline_dictionary for alias in row["aliases"]}
    existing_surfaces = {surface for row in baseline_dictionary for surface in row["surfaces"]}
    converted_dictionary = []
    merge_required = []
    seen_ids: set[str] = set()

    for index, row in enumerate(dictionary_draft["rows"], start=1):
        row_id = row.get("id") or ""
        if not row_id or row_id in seen_ids:
            errors.append(f"dictionary duplicate/blank id: {row_id!r}")
        seen_ids.add(row_id)
        if row.get("review_status") != "review_required":
            errors.append(f"{row_id}: unexpected review_status")

        raw_aliases = split(row.get("hangul_aliases"))
        aliases = unique([alias.replace(" ", "") for alias in raw_aliases])
        surfaces = unique(split(row.get("surfaces")))
        if not aliases or not surfaces:
            errors.append(f"{row_id}: aliases and surfaces are required")
            continue
        invalid_aliases = [alias for alias in aliases if not all("가" <= ch <= "힣" for ch in alias)]
        if invalid_aliases:
            errors.append(f"{row_id}: invalid normalized aliases {invalid_aliases}")

        priority_label = row.get("priority")
        if priority_label not in PRIORITY_BASE:
            errors.append(f"{row_id}: unknown priority {priority_label!r}")
            continue
        overlaps = {
            "ids": [row_id] if row_id in existing_ids else [],
            "aliases": sorted(set(aliases) & existing_aliases),
            "surfaces": sorted(set(surfaces) & existing_surfaces),
        }
        candidate = {
            "id": row_id,
            "priority": PRIORITY_BASE[priority_label] + index,
            "aliases": aliases,
            "surfaces": surfaces,
            "reviewStatus": row["review_status"],
            "metadata": {
                key: row.get(key)
                for key in ("kana_reading", "part_of_speech", "homophone_group", "usage_domain", "politeness", "source", "notes")
            },
        }
        if any(overlaps.values()):
            merge_required.append({"candidate": candidate, "overlaps": overlaps})
        else:
            converted_dictionary.append(candidate)

    draft_game_ids = {row["id"] for row in game_draft["rows"]}
    bundled_game_ids = {row["id"] for row in release_games}
    existing_game_ids = bundled_game_ids - draft_game_ids
    converted_games = []
    seen_game_ids: set[str] = set()
    unsupported_games = []
    for row in game_draft["rows"]:
        row_id = row.get("id") or ""
        if not row_id or row_id in seen_game_ids or row_id in existing_game_ids:
            errors.append(f"game duplicate/blank id: {row_id!r}")
        seen_game_ids.add(row_id)
        if row.get("review_status") != "review_required":
            errors.append(f"{row_id}: unexpected review_status")
        difficulty = DIFFICULTY.get(row.get("difficulty"))
        if difficulty is None:
            errors.append(f"{row_id}: unknown difficulty {row.get('difficulty')!r}")
            continue
        answers = unique(split(row.get("accepted_answers")))
        if not row.get("prompt") or not answers:
            errors.append(f"{row_id}: prompt and accepted answers are required")
            continue
        game_type = row.get("game_type")
        candidate = {
            "id": row_id,
            "category": row.get("topic"),
            "difficulty": difficulty,
            "sourceLanguage": "ja" if str(row.get("language_mode", "")).startswith("ja_") else "ko",
            "sourceText": row["prompt"],
            "targetLanguage": "ja" if str(row.get("language_mode", "")).startswith("ja_") else "ko",
            "acceptedAnswers": answers,
            "meaningHint": row.get("hint"),
            "enabledModes": [row.get("language_mode")],
            "gameTypes": [game_type],
            "reviewStatus": row["review_status"],
            "copyrightStatus": row.get("copyright_status"),
        }
        converted_games.append(candidate)
        if game_type not in PLAYABLE_GAME_TYPES:
            unsupported_games.append(row_id)

    if unsupported_games:
        warnings.append(f"{len(unsupported_games)} game rows use types with no current entry UI: convert")

    report = {
        "releaseEligible": False,
        "errors": errors,
        "warnings": warnings,
        "dictionary": {
            "draftRows": len(dictionary_draft["rows"]),
            "bundledDraftRows": len(draft_ids & {row["id"] for row in release_dictionary}),
            "convertedUniqueRows": len(converted_dictionary),
            "mergeRequiredRows": len(merge_required),
        },
        "games": {
            "draftRows": len(game_draft["rows"]),
            "bundledDraftRows": len(draft_game_ids & bundled_game_ids),
            "convertedRows": len(converted_games),
            "unsupportedGameTypeRows": len(unsupported_games),
        },
    }

    OUTPUT.mkdir(parents=True, exist_ok=True)
    outputs = {
        "ja_dictionary_candidates.json": {
            "releaseEligible": False,
            "reviewStatus": "review_required",
            "entries": converted_dictionary,
            "mergeRequired": merge_required,
        },
        "learning_item_candidates.json": {
            "releaseEligible": False,
            "reviewStatus": "review_required",
            "items": converted_games,
        },
        "validation_report.json": report,
    }
    for name, value in outputs.items():
        with (OUTPUT / name).open("w", encoding="utf-8") as handle:
            json.dump(value, handle, ensure_ascii=False, indent=2)
            handle.write("\n")

    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
