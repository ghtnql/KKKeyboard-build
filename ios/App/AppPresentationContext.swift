import UIKit

/// Common UIKit-only presenter resolution for app ads and consent.
///
/// Scene-first: uses foreground-active scene windows, falling back to the
/// legacy `UIApplication.delegate.window` only when it is a true legacy
/// window (`windowScene == nil`). Gated on `applicationState == .active`,
/// ignores hidden/non-visible windows, and requires the root to be
/// attached (when loaded) and not transitioning.
enum AppPresentationContext {
    // MARK: - Pure helper (deterministic, testable)

    /// Returns the strict root (no presented VC) from explicit inputs.
    static func resolveRoot(
        sceneWindows: [UIWindow],
        legacyWindow: UIWindow?,
        isActive: Bool
    ) -> UIViewController? {
        guard isActive else { return nil }
        if let root = firstStrictRoot(in: sceneWindows) {
            return root
        }
        guard let legacy = legacyWindow, legacy.windowScene == nil else { return nil }
        return firstStrictRoot(in: [legacy])
    }

    /// Returns the topmost stable presenter from explicit inputs.
    static func resolvePresenter(
        sceneWindows: [UIWindow],
        legacyWindow: UIWindow?,
        isActive: Bool
    ) -> UIViewController? {
        guard isActive else { return nil }
        if let presenter = firstTopmostPresenter(in: sceneWindows) {
            return presenter
        }
        guard let legacy = legacyWindow, legacy.windowScene == nil else { return nil }
        return firstTopmostPresenter(in: [legacy])
    }

    // MARK: - Live helpers

    /// Strict root: nil when the root is presenting anything.
    static func rootViewController() -> UIViewController? {
        guard UIApplication.shared.applicationState == .active else { return nil }
        let sceneWindows = activeSceneWindows()
        if let root = firstStrictRoot(in: sceneWindows) {
            return root
        }
        guard let legacy = flattenedDelegateWindow(), legacy.windowScene == nil else { return nil }
        return firstStrictRoot(in: [legacy])
    }

    /// Topmost stable presenter for rewarded ads.
    static func presenter() -> UIViewController? {
        guard UIApplication.shared.applicationState == .active else { return nil }
        let sceneWindows = activeSceneWindows()
        if let presenter = firstTopmostPresenter(in: sceneWindows) {
            return presenter
        }
        guard let legacy = flattenedDelegateWindow(), legacy.windowScene == nil else { return nil }
        return firstTopmostPresenter(in: [legacy])
    }

    // MARK: - Private

    private static func activeSceneWindows() -> [UIWindow] {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let active = scenes.filter { $0.activationState == .foregroundActive }
        return active.flatMap(\.windows)
    }

    /// Flattens the double-optional `delegate?.window` (`UIWindow??`)
    /// without touching the deprecated `UIApplication.windows`.
    private static func flattenedDelegateWindow() -> UIWindow? {
        UIApplication.shared.delegate?.window ?? nil
    }

    private static func orderedEligibleWindows(_ windows: [UIWindow]) -> [UIWindow] {
        let eligible = windows.filter { !$0.isHidden && $0.alpha > 0.01 && $0.rootViewController != nil }
        guard !eligible.isEmpty else { return [] }
        let key = eligible.filter(\.isKeyWindow)
        let rest = eligible.filter { !$0.isKeyWindow }
        return key + rest
    }

    private static func isRootUsable(_ root: UIViewController) -> Bool {
        guard !root.isBeingPresented, !root.isBeingDismissed else { return false }
        guard root.viewIfLoaded?.window != nil else { return false }
        return true
    }

    private static func firstStrictRoot(in windows: [UIWindow]) -> UIViewController? {
        for window in orderedEligibleWindows(windows) {
            guard let root = window.rootViewController else { continue }
            guard isRootUsable(root) else { continue }
            guard root.presentedViewController == nil else { continue }
            return root
        }
        return nil
    }

    private static func firstTopmostPresenter(in windows: [UIWindow]) -> UIViewController? {
        for window in orderedEligibleWindows(windows) {
            guard let root = window.rootViewController else { continue }
            guard isRootUsable(root) else { continue }
            var presenter = root
            while let next = presenter.presentedViewController {
                guard !next.isBeingDismissed, !next.isBeingPresented else { return nil }
                presenter = next
            }
            return presenter
        }
        return nil
    }
}
