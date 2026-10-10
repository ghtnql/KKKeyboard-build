"""Upload captured release assets to the existing Korean App Store listing."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request

spec = importlib.util.spec_from_file_location('asc', Path(__file__).with_name('resolve-ios-build-number.py'))
asc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(asc)
token = asc._jwt()
root = Path(os.environ['SCREENSHOTS_DIR'])
manifest = json.loads((root / 'manifest.json').read_text())
assert manifest['source_commit'] == os.environ['SOURCE_SHA']
assert manifest['bundle_id'] == 'com.ghtnql.kkkeyboard'
mapping = {'IPHONE_DYNAMIC_ISLAND_MEDIUM_PROFILE': 'APP_IPHONE_61', 'IPAD_13_PROFILE': 'APP_IPAD_PRO_3GEN_129'}
assert {s['profile'] for s in manifest['screenshots']} == set(mapping)

def api(path, method='GET', data=None):
    req = urllib.request.Request(asc.API_ROOT + path, method=method,
        data=None if data is None else json.dumps(data).encode(),
        headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        print('APPLE_API_ERROR', e.code, e.read().decode())
        raise

apps = api('/v1/apps?filter[bundleId]=com.ghtnql.kkkeyboard')['data']
assert len(apps) == 1
versions = api('/v1/apps/' + apps[0]['id'] + '/appStoreVersions')['data']
versions = [v for v in versions if v['attributes']['platform'] == 'IOS' and v['attributes']['versionString'] == '1.0']
assert len(versions) == 1
locs = api('/v1/appStoreVersions/' + versions[0]['id'] + '/appStoreVersionLocalizations')['data']
loc = next(loc for loc in locs if loc['attributes']['locale'] == 'ko')
sets = api('/v1/appStoreVersionLocalizations/' + loc['id'] + '/appScreenshotSets')['data']
report = {'source_commit': manifest['source_commit'], 'app_store_version': versions[0]['id'], 'locale': 'ko', 'screenshots': []}
for shot in manifest['screenshots']:
    assert Path(shot['file']).name == shot['file']
    blob = (root / shot['file']).read_bytes()
    assert hashlib.sha256(blob).hexdigest() == shot['sha256'] and len(blob) == shot['bytes']
    display = mapping[shot['profile']]
    screenshot_set = next((s for s in sets if s['attributes']['screenshotDisplayType'] == display), None)
    if screenshot_set is None:
        screenshot_set = api('/v1/appScreenshotSets', 'POST', {'data': {
            'type': 'appScreenshotSets', 'attributes': {'screenshotDisplayType': display},
            'relationships': {'appStoreVersionLocalization': {'data': {'type': 'appStoreVersionLocalizations', 'id': loc['id']}}}}})['data']
    checksum = hashlib.md5(blob).hexdigest()
    existing = api('/v1/appScreenshotSets/' + screenshot_set['id'] + '/appScreenshots')['data']
    reservation = next((s for s in existing if s['attributes'].get('sourceFileChecksum') == checksum and
        s['attributes'].get('assetDeliveryState', {}).get('state') == 'COMPLETE'), None)
    if reservation is None:
        reservation = api('/v1/appScreenshots', 'POST', {'data': {
            'type': 'appScreenshots', 'attributes': {'fileSize': len(blob), 'fileName': shot['file']},
            'relationships': {'appScreenshotSet': {'data': {'type': 'appScreenshotSets', 'id': screenshot_set['id']}}}}})['data']
        for op in reservation['attributes']['uploadOperations']:
            chunk = blob[op['offset']:op['offset'] + op['length']]
            assert len(chunk) == op['length']
            req = urllib.request.Request(op['url'], method=op['method'], data=chunk,
                headers={h['name']: h['value'] for h in op['requestHeaders']})
            try:
                with urllib.request.urlopen(req, timeout=90) as response:
                    response.read()
            except Exception:
                raise RuntimeError('Screenshot binary upload failed; signed upload URL omitted') from None
        api('/v1/appScreenshots/' + reservation['id'], 'PATCH', {'data': {'type': 'appScreenshots',
            'id': reservation['id'], 'attributes': {'uploaded': True, 'sourceFileChecksum': checksum}}})
    for attempt in range(60):
        current = api('/v1/appScreenshots/' + reservation['id'])['data']
        state = current['attributes']['assetDeliveryState']['state']
        print('SCREENSHOT_STATE', display, state, flush=True)
        if state == 'COMPLETE':
            break
        if state == 'FAILED':
            raise RuntimeError(json.dumps(current['attributes']['assetDeliveryState']))
        time.sleep(5)
    else:
        raise RuntimeError('Screenshot processing still pending')
    image = current['attributes']['imageAsset']
    assert (image['width'], image['height']) == (shot['width'], shot['height'])
    report['screenshots'].append({'display_type': display, 'id': current['id'], 'state': state,
        'file': shot['file'], 'sha256': shot['sha256'], 'width': image['width'], 'height': image['height']})
(root / 'apple-upload-result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print('SCREENSHOTS_UPLOADED', json.dumps(report, ensure_ascii=False))
