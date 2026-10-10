import UIKit
import XCTest

final class KeyboardToolbarRoutingTests: XCTestCase {
    private let routes: [(title: String, action: String)] = [
        ("▤", "handlePhraseBrowser"),
        ("📋", "handleClipboard"),
        ("⌨", "handleLayoutPicker"),
        ("🌐", "handleNextKeyboard"),
        ("⚙", "handleSettingsToggle")
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
        for width in [320, 393, 800] as [CGFloat] {
            let keyboard = controller(width: width)
            defer { keyboard.viewWillDisappear(false) }
            for route in routes {
                let control = try button(route.title, in: keyboard)
                XCTAssertGreaterThan(control.bounds.width, 0)
                XCTAssertGreaterThan(control.bounds.height, 0)
                let scroll = try XCTUnwrap(identifiedView("keyboard.toolbar.scroll", in: keyboard.view) as? UIScrollView)
                scroll.scrollRectToVisible(control.convert(control.bounds, to: scroll), animated: false)
                keyboard.view.layoutIfNeeded()
                let center = control.convert(CGPoint(x: control.bounds.midX, y: control.bounds.midY),
                                             to: keyboard.view)
                XCTAssertTrue(keyboard.view.bounds.contains(center))
                let hit = keyboard.view.hitTest(center, with: nil)
                XCTAssertTrue(hit === control || hit?.isDescendant(of: control) == true,
                              "Toolbar \(route.title) center must hit its own control at width \(width)")
            }
        }
    }

