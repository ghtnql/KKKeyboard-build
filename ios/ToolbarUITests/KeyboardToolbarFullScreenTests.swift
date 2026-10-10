import XCTest

/// Gestures target the production keyboard and Compose controls. The host's
/// diagnostics expose focus/preferences only; they cannot drive navigation.
final class KeyboardToolbarFullScreenTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        assertReadyKeyboard(routes: 0)
    }

    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
        app.terminate()
        app = nil
    }

    func testSettingsIsFullScreenAndScrollsToLandscapeNumberRow() {
        tapToolbar("settings")
        assertStateContains("focus=inactive")
        assertStateContains("routes=1")
        let scroll = element("keyboard-settings-scroll")
        waitHittable(scroll)
        XCTAssertGreaterThan(scroll.frame.height, app.frame.height * 0.65,
                             "Settings must occupy the full app screen, not the IME panel")
        XCTAssertFalse(toolbar("settings").isHittable)
        let close = closeButton()
        waitHittable(close)
        let closeFrame = close.frame
        capture("settings-fullscreen-top")

        let landscapeNumberRow = element("keyboard.settings.number-row.landscape")
        scrollTo(landscapeNumberRow, in: scroll)
        assertStateContains("landscapeNumberRow=true")
        landscapeNumberRow.tap()
        assertStateContains("landscapeNumberRow=false")
        waitHittable(close)
        XCTAssertEqual(close.frame.minY, closeFrame.minY, accuracy: 2,
                       "Close must stay pinned while the settings body scrolls")
        capture("settings-fullscreen-bottom-landscape-number-row")
        close.tap()
        assertReadyKeyboard(routes: 1)
        assertStateContains("landscapeNumberRow=false")
        capture("settings-close-restores-host-keyboard")
    }

    func testSettingsBackClosesAndLandscapeViewportStillScrolls() {
        XCUIDevice.shared.orientation = .landscapeLeft
        tapToolbar("settings")
        let scroll = element("keyboard-settings-scroll")
        waitHittable(scroll)
        let numberRow = element("keyboard.settings.number-row.landscape")
        scrollTo(numberRow, in: scroll)
        waitHittable(closeButton())
        capture("settings-device-landscape-bottom")
        let back = element("keyboard.settings.back")
        waitHittable(back)
        back.tap()
        assertReadyKeyboard(routes: 1)
    }

    func testThemesOpensFullScreenAppliesFreeThemeAndReturns() {
        tapToolbar("settings")
        let settingsScroll = element("keyboard-settings-scroll")
        waitHittable(settingsScroll)
        // Tap the accessible parent button, not its nested Compose text node.
        // Tapping a staticText coordinate could just dismiss the IME/scroll gesture.
        let themesLink = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "App + keyboard themes")).firstMatch
        scrollTo(themesLink, in: settingsScroll, requireFullyVisible: true)
        stabilize(themesLink, in: settingsScroll)
        capture("themes-link-beforetap")
        themesLink.tap()
        capture("themes-link-aftertap")
        // Navigation signal: the unique visible Themes subtitle proves entry.
        // Do not use apply.basic_light here; it may sit offscreen after entry.
        let themesSubtitle = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Applies to the app and keyboard · keyboard preview below")).firstMatch
        XCTAssertTrue(themesSubtitle.waitForExistence(timeout: 12), "Theme page did not open")
        assertStateContains("focus=inactive")
        assertStateContains("routes=1")
        let scroll = element("keyboard-settings-scroll")
        waitHittable(scroll)
        XCTAssertGreaterThan(scroll.frame.height, app.frame.height * 0.65)
        waitHittable(closeButton())
        XCTAssertFalse(toolbar("themes").isHittable,
                       "The toolbar should yield to a real full-screen theme page")
        capture("themes-fullscreen-top")

        let applyLight = element("keyboard.theme.apply.basic_light")
        scrollTo(applyLight, in: scroll, requireFullyVisible: true)
        stabilize(applyLight, in: scroll)
        capture("themes-apply-beforetap")
        applyLight.tap()
        capture("themes-apply-aftertap")
        assertStateContains("theme=basic_light")
        assertStateContains("focus=inactive")
        waitHittable(closeButton())
        capture("themes-free-light-applied")
        closeButton().tap()
        assertReadyKeyboard(routes: 1)
        assertStateContains("theme=basic_light")
        capture("themes-close-restores-host-keyboard")
    }

    func testLayoutPopupSelectsLayoutWithoutLeavingHostKeyboard() {
        let originalHeight = keyboardHeight()
        XCTAssertGreaterThan(originalHeight, 0)
        tapToolbar("layout")
        let qwerty = app.buttons["keyboard.layout.qwerty"]
        waitHittable(qwerty)
        assertStateContains("focus=active")
        assertStateContains("routes=0")
        XCTAssertEqual(keyboardHeight(), originalHeight, "The layout popup must preserve the host keyboard height")
        capture("layout-native-popup")
        qwerty.tap()
        assertReadyKeyboard(routes: 0)
        assertStateContains("layout=qwerty")
        XCTAssertFalse(qwerty.exists)
        capture("layout-qwerty-selected")

        tapToolbar("layout")
        let cancel = app.buttons["keyboard.layout.close"]
        waitHittable(cancel)
        cancel.tap()
        assertReadyKeyboard(routes: 0)
        assertStateContains("layout=qwerty")
        assertStateContains("focusLosses=0")
    }

    private func toolbar(_ name: String) -> XCUIElement {
        app.buttons["keyboard.toolbar.\(name)"].firstMatch
    }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    private func closeButton() -> XCUIElement {
        element("keyboard.settings.close")
    }

    private func tapToolbar(_ name: String) {
        let button = toolbar(name)
        XCTAssertTrue(button.waitForExistence(timeout: 20))
        let scroll = element("keyboard.toolbar.scroll")
        for _ in 0..<4 {
            if button.isHittable { break }
            if name == "themes" { scroll.swipeRight() } else { scroll.swipeLeft() }
        }
        waitHittable(button)
        button.tap()
    }

    private func assertReadyKeyboard(routes: Int, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(toolbar("settings").waitForExistence(timeout: 20), file: file, line: line)
        XCTAssertFalse(toolbar("themes").exists, file: file, line: line)
        assertStateContains("focus=active", file: file, line: line)
        assertStateContains("keyboard=visible", file: file, line: line)
        assertStateContains("routes=\(routes)", file: file, line: line)
    }

    private func assertStateContains(_ fragment: String, file: StaticString = #filePath, line: UInt = #line) {
        // Re-query the host state label on every poll: a single captured
        // XCUIElement snapshot can go stale and time out even while the fresh
        // label already matches. Bounded to 30s; never accepts a missing or
        // non-matching state silently.
        let deadline = Date().addingTimeInterval(30)
        var lastLabel = "<missing>"
        var matched = false
        while Date() < deadline {
            let state = app.staticTexts["toolbar.host.state"].firstMatch
            if state.exists {
                lastLabel = state.label
                if lastLabel.contains(fragment) {
                    matched = true
                    break
                }
            } else {
                lastLabel = "<missing>"
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        }
        XCTAssertTrue(matched, "Host state should contain '\(fragment)': \(lastLabel)", file: file, line: line)
    }

    private func keyboardHeight() -> Int {
        let label = app.staticTexts["toolbar.host.state"].firstMatch.label
        let pair = label.split(separator: " ").first { $0.hasPrefix("keyboardHeight=") }
        guard let pair, let height = Int(pair.dropFirst("keyboardHeight=".count)) else {
            XCTFail("The real input view must report its height: \(label)")
            return 0
        }
        return height
    }

    private func waitHittable(_ target: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        let predicate = NSPredicate(format: "exists == true AND hittable == true")
        let result = XCTWaiter.wait(for: [XCTNSPredicateExpectation(predicate: predicate, object: target)], timeout: 20)
        XCTAssertEqual(result, .completed, "Control must be visible and tappable: \(target)", file: file, line: line)
    }

    private func scrollTo(_ target: XCUIElement, in scroll: XCUIElement, requireFullyVisible: Bool = false, file: StaticString = #filePath, line: UInt = #line) {
        // Enough for all real settings sections; never swipe the host text field
        // or a system keyboard in place of the Compose scroll area.
        // Generic callers keep the historic hittable-only behavior. Themes
        // callers pass requireFullyVisible:true so a target that is merely
        // hittable while partly below the fold is nudged fully into view with
        // small frame-vs-viewport gestures instead of full-page overshoot.
        if !requireFullyVisible {
            for _ in 0..<24 {
                if target.exists && target.isHittable { break }
                scroll.swipeUp()
            }
            XCTAssertTrue(target.exists && target.isHittable, "The lower settings control must be reachable by swiping", file: file, line: line)
            return
        }
        for _ in 0..<24 {
            if target.exists {
                let frame = target.frame
                let viewport = scroll.frame
                let fullyWithin = frame.width > 0 && frame.height > 0
                    && viewport.minX <= frame.minX && viewport.minY <= frame.minY
                    && viewport.maxX >= frame.maxX && viewport.maxY >= frame.maxY
                if fullyWithin && target.isHittable { break }
                guard viewport.height > 0 else { scroll.swipeUp(); continue }
                if frame.maxY > viewport.maxY + 1 {
                    let overshoot = min(frame.maxY - viewport.maxY + 8, viewport.height * 0.4)
                    let delta = max(0.15, min(0.4, overshoot / viewport.height))
                    let start = scroll.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5 + delta / 2))
                    let end = scroll.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5 - delta / 2))
                    start.press(forDuration: 0.1, thenDragTo: end)
                } else if frame.minY < viewport.minY - 1 {
                    let overshoot = min(viewport.minY - frame.minY + 8, viewport.height * 0.4)
                    let delta = max(0.15, min(0.4, overshoot / viewport.height))
                    let start = scroll.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5 - delta / 2))
                    let end = scroll.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5 + delta / 2))
                    start.press(forDuration: 0.1, thenDragTo: end)
                } else {
                    // Vertically inside the viewport but clipped or not yet
                    // hittable: nudge a small step upward without overshooting.
                    let start = scroll.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.6))
                    let end = scroll.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.45))
                    start.press(forDuration: 0.1, thenDragTo: end)
                }
            } else {
                scroll.swipeUp()
            }
        }
        let frame = target.frame
        let viewport = scroll.frame
        XCTAssertTrue(target.exists && target.isHittable, "The lower settings control must be reachable by swiping", file: file, line: line)
        XCTAssertTrue(frame.width > 0 && frame.height > 0
                      && viewport.minX <= frame.minX && viewport.minY <= frame.minY
                      && viewport.maxX >= frame.maxX && viewport.maxY >= frame.maxY,
                      "Target frame must sit fully within the visible scroll viewport: \(target)", file: file, line: line)
    }

    /// Bounded settle after swipe-driven scrolling. Polls the target frame
    /// until it is geometrically stable inside the scroll viewport with
    /// positive dimensions and hittable, then returns so the caller taps
    /// the real button element. Bounded to 20 polls of 0.1s.
    private func stabilize(_ target: XCUIElement, in scroll: XCUIElement, file: StaticString = #filePath, line: UInt = #line) {
        var last = target.frame
        var stable = 0
        for _ in 0..<20 {
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
            guard target.exists else { stable = 0; continue }
            let frame = target.frame
            let settled = abs(frame.minX - last.minX) < 0.5
                && abs(frame.minY - last.minY) < 0.5
                && abs(frame.width - last.width) < 0.5
                && abs(frame.height - last.height) < 0.5
            last = frame
            stable = settled ? stable + 1 : 0
            let viewport = scroll.frame
            let fullyWithin = frame.width > 0 && frame.height > 0
                && viewport.minX <= frame.minX && viewport.minY <= frame.minY
                && viewport.maxX >= frame.maxX && viewport.maxY >= frame.maxY
            if stable >= 2 && fullyWithin && target.isHittable { break }
        }
        let frame = target.frame
        let viewport = scroll.frame
        XCTAssertGreaterThanOrEqual(stable, 2,
                      "Target frame never stabilized before tap: \(target)", file: file, line: line)
        XCTAssertTrue(frame.width > 0 && frame.height > 0,
                      "Target must have positive dimensions before tap: \(target)", file: file, line: line)
        XCTAssertTrue(viewport.minX <= frame.minX && viewport.minY <= frame.minY
                      && viewport.maxX >= frame.maxX && viewport.maxY >= frame.maxY,
                      "Target frame must sit fully within the visible scroll viewport: \(target)", file: file, line: line)
        XCTAssertTrue(target.isHittable,
                      "Target must be hittable before tap: \(target)", file: file, line: line)
    }

    private func capture(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
