# Hangul Flick (Android prototype)

## Benchmarks and scope

- [Samsung Japan keyboard guide](https://www.samsung.com/jp/explore/special/smartphone-tips-24-smartphone-keyboard/): center tap plus left/up/right/down selection, and flick-only repeated input without multi-tap waiting.
- [NTT Docomo / Samsung SC-03E guide, printed pages 15-16](https://www.docomo.ne.jp/binary/pdf/support/trouble/smart_phone/sc03e/SC-03E_startup_guide_08.pdf): inspected the ten-key keyboard, adjacent function keys and cross-shaped flick guide. This older guide is a structural reference, not a claim about current visual styling.

The Korean mapping below is our own experimental adaptation, not a standardized
Korean layout or a copy of Samsung's implementation. It emits individual jamo
into the existing Hangul composer and Japanese word/candidate pipeline.
No dictionary, transliteration, or composition timeout changes are involved.

## Mapping

The twelve large keys occupy three columns and four rows. All 19 modern
initials and 21 vowels appear exactly once. Compound finals are composed using
successive consonants, as on QWERTY.

| Grid | Tap | Left | Up | Right | Down |
| --- | --- | --- | --- | --- | --- |
| 1,1 | ㅏ | ㅑ | ㅐ | ㅒ | |
| 1,2 | ㅓ | ㅕ | ㅔ | ㅖ | |
| 1,3 | ㅗ | ㅛ | ㅘ | ㅙ | ㅚ |
| 2,1 | ㄱ | ㅋ | ㄲ | | |
| 2,2 | ㄴ | ㄹ | | | |
| 2,3 | ㅜ | ㅠ | ㅝ | ㅞ | ㅟ |
| 3,1 | ㄷ | ㅌ | ㄸ | | |
| 3,2 | ㅂ | ㅍ | ㅃ | | |
| 3,3 | ㅡ | ㅣ | ㅢ | | |
| 4,1 | ㅅ | ㅎ | ㅆ | | |
| 4,2 | ㅈ | ㅊ | ㅉ | | |
| 4,3 | ㅇ | ㅁ | | | |

The left rail contains symbols, Korean/Japanese mode, cursor-left and
punctuation. The right rail contains delete, cursor-right, space and the editor
action. Punctuation uses tap `.`, left `,`, up `?`, right `!`, down `…`;
email/URI fields substitute `@`/`/` for its tap. Number/phone fields retain their
dedicated keypads. The symbol pages return to whichever letter layout is saved.

## Interaction and preferences

- Default remains QWERTY for existing installations. The toolbar keyboard icon
  opens a checked layout menu; the same choice is available in the app settings.
- Layout choice and flick distance persist separately from language mode,
  orientation-specific key height, number/cursor rows and theme colors.
- Distance defaults to 20dp, adjustable from 12 to 32dp. It is measured from
  the actual touch origin, not the key center. There is no waiting timer.
- Direction hints stay visible. During a gesture the selected character also
  appears in the fixed-height toolbar, without moving keys or committing text.
- Commit once on release. Small movement remains a tap; return to the center
  cancels a directional choice. A small diagonal hysteresis avoids jitter.
- An unassigned direction, ACTION_CANCEL, an excessive drag or hiding the input
  view commits nothing. Extra pointer releases do not duplicate the active key.
- Accessibility click selects the center; named custom actions expose the four
  directions. No gesture is required to reach a variant through accessibility.
- Layout switching preserves the active composing span. The prior batched-edit
  and delayed-selection-acknowledgement fix is reused for all flick input.

## Verification and limitations

Run `gradle testDebugUnitTest lintDebug assembleDebug` from `android/`.
Pure tests cover all jamo and direction rules. Robolectric Android 35 native UI
tests cover real MotionEvents, all four directions, repeated taps, cancellation,
pointer ownership, cross-key dragging, accessibility actions, Korean sentences,
Japanese predictions, switching layouts mid-composition and saved sensitivity.
Rendered screenshots are under `android/app/build/reports/keyboard/flick-*.png`.

This is an Android-only prototype. The Korean grouping still needs physical
device usability feedback. Host-side rendering and gesture tests are not
real-device validation. iOS Flick has not been implemented in this change.
