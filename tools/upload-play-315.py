#!/usr/bin/env python3
"""One-shot Play v315 publisher. Receives scoped Google token via GH OIDC and uploaded signed AAB."""
import json, os, sys, urllib.request, urllib.error, urllib.parse
from pathlib import Path

app = "com.ghtnql.kkkeyboard"
ver = "315"
asset = Path("play-upload-v315.aab")
api = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{app}/edits"
token = os.environ["ACCESS_TOKEN"]
assert asset.is_file() and asset.stat().st_size > 10_000_000

def req(url, method="GET", payload=None, binary=False):
    if binary:
        body = asset.read_bytes()
        content_type = "application/octet-stream"
    elif payload is not None:
        body = json.dumps(payload).encode()
        content_type = "application/json"
    else:
        body = None
        content_type = "application/json"
    headers = {"Authorization": "Bearer " + token, "Content-Type": content_type}
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=body, method=method, headers=headers),timeout=120) as r:
            data = r.read()
            return json.loads(data) if data else {}
    except urllib.error.HTTPError as exc:
        print("PLAY_API_ERROR", exc.code, exc.read().decode()[:1600], file=sys.stderr)
        raise

edit_id = None
try:
    edit_id = req(api,"POST",{})["id"]
    base = api + "/" + edit_id
    before = req(base + "/tracks")["tracks"]
    print("TRACKS_BEFORE",json.dumps([{"track":t["track"],"releases":[{"status":r.get("status"),"codes":r.get("versionCodes")} for r in t.get("releases",[])]} for t in before],ensure_ascii=False))
    upload = req(f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{app}/edits/{edit_id}/bundles?uploadType=media","POST",binary=True)
    code = str(upload.get("versionCode"))
    if code != ver: raise RuntimeError(f"Expected {ver}, uploaded {code}")
    print("BUNDLE_UPLOAD_CONFIRMED",code)
    closed = "ㅋㅋ키보드"
    current_closed = next(t for t in before if t["track"] == closed)
    req(base+"/tracks/"+urllib.parse.quote(closed,safe=""),"PUT",{
        "track":closed,
        "releases":[{"versionCodes":[ver],"status":"completed","name":"KKKeyboard 1.0.1"}],
    })
    receipt = req(base+":commit","POST",{})
    print("CLOSED_TEST_COMMIT", json.dumps(receipt,ensure_ascii=False))
    edit_id = None
finally:
    if edit_id:
        try: req(api+"/"+edit_id,"DELETE")
        except Exception: pass

# Production is separate: if Play Console policy blocks it, leave the newly
# published closed-test release in place and report that explicitly.
prod = None
try:
    prod = req(api,"POST",{})["id"]
    base = api+"/"+prod
    req(base+"/tracks/production","PUT",{
        "track":"production",
        "releases":[{"versionCodes":[ver],"status":"completed","name":"KKKeyboard 1.0.1"}]
    })
    result=req(base+":commit","POST",{})
    prod=None
    print("PRODUCTION_COMMIT_SUCCESS",json.dumps(result,ensure_ascii=False))
except urllib.error.HTTPError as exc:
    if exc.code in (400,403,409,422):
        print("PRODUCTION_COMMIT_REJECTED_POLICY_OR_REVIEW",exc.code)
    else:
        raise
finally:
    if prod:
        try:req(api+"/"+prod,"DELETE")
        except Exception:pass
