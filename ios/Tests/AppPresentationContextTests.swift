import UIKit
import XCTest


/// UIKit-only tests for the pure presenter-resolution helpers.
/// No SharedUI, no UMP SDK, no live UIApplication state.
final class AppPresentationContextTests: XCTestCase {

    private func makeWindow(root: UIViewController?, hidden: Bool = false) -> UIWindow {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 320, height: 480))
        window.rootViewController = root
        window.isHidden = hidden
        window.layoutIfNeeded()
        addTeardownBlock { window.isHidden = true }
        return window
    }

    private final class StubViewController: UIViewController {
        var stubPresented: UIViewController?
        override var presentedViewController: UIViewController? { stubPresented }
    }

    private final class LegacyWindow: UIWindow {
        // Visible UIWindow(frame:) can automatically join a foreground scene.
        // Model the true legacy input required by the helper without changing its guard.
        override var windowScene: UIWindowScene? {
            get { nil }
            set { super.windowScene = newValue }
        }
    }

    func testLegacyVisibleWindowResolvesStrictRoot() {
        let root = UIViewController()
        let legacy = LegacyWindow(frame: CGRect(x: 0, y: 0, width: 320, height: 480))
        legacy.rootViewController = root
        root.loadViewIfNeeded()
        root.view.frame = legacy.bounds
        legacy.addSubview(root.view)
        legacy.isHidden = false
        legacy.layoutIfNeeded()
        addTeardownBlock { legacy.isHidden = true }
        XCTAssertNil(legacy.windowScene)
        XCTAssertTrue(root.view.window === legacy)
        XCTAssertNotNil(legacy.rootViewController)
        let resolved = AppPresentationContext.resolveRoot(
            sceneWindows: [], legacyWindow: legacy, isActive: true
        )
        XCTAssertTrue(resolved === root)
    }

    func testSceneWindowPreferredOverLegacy() {
        let sceneRoot = UIViewController()
        let legacyRoot = UIViewController()
        let sceneWindow = makeWindow(root: sceneRoot)
        let legacy = makeWindow(root: legacyRoot)
        let resolved = AppPresentationContext.resolveRoot(
            sceneWindows: [sceneWindow], legacyWindow: legacy, isActive: true
        )
        XCTAssertTrue(resolved === sceneRoot)
    }

    func testInactiveStateRejectsAllWindows() {
        let root = UIViewController()
        let window = makeWindow(root: root)
        XCTAssertNil(
            AppPresentationContext.resolveRoot(
                sceneWindows: [window], legacyWindow: window, isActive: false
            )
        )
        XCTAssertNil(
            AppPresentationContext.resolvePresenter(
                sceneWindows: [window], legacyWindow: window, isActive: false
            )
        )
    }

    func testHiddenWindowsRejected() {
        let root = UIViewController()
        let hidden = makeWindow(root: root, hidden: true)
        XCTAssertNil(
            AppPresentationContext.resolveRoot(
                sceneWindows: [hidden], legacyWindow: nil, isActive: true
            )
        )
        XCTAssertNil(
            AppPresentationContext.resolvePresenter(
                sceneWindows: [hidden], legacyWindow: nil, isActive: true
            )
        )
    }

    func testStrictRootRejectsPresentedWhilePresenterReturnsTopmost() {
        let root = StubViewController()
        let topmost = UIViewController()
        root.stubPresented = topmost
        let window = makeWindow(root: root)
        XCTAssertNil(
            AppPresentationContext.resolveRoot(
                sceneWindows: [window], legacyWindow: nil, isActive: true
            ),
            "strict root must be nil while the root is presenting"
        )
        let presenter = AppPresentationContext.resolvePresenter(
            sceneWindows: [window], legacyWindow: nil, isActive: true
        )
        XCTAssertTrue(presenter === topmost)
    }
}
