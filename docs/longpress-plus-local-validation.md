# Cheonjiin Plus and long-press symbols — local implementation

Workspace: `feature/longpress-plus`, base HEAD `d4193b7b98cd9d5076ebf297b158beec255a2ec2`.
Original implementation was committed as `aa5cdd8d1155e99f8163f6debff39448bb8d1a62` on `feature/longpress-plus`. The checks below describe that original source before the 2026-10-06 coding-only integration; they do not validate subsequent edits. See `docs/gpt-coding-review-handoff-2026-10-06.md` for the current state. Original scope was source implementation and local unit/compile checks only. No APK/AAB/TestFlight build, deployment, or Google Drive access/update was performed.

Implemented:
- `cheonjiin_plus` round-trips in shared, Android and iOS settings. Legacy IDs and default Cheonjiin remain compatible.
- Plus splits consonants into ㄱ ㅋ ㄴ ㄹ ㄷ ㅌ / ㅂ ㅍ ㅅ ㅎ ㅈ ㅊ / ㅇ ㅁ and retains the three existing vowel strokes/composition engine. Fixed hold choices are ㅋ→ㄲ, ㅌ→ㄸ, ㅍ→ㅃ, ㅎ→ㅆ, ㅊ→ㅉ.
- Shared catalog defines default/editable slots for standard Cheonjiin, Plus, and QWERTY; Flick has no long-press catalog. Three slots preserve intentional empties and reset per key.
- Compact shared settings choose layout and key, then edit only three fields. Android app/IME use the same preferences and refresh revision. iOS app writes canonical App Group symbols and the extension reads them.
- Native keys defer supported taps until release; holds suppress normal input and show a selection popup. Holds default to the first choice (fixed tense on Plus). Slide selection, hints and accessible choices are included. Existing composer, candidate, symbol page, delete repeat and Flick paths remain in use.

## iOS storage behavior and remaining verification

The existing privacy policy uses `RequestsOpenAccess=false`. iOS extension settings therefore display canonical symbols read-only with an instruction to edit them in the containing app. This avoids divergent extension-local overrides; layout and other pre-existing settings retain their existing behavior. No Full Access entitlement/privacy change was made. App Group visibility and persistence must still be verified on an actual iPhone.

Swift and Xcode executables are unavailable on this Linux host. Authored Swift XCTest tests have not run. UIKit/Swift compilation, native rendering, actual touch/slide gestures, VoiceOver actions, App Group entitlements and host-app candidate/composition interactions remain unverified. Kotlin iOS target compilation does not establish those results. No iOS workflow was dispatched.

## Local validation

Final local validation passed on Linux:
- `:sharedCore:testDebugUnitTest` and `:sharedUI:testDebugUnitTest` passed.
- Focused Android app tests including `KeyboardViewTest`, `CheonjiinPlusInputTest`, layout/settings tests passed; latest `KeyboardViewTest` report: 59 tests, 0 failures.
- Combined validation finished `BUILD SUCCESSFUL` (91 tasks) and `:sharedUI:compileKotlinIosSimulatorArm64` passed.
- Native Swift/Xcode/iPhone tests remain unavailable on Linux and are not claimed.

## Delegation

Muse was tried on an isolated catalog/gesture task. The initial run and narrowed follow-up failed at runtime setup (read-only launcher update path and tool-output root). A third run corrected those paths and confirmed Meta `muse-spark-1.3-contributor`, then failed with `transport_connect_unreachable` to Meta before any edit. A diagnostic confirmed the same transport failure. No Muse changes were integrated. Two bounded `gpt-6.1-sol` workers implemented the shared settings/catalog and iOS portions, with focused follow-ups; the manager implemented Android gestures/layout and reviews/integration/testing. No usage or savings are claimed.

## Exact changed files

- `android/app/src/main/java/com/ghtnql/kkkeyboard/CheonjiinInput.kt`
- `android/app/src/main/java/com/ghtnql/kkkeyboard/ComposeMainActivity.kt`
- `android/app/src/main/java/com/ghtnql/kkkeyboard/KeyboardLayoutSettings.kt`
- `android/app/src/main/java/com/ghtnql/kkkeyboard/KoreanKeyboardService.kt`
- `android/app/src/main/java/com/ghtnql/kkkeyboard/LongPressKeyButton.kt`
- `android/app/src/main/java/com/ghtnql/kkkeyboard/MainActivity.kt`
- `android/app/src/main/res/values-en/strings.xml`
- `android/app/src/main/res/values-ja/strings.xml`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/test/java/com/ghtnql/kkkeyboard/CheonjiinPlusInputTest.kt`
- `android/app/src/test/java/com/ghtnql/kkkeyboard/ComposeSharedSettingsTest.kt`
- `android/app/src/test/java/com/ghtnql/kkkeyboard/KeyboardLayoutSettingsTest.kt`
- `android/app/src/test/java/com/ghtnql/kkkeyboard/KeyboardViewTest.kt`
- `ios/KeyboardExtension/KeyboardViewController.swift`
- `ios/KeyboardExtension/LongPressKeyButton.swift`
- `ios/Shared/CheonjiinInput.swift`
- `ios/Shared/IOSKeyboardSettingsPlatform.swift`
- `ios/Shared/KeyboardLayoutSettings.swift`
- `ios/Tests/CheonjiinInputTests.swift`
- `ios/Tests/CheonjiinKeyboardRenderingTests.swift`
- `ios/Tests/KeyboardLayoutSettingsTests.swift`
- `ios/Tests/LongPressKeyButtonTests.swift`
- `ios/project.yml`
- `sharedCore/src/commonMain/kotlin/com/ghtnql/kkkeyboard/sharedcore/LongPressSymbols.kt`
- `sharedCore/src/commonTest/kotlin/com/ghtnql/kkkeyboard/sharedcore/LongPressSymbolsTest.kt`
- `sharedUI/src/commonMain/kotlin/com/ghtnql/kkkeyboard/sharedui/KKKeyboardApp.kt`
- `sharedUI/src/commonMain/kotlin/com/ghtnql/kkkeyboard/sharedui/LearningModels.kt`
- `sharedUI/src/commonMain/kotlin/com/ghtnql/kkkeyboard/sharedui/UiLocalization.kt`
- `sharedUI/src/commonTest/kotlin/com/ghtnql/kkkeyboard/sharedui/LayoutOptionsTest.kt`
- `docs/longpress-plus-local-validation.md` (this report)
