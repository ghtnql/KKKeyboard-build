# Android QWERTY usability

Reference: Samsung's official keyboard screenshots in
https://www.samsungsvc.co.kr/solution/1389158 (reviewed 2026-09-20).
Only the layout conventions are referenced, not their artwork or implementation.

- Preserve the standard two-beolsik letter order on a ten-unit grid.
- Center the nine-key second row with half-unit spacers.
- Keep Shift and Delete at the ends of the third row, each 1.5 units wide.
  Delete is immediately to the right of the final vowel key.
- Footer: symbols, Korean/Japanese mode, comma, wide space, period, editor action.
  Email and URI fields replace comma with `@` and `/` without adding a row.
- Keep the system keyboard picker and settings in the fixed-height candidate bar.
  Candidate changes must not move any character key.
- Two symbol pages use the same row geometry and preserve pending composition
  when switching pages. Literal symbol input retains the existing commit behavior.
- The action key reflects the editor action; pending Japanese input shows Convert.
  Space/Enter conversion semantics and prediction ranking are unchanged.
- Key backgrounds have visual insets, not dead touch gaps. Shift has an active
  state; Delete supports accessibility clicks, hold-repeat, and cancellation.
- Input/output modes, symbol pages, and future alternative layouts are separate
  concepts. No Flick or ten-key layout is introduced by this change.

## Verification

Run `gradle testDebugUnitTest lintDebug assembleDebug` from `android/` with an
Android SDK configured. The project currently relies on an installed Gradle 8.9.

`KeyboardViewTest` uses Robolectric with Android 35 and native graphics to test
the actual Android view tree and input-connection operations. It covers narrow
and wide widths, landscape, enlarged system text, Japanese candidate selection,
symbol switching, editor actions, password restrictions, and delete repeat.
Rendered images are written to `android/app/build/reports/keyboard/`.

These are host-side UI tests, not an emulator or physical-device session.
Real-device touch feel, third-party editor integration, and iOS layout parity
still require separate verification. This change is Android-only.
