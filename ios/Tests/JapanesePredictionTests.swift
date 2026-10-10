import XCTest

final class JapanesePredictionTests: XCTestCase {
    func testSharedPredictionFixtures() throws {
        let url = try XCTUnwrap(Bundle(for: JapaneseDictionary.self).url(forResource: "ja_predictions", withExtension: "json"))
        let fixtures = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [[String: Any]])
        for fixture in fixtures {
            let id = try XCTUnwrap(fixture["id"] as? String)
            let input = try XCTUnwrap(fixture["input"] as? String)
            let candidates = JapaneseTransliterator.suggestionsExact(for: input)
            XCTAssertLessThanOrEqual(candidates.count, JapaneseDictionary.maxCandidates, id)
            XCTAssertEqual(Set(candidates).count, candidates.count, id)
            if fixture["empty"] as? Bool == true { XCTAssertTrue(candidates.isEmpty, id) }
            if let first = fixture["first"] as? String { XCTAssertEqual(candidates.first, first, id) }
            for expected in fixture["contains"] as? [String] ?? [] {
                XCTAssertTrue(candidates.contains(expected), "\(id): \(expected)")
            }
            for forbidden in fixture["forbidden"] as? [String] ?? [] {
                XCTAssertFalse(candidates.contains(forbidden), id)
            }
            if let first = fixture["automaticFirst"] as? String {
                XCTAssertEqual(JapaneseTransliterator.candidatesExact(for: input).first, first, id)
            }
            if fixture["automaticEmpty"] as? Bool == true {
                XCTAssertTrue(JapaneseTransliterator.candidatesExact(for: input).isEmpty, id)
            }
        }
    }

    func testEveryBundledAliasFindsItsVocabularyEntry() {
        let dictionary = JapaneseDictionary.shared
        XCTAssertEqual(dictionary.entries.count, 479)
        XCTAssertEqual(dictionary.entries.filter { $0.id.hasPrefix("dict_ja_") }.count, 200)
        for entry in dictionary.entries {
            for alias in entry.aliases {
                let candidates = JapaneseTransliterator.candidatesExact(for: alias)
                XCTAssertTrue(entry.surfaces.allSatisfy { candidates.contains($0) }, "\(entry.id): \(alias)")
            }
        }
    }

    func testDraftMergeKeepsLegacyCandidatesFirstAndAcceptsSingleSyllables() {
        let dictionary = JapaneseDictionary.shared
        XCTAssertEqual(dictionary.exact("스시"), ["すし", "寿司"])
        XCTAssertEqual(dictionary.exact("겐키"), ["げんき", "元気", "元気？", "げんき？"])
        XCTAssertEqual(dictionary.exact("운"), ["うん"])
        XCTAssertEqual(dictionary.exact("엣"), ["えっ"])
        XCTAssertEqual(dictionary.exact("판"), ["パン", "ぱん"])
        XCTAssertNil(dictionary.longestMatch(Array("운가"), offset: 0))
    }

    func testDailyVocabularyAndSentences() throws {
        let url = try XCTUnwrap(Bundle(for: JapaneseDictionary.self).url(forResource: "ja_daily_vocabulary", withExtension: "json"))
        let rows = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [[String: Any]])
        for row in rows {
            let input = try XCTUnwrap(row["input"] as? String)
            let surface = try XCTUnwrap(row["surface"] as? String)
            if row["requireAutomatic"] as? Bool != false {
                XCTAssertTrue(JapaneseTransliterator.candidatesExact(for: input).contains(surface), input)
            }
            XCTAssertTrue(JapaneseTransliterator.suggestionsExact(for: input).contains(surface), input)
        }
    }

    func testPriorityIsIndependentOfFileOrder() throws {
        let data = Data("""
        {"version":1,"entries":[
            {"id":"b","priority":20,"aliases":["아리타"],"surfaces":["B"]},
            {"id":"c","priority":10,"aliases":["아리마"],"surfaces":["C"]},
            {"id":"a","priority":10,"aliases":["아리가"],"surfaces":["A"]}
        ]}
        """.utf8)
        XCTAssertEqual(try JapaneseDictionary(data: data).completions("아리"), ["A", "C", "B"])
    }
}
