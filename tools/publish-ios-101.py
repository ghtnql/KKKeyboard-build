#!/usr/bin/env python3
"""Add localized App Store metadata; optionally attach an uploaded iOS build and submit.

Backward compatible: IOS_MARKETING_VERSION defaults to 1.0.1 (legacy behavior).
Set IOS_MARKETING_VERSION=1.0.2 for the next-version update.
"""
import importlib.util
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("asc", ROOT / "tools/resolve-ios-build-number.py")
asc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(asc)
TOKEN = asc._jwt()
BASE = "https://api.appstoreconnect.apple.com"
NAME = "ㅋㅋキーボード"
metadata = json.loads((ROOT / "docs/app-store-ja-update-2026-10-09.json").read_text(encoding="utf8"))

TARGET = os.environ.get("IOS_MARKETING_VERSION", "1.0.1").strip() or "1.0.1"
TAG = TARGET.replace(".", "")

# 1.0.2 localized content.
KO_WHATS_NEW_102 = "일본어 일반 회화 사전 확대, 완성 발음 입력의 변환 후보 수정, 앱 이름 표시 개선."
JA_WHATS_NEW_102 = "日常会話の日本語辞書を拡充し、最後まで入力した発音の変換候補とアプリ名の表示を改善しました。"
JA_KEYWORDS_102 = "ククキーボード,ケーケーキーボード,ダブルケーキーボード,kkkeyboard,ハングル,韓国語入力,日本語変換,かな,フリック,タイピング"
# Legacy 1.0.1 content (unchanged default behavior).
KO_WHATS_NEW_101 = "일본어 한자 변환 후보 보강, 키보드 하단 표시 안정성 개선."
JA_WHATS_NEW_101 = "日本語変換候補を追加し、キーボード下部の表示を改善しました。"
JA_FIRST_LINE_102 = (
    "ㅋㅋキーボード（ククキーボード／ケーケーキーボード／ダブルケーキーボード、kkkeyboard）は、"
    "ハングルを使って日本語の発音を入力し、日本語の変換候補を選べるキーボードアプリです。"
)


def parse_version(text):
    parts = []
    for piece in str(text).split("."):
        if not piece.isdigit():
            return None
        parts.append(int(piece))
    return tuple(parts) if parts else None


def is_published(attrs):
    if not isinstance(attrs, dict):
        return False
    states = [
        attrs.get("appStoreState"),
        attrs.get("state"),
        attrs.get("appVersionState"),
    ]
    return "READY_FOR_SALE" in states or "READY_FOR_DISTRIBUTION" in states


