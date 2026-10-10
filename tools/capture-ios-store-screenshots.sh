#!/usr/bin/env bash
# Builds and launches the real app. This script never runs XCTest or Gradle tests.
set -euo pipefail

source_root="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
output_dir="${2:-$source_root/build/ios-store-screenshots}"
[[ "$(uname -s)" == Darwin ]] || { echo 'Capture requires macOS with Xcode simulators.' >&2; exit 1; }
mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"
source_commit="$(git -C "$source_root" rev-parse HEAD)"
if [[ -n "${SOURCE_SHA:-}" && "$source_commit" != "$SOURCE_SHA" ]]; then
  echo "Checkout mismatch: $source_commit != $SOURCE_SHA" >&2
  exit 1
fi
if [[ -n "$(git -C "$source_root" status --porcelain --untracked-files=no)" ]]; then
  echo 'Capture source has tracked modifications; use an exact clean checkout.' >&2
  exit 1
fi

xcodebuild -version > "$output_dir/xcode-version.txt"
xcrun simctl list runtimes -j > "$output_dir/runtimes.json"
xcrun simctl list devicetypes -j > "$output_dir/device-types.json"

# Prefer 1206x2622 iPhones and a 2064x2752 iPad. Do not choose Plus/Max or 11-inch devices.
python3 - "$output_dir" <<'PY'
import json, pathlib, sys
out = pathlib.Path(sys.argv[1])
runtimes = json.loads((out / 'runtimes.json').read_text())['runtimes']
runtimes = [r for r in runtimes if r.get('isAvailable') and '.iOS-' in r['identifier']]
if not runtimes:
    raise SystemExit('No available iOS simulator runtime. Install one before capture.')
runtime = max(runtimes, key=lambda r: tuple(int(v) for v in r['version'].split('.')))
types = json.loads((out / 'device-types.json').read_text())['devicetypes']
phones = ['iPhone 17', 'iPhone 17 Pro', 'iPhone 16 Pro', 'iPhone 16', 'iPhone 15 Pro', 'iPhone 15']
ipads = ['iPad Pro 13-inch (M5)', 'iPad Pro 13-inch (M4)']
selected = []
for profile, names in [('IPHONE_DYNAMIC_ISLAND_MEDIUM_PROFILE', phones), ('IPAD_13_PROFILE', ipads)]:
    device = next((d for name in names for d in types if d['name'] == name), None)
    if not device:
        raise SystemExit(f'No supported simulator device type for {profile}. Available: {[d["name"] for d in types]}')
    selected.append({'profile': profile, 'device': device['name'], 'device_type': device['identifier'], 'runtime': runtime['identifier']})
(out / 'selected-devices.json').write_text(json.dumps(selected, ensure_ascii=False, indent=2) + '\n')
with (out / 'devices.tsv').open('w') as f:
    for d in selected:
        f.write('\t'.join(d[k] for k in ('profile', 'device_type', 'runtime')) + '\n')
PY

created_devices=()
cleanup() {
  for udid in "${created_devices[@]}"; do
    xcrun simctl shutdown "$udid" >/dev/null 2>&1 || true
    xcrun simctl delete "$udid" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT
while IFS=$'\t' read -r profile device_type runtime; do
  udid="$(xcrun simctl create "KKStore-$profile-$$" "$device_type" "$runtime")"
  created_devices+=("$udid")
done < "$output_dir/devices.tsv"

derived_data="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/KKKeyboard-store-derived-$$"
(cd "$source_root/ios" && xcodegen generate) > "$output_dir/xcodegen.log" 2>&1
# A build action omits the scheme's test-only targets. The keyboard extension is embedded normally.
xcodebuild -project "$source_root/ios/KKKeyboard.xcodeproj" \
  -scheme KKKeyboard -configuration Debug -sdk iphonesimulator \
  -destination "platform=iOS Simulator,id=${created_devices[0]}" \
  -derivedDataPath "$derived_data" \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO 'TARGETED_DEVICE_FAMILY=1,2' \
  build 2>&1 | tee "$output_dir/xcodebuild-capture.log"
app="$derived_data/Build/Products/Debug-iphonesimulator/KKKeyboardApp.app"
[[ -d "$app" ]] || { echo 'Actual KKKeyboard app product missing.' >&2; exit 1; }
bundle_id="$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$app/Info.plist")"