    func testClipboardClosesAndDiscardsPanelOnInputEnd() throws {
        let keyboard = controller()
        let height = keyboard.view.bounds.height
        try button("📋", in: keyboard).sendActions(for: .touchUpInside)
        let panel = try XCTUnwrap(keyboard.view.subviews.compactMap { $0 as? ClipboardPanel }.first)
        XCTAssertFalse(panel.isHidden)
        keyboard.view.layoutIfNeeded()
        XCTAssertEqual(keyboard.view.bounds.height, height)
        keyboard.viewWillDisappear(false)
        XCTAssertTrue(panel.isHidden)
        try button("📋", in: keyboard).sendActions(for: .touchUpInside)
        XCTAssertFalse(panel.isHidden)
        panel.close()
        XCTAssertTrue(panel.isHidden)
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

    func testSettingsAndThemesRequestFullScreenAppRouteWithoutEmbedding() throws {
        let keyboard = controller()
        defer { keyboard.viewWillDisappear(false) }
        let originalHeight = keyboard.view.bounds.height
        var destinations: [Bool] = []
        keyboard.onOpenAppScreen = { destinations.append($0) }
        for title in ["⚙"] {
            try button(title, in: keyboard).sendActions(for: .touchUpInside)
            XCTAssertNil(settingsController(in: keyboard), "Settings must open full screen in the app")
            XCTAssertTrue(keyboard.children.isEmpty)
            keyboard.view.layoutIfNeeded()
            XCTAssertEqual(keyboard.view.bounds.height, originalHeight)
            XCTAssertTrue(try button(title, in: keyboard).isEnabled)
        }
        XCTAssertEqual(destinations, [false])
    }

    func testToolbarOrderIconsAndTargetWidthsMatchAndroid() throws {
        let keyboard = controller()
        defer { keyboard.viewWillDisappear(false) }
        let scroll = try XCTUnwrap(identifiedView("keyboard.toolbar.scroll", in: keyboard.view) as? UIScrollView)
        let row = try XCTUnwrap(scroll.subviews.compactMap { $0 as? UIStackView }.first)
        let actions = row.arrangedSubviews.compactMap { $0 as? UIButton }
            .filter { $0.accessibilityIdentifier?.hasPrefix("keyboard.toolbar.") == true }
        XCTAssertEqual(actions.compactMap(\.currentTitle), routes.map(\.title))
        for button in actions { XCTAssertEqual(button.bounds.width, 44, accuracy: 0.5) }
        XCTAssertFalse(visibleButtons(in: keyboard.view).contains { $0.currentTitle == "⌄" })
        XCTAssertTrue(keyboard.view.gestureRecognizers?.contains {
            ($0 as? UISwipeGestureRecognizer)?.direction == .down
        } == true)
        XCTAssertEqual(keyboard.view.accessibilityCustomActions?.count, 1)
    }

    private func identifiedView(_ identifier: String, in view: UIView) -> UIView? {
        if view.accessibilityIdentifier == identifier { return view }
        for child in view.subviews {
            if let match = identifiedView(identifier, in: child) { return match }
        }
        return nil
    }

    private func fit(_ keyboard: KeyboardViewController, width: CGFloat = 393) {
        let size = keyboard.view.systemLayoutSizeFitting(
            CGSize(width: width, height: 0),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel)
        keyboard.view.frame.size = size
        keyboard.view.layoutIfNeeded()
    }

    private func attach(_ view: UIView, name: String) {
        let renderer = UIGraphicsImageRenderer(bounds: view.bounds)
        let image = renderer.image { context in
            if view.window != nil {
                view.drawHierarchy(in: view.bounds, afterScreenUpdates: true)
            } else {
                view.layer.render(in: context.cgContext)
            }
        }
        let attachment = XCTAttachment(image: image)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testLayoutPickerSelectionsPersistAndReturnToUsableKeyboard() throws {
        let settings = KeyboardLayoutSettings()
        let originalLayout = settings.inputLayout
        defer { settings.setInputLayout(originalLayout) }
        let keyboard = controller()
        defer { keyboard.viewWillDisappear(false) }
        for layout in ["cheonjiin", "cheonjiin_plus", "qwerty", "hangul_flick"] {
            let originalHeight = keyboard.view.bounds.height
            try button("⌨", in: keyboard).sendActions(for: .touchUpInside)
            let chooser = try XCTUnwrap(settingsController(in: keyboard))
            XCTAssertEqual(chooser.view.accessibilityIdentifier, "keyboard.layout.surface")
            fit(keyboard)
            XCTAssertEqual(keyboard.view.bounds.height, originalHeight,
                           "Layout popup must preserve keyboard geometry")
            let select = try XCTUnwrap(identifiedView("keyboard.layout.\(layout)", in: chooser.view) as? UIButton)
            XCTAssertTrue(select.isEnabled)
            if layout == "qwerty" { attach(keyboard.view, name: "layout-picker-before-selection") }
            select.sendActions(for: .touchUpInside)
            XCTAssertEqual(settings.inputLayout, layout)
            XCTAssertNil(settingsController(in: keyboard))
            XCTAssertTrue(keyboard.children.isEmpty)
            fit(keyboard)
            XCTAssertTrue(try button("⌨", in: keyboard).isEnabled)
            XCTAssertTrue(try button("⚙", in: keyboard).isEnabled)
            let expectedKeyboard = controller()
            XCTAssertEqual(keyboard.view.bounds.height, expectedKeyboard.view.bounds.height, accuracy: 0.5,
                           "Layout chooser must restore the selected layout's actual height")
            expectedKeyboard.viewWillDisappear(false)
            if layout == "qwerty" { attach(keyboard.view, name: "qwerty-restored-after-selection") }

            // Reopening shows the actual persisted selection, not a stale snapshot.
            try button("⌨", in: keyboard).sendActions(for: .touchUpInside)
            let reopened = try XCTUnwrap(settingsController(in: keyboard))
            let selected = try XCTUnwrap(identifiedView("keyboard.layout.\(layout)", in: reopened.view) as? UIButton)
            XCTAssertTrue(selected.accessibilityTraits.contains(.selected))
            let close = try XCTUnwrap(identifiedView("keyboard.layout.close", in: reopened.view) as? UIButton)
            close.sendActions(for: .touchUpInside)
            XCTAssertNil(settingsController(in: keyboard))
        }
    }

    func testLayoutPickerScrollKeepsCloseAvailableAndCancelPreservesSelection() throws {
        let settings = KeyboardLayoutSettings()
        let originalLayout = settings.inputLayout
        let keyboard = controller(width: 320)
        defer { keyboard.viewWillDisappear(false) }
        let originalHeight = keyboard.view.bounds.height
        try button("⌨", in: keyboard).sendActions(for: .touchUpInside)
        let chooser = try XCTUnwrap(settingsController(in: keyboard))
        // Exercise a constrained landscape-sized viewport; content must remain scrollable.
        chooser.view.frame = CGRect(x: 0, y: 0, width: 320, height: 180)
        chooser.view.layoutIfNeeded()
        let scroll = try XCTUnwrap(identifiedView("keyboard.layout.scroll", in: chooser.view) as? UIScrollView)
        XCTAssertGreaterThan(scroll.contentSize.height, scroll.bounds.height)
        scroll.setContentOffset(CGPoint(x: 0, y: scroll.contentSize.height - scroll.bounds.height), animated: false)
        XCTAssertGreaterThan(scroll.contentOffset.y, 0)
        let close = try XCTUnwrap(identifiedView("keyboard.layout.close", in: chooser.view) as? UIButton)
        let center = close.convert(CGPoint(x: close.bounds.midX, y: close.bounds.midY), to: chooser.view)
        XCTAssertTrue(chooser.view.bounds.contains(center))
        XCTAssertTrue(chooser.view.hitTest(center, with: nil) === close)
        attach(chooser.view, name: "layout-picker-scrolled-fixed-close")
        close.sendActions(for: .touchUpInside)
        XCTAssertEqual(settings.inputLayout, originalLayout)
        fit(keyboard, width: 320)
        XCTAssertEqual(keyboard.view.bounds.height, originalHeight, accuracy: 0.5)
    }

    func testDefaultFullScreenRoutesPostDarwinRequestsAndAcknowledgementPreventsNotice() throws {
        let names = ["com.ghtnql.kkkeyboard.openSettings"]
        let notification = expectation(description: "Toolbar requests full-screen app settings")
        let observer = Unmanaged.passUnretained(notification).toOpaque()
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        for name in names {
            CFNotificationCenterAddObserver(center, observer, { _, observer, _, _, _ in
                guard let observer else { return }
                Unmanaged<XCTestExpectation>.fromOpaque(observer).takeUnretainedValue().fulfill()
                CFNotificationCenterPostNotification(CFNotificationCenterGetDarwinNotifyCenter(),
                    CFNotificationName("com.ghtnql.kkkeyboard.appScreenOpened" as CFString), nil, nil, true)
            }, name as CFString, nil, .deliverImmediately)
        }
        defer { CFNotificationCenterRemoveEveryObserver(center, observer) }
        let keyboard = controller()
        defer { keyboard.viewWillDisappear(false) }
        for title in ["⚙"] {
            try button(title, in: keyboard).sendActions(for: .touchUpInside)
            XCTAssertNil(settingsController(in: keyboard))
        }
        wait(for: [notification], timeout: 3)
        let settled = expectation(description: "Acknowledged request passes the notice deadline")
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) { settled.fulfill() }
        wait(for: [settled], timeout: 3)
        XCTAssertNil(settingsController(in: keyboard))
    }

    func testUnacknowledgedRequestShowsAppGuidanceWithCloseAndPreservesHeight() throws {
        let keyboard = controller()
        defer { keyboard.viewWillDisappear(false) }
        let originalHeight = keyboard.view.bounds.height
        try button("⚙", in: keyboard).sendActions(for: .touchUpInside)
        let settled = expectation(description: "External-host app guidance appears")
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) { settled.fulfill() }
        wait(for: [settled], timeout: 3)
        let notice = try XCTUnwrap(settingsController(in: keyboard))
        XCTAssertEqual(notice.view.accessibilityIdentifier, "keyboard.appNavigation.notice")
        fit(keyboard)
        XCTAssertEqual(keyboard.view.bounds.height, originalHeight)
        let close = try XCTUnwrap(identifiedView("keyboard.appNavigation.close", in: notice.view) as? UIButton)
        close.sendActions(for: .touchUpInside)
        XCTAssertNil(settingsController(in: keyboard))
        XCTAssertTrue(try button("⚙", in: keyboard).isEnabled)
    }

