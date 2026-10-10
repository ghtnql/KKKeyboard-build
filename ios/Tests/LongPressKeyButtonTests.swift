import UIKit
import XCTest

final class LongPressKeyButtonTests: XCTestCase {
    private final class Receiver: NSObject {
        var taps = 0
        @objc func tap() { taps += 1 }
    }
    func testAccessibleActivationCommitsOneTapAndAdvertisesEveryChoice() {
        let button = LongPressKeyButton(type: .system)
        let receiver = Receiver()
        button.choicesProvider = { ["ㄲ", "!", "?"] }
        button.addTarget(receiver, action: #selector(Receiver.tap), for: LongPressKeyButton.tapEvent)
        button.refreshHint()
        XCTAssertEqual(button.accessibilityCustomActions?.map { $0.name }, ["ㄲ", "!", "?"])
        XCTAssertTrue(button.accessibilityActivate())
        XCTAssertEqual(receiver.taps, 1)
        // Automatic UIKit events cannot invoke a deferred key's action.
        button.sendActions(for: .touchDown)
        button.sendActions(for: .touchUpInside)
        button.sendActions(for: .primaryActionTriggered)
        XCTAssertEqual(receiver.taps, 1)
        button.isEnabled = false
        XCTAssertFalse(button.accessibilityActivate())
        XCTAssertEqual(receiver.taps, 1)
    }
    func testManualCustomTapDispatchesExactlyOnce() {
        let button = LongPressKeyButton(type: .system)
        let receiver = Receiver()
        button.choicesProvider = { ["a"] }
        button.addTarget(receiver, action: #selector(Receiver.tap), for: LongPressKeyButton.tapEvent)
        button.sendActions(for: LongPressKeyButton.tapEvent)
        XCTAssertEqual(receiver.taps, 1)
    }
    func testExplicitTapIsolatedFromStandardEventsRespectsDisabledAndNilProviderTouchDown() {
        let button = LongPressKeyButton(type: .system)
        let receiver = Receiver()
        button.choicesProvider = { ["a"] }
        button.addTarget(receiver, action: #selector(Receiver.tap), for: LongPressKeyButton.tapEvent)
        button.sendActions(for: LongPressKeyButton.tapEvent)
        XCTAssertEqual(receiver.taps, 1)
        button.sendActions(for: .touchDown)
        button.sendActions(for: .touchUpInside)
        button.sendActions(for: .primaryActionTriggered)
        XCTAssertEqual(receiver.taps, 1)
        button.isEnabled = false
        button.sendActions(for: LongPressKeyButton.tapEvent)
        XCTAssertEqual(receiver.taps, 1)
        let plain = LongPressKeyButton(type: .system)
        let plainReceiver = Receiver()
        plain.addTarget(plainReceiver, action: #selector(Receiver.tap), for: .touchDown)
        XCTAssertTrue(plain.accessibilityActivate())
        XCTAssertEqual(plainReceiver.taps, 1)
    }
}
