import XCTest

final class HangulLearningProgressTests: XCTestCase {
    func testHiraganaGuideAcknowledgementSurvivesRecreationWithoutMarkingLetters() {
        let defaults = UserDefaults.standard
        let guideKey = "hangul_learning.hiragana_guide_seen"
        let progressKey = "hangul_learning.completed_ids"
        let previous = defaults.object(forKey: guideKey)
        let previousProgress = defaults.object(forKey: progressKey)
        defer {
            if let previous { defaults.set(previous, forKey: guideKey) }
            else { defaults.removeObject(forKey: guideKey) }
            if let previousProgress { defaults.set(previousProgress, forKey: progressKey) }
            else { defaults.removeObject(forKey: progressKey) }
        }
        defaults.removeObject(forKey: guideKey)
        let first = IOSKeyboardSettingsPlatform()
        XCTAssertFalse(first.readHiraganaGuideSeen())
        first.saveHiraganaGuideSeen()
        XCTAssertTrue(IOSKeyboardSettingsPlatform().readHiraganaGuideSeen())
        XCTAssertEqual(defaults.object(forKey: progressKey) as? NSObject, previousProgress as? NSObject)
    }
    func testCompletedLettersSurvivePlatformRecreationWithoutChangingGameProgress() {
        let defaults = UserDefaults.standard
        let key = "hangul_learning.completed_ids"
        let previous = defaults.object(forKey: key)
        let existingKeys = defaults.dictionaryRepresentation().filter { $0.key != key }
        defer {
            if let previous { defaults.set(previous, forKey: key) }
            else { defaults.removeObject(forKey: key) }
        }
        defaults.removeObject(forKey: key)
        let first = IOSKeyboardSettingsPlatform()
        XCTAssertEqual(first.readHangulLearnedIds(), [])
        first.saveHangulLearnedIds(ids: ["c_ㄱ", "v_ㅏ", "c_ㄱ"])
        let reopened = IOSKeyboardSettingsPlatform()
        XCTAssertEqual(Set(reopened.readHangulLearnedIds()), Set(["c_ㄱ", "v_ㅏ"]))
        for (otherKey, value) in existingKeys {
            XCTAssertEqual(defaults.object(forKey: otherKey) as? NSObject, value as? NSObject)
        }
    }
}
