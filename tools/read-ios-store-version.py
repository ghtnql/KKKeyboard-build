"""Read-only exact version/listing diagnostics; credentials remain in runner environment."""
import importlib.util,json,os,urllib.request
from pathlib import Path
spec=importlib.util.spec_from_file_location('asc',Path(__file__).with_name('resolve-ios-build-number.py'))
asc=importlib.util.module_from_spec(spec);spec.loader.exec_module(asc)
token=asc._jwt()
def get(path):
    req=urllib.request.Request(asc.API_ROOT+path,headers={'Authorization':'Bearer '+token})
    with urllib.request.urlopen(req,timeout=60) as r:return json.loads(r.read())
app=get('/v1/apps?filter[bundleId]=com.ghtnql.kkkeyboard')['data'][0]
target=os.environ['IOS_MARKETING_VERSION']
v=next(v for v in get('/v1/apps/'+app['id']+'/appStoreVersions?limit=100')['data'] if v['attributes']['versionString']==target and v['attributes']['platform']=='IOS')
print('EXACT_VERSION_READBACK',v['id'],target,v['attributes']['appStoreState'],v['attributes']['releaseType'])
print('EXACT_BUILD_READBACK',json.dumps(get('/v1/appStoreVersions/'+v['id']+'/build')['data']))
for loc in get('/v1/appStoreVersions/'+v['id']+'/appStoreVersionLocalizations?limit=100')['data']:
    a=loc['attributes'];print('LOCALIZATION_READBACK',a['locale'],a.get('keywords'),a.get('whatsNew'))
    for group in get('/v1/appStoreVersionLocalizations/'+loc['id']+'/appScreenshotSets?limit=200')['data']:
        shots=get('/v1/appScreenshotSets/'+group['id']+'/appScreenshots?limit=200')['data']
        print('SCREENSHOT_READBACK',a['locale'],group['attributes']['screenshotDisplayType'],[(s['id'],s['attributes'].get('assetDeliveryState',{}).get('state')) for s in shots])
