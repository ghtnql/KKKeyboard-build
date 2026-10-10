import XCTest

final class AppUILanguageTests: XCTestCase {
    func testMapsSupportedLocaleIdentifiers() {
        XCTAssertEqual(AppUILanguage.from(languageIdentifier: "ko-KR"), .korean)
        XCTAssertEqual(AppUILanguage.from(languageIdentifier: "ko_KP"), .korean)
        XCTAssertEqual(AppUILanguage.from(languageIdentifier: "ja-JP"), .japanese)
        XCTAssertEqual(AppUILanguage.from(languageIdentifier: "en-GB"), .english)
    }

    func testFallsBackToEnglishForUnsupportedOrMissingLocales() {
        XCTAssertEqual(AppUILanguage.from(languageIdentifier: "fr-FR"), .english)
        XCTAssertEqual(AppUILanguage.preferred(from: []), .english)
    }

    func testPersistedSelectionOverridesPreferredLocale() {
        let suiteName = "AppUILanguageTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }

        let preferences = AppUILanguagePreferences(
            defaults: defaults,
            preferredLanguages: { ["ko-KR"] }
        )
        XCTAssertEqual(preferences.selectedLanguage(), .korean)

        preferences.select(.japanese)
        XCTAssertEqual(preferences.selectedLanguage(), .japanese)
    }
}
