# Japanese vocabulary candidates

`ja_common.json` is the single bundled vocabulary source for Android and iOS.
It currently contains 100 common words and expressions. `priority` is a curated
everyday-use ordering (smaller first), not a measured corpus frequency. Korean
pronunciation aliases are explicitly authored. Compatibility aliases such as
`한국` are retained, but this is not a general translation or fuzzy spelling
correction model. Review status is `machine_checked`, not
native-speaker approval.

Candidate order is exact aliases, up to three prefix predictions, longest-match
word combinations, then literal kana. Ties use the stable entry ID. Prefixes are
indexed by decomposed Hangul keys, including intermediate final consonants.
Longer predictions require an explicit candidate tap. Android space/enter still
confirm only the current reading, not a longer prediction. The original literal
reading remains selectable. No input history, network calls, or user text logs
are used.

Common greeting spellings were cross-checked against the Japan Foundation's
[greeting reference](https://www.jpf.go.jp/j/project/japanese/survey/area/country/syllabus/pdf/11sy_honyaku_1korea.pdf).
The vocabulary selection and Hangul aliases are maintained here independently;
this is not a copy of a textbook or a corpus-derived frequency table.

`../test-fixtures/ja_predictions.json` is used by both platforms. Tests cover
prefix ranking, Korean pronunciation variants, particles in greetings, long
vowels, incomplete Hangul, word suffixes, homophones, literal alternatives, and
automatic-versus-explicit confirmation. Tests also check every bundled alias.

Android packages the JSON as a Java resource; iOS includes it in the extension
and test bundles through `ios/project.yml`. Resource loading happens once when
the keyboard is created. Changes to this file do not require separate platform
dictionary edits.

The Foundation-only Swift prediction tests can also be compiled on Linux:

```sh
swiftc ios/Shared/JapaneseDictionary.swift ios/Shared/JapaneseTransliterator.swift \
  ios/Tests/JapanesePredictionTests.swift tools/verify-japanese.swift \
  -o /tmp/verify-japanese
cp shared/dictionaries/ja_common.json /tmp/ja_common.json
cp shared/test-fixtures/ja_predictions.json /tmp/ja_predictions.json
/tmp/verify-japanese
```

This does not replace the Xcode build or real keyboard interaction tests.
