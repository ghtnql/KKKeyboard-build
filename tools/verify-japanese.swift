import XCTest

// Compile with the two Shared engine files and JapanesePredictionTests.swift.
// Keep ja_common.json and ja_predictions.json beside the executable on Linux.
@main
struct VerifyJapanese {
    static func main() {
        XCTMain([testCase([
            ("sharedPredictionFixtures", JapanesePredictionTests.testSharedPredictionFixtures),
            ("allAliases", JapanesePredictionTests.testEveryBundledAliasFindsItsVocabularyEntry),
            ("stablePriority", JapanesePredictionTests.testPriorityIsIndependentOfFileOrder)
        ])])
    }
}
