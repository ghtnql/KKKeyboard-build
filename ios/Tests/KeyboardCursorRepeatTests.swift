import UIKit
import XCTest

final class KeyboardCursorRepeatTests: XCTestCase {
    private final class Probe: UILongPressGestureRecognizer {
        var probeState: UIGestureRecognizer.State = .possible
        var probeLocation = CGPoint(x: 10, y: 10)
        override var state: UIGestureRecognizer.State {
            get { probeState }
            set { probeState = newValue }
        }
        override func location(in view: UIView?) -> CGPoint { probeLocation }
    }

    private func visibleButtons(_ view: UIView) -> [UIButton] {
        guard !view.isHidden else { return [] }
        return (view as? UIButton).map { [$0] } ?? view.subviews.flatMap(visibleButtons)
    }

    private func timer(_ keyboard: KeyboardViewController) -> Timer? {
        Mirror(reflecting: keyboard).children.first { $0.label == "cursorRepeatTimer" }?.value as? Timer
    }

    func testCursorGestureUsesMatchingDelayAndCancelsOnEndOrViewDismissal() {
        let settings = KeyboardInputModeSettings()
        let previous = settings.language()
        settings.selectLanguage(.korean)
        defer { settings.selectLanguage(previous) }
        let keyboard = KeyboardViewController()
        keyboard.loadViewIfNeeded()
        keyboard.view.frame = CGRect(x: 0, y: 0, width: 393, height: 360)
        keyboard.view.layoutIfNeeded()
        guard let left = visibleButtons(keyboard.view).first(where: { $0.currentTitle == "←" }) else {
            return XCTFail("Missing left cursor key")
        }
        guard let configured = left.gestureRecognizers?.compactMap({ $0 as? UILongPressGestureRecognizer }).first else {
            return XCTFail("Cursor long hold is not connected")
        }
        XCTAssertEqual(configured.minimumPressDuration, 0.35, accuracy: 0.001)
        XCTAssertTrue(configured.cancelsTouchesInView)
        XCTAssertEqual(left.tag, -1)
        let probe = Probe()
        left.addGestureRecognizer(probe)
        probe.probeState = .began
        keyboard.perform(NSSelectorFromString("handleCursorLongPress:"), with: probe)
        guard let heldTimer = timer(keyboard) else { return XCTFail("Hold did not start a repeat timer") }
        XCTAssertEqual(heldTimer.timeInterval, 0.05, accuracy: 0.001)
        probe.probeState = .ended
        keyboard.perform(NSSelectorFromString("handleCursorLongPress:"), with: probe)
        XCTAssertFalse(heldTimer.isValid)
        XCTAssertNil(timer(keyboard))
        probe.probeState = .began
        keyboard.perform(NSSelectorFromString("handleCursorLongPress:"), with: probe)
        let beforeDismiss = timer(keyboard)
        keyboard.viewWillDisappear(false)
        XCTAssertFalse(beforeDismiss?.isValid ?? true)
        XCTAssertNil(timer(keyboard))
    }

    func testLeavingCursorKeyCancelsAndOldFingerCannotStopNewRepeat() {
        let settings = KeyboardInputModeSettings()
        let previous = settings.language()
        settings.selectLanguage(.korean)
        defer { settings.selectLanguage(previous) }
        let keyboard = KeyboardViewController()
        keyboard.loadViewIfNeeded()
        keyboard.view.frame = CGRect(x: 0, y: 0, width: 393, height: 360)
        keyboard.view.layoutIfNeeded()
        let buttons = visibleButtons(keyboard.view)
        guard let left = buttons.first(where: { $0.currentTitle == "←" }),
              let right = buttons.first(where: { $0.currentTitle == "→" }) else { return XCTFail("Missing cursors") }
        let first = Probe(); left.addGestureRecognizer(first)
        let second = Probe(); right.addGestureRecognizer(second)
        first.probeState = .began
        keyboard.perform(NSSelectorFromString("handleCursorLongPress:"), with: first)
        second.probeState = .began
        keyboard.perform(NSSelectorFromString("handleCursorLongPress:"), with: second)
        let active = timer(keyboard)
        first.probeState = .ended
        keyboard.perform(NSSelectorFromString("handleCursorLongPress:"), with: first)
        XCTAssertTrue(active?.isValid ?? false)
        second.probeState = .changed
        second.probeLocation = CGPoint(x: -100, y: -100)
        keyboard.perform(NSSelectorFromString("handleCursorLongPress:"), with: second)
        XCTAssertFalse(active?.isValid ?? true)
        XCTAssertNil(timer(keyboard))
    }
}
