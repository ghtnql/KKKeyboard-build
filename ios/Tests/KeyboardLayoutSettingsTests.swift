import XCTest

final class KeyboardLayoutSettingsTests: XCTestCase {
    func testPlusAndCanonicalLongPressSlotsAreSharedWithoutExtensionWrites() {
        let local = isolatedDefaults()
        let shared = isolatedDefaults()
        let app = KeyboardLayoutSettings(defaults: shared)
        let keyboard = KeyboardLayoutSettings(defaults: local, readOnlySharedDefaults: shared)
        XCTAssertEqual(app.inputLayout, "cheonjiin")
        app.setInputLayout("cheonjiin_plus")
        XCTAssertEqual(keyboard.inputLayout, "cheonjiin_plus")
        XCTAssertNil(keyboard.longPressOverride(layout: "cheonjiin_plus", keyId: "ㅋ"))
        // Stale local state with a newer revision cannot shadow canonical symbols.
        local.set(["OLD", "", ""], forKey: "longpress_cheonjiin_plus_ㅋ")
        local.set(Date().timeIntervalSince1970 + 500, forKey: "longpress_cheonjiin_plus_ㅋ.modifiedAt")
        XCTAssertNil(keyboard.longPressOverride(layout: "cheonjiin_plus", keyId: "ㅋ"))
        app.setLongPressSlots(layout: "cheonjiin_plus", keyId: "ㅋ", slots: ["!", "", "?"])
        XCTAssertEqual(keyboard.longPressOverride(layout: "cheonjiin_plus", keyId: "ㅋ"), ["!", "", "?"])
        keyboard.setLongPressSlots(layout: "cheonjiin_plus", keyId: "ㅋ", slots: ["@", "", ""])
        keyboard.resetLongPressSlots(layout: "cheonjiin_plus", keyId: "ㅋ")
        XCTAssertEqual(app.longPressOverride(layout: "cheonjiin_plus", keyId: "ㅋ"), ["!", "", "?"])
        XCTAssertEqual(keyboard.longPressOverride(layout: "cheonjiin_plus", keyId: "ㅋ"), ["!", "", "?"])
        XCTAssertEqual(local.stringArray(forKey: "longpress_cheonjiin_plus_ㅋ"), ["OLD", "", ""])
        app.setLongPressSlots(layout: "cheonjiin_plus", keyId: "ㅋ", slots: ["", "@"])
        XCTAssertEqual(keyboard.longPressOverride(layout: "cheonjiin_plus", keyId: "ㅋ"), ["", "@", ""])
        XCTAssertNil(keyboard.longPressOverride(layout: "cheonjiin", keyId: "ㄱㅋ"))
        app.resetLongPressSlots(layout: "cheonjiin_plus", keyId: "ㅋ")
        XCTAssertNil(app.longPressOverride(layout: "cheonjiin_plus", keyId: "ㅋ"))
        XCTAssertNil(keyboard.longPressOverride(layout: "cheonjiin_plus", keyId: "ㅋ"))
        // Existing layout settings retain their independently revisioned local writes.
        keyboard.setInputLayout("qwerty")
        XCTAssertEqual(keyboard.inputLayout, "qwerty")
    }

    func testDefaultsAreStableForBothOrientations() {
        let defaults = isolatedDefaults()
        let settings = KeyboardLayoutSettings(defaults: defaults)

        XCTAssertEqual(
            settings.profile(for: .portrait),
            KeyboardLayoutProfile(height: 260, numberRowEnabled: true, cursorRowEnabled: false)
        )
        XCTAssertEqual(
            settings.profile(for: .landscape),
            KeyboardLayoutProfile(height: 260, numberRowEnabled: true, cursorRowEnabled: false)
        )
    }

    func testPortraitAndLandscapeProfilesAreIndependent() {
        let defaults = isolatedDefaults()
        let settings = KeyboardLayoutSettings(defaults: defaults)

        settings.setHeight(300, for: .portrait)
        settings.setNumberRowEnabled(true, for: .portrait)
        settings.setCursorRowEnabled(true, for: .portrait)
        settings.setHeight(220, for: .landscape)
        settings.setNumberRowEnabled(false, for: .landscape)
        settings.setCursorRowEnabled(false, for: .landscape)

        XCTAssertEqual(
            settings.profile(for: .portrait),
            KeyboardLayoutProfile(height: 300, numberRowEnabled: true, cursorRowEnabled: true)
        )
        XCTAssertEqual(
            settings.profile(for: .landscape),
            KeyboardLayoutProfile(height: 220, numberRowEnabled: false, cursorRowEnabled: false)
        )
    }

