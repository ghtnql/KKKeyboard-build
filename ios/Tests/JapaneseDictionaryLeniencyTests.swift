import XCTest

final class JapaneseDictionaryLeniencyTests: XCTestCase {
    private func dictionary(_ rows: [[String: Any]]) throws -> JapaneseDictionary {
        let data = try JSONSerialization.data(withJSONObject: ["version": 1, "entries": rows])
        return try JapaneseDictionary(data: data)
    }

    private func row(_ id: String, _ priority: Int, _ alias: String, _ surface: String) -> [String: Any] {
        ["id": id, "priority": priority, "aliases": [alias], "surfaces": [surface]]
    }

    func testLiteralAliasesPrecedeTolerantCollisions() throws {
        let dict = try dictionary([row("tolerant", 0, "규우뉴우", "牛乳"), row("literal", 9, "큐뉴", "literal")])
        XCTAssertEqual(dict.exact("큐뉴"), ["literal"])
        XCTAssertEqual(dict.exact("규뉴"), ["牛乳", "literal"])
        XCTAssertEqual(dict.exact("규우뉴우"), ["牛乳"])
    }

    func testPredictionsPreferLiteralPrefixesAndAcceptShortenedOrComposingVowels() throws {
        let dict = try dictionary([row("tolerant", 0, "규우뉴우가", "牛乳"), row("literal", 9, "큐뉴가", "literal")])
        XCTAssertEqual(dict.completions("큐뉴"), ["literal", "牛乳"])
        XCTAssertTrue(dict.completions("규뉴").contains("牛乳"))
        XCTAssertTrue(dict.completions("규늉").contains("牛乳"))
        let tokyo = try dictionary([row("tokyo", 0, "토우쿄우카메라", "東京カメラ")])
        XCTAssertEqual(tokyo.completions("도쿄카"), ["東京カメラ"])
        XCTAssertEqual(tokyo.completions("토쿄카"), ["東京カメラ"])
    }

    func testComposingOnsetVariantsKeepPredictionsWithoutChangingExactCodas() throws {
        let dict = try dictionary([row("camera", 10, "가토쿄카메라", "東京カメラ"),
                                   row("next", 20, "가타테", "fixture")])
        XCTAssertTrue(dict.completions("가돜").contains("東京カメラ"))
        XCTAssertTrue(dict.completions("가탙").contains("fixture"))
        XCTAssertTrue(dict.exact("가돜").isEmpty)
        XCTAssertTrue(dict.exact("가탙").isEmpty)
    }

    func testSegmentsConsumeOriginalLengthsAndPreserveHiatus() throws {
        let dict = try dictionary([row("milk", 0, "규뉴", "牛乳"), row("eye", 1, "아이", "愛")])
        let input = Array("규우뉴우데스")
        let match = dict.longestMatch(input, offset: 0)
        XCTAssertEqual(match?.length, 4)
        XCTAssertEqual(match?.surfaces, ["牛乳"])
        XCTAssertTrue(dict.exact("아").isEmpty)
        XCTAssertEqual(dict.exact("아이"), ["愛"])
        XCTAssertTrue(dict.exact("규 뉴").isEmpty)
    }
}
