import UIKit
import XCTest

final class KeyboardToolbarRoutingTests: XCTestCase {
    private let routes: [(title: String, action: String)] = [
        ("▤", "handlePhraseBrowser"),
        ("⌨", "handleSettingsToggle"),
        ("🌐", "handleNextKeyboard"),
        ("⚙", "handleSettingsToggle"),
        ("◈", "handleThemes"),
        ("⌄", "handleDismiss")
    ]

    private func controller(width: CGFloat = 393) -> KeyboardViewController {
        let keyboard = KeyboardViewController()
        keyboard.loadViewIfNeeded()
        // Respect the active layout's height instead of assuming a particular preference.
        let size = keyboard.view.systemLayoutSizeFitting(
            CGSize(width: width, height: 0),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel
        )
        XCTAssertEqual(size.width, width, accuracy: 0.5,
                       "Keyboard fitting must respect the requested test width")
        keyboard.view.frame = CGRect(x: 0, y: 0, width: width, height: size.height)
        keyboard.view.layoutIfNeeded()
        return keyboard
    }

    private func visibleButtons(in view: UIView) -> [UIButton] {
        // Prune hidden ancestors, not just hidden buttons in inactive keyboard layouts.
        guard !view.isHidden, view.alpha > 0.01 else { return [] }
        if let button = view as? UIButton { return [button] }
        return view.subviews.flatMap { visibleButtons(in: $0) }
    }

    private func button(_ title: String, in keyboard: KeyboardViewController) throws -> UIButton {
        let matches = visibleButtons(in: keyboard.view).filter { $0.currentTitle == title }
        XCTAssertEqual(matches.count, 1, "Expected one visible toolbar button for \(title)")
        return try XCTUnwrap(matches.first)
    }

    private func phraseBrowser(in keyboard: KeyboardViewController) throws -> UIView {
        try XCTUnwrap(Mirror(reflecting: keyboard).children
            .first { $0.label == "phraseBrowser" }?.value as? UIView)
    }

    private func settingsController(in keyboard: KeyboardViewController) -> UIViewController? {
        guard let value = Mirror(reflecting: keyboard).children
            .first(where: { $0.label == "settingsController" })?.value else { return nil }
        // The stored property is optional; explicitly inspect its wrapped value.
        return Mirror(reflecting: value).children.first?.value as? UIViewController
    }

    func testToolbarButtonsHaveOnlyTheirIntendedControllerRouteAndHapticAction() throws {
        let keyboard = controller()
        defer { keyboard.viewWillDisappear(false) }
        for route in routes {
            let control = try button(route.title, in: keyboard)
            let expectedActions = Set([route.action, "playKeyHaptic"])
            let tapActions = control.actions(forTarget: keyboard, forControlEvent: .touchUpInside) ?? []
            XCTAssertEqual(Set(tapActions), expectedActions, "Incorrect tap route for \(route.title)")
            XCTAssertEqual(control.allControlEvents, .touchUpInside,
                           "Toolbar \(route.title) must not route another control event")
            XCTAssertEqual(control.allTargets.count, 1)
            XCTAssertTrue(control.allTargets.contains(AnyHashable(keyboard)))
            XCTAssertTrue(control.isExclusiveTouch, "Toolbar actions must prevent overlapping touches")
            XCTAssertTrue(control.isEnabled)
            XCTAssertTrue(control.isUserInteractionEnabled)
            if route.title != "🌐" {
                for target in control.allTargets {
                    let actions = control.actions(forTarget: target.base, forControlEvent: .touchUpInside) ?? []
                    XCTAssertFalse(actions.contains("handleNextKeyboard"),
                                   "Only the globe may switch keyboards: \(route.title)")
                }
            }
        }
    }

    func testToolbarButtonCentersHitTheirOwnControlsAtPhoneAndTabletWidths() throws {
        for width in [393, 800] as [CGFloat] {
            let keyboard = controller(width: width)
            defer { keyboard.viewWillDisappear(false) }
            for route in routes {
                let control = try button(route.title, in: keyboard)
                XCTAssertGreaterThan(control.bounds.width, 0)
                XCTAssertGreaterThan(control.bounds.height, 0)
                let center = control.convert(CGPoint(x: control.bounds.midX, y: control.bounds.midY),
                                             to: keyboard.view)
                XCTAssertTrue(keyboard.view.bounds.contains(center))
                let hit = keyboard.view.hitTest(center, with: nil)
                XCTAssertTrue(hit === control || hit?.isDescendant(of: control) == true,
                              "Toolbar \(route.title) center must hit its own control at width \(width)")
            }
        }
    }

    func testPhraseButtonTouchOpensBrowserAndItsCloseButtonReturnsToKeyboard() throws {
        let keyboard = controller()
        defer { keyboard.viewWillDisappear(false) }
        let browser = try phraseBrowser(in: keyboard)
        XCTAssertTrue(browser.isHidden)
        try button("▤", in: keyboard).sendActions(for: .touchUpInside)
        XCTAssertFalse(browser.isHidden)
        XCTAssertNil(settingsController(in: keyboard))
        XCTAssertTrue(keyboard.children.isEmpty)

        // Locate close by its route so this test works in every UI language.
        let close = try XCTUnwrap(visibleButtons(in: browser).first {
            $0.actions(forTarget: keyboard, forControlEvent: .touchUpInside)?
                .contains("handlePhraseBrowserClose") == true
        })
        close.sendActions(for: .touchUpInside)
        XCTAssertTrue(browser.isHidden)
        XCTAssertTrue(try button("▤", in: keyboard).isEnabled)
    }

    func testLayoutSettingsAndThemeButtonsTouchOpenEmbeddedSettingsAndCanClose() throws {
        let keyboard = controller()
        defer { keyboard.closeSharedSettings(); keyboard.viewWillDisappear(false) }
        for title in ["⌨", "⚙", "◈"] {
            XCTAssertNil(settingsController(in: keyboard))
            try button(title, in: keyboard).sendActions(for: .touchUpInside)
            let settings = try XCTUnwrap(settingsController(in: keyboard),
                                         "Toolbar \(title) must open shared settings")
            XCTAssertTrue(settings.parent === keyboard)
            XCTAssertEqual(keyboard.children.count, 1)
            XCTAssertTrue(settings.view.superview === keyboard.view)
            XCTAssertFalse(settings.view.isHidden)
            XCTAssertTrue(try phraseBrowser(in: keyboard).isHidden)
            let nativeStack = try XCTUnwrap(Mirror(reflecting: keyboard).children
                .first { $0.label == "keyboardStack" }?.value as? UIStackView)
            XCTAssertTrue(nativeStack.isHidden,
                          "Native toolbar must be hidden while shared settings are open")

            keyboard.closeSharedSettings()
            keyboard.view.layoutIfNeeded()
            XCTAssertNil(settingsController(in: keyboard))
            XCTAssertNil(settings.parent)
            XCTAssertNil(settings.view.superview)
            XCTAssertTrue(keyboard.children.isEmpty)
            XCTAssertTrue(try button(title, in: keyboard).isEnabled)
        }
    }
}
