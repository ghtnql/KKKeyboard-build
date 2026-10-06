import XCTest

final class CheonjiinInputTests: XCTestCase {
    func testDirectPlusConsonantsAndAllTenseConsonantsResetCycling() {
        let input = CheonjiinInput()
        for character in Array("ㄱㅋㄴㄹㄷㅌㅂㅍㅅㅎㅈㅊㅇㅁㄲㄸㅃㅆㅉ") {
            XCTAssertEqual(input.inputDirectConsonant(character), .append(character))
            XCTAssertEqual(input.inputDirectConsonant(character), .append(character))
        }
        XCTAssertEqual(input.input(.i, at: 0), .append("ㅣ"))
        XCTAssertEqual(input.input(.dot, at: 0.1), .replaceLast("ㅏ"))
        XCTAssertEqual(input.inputDirectConsonant("ㄲ"), .append("ㄲ"))
        XCTAssertNil(input.backspace())
        XCTAssertEqual(input.input(.i, at: 0.2), .append("ㅣ"))
    }

    func testQuickSecondTapProducesTenseConsonantOnlyForFiveBaseKeys() {
        let pairs: [(Character, Character)] = [("ㄱ", "ㄲ"), ("ㄷ", "ㄸ"), ("ㅂ", "ㅃ"), ("ㅅ", "ㅆ"), ("ㅈ", "ㅉ")]
        for (base, tense) in pairs {
            let input = CheonjiinInput()
            XCTAssertEqual(input.inputDirectConsonant(base, at: 1.0, doubleTapTimeout: 0.9), .append(base))
            XCTAssertEqual(input.inputDirectConsonant(base, at: 1.1, doubleTapTimeout: 0.9), .replaceLast(tense))
            XCTAssertEqual(input.inputDirectConsonant(base, at: 1.2, doubleTapTimeout: 0.9), .append(base))
        }

        for plain in Array("ㅋㄴㄹㅌㅍㅎㅊㅇㅁ") {
            let input = CheonjiinInput()
            XCTAssertEqual(input.inputDirectConsonant(plain, at: 1.0, doubleTapTimeout: 0.9), .append(plain))
            XCTAssertEqual(input.inputDirectConsonant(plain, at: 1.1, doubleTapTimeout: 0.9), .append(plain))
        }

        let slow = CheonjiinInput()
        XCTAssertEqual(slow.inputDirectConsonant("ㄱ", at: 1.0, doubleTapTimeout: 0.9), .append("ㄱ"))
        XCTAssertEqual(slow.inputDirectConsonant("ㄱ", at: 2.0, doubleTapTimeout: 0.9), .append("ㄱ"))
    }

    func testVowelBreaksDirectDoubleTapSequence() {
        let input = CheonjiinInput()
        XCTAssertEqual(input.inputDirectConsonant("ㄱ", at: 1.0, doubleTapTimeout: 0.9), .append("ㄱ"))
        XCTAssertEqual(input.input(.i, at: 1.1), .append("ㅣ"))
        XCTAssertEqual(input.inputDirectConsonant("ㄱ", at: 1.2, doubleTapTimeout: 0.9), .append("ㄱ"))
    }

    func testVowelStrokesComposeAndBackspaceOneStrokeAtATime() {
        let input = CheonjiinInput()
        XCTAssertEqual(input.input(.i, at: 0), .append("ㅣ"))
        XCTAssertEqual(input.input(.dot, at: 0.1), .replaceLast("ㅏ"))
        XCTAssertEqual(input.input(.i, at: 0.2), .replaceLast("ㅐ"))
        XCTAssertEqual(input.backspace(), .replaceLast("ㅏ"))
        XCTAssertEqual(input.backspace(), .replaceLast("ㅣ"))
        XCTAssertEqual(input.backspace(), .removeLast)
        XCTAssertNil(input.backspace())
    }

    func testDotWaitsForNextStrokeAndBackspaceConsumesIt() {
        let input = CheonjiinInput()
        XCTAssertEqual(input.input(.dot, at: 0), .none)
        XCTAssertEqual(input.backspace(), .none)
        XCTAssertEqual(input.input(.dot, at: 0.1), .none)
        XCTAssertEqual(input.input(.dot, at: 0.2), .none)
        XCTAssertEqual(input.input(.eu, at: 0.3), .append("ㅛ"))
    }

    func testConsonantsCycleWithinTimeoutAndAppendAfterIt() {
        let input = CheonjiinInput()
        XCTAssertEqual(input.input(.giyeok, at: 1), .append("ㄱ"))
        XCTAssertEqual(input.input(.giyeok, at: 1.2), .replaceLast("ㅋ"))
        XCTAssertEqual(input.input(.giyeok, at: 1.4), .replaceLast("ㄲ"))
        XCTAssertEqual(input.input(.giyeok, at: 1.6), .replaceLast("ㄱ"))
        XCTAssertEqual(input.input(.giyeok, at: 2.6), .append("ㄱ"))
        XCTAssertEqual(input.input(.nieun, at: 2.7), .append("ㄴ"))
    }

    func testVowelBreaksConsonantCycle() {
        let input = CheonjiinInput()
        XCTAssertEqual(input.input(.giyeok, at: 0), .append("ㄱ"))
        XCTAssertEqual(input.input(.i, at: 0.1), .append("ㅣ"))
        XCTAssertEqual(input.input(.dot, at: 0.2), .replaceLast("ㅏ"))
        XCTAssertEqual(input.input(.giyeok, at: 0.3), .append("ㄱ"))
        XCTAssertNil(input.backspace())
    }
}
