# Cheonjiin layout

The Android experimental Cheonjiin layout follows the established Samsung 3x4 core:

```text
ㅣ    ·    ㅡ
ㄱㅋ  ㄴㄹ  ㄷㅌ
ㅂㅍ  ㅅㅎ  ㅈㅊ
     ㅇㅁ
```

- Repeated consonant taps cycle related letters, for example `ㄱ -> ㅋ -> ㄲ`.
- `ㅣ`, `·`, and `ㅡ` combine in writing order into all 21 modern vowels.
- The repeat-key timeout is adjustable from 400 ms to 1600 ms.
- Backspace removes one Cheonjiin vowel stroke at a time while the vowel is active.
- Korean and Korean-phonetic Japanese modes share the existing composers and candidate engine.

The control placement follows the ALKeyboard screenshot supplied by the user on
2026-09-21: cursor arrows share the left column; delete and enter share the right
column; symbols, language mode and a double-width space key occupy the bottom row.
The three vowel columns and the consonant groups align on an eight-unit grid.
This adapts the screenshot's organization to standard Cheonjiin; it does not
implement the screenshot's separate-consonant Cheonjiin Plus input rules.

Android cursor keys are always available. QWERTY and symbol pages place a pair
of 44dp buttons in the toolbar, while Cheonjiin and Flick use their existing side
keys. Numeric fields include them in the action row. The optional extra cursor
row and its settings controls are retired; saved legacy preferences are ignored.

The layout and multi-tap behavior were checked against the original Cheonjiin descriptions:

- https://patents.google.com/patent/KR20000049347A/ko
- https://patents.google.com/patent/KR20040009266A/ko

Cheonjiin Plus remains out of scope until its exact ALKeyboard mapping and combination rules are verified.
