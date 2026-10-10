"""Append the requested event context without changing the submitted build."""
import importlib.util
import json
from pathlib import Path
import urllib.request

spec = importlib.util.spec_from_file_location('asc', Path(__file__).with_name('resolve-ios-build-number.py'))
asc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(asc)
token = asc._jwt()

def api(path, method='GET', data=None):
    request = urllib.request.Request(asc.API_ROOT + path, method=method,
        data=None if data is None else json.dumps(data).encode(),
        headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=60) as response:
        raw = response.read()
        return json.loads(raw) if raw else {}

version_id = 'bb72777f-5440-4387-818a-dc8d426f9614'
version = api('/v1/appStoreVersions/' + version_id)['data']
state = version['attributes']['appStoreState']
print('SUBMITTED_VERSION_STATE', state, flush=True)
assert state in ('WAITING_FOR_REVIEW', 'IN_REVIEW', 'PENDING_DEVELOPER_RELEASE', 'PENDING_APPLE_RELEASE', 'READY_FOR_SALE')
detail = api('/v1/appStoreVersions/' + version_id + '/appStoreReviewDetail')['data']
assert detail
reason = ('KKKeyboard (ㅋㅋ키보드) is a Hangul typing and learning app planned for release '
    'on Hangul Day in South Korea, October 9, 2026. The app helps users practice Hangul input. '
    'We request an expedited review so it can be available for this event.')
notes = detail['attributes'].get('notes') or ''
if reason not in notes:
    notes = notes.rstrip() + '\n\n' + reason
    api('/v1/appStoreReviewDetails/' + detail['id'], 'PATCH', {'data': {
        'type': 'appStoreReviewDetails', 'id': detail['id'], 'attributes': {'notes': notes}}})
actual = api('/v1/appStoreReviewDetails/' + detail['id'])['data']['attributes']['notes']
assert reason in actual
report = {'app_id': '6814736737', 'version': version['attributes']['versionString'],
    'version_id': version_id, 'state': state, 'event': 'Hangul Day', 'event_date': '2026-10-09',
    'reason': reason, 'review_notes_saved': True, 'expedited_request_submitted': False}
Path('expedited-review-context.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print('EVENT_NOTES_SAVED', json.dumps(report, ensure_ascii=False))
