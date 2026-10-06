#!/usr/bin/env python3
"""Allocate the next iOS build number from all App Store Connect builds."""

import base64
import json
import os
import re
import subprocess
import tempfile
import time
import urllib.parse
import urllib.request
from pathlib import Path


API_ROOT = "https://api.appstoreconnect.apple.com"
BUNDLE_ID = "com.ghtnql.kkkeyboard"
BUILD_VERSION = re.compile(r"[0-9]+(?:\.[0-9]+){0,2}\Z")


def next_build_number(versions):
    """Use a new major component so it exceeds every existing dotted version."""
    highest = 0
    for version in versions:
        if not isinstance(version, str) or not BUILD_VERSION.fullmatch(version):
            raise ValueError(f"Unrecognized App Store Connect build version: {version!r}")
        components = version.split(".")
        if any(len(part) > (4 if index == 0 else 2) for index, part in enumerate(components)):
            raise ValueError(f"Unrecognized App Store Connect build version: {version!r}")
        highest = max(highest, int(components[0]))
    if highest >= 9999:
        raise ValueError("iOS build number exhausted: first component is already 9999")
    return highest + 1


def _b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=")


def _der_length(data, pos):
    value = data[pos]
    pos += 1
    if value < 128:
        return value, pos
    count = value & 127
    if not 1 <= count <= 4 or pos + count > len(data):
        raise ValueError("Invalid OpenSSL signature length")
    return int.from_bytes(data[pos:pos + count], "big"), pos + count


def _jwt():
    key = os.environ["APPSTORE_API_PRIVATE_KEY"]
    if not key or not os.environ["APPSTORE_API_KEY_ID"] or not os.environ["APPSTORE_ISSUER_ID"]:
        raise ValueError("Missing App Store Connect API credentials")
    now = int(time.time())
    header = {"alg": "ES256", "kid": os.environ["APPSTORE_API_KEY_ID"], "typ": "JWT"}
    payload = {"iss": os.environ["APPSTORE_ISSUER_ID"], "iat": now,
               "exp": now + 1140, "aud": "appstoreconnect-v1"}
    signing_input = b".".join(_b64(json.dumps(item, separators=(",", ":")).encode())
                              for item in (header, payload))
    key_path = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", prefix="asc-build-key-", suffix=".p8",
                                         dir=os.environ.get("RUNNER_TEMP"), delete=False) as key_file:
            key_path = Path(key_file.name)
            key_file.write(key)
        key_path.chmod(0o600)
        der = subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(key_path)],
                             input=signing_input, check=True, capture_output=True).stdout
    finally:
        if key_path is not None:
            key_path.unlink(missing_ok=True)
    if not der or der[0] != 0x30:
        raise ValueError("Invalid OpenSSL signature")
    _, pos = _der_length(der, 1)
    values = []
    for _ in range(2):
        if der[pos] != 0x02:
            raise ValueError("Invalid OpenSSL signature component")
        size, pos = _der_length(der, pos + 1)
        values.append(int.from_bytes(der[pos:pos + size], "big"))
        pos += size
    signature = b"".join(value.to_bytes(32, "big") for value in values)
    return (signing_input + b"." + _b64(signature)).decode()


def _get_json(token, url):
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or parsed.netloc != "api.appstoreconnect.apple.com":
        raise ValueError("App Store Connect pagination pointed outside its API host")
    request = urllib.request.Request(
        url, headers={"Authorization": "Bearer " + token, "Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def _pages(get_json, first_url, expected_path):
    seen = set()
    url = first_url
    while url:
        if url in seen:
            raise ValueError("App Store Connect pagination loop")
        seen.add(url)
        parsed = urllib.parse.urlparse(url)
        if parsed.path != expected_path:
            raise ValueError("Unexpected App Store Connect pagination path")
        result = get_json(url)
        if not isinstance(result, dict) or not isinstance(result.get("data"), list):
            raise ValueError("Invalid App Store Connect response")
        yield result["data"]
        next_url = result.get("links", {}).get("next")
        if next_url is not None and not isinstance(next_url, str):
            raise ValueError("Invalid App Store Connect next link")
        url = urllib.parse.urljoin(API_ROOT, next_url) if next_url else None


def fetch_versions(get_json):
    app_query = urllib.parse.urlencode({"filter[bundleId]": BUNDLE_ID, "limit": 200})
    apps = [app for page in _pages(get_json, f"{API_ROOT}/v1/apps?{app_query}", "/v1/apps")
            for app in page]
    if len(apps) != 1 or not isinstance(apps[0].get("id"), str):
        raise ValueError("Expected exactly one App Store Connect app for the bundle ID")
    app_id = apps[0]["id"]
    build_query = urllib.parse.urlencode({"filter[app]": app_id, "limit": 200})
    versions = []
    for page in _pages(get_json, f"{API_ROOT}/v1/builds?{build_query}", "/v1/builds"):
        for build in page:
            attributes = build.get("attributes")
            if not isinstance(attributes, dict) or "version" not in attributes:
                raise ValueError("App Store Connect build is missing its version")
            versions.append(attributes["version"])
    return versions


def main():
    token = _jwt()
    versions = fetch_versions(lambda url: _get_json(token, url))
    number = next_build_number(versions)
    print(f"Selected iOS build {number} from {len(versions)} App Store Connect builds")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"build_number={number}\n")
        output.write(f"source_build_count={len(versions)}\n")
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
        summary.write(f"iOS build number: **{number}**. Source: all {len(versions)} "
                      f"App Store Connect builds for `{BUNDLE_ID}`.\n")


if __name__ == "__main__":
    main()
