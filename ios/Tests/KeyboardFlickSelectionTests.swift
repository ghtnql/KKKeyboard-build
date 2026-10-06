import UIKit
import XCTest
import SharedUI

final class KeyboardFlickSelectionTests: XCTestCase {
    private var savedPreferences: [(UserDefaults, String, Any?)] = []

    override func setUp() {
        super.setUp()
        // Test the actual adapter stores; restore app-group and extension-local values.
        let stores = [UserDefaults.standard, KeyboardSharedDefaults.availableDefaults()].compactMap { $0 }
        for store in stores {
            for key in ["keyboard.inputLayout", "keyboard.inputLayout.modifiedAt", "keyboard.flickDistance",
                        "keyboard.flickDistance.modifiedAt", "keyboard.inputMode.japanese", "keyboard.inputMode.language"] {
                savedPreferences.append((store, key, store.object(forKey: key)))
                store.removeObject(forKey: key)
            }
        }
        KeyboardInputModeSettings().selectJapanese(false)
        IOSKeyboardSettingsPlatform().setInputLayout(layout: .cheonjiin)
    }

    override func tearDown() {
        for (store, key, value) in savedPreferences {
            if let value { store.set(value, forKey: key) }
            else { store.removeObject(forKey: key) }
        }
        savedPreferences.removeAll()
        super.tearDown()
    }

    private func controller(width: CGFloat = 393) -> KeyboardViewController {
        let controller = KeyboardViewController()
        controller.loadViewIfNeeded()
        controller.view.frame = CGRect(x: 0, y: 0, width: width, height: 360)
        controller.view.layoutIfNeeded()
        return controller
    }

    private func visibleFlickKeys(in view: UIView) -> [SharedFlickKeyControl] {
        guard !view.isHidden else { return [] }
        if let key = view as? SharedFlickKeyControl { return [key] }
        return view.subviews.flatMap { visibleFlickKeys(in: $0) }
    }

    private func visibleButtons(in view: UIView) -> [UIButton] {
        guard !view.isHidden else { return [] }
        var out: [UIButton] = []
        if let button = view as? UIButton { out.append(button) }
        for sub in view.subviews { out.append(contentsOf: visibleButtons(in: sub)) }
        return out
    }

    private func visibleStacks(in view: UIView) -> [UIStackView] {
        guard !view.isHidden else { return [] }
        var out: [UIStackView] = []
        if let stack = view as? UIStackView { out.append(stack) }
        for sub in view.subviews { out.append(contentsOf: visibleStacks(in: sub)) }
        return out
    }

    private func flickRows(in keyboard: KeyboardViewController) -> [UIStackView] {
        visibleStacks(in: keyboard.view).filter { stack in
            stack.arrangedSubviews.count == 5 &&
            stack.arrangedSubviews.contains(where: { $0 is SharedFlickKeyControl })
        }
    }

    private func useHangulFlick() {
        IOSKeyboardSettingsPlatform().setInputLayout(layout: .hangulFlick)
        KeyboardInputModeSettings().selectJapanese(false)
    }

    func testAdapterStoresFlickChoiceAndNotifiesSettingsChange() {
        let platform = IOSKeyboardSettingsPlatform()
        var changed = 0
        platform.onSettingsChanged = { changed += 1 }
        platform.setInputLayout(layout: .hangulFlick)
        XCTAssertEqual(changed, 1)
        XCTAssertEqual(KeyboardLayoutSettings().inputLayout, "hangul_flick")
        XCTAssertEqual(IOSKeyboardSettingsPlatform().readInputLayout(), .hangulFlick)
        XCTAssertFalse(KeyboardInputModeSettings().japaneseMode())
    }

