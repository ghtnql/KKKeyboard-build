import XCTest

final class JapaneseTransliteratorTests: XCTestCase {
    func testSeedFixturesMatchExpectedCandidates() {
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "아리가토"), ["ありがとう"])
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "곤니치와"), ["こんにちは"])
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "스시"), ["すし", "寿司"])
    }

    func testLongVowelSokuonNasalAndYouonFixtures() {
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "코히"), ["コーヒー"])
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "킷테"), ["きって"])
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "온나"), ["おんな"])
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "료코"), ["りょこう"])
    }

    func testReportedOhayoInputsAndFullGreeting() {
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "오하요"), ["おはよう"])
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "오하요우"), ["おはよう"])
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "오하이오"), ["おはよう"])
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "고자이마스"), ["ございます"])
        XCTAssertEqual(
            JapaneseTransliterator.candidates(for: "오하요 고자이마스"),
            ["おはようございます"]
        )
    }

    func testNormalizationAndRuleFallback() {
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "  아리가토\n"), ["ありがとう"])
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "가나다"), ["がなだ", "ガナダ"])
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "바파"), ["ばぱ", "バパ", "ゔぁぱ", "ヴァパ"])
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "츠츄"), ["つちゅ", "ツチュ"])
        XCTAssertEqual(JapaneseTransliterator.candidates(for: "즈"), ["ず", "ズ", "づ", "ヅ"])
        XCTAssertTrue(JapaneseTransliterator.candidates(for: "에반게리온").contains("エヴァンゲリオン"))
        XCTAssertTrue(JapaneseTransliterator.candidates(for: "latin").isEmpty)
        XCTAssertTrue(JapaneseTransliterator.candidates(for: "   ").isEmpty)
    }

    func testExactLookupDoesNotNormalizeHotPathInput() {
        XCTAssertEqual(JapaneseTransliterator.candidatesExact(for: "아리가토"), ["ありがとう"])
        XCTAssertTrue(JapaneseTransliterator.candidatesExact(for: " 아리가토 ").isEmpty)
        XCTAssertTrue(JapaneseTransliterator.candidatesExact(for: "").isEmpty)
    }
    func testBundledJapaneseGameKanjiHaveSelectableCandidates() {
        for (reading, surface) in [
            ("히카리", "光"),
            ("쿠모", "雲"),
            ("하시루", "走る"),
            ("쿄오와 이이 텐키데스", "今日はいい天気です。"),
        ] {
            XCTAssertTrue(JapaneseTransliterator.candidatesExact(for: reading).contains(surface), reading)
        }
    }

    func testShortenedMilkAndTokyoPronunciations() {
        for input in ["규뉴", "큐뉴", "큐우뉴우"] {
            XCTAssertTrue(JapaneseTransliterator.candidatesExact(for: input).contains("牛乳"), input)
        }
        for input in ["토쿄", "도쿄", "토우쿄우", "도오쿄오"] {
            XCTAssertTrue(JapaneseTransliterator.candidatesExact(for: input).contains("東京"), input)
            XCTAssertTrue(JapaneseTransliterator.suggestionsExact(for: input).contains("東京"), input)
        }
        XCTAssertTrue(JapaneseTransliterator.candidatesExact(for: "규 뉴").contains("牛乳"))
        XCTAssertTrue(JapaneseTransliterator.candidatesExact(for: "규뉴데스").contains("牛乳です"))
    }

}