def call(path, method="GET", payload=None):
    url = BASE + path
    blob = json.dumps(payload, ensure_ascii=False).encode() if payload is not None else None
    req = urllib.request.Request(url, method=method, data=blob, headers={"Authorization": "Bearer " + TOKEN, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        # Do not retry authorization errors; surface once and stop.
        if error.code in (401, 403):
            print("APPLE_API_AUTH_ERROR", error.code, flush=True)
            raise
        try:
            body = error.read().decode()[:2400]
        except Exception:
            body = "<unreadable>"
        print("APPLE_API_ERROR", error.code, body, flush=True)
        raise


def wrap(kind, attributes, relation_name=None, relation_id=None):
    data = {"type": kind, "attributes": attributes}
    if relation_name:
        data["relationships"] = {relation_name: {"data": {"type": {"app": "apps", "appInfo": "appInfos", "appStoreVersion": "appStoreVersions"}[relation_name], "id": relation_id}}}
    return {"data": data}


apps = call("/v1/apps?filter[bundleId]=com.ghtnql.kkkeyboard")["data"]
if len(apps) != 1:
    raise RuntimeError("Expected exactly one KKKeyboard app")
appid = apps[0]["id"]
versions = call("/v1/apps/" + appid + "/appStoreVersions?limit=100")["data"]
ios_versions = [v for v in versions if v.get("attributes", {}).get("platform") == "IOS"]
# Reference: previous latest published IOS version distinct from target, semantic numeric order.
candidates = []
for v in ios_versions:
    attrs = v.get("attributes", {})
    if attrs.get("versionString") == TARGET:
        continue
    if not is_published(attrs):
        continue
    parsed = parse_version(attrs.get("versionString", ""))
    if parsed is None:
        continue
    candidates.append((parsed, v))
if not candidates:
    raise RuntimeError("No published previous IOS version found")
candidates.sort(key=lambda item: item[0])
old = candidates[-1][1]
nextv = next((v for v in ios_versions if v["attributes"].get("versionString") == TARGET), None)
if not nextv:
    print("CREATE_VERSION_" + TAG)
    nextv = call("/v1/appStoreVersions", "POST", wrap("appStoreVersions", {"platform": "IOS", "versionString": TARGET, "copyright": "2026 KKKeyboard", "releaseType": "AFTER_APPROVAL"}, "app", appid))["data"]
vid = nextv["id"]
print("VERSION_" + TAG, vid, nextv["attributes"].get("appStoreState"))
if nextv["attributes"].get("appStoreState") not in ("PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED"):
    raise RuntimeError(TARGET + " is not editable; stop instead of modifying live metadata")

infos = call("/v1/apps/" + appid + "/appInfos?limit=100")["data"]
if not infos:
    raise RuntimeError("App info not found")
# Newest editable appInfo first; current 1.0 has one in ordinary App Store Connect setup.
info = next((candidate for candidate in infos if candidate["attributes"].get("state") == "PREPARE_FOR_SUBMISSION"), None)
if not info:
    raise RuntimeError("No editable AppInfo for " + TARGET)
local = call("/v1/appInfos/" + info["id"] + "/appInfoLocalizations?limit=100")["data"]
ja = next((x for x in local if x["attributes"]["locale"] in ("ja", "ja-JP")), None)
jaattr = {"locale": "ja", "name": NAME, "subtitle": metadata["appInfoLocalization"]["subtitle"],
        "privacyPolicyUrl": "https://ghtnql.github.io/KKKeyboard-policy/privacy-policy-ja.html"}
if ja:
    call("/v1/appInfoLocalizations/" + ja["id"], "PATCH", {"data": {"type": "appInfoLocalizations", "id": ja["id"], "attributes": {k: v for k, v in jaattr.items() if k != "locale"}}})
else:
    call("/v1/appInfoLocalizations", "POST", wrap("appInfoLocalizations", jaattr, "appInfo", info["id"]))
print("APP_JAPANESE_NAME_SAVED", NAME)
# Read-only reference from the published previous version; never mutate `old`.
oldlocals = call("/v1/appStoreVersions/" + old["id"] + "/appStoreVersionLocalizations?limit=100")["data"]
oldko = next(x for x in oldlocals if x["attributes"]["locale"] == "ko")
vlocals = call("/v1/appStoreVersions/" + vid + "/appStoreVersionLocalizations?limit=100")["data"]
if TARGET == "1.0.2":
    ko_whats_new = KO_WHATS_NEW_102
    ja_whats_new = JA_WHATS_NEW_102
else:
    ko_whats_new = KO_WHATS_NEW_101
    ja_whats_new = JA_WHATS_NEW_101
ko = next((x for x in vlocals if x["attributes"]["locale"] == "ko"), None)
if not ko:
    prev = oldko["attributes"]
    koattr = {"locale": "ko", "description": prev["description"], "keywords": prev.get("keywords") or "",
        "supportUrl": prev.get("supportUrl") or "https://ghtnql.github.io/KKKeyboard-policy/privacy-policy.html",
        "whatsNew": ko_whats_new}
    call("/v1/appStoreVersionLocalizations", "POST", wrap("appStoreVersionLocalizations", koattr, "appStoreVersion", vid))
else:
    call("/v1/appStoreVersionLocalizations/" + ko["id"], "PATCH",
         {"data": {"type": "appStoreVersionLocalizations", "id": ko["id"], "attributes": {"whatsNew": ko_whats_new}}})
ja = next((x for x in vlocals if x["attributes"]["locale"] in ("ja", "ja-JP")), None)
jaattr = dict(metadata["appStoreVersionLocalization"])
# App Store Review requires a support URL for each localization.
jaattr["supportUrl"] = oldko["attributes"].get("supportUrl") or "https://ghtnql.github.io/KKKeyboard-policy/privacy-policy.html"
if TARGET == "1.0.2":
    ref_desc = str(metadata["appStoreVersionLocalization"]["description"])
    _head, _sep, _tail = ref_desc.partition("\n")
    remainder = _tail.lstrip("\n")
    jaattr["description"] = JA_FIRST_LINE_102 + "\n\n" + remainder if remainder else JA_FIRST_LINE_102
    jaattr["keywords"] = JA_KEYWORDS_102
    assert len(JA_KEYWORDS_102) <= 100, "Japanese keywords exceed 100 chars"
    jaattr["whatsNew"] = ja_whats_new
else:
    jaattr["whatsNew"] = ja_whats_new
if ja:
    call("/v1/appStoreVersionLocalizations/" + ja["id"], "PATCH",
         {"data": {"type": "appStoreVersionLocalizations", "id": ja["id"], "attributes": jaattr}})
else:
    call("/v1/appStoreVersionLocalizations", "POST", wrap("appStoreVersionLocalizations", {"locale": "ja", **jaattr}, "appStoreVersion", vid))
print("VERSION_LOCALIZATIONS_SAVED", ["ko", "ja"])

buildno = os.environ.get("IOS_BUILD_NUMBER", "").strip()
if not buildno:
    print("NO_BUILD_SPECIFIED_STOP_AFTER_METADATA")
    raise SystemExit(0)
builds = call("/v1/builds?filter[app]=" + appid + "&filter[version]=" + urllib.parse.quote(buildno) + "&limit=100")["data"]
valid = [b for b in builds if str(b["attributes"].get("version")) == buildno and b["attributes"]["processingState"] == "VALID"]
if len(valid) != 1:
    print("BUILD_NOT_READY", buildno, [(b["id"], b["attributes"]["processingState"]) for b in builds])
    raise SystemExit(2)
bid = valid[0]["id"]
# Validate the build's marketing version via its preReleaseVersion relationship.
pre = call("/v1/builds/" + bid + "/preReleaseVersion")["data"]
pre_version = (pre.get("attributes") or {}).get("version") if isinstance(pre, dict) else None
if pre_version != TARGET:
    print("BUILD_VERSION_MISMATCH", buildno, bid, pre_version, TARGET)
    raise SystemExit(2)
call("/v1/appStoreVersions/" + vid + "/relationships/build", "PATCH", {"data": {"type": "builds", "id": bid}})
print("BUILD_" + TAG + "_ATTACHED", buildno, bid)
if os.environ.get("SUBMIT_101", "false").lower() != "true":
    print("NOT_SUBMITTED_BY_CONFIGURATION")
    raise SystemExit(0)

details = call("/v1/appStoreVersions/" + vid + "/appStoreReviewDetail")["data"]
if not details:
    olddetails = call("/v1/appStoreVersions/" + old["id"] + "/appStoreReviewDetail")["data"]
    if olddetails:
        attr = olddetails["attributes"]
        keys = ("contactFirstName", "contactLastName", "contactPhone", "contactEmail", "demoAccountRequired", "notes")
        call("/v1/appStoreReviewDetails", "POST", wrap("appStoreReviewDetails", {k: attr[k] for k in keys if k in attr}, "appStoreVersion", vid))
submissions = call("/v1/apps/" + appid + "/reviewSubmissions?limit=100")["data"]


def _submission_item_version_ids(submission_id):
    resp = call("/v1/reviewSubmissions/" + submission_id + "/items?include=appStoreVersion")
    items = resp.get("data") if isinstance(resp, dict) else None
    if not isinstance(items, list):
        return []
    found = []
    for item in items:
        try:
            found.append(item["relationships"]["appStoreVersion"]["data"]["id"])
        except (KeyError, TypeError):
            found.append("UNRELATED_ITEM:" + item["id"])
    return found


ready = [s for s in submissions
         if isinstance(s.get("attributes"), dict)
         and s["attributes"].get("platform") == "IOS"
         and s["attributes"].get("state") == "READY_FOR_REVIEW"]
# Retry-safe selection: reuse only an empty submission or one already holding
# target vid. Never submit a submission carrying unrelated items.
sub = None
sub_has_target = False
for candidate in ready:
    version_ids = _submission_item_version_ids(candidate["id"])
    if version_ids and all(value == vid for value in version_ids):
        sub = candidate
        sub_has_target = True
        break
    if not version_ids:
        sub = candidate
        sub_has_target = False
        break
    print("REVIEW_SUBMISSION_SKIPPED_HAS_OTHER_ITEMS", candidate["id"], version_ids, flush=True)
if sub is None:
    if ready:
        print("NO_EMPTY_REVIEW_SUBMISSION_TRY_CREATE", [s["id"] for s in ready], flush=True)
    try:
        sub = call("/v1/reviewSubmissions", "POST", wrap("reviewSubmissions", {"platform": "IOS"}, "app", appid))["data"]
    except urllib.error.HTTPError:
        print("REVIEW_SUBMISSION_CREATE_BLOCKED_STOP", flush=True)
        raise
    sub_has_target = False
if not sub_has_target:
    current_ids = _submission_item_version_ids(sub["id"])
    if current_ids and all(value == vid for value in current_ids):
        print("REVIEW_SUBMISSION_ITEM_EXISTS_SKIP_POST", sub["id"], vid, flush=True)
    elif current_ids:
        raise RuntimeError("Refusing to submit review submission " + sub["id"] + " with unrelated items " + repr(current_ids))
    else:
        call("/v1/reviewSubmissionItems", "POST", {"data": {"type": "reviewSubmissionItems", "relationships": {"reviewSubmission": {"data": {"type": "reviewSubmissions", "id": sub["id"]}}, "appStoreVersion": {"data": {"type": "appStoreVersions", "id": vid}}}}})
        print("REVIEW_SUBMISSION_ITEM_CREATED", sub["id"], vid, flush=True)
else:
    print("REVIEW_SUBMISSION_ITEM_EXISTS_SKIP_POST", sub["id"], vid, flush=True)
call("/v1/reviewSubmissions/" + sub["id"], "PATCH", {"data": {"type": "reviewSubmissions", "id": sub["id"], "attributes": {"submitted": True}}})
print("APPLE_REVIEW_SUBMITTED_" + TAG, sub["id"])
# Post-submit verification: actual version state plus attached build relationship.
verify_version = call("/v1/appStoreVersions/" + vid)["data"]
print("SUBMITTED_VERSION_STATE", vid, (verify_version.get("attributes") or {}).get("appStoreState"), flush=True)
attached = call("/v1/appStoreVersions/" + vid + "/relationships/build")
attached_id = None
try:
    attached_id = attached["data"]["id"]
except (KeyError, TypeError):
    attached_id = None
print("SUBMITTED_ATTACHED_BUILD", vid, attached_id, flush=True)
if attached_id != bid:
    print("SUBMITTED_BUILD_MISMATCH", bid, attached_id, flush=True)
    raise SystemExit(2)
# Optional keyword readback with counts and source.
try:
    readback_locals = call("/v1/appStoreVersions/" + vid + "/appStoreVersionLocalizations?limit=100")["data"]
    readback_ja = next((x for x in readback_locals if x.get("attributes", {}).get("locale") in ("ja", "ja-JP")), None)
    readback_kw = (readback_ja.get("attributes") or {}).get("keywords", "") if readback_ja else ""
    print("KEYWORDS_READBACK", "source=api", "chars=" + str(len(readback_kw)), "count=" + str(len([k for k in readback_kw.split(",") if k.strip()]) if readback_kw else 0), readback_kw, flush=True)
except Exception as readback_error:
    print("KEYWORDS_READBACK_UNAVAILABLE", type(readback_error).__name__, flush=True)