    func testEmbeddedSettingsSelectionUpdatesFlickRowsAndSurvivesCloseAndReopen() {
        let keyboard = controller()
        XCTAssertTrue(visibleFlickKeys(in: keyboard.view).isEmpty)
        keyboard.perform(NSSelectorFromString("handleSettingsToggle"))
        XCTAssertEqual(keyboard.children.count, 1, "The common settings must open inside the extension")
        // This is the same adapter instance passed to the common settings selector.
        guard let platform = Mirror(reflecting: keyboard).children
            .first(where: { $0.label == "settingsPlatform" })?.value as? IOSKeyboardSettingsPlatform else {
            return XCTFail("Missing settings adapter")
        }
        platform.setInputLayout(layout: .hangulFlick)
        platform.setFlickDistance(distance: 28)
        XCTAssertEqual(platform.readInputLayout(), .hangulFlick)
        keyboard.closeSharedSettings()
        keyboard.view.layoutIfNeeded()
        XCTAssertTrue(keyboard.children.isEmpty)
        XCTAssertEqual(visibleFlickKeys(in: keyboard.view).count, 13)
        XCTAssertTrue(visibleFlickKeys(in: keyboard.view).allSatisfy { $0.threshold == 28 })
        keyboard.viewWillAppear(false)
        XCTAssertEqual(visibleFlickKeys(in: keyboard.view).count, 13)
        XCTAssertEqual(visibleFlickKeys(in: controller().view).count, 13)
        XCTAssertFalse(KeyboardInputModeSettings().japaneseMode())

        let image = UIGraphicsImageRenderer(size: keyboard.view.bounds.size).image { context in
            keyboard.view.layer.render(in: context.cgContext)
        }
        let attachment = XCTAttachment(image: image)
        attachment.name = "iPhone selected Hangul Flick after settings close"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testModeTogglePersistsWithoutChangingFlickSelection() {
        IOSKeyboardSettingsPlatform().setInputLayout(layout: .hangulFlick)
        let keyboard = controller()
        keyboard.perform(NSSelectorFromString("handleModeToggle"))
        XCTAssertTrue(KeyboardInputModeSettings().japaneseMode())
        XCTAssertEqual(KeyboardLayoutSettings().inputLayout, "hangul_flick")
        XCTAssertEqual(visibleFlickKeys(in: keyboard.view).count, 13)
        let recreated = controller()
        XCTAssertEqual(visibleFlickKeys(in: recreated.view).count, 13)
        func buttons(in view: UIView) -> [UIButton] {
            if let button = view as? UIButton { return [button] }
            return view.subviews.flatMap { buttons(in: $0) }
        }
        XCTAssertTrue(buttons(in: recreated.view).contains { $0.currentTitle == "ABC" })
        recreated.perform(NSSelectorFromString("handleModeToggle"))
        XCTAssertFalse(KeyboardInputModeSettings().japaneseMode())
        XCTAssertEqual(KeyboardInputModeSettings().language(), .english)
        XCTAssertTrue(visibleFlickKeys(in: recreated.view).isEmpty)
        XCTAssertTrue(visibleButtons(in: recreated.view).contains { $0.currentTitle == "q" })
        recreated.perform(NSSelectorFromString("handleModeToggle"))
        XCTAssertEqual(KeyboardInputModeSettings().language(), .korean)
        XCTAssertEqual(visibleFlickKeys(in: recreated.view).count, 13)
        XCTAssertEqual(IOSKeyboardSettingsPlatform().readInputLayout(), .hangulFlick)
    }

    func testFlickGridHasFourFiveCellRowsWithSideActionsInOrder() {
        useHangulFlick()
        let keyboard = controller()
        keyboard.view.layoutIfNeeded()
        let rows = flickRows(in: keyboard)
        guard rows.count == 4 else { return XCTFail("Expected four 5-cell flick rows") }
        let expectedCenters = [["ㅏ", "ㅓ", "ㅗ"], ["ㄱ", "ㄴ", "ㅜ"], ["ㄷ", "ㅂ", "ㅡ"], ["ㅅ", "ㅈ", "ㅇ"]]
        let expectedLeft = ["←", "→", "?123", "한/日"]
        for (index, row) in rows.enumerated() {
            XCTAssertEqual(row.arrangedSubviews.count, 5)
            XCTAssertEqual((row.arrangedSubviews.first as? UIButton)?.currentTitle, expectedLeft[index])
            let centers = row.arrangedSubviews[1...3].compactMap { $0 as? SharedFlickKeyControl }
            XCTAssertEqual(centers.count, 3)
            XCTAssertEqual(centers.map { $0.accessibilityLabel ?? "" }, expectedCenters[index])
        }
        // Right column: backspace, return, punctuation, space.
        XCTAssertTrue(rows[0].arrangedSubviews.last is UIButton)
        XCTAssertEqual((rows[1].arrangedSubviews.last as? UIButton)?.currentTitle, "↵")
        XCTAssertEqual((rows[2].arrangedSubviews.last as? SharedFlickKeyControl)?.accessibilityLabel, ".")
        let space = rows[3].arrangedSubviews.last as? UIButton
        XCTAssertEqual(space?.currentTitle, "␣")
    }

    func testFlickWidthRatioCenterToSideAtMultipleWidths() {
        useHangulFlick()
        let expected = 1.9 / 1.1
        for width in [320, 393, 800] as [CGFloat] {
            let keyboard = controller(width: CGFloat(width))
            keyboard.view.layoutIfNeeded()
            let rows = flickRows(in: keyboard)
            XCTAssertEqual(rows.count, 4)
            for row in rows {
                let side = row.arrangedSubviews.first?.frame.width ?? 0
                let center = row.arrangedSubviews[1].frame.width
                XCTAssertGreaterThan(side, 0)
                XCTAssertGreaterThan(center, 0)
                XCTAssertEqual(center / side, expected, accuracy: 0.15,
                               "center/side ratio at width \(width)")
            }
        }
    }

    func testFlickCellsHavePositiveFramesAndNoOldQwertyControlRow() {
        useHangulFlick()
        let keyboard = controller()
        keyboard.view.layoutIfNeeded()
        for row in flickRows(in: keyboard) {
            row.layoutIfNeeded()
            for cell in row.arrangedSubviews {
                XCTAssertGreaterThan(cell.frame.width, 0)
                XCTAssertGreaterThan(cell.frame.height, 0)
                XCTAssertTrue(row.bounds.contains(cell.frame), "Cell must stay inside its row")
            }
        }
        let titles = Set(visibleButtons(in: keyboard.view).compactMap { $0.currentTitle })
        XCTAssertFalse(titles.contains("space"), "Old qwerty space row must be hidden during flick")
        XCTAssertFalse(titles.contains("return"), "Old qwerty return row must be hidden during flick")
        // Top toolbar globe/dismiss remain reachable outside the flick rows.
        let flickCells = Set(flickRows(in: keyboard).flatMap { $0.arrangedSubviews }.compactMap { $0 as? UIButton })
        let chrome = visibleButtons(in: keyboard.view).filter { !flickCells.contains($0) }
        XCTAssertTrue(chrome.contains { $0.currentTitle == "🌐" })
        XCTAssertTrue(chrome.contains { $0.currentTitle == "⌄" })
    }

    func testSymbolPageShowsQwertyAndReturnsToFlickPreservingMode() {
        useHangulFlick()
        let keyboard = controller()
        XCTAssertEqual(visibleFlickKeys(in: keyboard.view).count, 13)
        keyboard.perform(NSSelectorFromString("handlePageToggle"))
        keyboard.view.layoutIfNeeded()
        XCTAssertTrue(visibleFlickKeys(in: keyboard.view).isEmpty, "Symbol page must hide flick rows")
        XCTAssertFalse(visibleStacks(in: keyboard.view).filter {
            $0.arrangedSubviews.count == 5 &&
            $0.arrangedSubviews.contains(where: { $0 is SharedFlickKeyControl })
        }.count == 4)
        keyboard.perform(NSSelectorFromString("handlePageToggle"))
        keyboard.view.layoutIfNeeded()
        XCTAssertEqual(visibleFlickKeys(in: keyboard.view).count, 13)
        XCTAssertEqual(IOSKeyboardSettingsPlatform().readInputLayout(), .hangulFlick)
        keyboard.perform(NSSelectorFromString("handleModeToggle"))
        let recreated = controller()
        recreated.view.layoutIfNeeded()
        XCTAssertEqual(visibleFlickKeys(in: recreated.view).count, 13)
        XCTAssertTrue(KeyboardInputModeSettings().japaneseMode())
    }

    func testEnglishQwertySelectionPersistsAndShiftLabelsRemainLatin() {
        useHangulFlick()
        KeyboardInputModeSettings().selectLanguage(.english)
        let keyboard = controller()
        XCTAssertTrue(visibleFlickKeys(in: keyboard.view).isEmpty)
        XCTAssertTrue(visibleButtons(in: keyboard.view).contains { $0.currentTitle == "q" })
        XCTAssertFalse(visibleButtons(in: keyboard.view).contains { $0.currentTitle == "ㄱ" })
        keyboard.perform(NSSelectorFromString("handleShift"))
        for title in ["Q", "A", "Z", "M"] {
            XCTAssertTrue(visibleButtons(in: keyboard.view).contains { $0.currentTitle == title })
        }
        XCTAssertTrue(visibleButtons(in: controller().view).contains { $0.currentTitle == "q" })
        XCTAssertEqual(KeyboardLayoutSettings().inputLayout, "hangul_flick")
    }

    func testLanguageKeyOffersThreeAccessibleSlideChoicesWithoutDuplicateTapAction() {
        useHangulFlick()
        let keyboard = controller()
        guard let button = visibleButtons(in: keyboard.view).first(where: { $0.currentTitle == "한/日" }) as? LongPressKeyButton else {
            return XCTFail("Missing language slide key")
        }
        XCTAssertEqual(button.choicesProvider?(), ["한국어", "日本語", "English"])
        XCTAssertEqual(button.accessibilityCustomActions?.map { $0.name }, ["한국어", "日本語", "English"])
        XCTAssertTrue(button.actions(forTarget: keyboard, forControlEvent: .touchUpInside)?.isEmpty ?? true)
        XCTAssertFalse(button.actions(forTarget: keyboard, forControlEvent: LongPressKeyButton.tapEvent)?.isEmpty ?? true)
        button.onChoice?("English")
        XCTAssertEqual(KeyboardInputModeSettings().language(), .english)
        XCTAssertTrue(visibleButtons(in: keyboard.view).contains { $0.currentTitle == "q" })
        XCTAssertEqual(KeyboardLayoutSettings().inputLayout, "hangul_flick")
        guard let englishMode = visibleButtons(in: keyboard.view).first(where: { $0.currentTitle == "한글" }) as? LongPressKeyButton else {
            return XCTFail("Missing language key in English")
        }
        englishMode.onChoice?("한국어")
        XCTAssertEqual(visibleFlickKeys(in: keyboard.view).count, 13)
    }

    func testAllThreeSymbolBanksRenderEveryAndroidSymbolAndReturnToSelectedLayout() {
        useHangulFlick()
        let keyboard = controller()
        keyboard.perform(NSSelectorFromString("handlePageToggle"))
        keyboard.view.layoutIfNeeded()
        for title in SymbolLayout.pages[0].flattened {
            XCTAssertTrue(visibleButtons(in: keyboard.view).contains { $0.currentTitle == title }, "Missing symbol \(title)")
        }
        XCTAssertTrue(visibleButtons(in: keyboard.view).contains { $0.currentTitle == "1/3" })
        XCTAssertTrue(visibleButtons(in: keyboard.view).contains { $0.currentTitle == "◀" })
        XCTAssertTrue(visibleButtons(in: keyboard.view).contains { $0.currentTitle == "▶" })
        keyboard.perform(NSSelectorFromString("handleShift"))
        for title in SymbolLayout.pages[1].flattened {
            XCTAssertTrue(visibleButtons(in: keyboard.view).contains { $0.currentTitle == title }, "Missing second-bank symbol \(title)")
        }
        XCTAssertTrue(visibleButtons(in: keyboard.view).contains { $0.currentTitle == "2/3" })
        keyboard.perform(NSSelectorFromString("handleShift"))
        for title in SymbolLayout.pages[2].flattened {
            XCTAssertTrue(visibleButtons(in: keyboard.view).contains { $0.currentTitle == title }, "Missing third-bank symbol \(title)")
        }
        XCTAssertTrue(visibleButtons(in: keyboard.view).contains { $0.currentTitle == "3/3" })
        keyboard.perform(NSSelectorFromString("handlePageToggle"))
        XCTAssertEqual(visibleFlickKeys(in: keyboard.view).count, 13)
        XCTAssertEqual(KeyboardLayoutSettings().inputLayout, "hangul_flick")
    }
}