    func testMountedLayoutPopupPreservesHostEditorAndKeyboardGeometry() throws {
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        let previousWindow = (scene?.windows ?? UIApplication.shared.windows).first { $0.isKeyWindow }
        let window: UIWindow
        if let scene {
            window = UIWindow(windowScene: scene)
        } else {
            // The unit-test host intentionally uses the pre-scene app lifecycle.
            window = UIWindow(frame: UIScreen.main.bounds)
        }
        let host = UIViewController()
        host.view.backgroundColor = .systemBackground
        window.rootViewController = host
        let editor = UITextField(frame: CGRect(x: 16, y: 60, width: 280, height: 44))
        editor.borderStyle = .roundedRect
        editor.text = "Host editor fixture"
        editor.inputView = UIView(frame: .zero)
        host.view.addSubview(editor)
        let keyboard = controller(width: 320)
        host.addChild(keyboard)
        keyboard.view.translatesAutoresizingMaskIntoConstraints = false
        host.view.addSubview(keyboard.view)
        NSLayoutConstraint.activate([
            keyboard.view.leadingAnchor.constraint(equalTo: host.view.leadingAnchor),
            keyboard.view.trailingAnchor.constraint(equalTo: host.view.trailingAnchor),
            keyboard.view.bottomAnchor.constraint(equalTo: host.view.safeAreaLayoutGuide.bottomAnchor)
        ])
        keyboard.didMove(toParent: host)
        window.makeKeyAndVisible()
        XCTAssertTrue(editor.becomeFirstResponder())
        defer {
            editor.resignFirstResponder()
            keyboard.viewWillDisappear(false)
            window.isHidden = true
            previousWindow?.makeKeyAndVisible()
        }
        host.view.layoutIfNeeded()
        let originalHeight = keyboard.view.bounds.height
        try button("⌨", in: keyboard).sendActions(for: .touchUpInside)
        host.view.layoutIfNeeded()
        let popup = try XCTUnwrap(settingsController(in: keyboard))
        XCTAssertEqual(popup.view.accessibilityIdentifier, "keyboard.layout.surface")
        XCTAssertTrue(editor.isFirstResponder)
        XCTAssertEqual(editor.text, "Host editor fixture")
        XCTAssertTrue(popup.view.window === window)
        XCTAssertEqual(keyboard.view.bounds.height, originalHeight, accuracy: 0.5)
        attach(keyboard.view, name: "native-layout-popup-mounted")
        let close = try XCTUnwrap(identifiedView("keyboard.layout.close", in: popup.view) as? UIButton)
        close.sendActions(for: .touchUpInside)
        host.view.layoutIfNeeded()
        XCTAssertTrue(editor.isFirstResponder)
        XCTAssertEqual(keyboard.view.bounds.height, originalHeight, accuracy: 0.5)
        XCTAssertTrue(try button("⌨", in: keyboard).isEnabled)
    }
}
