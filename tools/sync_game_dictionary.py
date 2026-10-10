#!/usr/bin/env python3
"""Ensure Japanese game prompt readings can produce the displayed Japanese surface.

Only promotes already-bundled, user-facing Japanese learning content. It does not
transcribe, translate, normalize, or add unknown words from external sources.
"""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DICTIONARY = ROOT / "shared/dictionaries/ja_common.json"
LEARNING = ROOT / "shared/content/learning_items.json"
HANGUL = re.compile(r"[가-힣]{1,64}\Z")


def sync(dictionary: dict, learning: list[dict]) -> tuple[dict, dict]:
    entries = dictionary["entries"]
    used_ids = {entry["id"] for entry in entries}
    by_surface: dict[str, list[dict]] = {}
    for entry in entries:
        for surface in entry["surfaces"]:
            by_surface.setdefault(surface, []).append(entry)

    created = 0
    aliases_added = 0
    next_priority = max((int(e["priority"]) for e in entries), default=0) + 1
    for item in learning:
        if item.get("sourceLanguage") != "ja":
            continue
        target = item.get("sourceText", "")
        if not target:
            continue
        for raw_alias in item.get("acceptedAnswers", []):
            alias = raw_alias.replace(" ", "")
            if not HANGUL.fullmatch(alias):
                raise ValueError(f"Unexpected non-Hangul game alias for {item['id']}: {raw_alias!r}")
            existing = by_surface.get(target, [])
            if any(alias in entry["aliases"] for entry in existing):
                continue
            single = next((e for e in existing if e["surfaces"] == [target]), None)
            if single is not None:
                single["aliases"].append(alias)
            else:
                entry_id = f"game_{item['id']}"
                if entry_id in used_ids:
                    raise ValueError(f"Duplicate new dictionary ID: {entry_id}")
                single = {"id": entry_id, "priority": next_priority, "aliases": [alias], "surfaces": [target]}
                used_ids.add(entry_id)
                entries.append(single)
                by_surface.setdefault(target, []).append(single)
                next_priority += 1
                created += 1
            aliases_added += 1

    # Check the actual dictionary's literal matching and its first-eight limit.
    alias_hits: dict[str, list[str]] = {}
    for entry in sorted(entries, key=lambda e: (e["priority"], e["id"])):
        for alias in entry["aliases"]:
            found = alias_hits.setdefault(alias, [])
            for surface in entry["surfaces"]:
                if surface not in found:
                    found.append(surface)
    checked = 0
    missing = []
    for item in learning:
        if item.get("sourceLanguage") != "ja":
            continue
        for raw_alias in item.get("acceptedAnswers", []):
            alias = raw_alias.replace(" ", "")
            checked += 1
            if item["sourceText"] not in alias_hits.get(alias, [])[:8]:
                missing.append((item["id"], alias, item["sourceText"]))
    if missing:
        raise ValueError(f"{len(missing)} game inputs have no selectable dictionary target: {missing[:5]}")
    return dictionary, {"createdEntries": created, "addedAliases": aliases_added, "verifiedGameReadings": checked, "missing": len(missing)}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    source = json.loads(DICTIONARY.read_text(encoding="utf-8"))
    learning = json.loads(LEARNING.read_text(encoding="utf-8"))
    updated, report = sync(source, learning)
    if args.apply:
        DICTIONARY.write_text(json.dumps(updated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(dict(report, applied=args.apply), ensure_ascii=False))


if __name__ == "__main__":
    main()
