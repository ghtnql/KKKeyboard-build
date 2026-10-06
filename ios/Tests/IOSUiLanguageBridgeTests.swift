import XCTest

final class IOSUiLanguageBridgeTests: XCTestCase {
    func testLatestExplicitChoiceWinsAcrossAppAndExtension() {
        let localSuite = "IOSUiLanguageBridgeTests.local.\(UUID().uuidString)"
        let sharedSuite = "IOSUiLanguageBridgeTests.shared.\(UUID().uuidString)"
        let local = UserDefaults(suiteName: localSuite)!
        let shared = UserDefaults(suiteName: sharedSuite)!
        defer {
            local.removePersistentDomain(forName: localSuite)
            shared.removePersistentDomain(forName: sharedSuite)
        }
        XCTAssertNil(IOSKeyboardSettingsPlatform.latestUiLanguage(local: local, shared: shared))
        local.set("ko", forKey: "app.uiLanguage")
        local.set(10.0, forKey: "app.uiLanguage.modifiedAt")
        shared.set("ja", forKey: "app.uiLanguage")
        shared.set(20.0, forKey: "app.uiLanguage.modifiedAt")
        XCTAssertEqual(IOSKeyboardSettingsPlatform.latestUiLanguage(local: local, shared: shared), "ja")
        local.set("en", forKey: "app.uiLanguage")
        local.set(30.0, forKey: "app.uiLanguage.modifiedAt")
        XCTAssertEqual(IOSKeyboardSettingsPlatform.latestUiLanguage(local: local, shared: shared), "en")
        shared.set(30.0, forKey: "app.uiLanguage.modifiedAt")
        XCTAssertEqual(IOSKeyboardSettingsPlatform.latestUiLanguage(local: local, shared: shared), "ja")
        XCTAssertEqual(IOSKeyboardSettingsPlatform.latestUiLanguage(local: local, shared: nil), "en")
    }
}