index=0
while IFS=$'\t' read -r profile device_type runtime; do
  udid="${created_devices[$index]}"
  index=$((index + 1))
  xcrun simctl boot "$udid"
  xcrun simctl bootstatus "$udid" -b
  xcrun simctl ui "$udid" appearance light
  xcrun simctl status_bar "$udid" override --time '9:41' --dataNetwork wifi \
    --wifiMode active --wifiBars 3 --batteryState charged --batteryLevel 100
  xcrun simctl install "$udid" "$app"
  xcrun simctl launch "$udid" "$bundle_id" -AppleLanguages '(ko)' -AppleLocale ko_KR \
    | tee "$output_dir/$profile-launch.log"
  # Compose/Skia and initial content loading finish before the store asset capture.
  sleep 20
  xcrun simctl io "$udid" screenshot --type=png --mask=ignored "$output_dir/$profile-home.png"
  xcrun simctl shutdown "$udid"
done < "$output_dir/devices.tsv"

# Re-encode pixels in an opaque RGB buffer: App Store does not accept an alpha channel.
cat > "$derived_data/opaque-screenshot.swift" <<'SWIFT'
import Foundation
import CoreGraphics
import ImageIO

for path in CommandLine.arguments.dropFirst() {
    let url = URL(fileURLWithPath: path)
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil),
          let context = CGContext(data: nil, width: image.width, height: image.height,
              bitsPerComponent: 8, bytesPerRow: image.width * 4,
              space: CGColorSpace(name: CGColorSpace.sRGB)!,
              bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else {
        fatalError("Cannot read screenshot: \(path)")
    }
    context.draw(image, in: CGRect(x: 0, y: 0, width: CGFloat(image.width), height: CGFloat(image.height)))
    guard let opaque = context.makeImage(),
          let destination = CGImageDestinationCreateWithURL(url as CFURL, "public.png" as CFString, 1, nil) else {
        fatalError("Cannot write screenshot: \(path)")
    }
    CGImageDestinationAddImage(destination, opaque, nil)
    guard CGImageDestinationFinalize(destination) else { fatalError("PNG write failed: \(path)") }
}
SWIFT
xcrun swift "$derived_data/opaque-screenshot.swift" "$output_dir/"*-home.png

python3 - "$output_dir" "$source_commit" "$bundle_id" <<'PY'
import hashlib, json, pathlib, struct, sys
out, commit, bundle = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
manifest = {'source_commit': commit, 'bundle_id': bundle, 'locale': 'ko-KR', 'configuration': 'Debug', 'screen': 'actual app home', 'capture_method': 'simctl io screenshot', 'tests_run': False, 'release_uploaded': False, 'screenshots': []}
accepted = {'IPHONE_DYNAMIC_ISLAND_MEDIUM_PROFILE': {(1179, 2556), (1206, 2622)}, 'IPAD_13_PROFILE': {(2064, 2752), (2048, 2732)}}
for device in json.loads((out / 'selected-devices.json').read_text()):
    path = out / (device['profile'] + '-home.png')
    data = path.read_bytes()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise SystemExit(f'Invalid PNG: {path}')
    dimensions = struct.unpack('>II', data[16:24])
    if dimensions not in accepted[device['profile']]:
        raise SystemExit(f'Wrong native screenshot dimensions for {device["profile"]}: {dimensions}')
    chunks = []
    offset = 8
    while offset < len(data):
        length = struct.unpack('>I', data[offset:offset + 4])[0]
        chunks.append(data[offset + 4:offset + 8])
        offset += length + 12
    if data[25] in (4, 6) or b'tRNS' in chunks:
        raise SystemExit(f'Screenshot contains transparency: {path}; do not upload.')
    manifest['screenshots'].append({**device, 'file': path.name, 'width': dimensions[0], 'height': dimensions[1], 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)})
(out / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(manifest, ensure_ascii=False, indent=2))
PY
