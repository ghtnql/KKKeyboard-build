import Foundation
import XCTest

final class JapaneseCandidateLearningTests: XCTestCase {
    private var suiteName = ""
    private var defaults: UserDefaults!
    private var learning: JapaneseCandidateLearning!

    override func setUp() {
        super.setUp()
        suiteName = "JapaneseCandidateLearningTests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)
        learning = JapaneseCandidateLearning(defaults: defaults)
    }

    override func tearDown() {
        defaults.removePersistentDomain(forName: suiteName)
        learning = nil
        defaults = nil
        suiteName = ""
        super.tearDown()
    }

    func testUnseenReadingKeepsDictionaryOrder() {
        let original = ["にほん", "日本", "二本"]
        XCTAssertEqual(learning.rank(reading: "니혼", candidates: original), original)
    }

    func testSelectionMovesCandidateAndFrequencyWins() {
        let original = ["橋", "箸", "端"]
        learning.recordSelection(reading: "하시", candidate: "箸")
        XCTAssertEqual(learning.rank(reading: "하시", candidates: original), ["箸", "橋", "端"])

        learning.recordSelection(reading: "하시", candidate: "橋")
        XCTAssertEqual(learning.rank(reading: "하시", candidates: original), ["橋", "箸", "端"])

        learning.recordSelection(reading: "하시", candidate: "箸")
        XCTAssertEqual(learning.rank(reading: "하시", candidates: original), ["箸", "橋", "端"])
    }

    func testLearningIsScopedToReading() {
        learning.recordSelection(reading: "니혼", candidate: "日本")
        XCTAssertEqual(
            learning.rank(reading: "닛폰", candidates: ["にっぽん", "日本"]),
            ["にっぽん", "日本"]
        )
    }
}
