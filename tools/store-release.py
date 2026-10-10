import importlib.util
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

spec = importlib.util.spec_from_file_location('asc', Path(__file__).with_name('resolve-ios-build-number.py'))
asc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(asc)
token = asc._jwt()

def request(path, method='GET', data=None):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(asc.API_ROOT + path, data=body, method=method,
        headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        print('APPLE_ERROR', error.code, error.read().decode())
        raise

apps = request('/v1/apps?filter[bundleId]=com.ghtnql.kkkeyboard')['data']
assert len(apps) == 1
app = apps[0]
print('APP', json.dumps({'id': app['id'], 'attributes': app['attributes']}))
versions = request('/v1/apps/' + app['id'] + '/appStoreVersions')['data']
print('VERSIONS', json.dumps(versions))
builds = request('/v1/builds?filter[app]=' + app['id'] + '&filter[version]=27')['data']
print('BUILDS', json.dumps(builds))
infos = request('/v1/apps/' + app['id'] + '/appInfos')['data']
print('APP_INFOS', json.dumps(infos))
print('BETA_CONTACT_FIELDS', json.dumps({k: bool(v) for k,v in request('/v1/apps/' + app['id'] + '/betaAppReviewDetail')['data']['attributes'].items()}))
for info in infos:
    print('AGE_RATING', json.dumps(request('/v1/appInfos/' + info['id'] + '/ageRatingDeclaration')))
    print('INFO_LOCALIZATIONS', json.dumps(request('/v1/appInfos/' + info['id'] + '/appInfoLocalizations')))
assert len(builds) == 1 and builds[0]['attributes']['processingState'] == 'VALID'
if os.environ.get('PREPARE_METADATA') == 'true':
    policy = 'https://ghtnql.github.io/KKKeyboard-policy/privacy-policy.html'
    points = request('/v1/apps/' + app['id'] + '/appPricePoints?filter[territory]=KOR&limit=200')['data']
    free = next(point for point in points if float(point['attributes']['customerPrice']) == 0)
    price_id = '${newprice-0}'
    print('FREE_PRICE_SCHEDULE', json.dumps(request('/v1/appPriceSchedules', 'POST', {
        'data': {'type': 'appPriceSchedules', 'relationships': {
            'app': {'data': {'type': 'apps', 'id': app['id']}},
            'baseTerritory': {'data': {'type': 'territories', 'id': 'KOR'}},
            'manualPrices': {'data': [{'type': 'appPrices', 'id': price_id}]}}},
        'included': [{'type': 'appPrices', 'id': price_id, 'attributes': {'startDate': None, 'endDate': None},
            'relationships': {'appPricePoint': {'data': {'type': 'appPricePoints', 'id': free['id']}}}}]})))
    for info in infos:
        iid = info['id']
        request('/v1/appInfos/' + iid, 'PATCH', {'data': {'type': 'appInfos', 'id': iid, 'relationships': {'primaryCategory': {'data': {'type': 'appCategories', 'id': 'UTILITIES'}}}}})
        for loc in request('/v1/appInfos/' + iid + '/appInfoLocalizations')['data']:
            request('/v1/appInfoLocalizations/' + loc['id'], 'PATCH', {'data': {'type': 'appInfoLocalizations', 'id': loc['id'], 'attributes': {'privacyPolicyUrl': policy}}})
        age = request('/v1/appInfos/' + iid + '/ageRatingDeclaration')['data']
        frequency = ['alcoholTobaccoOrDrugUseOrReferences','gamblingSimulated','gunsOrOtherWeapons','healthOrWellnessTopics','medicalOrTreatmentInformation','profanityOrCrudeHumor','sexualContentGraphicAndNudity','sexualContentOrNudity','horrorOrFearThemes','matureOrSuggestiveThemes','violenceCartoonOrFantasy','violenceRealisticProlongedGraphicOrSadistic','violenceRealistic']
        attrs = {key: 'NONE' for key in frequency}
        attrs.update({key: False for key in ['contests','gambling','lootBox','messagingAndChat','parentalControls','ageAssurance','unrestrictedWebAccess','userGeneratedContent']})
        attrs['advertising'] = True
        attrs['contests'] = 'NONE'
        attrs['healthOrWellnessTopics'] = False
        request('/v1/ageRatingDeclarations/' + age['id'], 'PATCH', {'data': {'type': 'ageRatingDeclarations', 'id': age['id'], 'attributes': attrs}})
for version in versions:
    if version['attributes']['platform'] != 'IOS':
        continue
    vid = version['id']
    print('LOCALIZATIONS', json.dumps(request('/v1/appStoreVersions/' + vid + '/appStoreVersionLocalizations')))
    print('REVIEW_DETAIL', json.dumps(request('/v1/appStoreVersions/' + vid + '/appStoreReviewDetail')))
    if os.environ.get('PREPARE_METADATA') == 'true':
        request('/v1/appStoreVersions/' + vid, 'PATCH', {'data': {'type': 'appStoreVersions', 'id': vid, 'attributes': {'copyright': '2026 KKKeyboard', 'releaseType': 'AFTER_APPROVAL'}}})
        for loc in request('/v1/appStoreVersions/' + vid + '/appStoreVersionLocalizations')['data']:
            request('/v1/appStoreVersionLocalizations/' + loc['id'], 'PATCH', {'data': {'type': 'appStoreVersionLocalizations', 'id': loc['id'], 'attributes': {'keywords': '키보드,한글,일본어,천지인,플릭,타자,변환,학습', 'supportUrl': policy}}})
        if not request('/v1/appStoreVersions/' + vid + '/appStoreReviewDetail')['data']:
            contact = request('/v1/apps/' + app['id'] + '/betaAppReviewDetail')['data']['attributes']
            attrs = {key: contact[key] for key in ['contactFirstName','contactLastName','contactPhone','contactEmail','demoAccountRequired','notes']}
            request('/v1/appStoreReviewDetails', 'POST', {'data': {'type': 'appStoreReviewDetails', 'attributes': attrs, 'relationships': {'appStoreVersion': {'data': {'type': 'appStoreVersions', 'id': vid}}}}})
        print('METADATA_PREPARED', vid)
    if os.environ.get('SUBMIT_RELEASE') != 'true':
        continue
    if version['attributes']['appStoreState'] not in ('PREPARE_FOR_SUBMISSION', 'DEVELOPER_REJECTED', 'REJECTED'):
        print('EXISTING_STATE', version['attributes']['appStoreState'])
        continue
    request('/v1/appStoreVersions/' + vid, 'PATCH', {'data': {'type': 'appStoreVersions', 'id': vid, 'attributes': {'releaseType': 'AFTER_APPROVAL'}}})
    request('/v1/appStoreVersions/' + vid + '/relationships/build', 'PATCH', {'data': {'type': 'builds', 'id': builds[0]['id']}})
    reviews = request('/v1/apps/' + app['id'] + '/reviewSubmissions')['data']
    review = next((r for r in reviews if r['attributes'].get('platform') == 'IOS' and r['attributes'].get('state') == 'READY_FOR_REVIEW'), None)
    if review is None:
        review = request('/v1/reviewSubmissions', 'POST', {'data': {'type': 'reviewSubmissions', 'attributes': {'platform': 'IOS'}, 'relationships': {'app': {'data': {'type': 'apps', 'id': app['id']}}}}})['data']
    request('/v1/reviewSubmissionItems', 'POST', {'data': {'type': 'reviewSubmissionItems', 'relationships': {'reviewSubmission': {'data': {'type': 'reviewSubmissions', 'id': review['id']}}, 'appStoreVersion': {'data': {'type': 'appStoreVersions', 'id': vid}}}}})
    print('SUBMITTED', json.dumps(request('/v1/reviewSubmissions/' + review['id'], 'PATCH', {'data': {'type': 'reviewSubmissions', 'id': review['id'], 'attributes': {'submitted': True}}})))
    print('VERSION_AFTER', json.dumps(request('/v1/appStoreVersions/' + vid)))
