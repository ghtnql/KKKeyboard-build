import UIKit
import XCTest
import SharedUI

final class CheonjiinKeyboardRenderingTests: XCTestCase {
    private var savedPreferences: [(UserDefaults, String, Any?)] = []
    override func setUp() {
        super.setUp()
        for store in [UserDefaults.standard, KeyboardSharedDefaults.availableDefaults()].compactMap({ $0 }) {
            for key in ["keyboard.inputLayout", "keyboard.inputLayout.modifiedAt", "keyboard.inputMode.japanese", "keyboard.inputMode.language"] {
                savedPreferences.append((store, key, store.object(forKey: key)))
                store.removeObject(forKey: key)
            }
        }
        KeyboardInputModeSettings().selectJapanese(false)
        IOSKeyboardSettingsPlatform().setInputLayout(layout: .cheonjiin)
    }
    override func tearDown() {
        for (store, key, value) in savedPreferences {
            if let value { store.set(value, forKey: key) } else { store.removeObject(forKey: key) }
        }
        savedPreferences.removeAll()
        super.tearDown()
    }
    func testPlusRendersIndividualKeysAndAccessibleTenseChoice() {
        let settings = KeyboardLayoutSettings()
        let previousLayout = settings.inputLayout
        settings.setInputLayout("cheonjiin_plus")
        defer { settings.setInputLayout(previousLayout) }
        let controller = KeyboardViewController()
        controller.loadViewIfNeeded()
        controller.view.frame = CGRect(x: 0, y: 0, width: 393, height: 318)
        controller.view.layoutIfNeeded()
        func visibleButtons(_ view: UIView) -> [UIButton] {
            guard !view.isHidden else { return [] }
            return (view as? UIButton).map { [$0] } ?? view.subviews.flatMap(visibleButtons)
        }
        let buttons = visibleButtons(controller.view)
        for label in ["ㅣ", "·", "ㅡ", "ㄱ", "ㅋ", "ㄴ", "ㄹ", "ㄷ", "ㅌ", "ㅂ", "ㅍ", "ㅅ", "ㅎ", "ㅈ", "ㅊ", "ㅇ", "ㅁ", "!", "?", "?123", "⌫", "↵", "␣", "🌐", "한/日", "←", "→"] {
            XCTAssertTrue(buttons.contains { $0.currentTitle == label }, "Missing plus key \(label)")
        }
        XCTAssertFalse(buttons.contains { $0.currentTitle == "ㄱㅋ" })
        guard let bang = buttons.first(where: { $0.currentTitle == "!" }),
              let bieup = buttons.first(where: { $0.currentTitle == "ㅂ" }) else {
            return XCTFail("Missing ! / ㅂ")
        }
        let bangFrame = bang.convert(bang.bounds, to: controller.view)
        let bieupFrame = bieup.convert(bieup.bounds, to: controller.view)
        XCTAssertEqual(bangFrame.midY, bieupFrame.midY, accuracy: 1)
        XCTAssertLessThanOrEqual(bangFrame.maxX, bieupFrame.minX)
        XCTAssertEqual(bang.bounds.width, bieup.bounds.width, accuracy: 1)


        guard let plusBottomRow = buttons.first(where: { $0.currentTitle == "ㅇ" })?.superview else {
            return XCTFail("Missing Cheonjiin Plus bottom row")
        }
        let bottomRowButtons = plusBottomRow.subviews.compactMap { $0 as? UIButton }
        let requestedBottom = ["?", "?123", "한/日", "ㅇ", "ㅁ", "␣", "."]
            .compactMap { title in bottomRowButtons.first { $0.currentTitle == title } }
        XCTAssertEqual(requestedBottom.count, 7)
        let bottomFrames = requestedBottom.map { $0.convert($0.bounds, to: controller.view) }
        XCTAssertTrue(zip(bottomFrames, bottomFrames.dropFirst()).allSatisfy { $0.maxX <= $1.minX })
        XCTAssertTrue(bottomFrames.allSatisfy { abs($0.minY - bottomFrames[0].minY) < 1 })
        for (key, tense) in [("ㅋ", "ㄲ"), ("ㅌ", "ㄸ"), ("ㅍ", "ㅃ"), ("ㅎ", "ㅆ"), ("ㅊ", "ㅉ")] {
            let button = buttons.first { $0.currentTitle == key }
            XCTAssertEqual(button?.accessibilityCustomActions?.first?.name, tense)
            XCTAssertTrue(button?.actions(forTarget: controller, forControlEvent: .touchDown)?.isEmpty ?? true)
            XCTAssertFalse(button?.actions(forTarget: controller, forControlEvent: LongPressKeyButton.tapEvent)?.isEmpty ?? true)
        }
        let image = UIGraphicsImageRenderer(size: controller.view.bounds.size).image { context in
            controller.view.layer.render(in: context.cgContext)
        }
        let attachment = XCTAttachment(image: image)
        attachment.name = "iOS Cheonjiin Plus keyboard"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testMainKeyboardRendersCheonjiinRows() {
        let controller = KeyboardViewController()
        controller.loadViewIfNeeded()
        controller.view.frame = CGRect(x: 0, y: 0, width: 393, height: 318)
        controller.view.layoutIfNeeded()

        func key(_ title: String) -> UIButton? {
            func search(_ view: UIView) -> UIButton? {
                guard !view.isHidden else { return nil }
                if let button = view as? UIButton,
                   button.currentTitle == title {
                    return button
                }
                return view.subviews.lazy.compactMap(search).first
            }
            return search(controller.view)
        }

        let firstRow = ["←", "ㅣ", "·", "ㅡ", "⌫"].compactMap(key)
        let secondRow = ["→", "ㄱㅋ", "ㄴㄹ", "ㄷㅌ", "↵"].compactMap(key)
        let thirdRow = ["!", "ㅂㅍ", "ㅅㅎ", "ㅈㅊ", ","].compactMap(key)
        let fourthRow = ["?", "?123", "한/日", "ㅇㅁ", "␣", "."].compactMap(key)
        XCTAssertEqual(firstRow.count, 5)
        XCTAssertEqual(secondRow.count, 5)
        XCTAssertEqual(thirdRow.count, 5)
        XCTAssertEqual(fourthRow.count, 6)
        XCTAssertNotNil(key("1"), "The number row should be visible by default")

        for row in [firstRow, secondRow, thirdRow, fourthRow] {
            let frames = row.map { $0.convert($0.bounds, to: controller.view) }
            XCTAssertTrue(zip(frames, frames.dropFirst()).allSatisfy { $0.maxX <= $1.minX })
        }
        if let cursor = key("←"), let vowel = key("ㅣ") {
            XCTAssertGreaterThan(vowel.bounds.width, cursor.bounds.width * 1.7)
        }
        if let enter = key("↵") {
            XCTAssertGreaterThan(enter.backgroundColor?.cgColor.components?.count ?? 0, 0)
        }

        for title in ["ㅣ", "ㄱㅋ", "␣", "1", "!"] {
            guard let button = key(title) else {
                XCTFail("Missing input key \(title)")
                continue
            }
            XCTAssertFalse(button.isExclusiveTouch, "\(title) must allow another simultaneous key press")
            XCTAssertFalse(button.actions(forTarget: controller, forControlEvent: ["ㅣ", "ㄱㅋ"].contains(title) ? LongPressKeyButton.tapEvent : .touchDown)?.isEmpty ?? true,
                           "\(title) must expose one activation action")
            XCTAssertTrue(button.actions(forTarget: controller, forControlEvent: .touchUpInside)?.isEmpty ?? true,
                          "\(title) must not insert again on touch up")
        }
        if let enter = key("↵") {
            XCTAssertFalse(enter.actions(forTarget: controller, forControlEvent: .touchUpInside)?.isEmpty ?? true)
        }

        let image = UIGraphicsImageRenderer(size: controller.view.bounds.size).image { context in
            controller.view.layer.render(in: context.cgContext)
        }
        let attachment = XCTAttachment(image: image)
        attachment.name = "iOS Cheonjiin keyboard"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