    func testRenderedHeightPreservesMinimumUsableRows() {
        XCTAssertEqual(
            KeyboardLayoutSettings.renderedHeight(
                requestedHeight: 220,
                numberRowEnabled: false,
                cursorRowEnabled: false
            ),
            220
        )
        XCTAssertEqual(
            KeyboardLayoutSettings.renderedHeight(
                requestedHeight: 220,
                numberRowEnabled: true,
                cursorRowEnabled: true
            ),
            276
        )
        XCTAssertEqual(
            KeyboardLayoutSettings.renderedHeight(
                requestedHeight: 300,
                numberRowEnabled: false,
                cursorRowEnabled: false
            ),
            300
        )
    }

    func testUnsupportedHeightIsIgnoredAndHeightCycleWraps() {
        let defaults = isolatedDefaults()
        let settings = KeyboardLayoutSettings(defaults: defaults)

        settings.setHeight(999, for: .portrait)
        XCTAssertEqual(settings.profile(for: .portrait).height, 260)
        XCTAssertEqual(settings.nextHeight(after: 220), 260)
        XCTAssertEqual(settings.nextHeight(after: 260), 300)
        XCTAssertEqual(settings.nextHeight(after: 300), 220)
        XCTAssertEqual(settings.nextHeight(after: 999), 260)
    }

    func testHeightCycleUsesStoredRequestWhenRenderedHeightIsExpanded() {
        let defaults = isolatedDefaults()
        let settings = KeyboardLayoutSettings(defaults: defaults)

        settings.setHeight(260, for: .portrait)
        settings.setNumberRowEnabled(true, for: .portrait)
        settings.setCursorRowEnabled(true, for: .portrait)

        XCTAssertEqual(settings.profile(for: .portrait).height, 276)
        XCTAssertEqual(settings.requestedHeight(for: .portrait), 260)
        XCTAssertEqual(settings.nextHeight(for: .portrait), 300)
    }

    func testSettingsRowOnlyExpandsHeightWhileItIsVisible() {
        XCTAssertEqual(
            KeyboardLayoutSettings.renderedHeight(
                requestedHeight: 220,
                numberRowEnabled: false,
                cursorRowEnabled: false,
                settingsRowVisible: true
            ),
            238
        )
    }

    func testMigratesLegacyLayoutWithoutRepeatingStaleValues() {
        let localName = "KeyboardLayoutLegacyTests.\(UUID().uuidString)"
        let sharedName = "KeyboardLayoutSharedTests.\(UUID().uuidString)"
        let local = UserDefaults(suiteName: localName)!
        let shared = UserDefaults(suiteName: sharedName)!
        defer {
            local.removePersistentDomain(forName: localName)
            shared.removePersistentDomain(forName: sharedName)
        }

        let legacy = KeyboardLayoutSettings(defaults: local)
        legacy.setHeight(300, for: .portrait)
        legacy.setNumberRowEnabled(true, for: .portrait)
        KeyboardLayoutSettings(defaults: shared).setHeight(220, for: .portrait)
        let migrated = KeyboardLayoutSettings(defaults: shared, legacyDefaults: local, migrateLegacy: true)
        XCTAssertEqual(migrated.profile(for: .portrait).height, 238)
        XCTAssertEqual(migrated.nextHeight(for: .portrait), 260)
        XCTAssertTrue(migrated.profile(for: .portrait).numberRowEnabled)

        migrated.setHeight(260, for: .portrait)
        XCTAssertEqual(
            KeyboardLayoutSettings(defaults: shared, legacyDefaults: local, migrateLegacy: true)
                .profile(for: .portrait).height,
            260
        )
        XCTAssertEqual(legacy.profile(for: .portrait).height, 300)
    }

