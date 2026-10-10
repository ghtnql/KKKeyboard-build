import XCTest

final class HangulComposerTests: XCTestCase {
    func testComposesSimpleSyllable() {
        let composer = HangulComposer()
        XCTAssertEqual(composer.input("ㄱ").composing, "ㄱ")
        XCTAssertEqual(composer.input("ㅏ").composing, "가")
    }

    func testMovesFinalConsonantToNextSyllable() {
        let composer = HangulComposer()
        _ = composer.input("ㄱ")
        _ = composer.input("ㅏ")
        XCTAssertEqual(composer.input("ㄴ").composing, "간")

        let edit = composer.input("ㅏ")
        XCTAssertEqual(edit.commit, "가")
        XCTAssertEqual(edit.composing, "나")
    }

    func testCompoundVowelAndBackspace() {
        let composer = HangulComposer()
        _ = composer.input("ㅇ")
        _ = composer.input("ㅗ")
        XCTAssertEqual(composer.input("ㅏ").composing, "와")
        XCTAssertEqual(composer.backspace().composing, "오")
    }

    func testAllCompoundVowelsCompose() {
        let cases: [(Character, Character, String)] = [
            ("ㅗ", "ㅏ", "와"), ("ㅗ", "ㅐ", "왜"), ("ㅗ", "ㅣ", "외"),
            ("ㅜ", "ㅓ", "워"), ("ㅜ", "ㅔ", "웨"), ("ㅜ", "ㅣ", "위"),
            ("ㅡ", "ㅣ", "의")
        ]

        for (first, second, expected) in cases {
            let composer = HangulComposer()
            _ = composer.input("ㅇ")
            _ = composer.input(first)
            XCTAssertEqual(composer.input(second).composing, expected)
        }
    }

    func testCompoundFinalSplitsBeforeVowel() {
        let composer = HangulComposer()
        _ = composer.input("ㄷ")
        _ = composer.input("ㅏ")
        _ = composer.input("ㄹ")
        XCTAssertEqual(composer.input("ㄱ").composing, "닭")

        let edit = composer.input("ㅏ")
        XCTAssertEqual(edit.commit, "달")
        XCTAssertEqual(edit.composing, "가")
    }

    func testAllCompoundFinalsComposeAndSplit() {
        let cases: [(Character, Character, Character, Character, String, String, String)] = [
            ("ㄴ", "ㅓ", "ㄱ", "ㅅ", "넋", "넉", "사"),
            ("ㅇ", "ㅏ", "ㄴ", "ㅈ", "앉", "안", "자"),
            ("ㅁ", "ㅏ", "ㄴ", "ㅎ", "많", "만", "하"),
            ("ㄷ", "ㅏ", "ㄹ", "ㄱ", "닭", "달", "가"),
            ("ㅅ", "ㅏ", "ㄹ", "ㅁ", "삶", "살", "마"),
            ("ㅂ", "ㅏ", "ㄹ", "ㅂ", "밟", "발", "바"),
            ("ㄱ", "ㅗ", "ㄹ", "ㅅ", "곬", "골", "사"),
            ("ㅎ", "ㅏ", "ㄹ", "ㅌ", "핥", "할", "타"),
            ("ㅇ", "ㅡ", "ㄹ", "ㅍ", "읊", "을", "파"),
            ("ㅇ", "ㅣ", "ㄹ", "ㅎ", "잃", "일", "하"),
            ("ㄱ", "ㅏ", "ㅂ", "ㅅ", "값", "갑", "사")
        ]

        for (initial, medial, firstFinal, secondFinal, expectedCompound, expectedCommit, expectedNext) in cases {
            let composer = HangulComposer()
            _ = composer.input(initial)
            _ = composer.input(medial)
            _ = composer.input(firstFinal)
            XCTAssertEqual(composer.input(secondFinal).composing, expectedCompound)

            let split = composer.input("ㅏ")
            XCTAssertEqual(split.commit, expectedCommit)
            XCTAssertEqual(split.composing, expectedNext)
        }
    }

    func testAllInitialAndMedialIndexesRemainMappedCorrectly() {
        let initials: [Character] = ["ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"]
        let expectedA: [String] = ["가", "까", "나", "다", "따", "라", "마", "바", "빠", "사", "싸", "아", "자", "짜", "차", "카", "타", "파", "하"]

        for (initial, expected) in zip(initials, expectedA) {
            let composer = HangulComposer()
            _ = composer.input(initial)
            XCTAssertEqual(composer.input("ㅏ").composing, expected)
        }

        let vowels: [Character] = ["ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ", "ㅖ", "ㅗ", "ㅘ", "ㅙ", "ㅚ", "ㅛ", "ㅜ", "ㅝ", "ㅞ", "ㅟ", "ㅠ", "ㅡ", "ㅢ", "ㅣ"]
        for vowel in vowels {
            let composer = HangulComposer()
            XCTAssertEqual(composer.input(vowel).composing, String(vowel))
        }
    }

    func testFlushClearsState() {
        let composer = HangulComposer()
        _ = composer.input("ㅎ")
        _ = composer.input("ㅏ")
        _ = composer.input("ㄴ")
        XCTAssertEqual(composer.flush(), "한")
        XCTAssertEqual(composer.currentText(), "")
        XCTAssertFalse(composer.backspace().consumed)
    }

    func testRepeatedFastCompositionDoesNotLoseOrLeakState() {
        let composer = HangulComposer()
        var output = ""
        output.reserveCapacity(2_000)

        for _ in 0..<2_000 {
            _ = composer.input("ㅎ")
            _ = composer.input("ㅏ")
            _ = composer.input("ㄴ")
            output += composer.flush()
            XCTAssertEqual(composer.currentText(), "")
        }

        XCTAssertEqual(output.count, 2_000)
        XCTAssertEqual(output, String(repeating: "한", count: 2_000))
    }
}
