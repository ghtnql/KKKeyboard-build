import XCTest
import SharedUI

final class KeyboardInputModeSettingsTests: XCTestCase {
    private var suiteNames: [String] = []

    private func makeDefaults() -> UserDefaults {
        let name = "KeyboardInputModeSettingsTests.\(UUID().uuidString)"
        suiteNames.append(name)
        return UserDefaults(suiteName: name)!
    }

    override func tearDown() {
        for name in suiteNames {
            UserDefaults.standard.removePersistentDomain(forName: name)
            UserDefaults(suiteName: name)?.removePersistentDomain(forName: name)
        }
        suiteNames.removeAll()
        super.tearDown()
    }

    func testJapaneseHyphenLocaleDefaultsToJapanese() {
        let settings = KeyboardInputModeSettings(
            defaults: makeDefaults(),
            preferredLanguages: { ["ja-JP"] }
        )
        XCTAssertTrue(settings.japaneseMode())
    }

    func testJapaneseUnderscoreLocaleDefaultsToJapanese() {
        let settings = KeyboardInputModeSettings(
            defaults: makeDefaults(),
            preferredLanguages: { ["ja_JP"] }
        )
        XCTAssertTrue(settings.japaneseMode())
    }

    func testKoreanLocaleDefaultsToKorean() {
        let settings = KeyboardInputModeSettings(
            defaults: makeDefaults(),
            preferredLanguages: { ["ko-KR"] }
        )
        XCTAssertFalse(settings.japaneseMode())
    }

    func testEnglishLocaleDefaultsToKorean() {
        let settings = KeyboardInputModeSettings(
            defaults: makeDefaults(),
            preferredLanguages: { ["en-US"] }
        )
        XCTAssertFalse(settings.japaneseMode())
    }

    func testExplicitTrueOverridesNonJapaneseLocale() {
        let defaults = makeDefaults()
        let settings = KeyboardInputModeSettings(
            defaults: defaults,
            preferredLanguages: { ["en-US"] }
        )
        settings.selectJapanese(true)
        XCTAssertTrue(settings.japaneseMode())
        let recreated = KeyboardInputModeSettings(
            defaults: defaults,
            preferredLanguages: { ["en-US"] }
        )
        XCTAssertTrue(recreated.japaneseMode())
    }

    func testExplicitFalseOverridesJapaneseLocale() {
        let defaults = makeDefaults()
        let settings = KeyboardInputModeSettings(
            defaults: defaults,
            preferredLanguages: { ["ja-JP"] }
        )
        settings.selectJapanese(false)
        XCTAssertFalse(settings.japaneseMode())
        let recreated = KeyboardInputModeSettings(
            defaults: defaults,
            preferredLanguages: { ["ja-JP"] }
        )
        XCTAssertFalse(recreated.japaneseMode())
    }

    func testEnglishPersistsThroughRecreation() {
        let defaults = makeDefaults()
        let settings = KeyboardInputModeSettings(
            defaults: defaults,
            preferredLanguages: { ["ko-KR"] }
        )
        settings.selectLanguage(.english)
        XCTAssertEqual(settings.language(), .english)
        XCTAssertFalse(settings.japaneseMode())
        let recreated = KeyboardInputModeSettings(
            defaults: defaults,
            preferredLanguages: { ["ko-KR"] }
        )
        XCTAssertEqual(recreated.language(), .english)
        XCTAssertFalse(recreated.japaneseMode())
    }

    func testLegacyJapaneseBoolMigrationFallback() {
        let trueDefaults = makeDefaults()
        trueDefaults.set(true, forKey: "keyboard.inputMode.japanese")
        XCTAssertEqual(
            KeyboardInputModeSettings(
                defaults: trueDefaults,
                preferredLanguages: { ["ko-KR"] }
            ).language(),
            .japanese
        )
        let falseDefaults = makeDefaults()
        falseDefaults.set(false, forKey: "keyboard.inputMode.japanese")
        XCTAssertEqual(
            KeyboardInputModeSettings(
                defaults: falseDefaults,
                preferredLanguages: { ["ja-JP"] }
            ).language(),
            .korean
        )
    }

    func testInvalidLanguageRawFallsBackToLegacyPreference() {
        let defaults = makeDefaults()
        defaults.set("xx", forKey: "keyboard.inputMode.language")
        defaults.set(true, forKey: "keyboard.inputMode.japanese")
        XCTAssertEqual(
            KeyboardInputModeSettings(
                defaults: defaults,
                preferredLanguages: { ["ko-KR"] }
            ).language(),
            .japanese
        )
        let fallbackDefaults = makeDefaults()
        fallbackDefaults.set("xx", forKey: "keyboard.inputMode.language")
        XCTAssertEqual(
            KeyboardInputModeSettings(
                defaults: fallbackDefaults,
                preferredLanguages: { ["ja-JP"] }
            ).language(),
            .japanese
        )
        XCTAssertEqual(
            KeyboardInputModeSettings(
                defaults: fallbackDefaults,
                preferredLanguages: { ["ko-KR"] }
            ).language(),
            .korean
        )
    }

    func testThreeCycleOrderAndSelectJapaneseResetsEnglish() {
        XCTAssertEqual(KeyboardInputLanguage.korean.next, .japanese)
        XCTAssertEqual(KeyboardInputLanguage.japanese.next, .english)
        XCTAssertEqual(KeyboardInputLanguage.english.next, .korean)
        let defaults = makeDefaults()
        let settings = KeyboardInputModeSettings(
            defaults: defaults,
            preferredLanguages: { ["ko-KR"] }
        )
        settings.selectLanguage(.english)
        XCTAssertEqual(settings.language(), .english)
        settings.selectJapanese(true)
        XCTAssertEqual(settings.language(), .japanese)
        XCTAssertTrue(settings.japaneseMode())
        settings.selectJapanese(false)
        XCTAssertEqual(settings.language(), .korean)
        XCTAssertEqual(defaults.string(forKey: "keyboard.inputMode.language"), "korean")
    }

    func testLatinQwertyMapping() {
        let all = LatinQwertyLayout.characterRows.flatMap { $0 } + LatinQwertyLayout.bottomRow
        XCTAssertEqual(all.count, 26)
        XCTAssertEqual(Set(all).count, 26)
        XCTAssertEqual(LatinQwertyLayout.characterRows[0], ["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"])
        XCTAssertEqual(LatinQwertyLayout.characterRows[1], ["a", "s", "d", "f", "g", "h", "j", "k", "l"])
        XCTAssertEqual(LatinQwertyLayout.bottomRow, ["z", "x", "c", "v", "b", "n", "m"])
        XCTAssertEqual(LatinQwertyLayout.label(for: "q", shifted: false), "q")
        XCTAssertEqual(LatinQwertyLayout.label(for: "q", shifted: true), "Q")
        XCTAssertEqual(LatinQwertyLayout.label(for: "m", shifted: true), "M")
        XCTAssertTrue(LatinQwertyLayout.hasShiftVariant("a"))
        XCTAssertTrue(LatinQwertyLayout.hasShiftVariant("z"))
        XCTAssertFalse(LatinQwertyLayout.hasShiftVariant("Q"))
    }
}
