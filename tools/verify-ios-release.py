#!/usr/bin/env python3
"""Check the exported iOS bundle against the exact release snapshot."""
import argparse
import hashlib
import json
import os
import plistlib
import re
from pathlib import Path

SAMPLE_PUBLISHER = "3940256099942544"
_APP_ID_RE = re.compile(r"ca-app-pub-([0-9]+)~[0-9]+")
_AD_UNIT_RE = re.compile(r"ca-app-pub-([0-9]+)/[0-9]+")


def verify_production_ads(info):
    """Fail fast unless the exported app Info.plist carries real operating ads.

    Compares Info.plist keys GADApplicationIdentifier / KKRewardedAdUnitID /
    KKInterstitialAdUnitID against expected KK_ADMOB_IOS_* env vars, rejects
    blank/sample/mismatched IDs, and requires KKUseTestAds to be boolean False.
    Errors name the setting without echoing raw values. Returns a concise
    manifest dict (IDs are public, not credentials).
    """
    expected = {
        "GADApplicationIdentifier": os.environ.get("KK_ADMOB_IOS_APP_ID", ""),
        "KKRewardedAdUnitID": os.environ.get("KK_ADMOB_IOS_REWARDED_ID", ""),
        "KKInterstitialAdUnitID": os.environ.get("KK_ADMOB_IOS_INTERSTITIAL_ID", ""),
    }
    patterns = {
        "GADApplicationIdentifier": _APP_ID_RE,
        "KKRewardedAdUnitID": _AD_UNIT_RE,
        "KKInterstitialAdUnitID": _AD_UNIT_RE,
    }
    publishers = {}
    for key, value in expected.items():
        if not isinstance(value, str) or value == "":
            raise ValueError(f"{key} expected operating ID is blank or missing")
        match = patterns[key].fullmatch(value)
        if not match:
            raise ValueError(f"{key} expected operating ID has invalid syntax")
        if match.group(1) == SAMPLE_PUBLISHER:
            raise ValueError(f"{key} expected operating ID uses sample publisher")
        publishers[key] = match.group(1)
    if len(set(publishers.values())) != 1:
        raise ValueError("AdMob publisher mismatch between operating IDs")
    for key, want in expected.items():
        actual = info.get(key)
        if not isinstance(actual, str) or actual == "":
            raise ValueError(f"{key} is blank or missing in exported Info.plist")
        if actual != want:
            raise ValueError(f"{key} in exported Info.plist does not match operating ID")
        match = patterns[key].fullmatch(actual)
        if not match:
            raise ValueError(f"{key} in exported Info.plist has invalid syntax")
        if match.group(1) == SAMPLE_PUBLISHER:
            raise ValueError(f"{key} in exported Info.plist uses sample publisher")
        if match.group(1) != publishers[key]:
            raise ValueError(f"{key} in exported Info.plist has unexpected publisher")
    if info.get("KKUseTestAds") is not False:
        raise ValueError("KKUseTestAds must be boolean false in exported Info.plist")
    return {
        "app_id": info["GADApplicationIdentifier"],
        "rewarded_id": info["KKRewardedAdUnitID"],
        "interstitial_id": info["KKInterstitialAdUnitID"],
        "test_ads": False,
    }


def verify(app, source_root, build_number):
    extension = app / "PlugIns/KKKeyboardExtension.appex"
    manifest = {"build_number": build_number, "bundles": {}, "assets": []}
    stamp = (source_root / "BUILD_SOURCE.txt").read_text()
    match = re.search(r"^Source commit: ([0-9a-f]{40})$", stamp, re.MULTILINE)
    if not match:
        raise ValueError("Missing exact release source commit")
    manifest["source_commit"] = match.group(1)
    app_info = None
    for label, bundle, identifier in (
        ("app", app, "com.ghtnql.kkkeyboard"),
        ("extension", extension, "com.ghtnql.kkkeyboard.keyboard"),
    ):
        with (bundle / "Info.plist").open("rb") as stream:
            info = plistlib.load(stream)
        if label == "app":
            app_info = info
        if info["CFBundleIdentifier"] != identifier or str(info["CFBundleVersion"]) != build_number:
            raise ValueError(f"{label} package or build number mismatch")
        if info.get("CADisableMinimumFrameDurationOnPhone") is not True:
            raise ValueError(f"{label} must enable CADisableMinimumFrameDurationOnPhone for Compose")
        manifest["bundles"][label] = {
            "identifier": identifier,
            "version": info["CFBundleShortVersionString"],
            "build": str(info["CFBundleVersion"]),
            "compose_frame_duration_enabled": True,
        }
    if manifest["bundles"]["app"]["version"] != manifest["bundles"]["extension"]["version"]:
        raise ValueError("App and extension marketing versions differ")
    manifest["ads"] = verify_production_ads(app_info)
    drawable = source_root / "sharedUI/src/commonMain/composeResources/drawable"
    required = [(path, app) for path in sorted(drawable.glob("*.png"))]
    if len(required) < 8:
        raise ValueError("Expected common game and theme image catalog")
    required += [(source_root / "shared/themes" / name, extension) for name in (
        "seoul_day.jpg", "seoul_day_portrait.jpg", "seoul_night.jpg", "seoul_night_portrait.jpg",
    )]
    required += [(source_root / "shared/themes/themes.json", app)]
    required += [(source_root / "shared/content/learning_items.json", app)]
    required += [(source_root / "shared/dictionaries/ja_common.json", extension)]
    audio = sorted((source_root / "shared/audio").glob("*.wav"))
    if len(audio) != 9:
        raise ValueError("Expected 3 BGM and 6 sound effect WAV assets")
    required += [(path, app) for path in audio]
    required += [(source_root / "shared/phrases/default_phrases.json", bundle) for bundle in (app, extension)]
    for source, bundle in required:
        matches = [path for path in bundle.rglob(source.name)
                   if "PlugIns" not in path.relative_to(bundle).parts]
        if len(matches) != 1:
            raise ValueError(f"Expected one bundled {source.name}, found {len(matches)}")
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        if hashlib.sha256(matches[0].read_bytes()).hexdigest() != digest:
            raise ValueError(f"Bundled asset differs from release source: {source.name}")
        manifest["assets"].append({"path": str(matches[0].relative_to(app)), "sha256": digest})
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app", type=Path, required=True)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--build-number", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    manifest = verify(args.app, args.source_root, args.build_number)
    args.output.write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Verified iOS build {args.build_number}: source {manifest['source_commit']}, "
          f"{len(manifest['assets'])} bundled assets")


if __name__ == "__main__":
    main()
