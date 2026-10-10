# Android composition callback regression

The QWERTY build had no composition timeout. The failure was in editor updates:
`applyEdit` could commit a syllable and start the next composing syllable in two
separate operations. A selection notification between those operations reset
the composer. A delayed acknowledgement of a space or candidate commit could
also reset a newer composition.

The previous input path was reproduced with a notifying `BaseInputConnection`:
all three regression tests failed, including a space followed by a split
consonant/vowel and a multi-word sentence containing compound final consonants.

The fix batches each keyboard action using `beginBatchEdit`/`endBatchEdit` in
`try/finally`. `CompositionConnection` also predicts UTF-16 selection/composition
offsets and keeps up to 128 pending acknowledgements. Acknowledgements of the
IME's own writes do not clear newer composition. Unmatched notifications still
honor actual cursor movement, selections, and editor-side composition removal.
Only offsets are retained; no editor text is read or logged by this tracker.

Reference: Android's [InputConnection contract](https://developer.android.com/reference/android/view/inputmethod/InputConnection#beginBatchEdit()).

`KeyboardViewTest` exercises synchronous, delayed and coalesced notifications,
editors declining batches, 10/30-second pauses, genuine cursor moves, selection
replacement, editor-side composition cancellation, and Japanese candidate
commit followed immediately by the next reading. These are host-side Android
tests, not confirmation on the user's physical device or messaging app.
