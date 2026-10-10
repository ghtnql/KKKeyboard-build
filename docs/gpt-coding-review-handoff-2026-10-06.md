# GPT coding handoff — 2026-10-06

The user asked Codex to finish source implementation and code review only. Build, compilation, executable tests, emulator/phone interaction, APK/AAB/iOS release, Drive update and remote push were not performed in this follow-up. GPT owns the subsequent verification and build. Original GPT worktrees are preserved; use the integrated `migration/kmp-compose` checkout, not the unfinished pronunciation branch.

## Integrated behavior

- Cheonjiin Plus, original feature commit `aa5cdd8d1155e99f8163f6debff39448bb8d1a62`, is included together with the unfinished pronunciation changes based on `d4193b7`.
- Plus has split consonants and fixed holds ㅋ→ㄲ / ㅌ→ㄸ / ㅍ→ㅃ / ㅎ→ㅆ / ㅊ→ㅉ. Standard Cheonjiin/Plus/QWERTY have three editable symbol slots per key and reset; fixed tense choices remain first, empty slots are retained in storage and hidden in the popup. Flick behavior is retained.
- Japanese word/convert practice, Rain, Cafe and Japanese keyboard dictionary conversion accept ㄱ/ㅋ, ㄷ/ㅌ equivalents and optional long-vowel extensions. Examples: 규슈/큐슈, 도쿄/토쿄/토우쿄우, 규뉴/규우뉴우. The original displayed vocabulary and entered Hangul are preserved. Canonical matching is a lookup/judgment operation; it does not rewrite Korean input.
- Existing literal Japanese dictionary matches remain first. Tolerant dictionary aliases also participate in predictions and segmentation. Both native keyboards use the changed dictionary/transliterator, including composing onset prefixes and optional long-vowel prefixes. Full dictionary matches still preserve actual codas; the last composing ㅋ/ㅌ alternative applies only to predictions.
- Exact Korean typing and sentence-copy grading retain their existing semantics. Shared Japanese pronunciation flags propagate to word practice/Rain/Cafe; the native Android/iOS learning adapters use the same matcher. Both platform candidate commit paths use the same tolerant Japanese provider.

## Review corrections

- The reported `ComposeGameInputTest.everyPlayableBundledItemHasAllThreeDisplayLines` failure was an obsolete expected count 563 versus bundled 564 after 野菜/야사이/채소. Updated the count while retaining per-item completeness assertions; no learning content was removed or weakened.
- Restored iOS dictionary `maxAliasLength` declaration/update used by segmentation. Removed a dangling `ja_pronunciation_leniency.json` Xcode resource reference; no such resource existed or was consumed by a test.
- Corrected iOS candidate-order fixture to expect literal-first plus the additional tolerant candidate, matching Android's policy.
- Added matching Android/iOS regression fixtures for candidate predictions while a next onset is still represented as a previous syllable's coda. Exact-match negative checks protect codas.
- iOS hold gesture now clears prior timer/popup/pointer state before reuse, cancels pre-hold drag-outs irreversibly, cleans up before commit callbacks, and cancels pending touch when an accessibility choice is invoked. Disabled keys cannot emit choices; Android disabled-key protection was added too.
- Original literal reading candidates remain selectable before the additional tolerant fallback. No generic Korean normalization, source dictionary asset, Hangul composer, entitlement or privacy setting was changed by the pronunciation patch.

## Static verification performed

- `git diff --check` passed.
- Parsed bundled JSON: 564 IDs, 564 playable records, no missing Japanese original / Hangul pronunciation / Korean meaning under the existing translation resolver rules. This was a static data audit, not the Kotlin/Compose test execution.
- Parsed Android KO/JA/EN strings XML and iOS project YAML. Every declared iOS source/resource path exists after the dangling reference was removed.
- Reviewed shared settings → Android SharedPreferences/revision → IME and iOS app → App Group → extension paths. Plus layout IDs round-trip through platform settings. Common settings screens retain dismiss/back/close routes.
- Muse `muse-spark-1.3-contributor` performed bounded static pronunciation and gesture reviews plus an isolated iOS gesture edit. The manager inspected actual diffs, discarded findings contradicted by explicit UI rules or current code, integrated changes, and checked both platform call paths. Usage was not returned; no savings claim.
- A final Muse review of the integrated Android/iOS dictionary and gesture files found no additional blocking issue under static review. This does not replace compilation or runtime checks.
- Original GPT `aa5cdd8` unit/compile results are historical only. No passing build or executed regression-test result is claimed for this integrated source.

## GPT verification and build handoff

1. Record exact `git rev-parse HEAD` from `/home/ghtnql/KKKeyboard`, branch `migration/kmp-compose`, and preserve user-modified `AGENTS.md` and untracked parity audit. Do not replay the raw pronunciation worktree over the integrated changes. First validate source; failure means fix/review the same source before release.
2. Run the meaningful checks: common `JapanesePronunciationMatcherTest`, `LongPressSymbolsTest`, existing shared practice and layout tests, `PronunciationLeniencyTest`; Android `JapaneseDictionaryLeniencyTest`, `JapaneseTransliteratorTest`, `PronunciationLearningTest`, `CheonjiinPlusInputTest`, `KeyboardViewTest`, settings/ComposeGameInput checks. The combined suite may be run with `:sharedCore:testDebugUnitTest :sharedUI:testDebugUnitTest :app:testDebugUnitTest -PKK_PARALLEL_DEBUG=true` from `android`, correct Java17/Android SDK environment.
3. Kotlin `:sharedUI:compileKotlinIosSimulatorArm64` alone is not Swift/Xcode/iPhone validation. Native iOS dictionary, keyboard/layout, touch/slide/cancel/accessibility and App Group readback require actual Xcode/iPhone checks through the authorized release workflow. Do not silently start AAB/TestFlight before the APK test gate.
4. Follow existing `/home/ghtnql/KKKeyboard/AGENTS.md`, `docs/release-workflow.md` and APK delivery skill. Before an APK build, update existing Drive progress file ID `1H3StHsJ2wX7Tr8QliJVNejrU6c5hA-n0` with actual source/tests/remaining/platform gaps and read it back exactly. Select a fresh version/immutable filename after inspecting previous releases. Build locally, preserve `.dev` debug package/signature continuity if continuing that channel, verify clean-device install and native gestures, then upload to the established Drive folder and verify a full download checksum. Update the same progress record after delivery.
5. Actual touch checks: Plus tap produces the chosen consonant once; holding selects its fixed tense once; slide selects custom symbols; drag/cancel before hold emits nothing, including drag back inside; hiding/reusing keyboard cancels timers; disabled/accessibility choices do not duplicate; editing/resetting symbol slots reaches both app and extension. Verify the Japanese variants above in suggestions and space/enter conversion, including prefixes, and verify ordinary Korean text remains entered literally.

## Remaining platform limits and status

The iOS extension retains `RequestsOpenAccess=false`. Long-press symbol editing happens in the containing app; the extension settings surface reads canonical App Group values and directs the user to the app. Entitlement visibility, app-written symbols and actual extension readback must be verified on iPhone; defaults are used when the group is unavailable. Native UI/gesture, signed iOS framework/export and phone parity are unverified here. Existing unrelated keyboard/App Group/ad audit items remain open.

Source implementation and static review are ready for GPT's build/test stage. New APK delivery and user test are pending; this coding task does not mark prior APK tests approved or begin any release.
