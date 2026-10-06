import XCTest

final class LearningCoreTests: XCTestCase {
    func testSharedContentLoadsAndFilters() throws {
        let items = try LearningContent.load(bundle: Bundle(for: LearningCoreTests.self))
        XCTAssertEqual(items.count, 564)
        XCTAssertEqual(
            LearningContent.items(from: items, mode: .japaneseToHangul, gameType: "typing").prefix(2).map(\.sourceText),
            ["ありがとう", "こんにちは"]
        )
        XCTAssertEqual(LearningContent.items(from: items, mode: .koreanTyping, gameType: "rain").count, 64)
    }

    func testAddedShortJamoVocabularyIsBundledForBothGames() throws {
        let items = try LearningContent.load(bundle: Bundle(for: LearningCoreTests.self))
        let word = try XCTUnwrap(items.first { $0.id == "jamo_ja_word_vegetables" })
        XCTAssertEqual(word.sourceText, "野菜")
        XCTAssertEqual(word.acceptedAnswers, ["야사이"])
        XCTAssertEqual(word.meaningHint, "뜻: 채소")
        XCTAssertEqual(Set(word.gameTypes), Set(["rain", "cafe"]))
        XCTAssertEqual(LearningContent.items(from: items, mode: .japaneseToHangul, gameType: "rain").count, 63)
        XCTAssertEqual(LearningContent.items(from: items, mode: .japaneseToHangul, gameType: "cafe").count, 274)
    }

    func testPracticeAcceptsAlternativeAndCalculatesResult() throws {
        let items = try LearningContent.load(bundle: Bundle(for: LearningCoreTests.self))
        let selected = LearningContent.items(from: items, mode: .japaneseToHangul, gameType: "typing")
        let session = PracticeSession(mode: .japaneseToHangul, items: Array(selected.prefix(2)), startedAtMs: 1_000)
        XCTAssertTrue(session.submit("아리가또").correct)
        XCTAssertTrue(session.submit("콘니치와").completed)
        let result = session.result(finishedAtMs: 61_000)
        XCTAssertEqual(result.accuracy, 100)
        XCTAssertEqual(result.maxCombo, 2)
    }

    func testRainScoresAndCostsLives() throws {
        let items = try LearningContent.load(bundle: Bundle(for: LearningCoreTests.self))
        let selected = LearningContent.items(from: items, mode: .koreanTyping, gameType: "rain")
        var config = RainGameConfig()
        config.fallPerSecond = 10
        config.startingLives = 1
        let game = RainGame(mode: .koreanTyping, items: selected, startedAtMs: 0, config: config) { 0.5 }
        game.tick(deltaMs: 0)
        XCTAssertTrue(game.submit("안녕하세요"))
        XCTAssertEqual(game.score, 50)
        repeatElement((), count: 10).forEach { _ in game.tick(deltaMs: 240) }
        game.tick(deltaMs: 100)
        XCTAssertEqual(game.lives, 0)
        XCTAssertTrue(game.isFinished)
    }
}