    func testReadOnlySharedSettingsYieldToExtensionLocalOverride() {
        let localName = "KeyboardLayoutLegacyTests.\(UUID().uuidString)"
        let sharedName = "KeyboardLayoutSharedTests.\(UUID().uuidString)"
        let local = UserDefaults(suiteName: localName)!
        let shared = UserDefaults(suiteName: sharedName)!
        defer {
            local.removePersistentDomain(forName: localName)
            shared.removePersistentDomain(forName: sharedName)
        }

        KeyboardLayoutSettings(defaults: shared).setNumberRowEnabled(true, for: .landscape)
        let extensionSettings = KeyboardLayoutSettings(defaults: local, readOnlySharedDefaults: shared)
        XCTAssertTrue(extensionSettings.profile(for: .landscape).numberRowEnabled)
        extensionSettings.setNumberRowEnabled(false, for: .landscape)
        XCTAssertFalse(extensionSettings.profile(for: .landscape).numberRowEnabled)
        XCTAssertTrue(KeyboardLayoutSettings(defaults: shared).profile(for: .landscape).numberRowEnabled)
    }

    func testAllFourInputLayoutsAndSharedBehaviorBounds() {
        let settings = KeyboardLayoutSettings(defaults: isolatedDefaults())
        for layout in ["cheonjiin", "cheonjiin_plus", "qwerty", "hangul_flick"] {
            settings.setInputLayout(layout)
            XCTAssertEqual(settings.inputLayout, layout)
        }
        settings.setInputLayout("unavailable")
        XCTAssertEqual(settings.inputLayout, "hangul_flick")
        settings.setFlickDistance(100)
        XCTAssertEqual(settings.flickDistance, 32)
        settings.setCycleTimeout(1)
        XCTAssertEqual(settings.cycleTimeout, 400)
    }

    func testNewerAppLayoutSupersedesEarlierExtensionOverride() {
        let local = isolatedDefaults()
        let shared = isolatedDefaults()
        let extensionSettings = KeyboardLayoutSettings(defaults: local, readOnlySharedDefaults: shared)
        extensionSettings.setInputLayout("hangul_flick")
        local.set(100.0, forKey: "keyboard.inputLayout.modifiedAt")
        let appSettings = KeyboardLayoutSettings(defaults: shared)
        appSettings.setInputLayout("qwerty")
        shared.set(200.0, forKey: "keyboard.inputLayout.modifiedAt")
        XCTAssertEqual(extensionSettings.inputLayout, "qwerty")
        XCTAssertEqual(local.string(forKey: "keyboard.inputLayout"), "hangul_flick")
        XCTAssertEqual(shared.string(forKey: "keyboard.inputLayout"), "qwerty")
    }

    func testHapticPreferencePersistsAndDefaultsToEnabled() {
        let defaults = isolatedDefaults()
        let settings = KeyboardLayoutSettings(defaults: defaults)
        XCTAssertTrue(settings.hapticFeedbackEnabled)
        settings.setHapticFeedbackEnabled(false)
        XCTAssertFalse(KeyboardLayoutSettings(defaults: defaults).hapticFeedbackEnabled)
        settings.setHapticFeedbackEnabled(true)
        XCTAssertTrue(KeyboardLayoutSettings(defaults: defaults).hapticFeedbackEnabled)
    }

    func testHapticReadOnlyAppBridgeAndLocalOverrideFollowRevision() {
        let local = isolatedDefaults()
        let shared = isolatedDefaults()
        let app = KeyboardLayoutSettings(defaults: shared)
        app.setHapticFeedbackEnabled(false)
        shared.set(100.0, forKey: "keyboard.hapticFeedbackEnabled.modifiedAt")
        let keyboard = KeyboardLayoutSettings(defaults: local, readOnlySharedDefaults: shared)
        XCTAssertFalse(keyboard.hapticFeedbackEnabled)
        keyboard.setHapticFeedbackEnabled(true)
        local.set(200.0, forKey: "keyboard.hapticFeedbackEnabled.modifiedAt")
        XCTAssertTrue(keyboard.hapticFeedbackEnabled)
        XCTAssertFalse(app.hapticFeedbackEnabled, "The extension must not write app-group defaults")
        app.setHapticFeedbackEnabled(false)
        shared.set(300.0, forKey: "keyboard.hapticFeedbackEnabled.modifiedAt")
        XCTAssertFalse(keyboard.hapticFeedbackEnabled)
    }

    private func isolatedDefaults() -> UserDefaults {
        let suite = "KeyboardLayoutSettingsTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return defaults
    }
}
