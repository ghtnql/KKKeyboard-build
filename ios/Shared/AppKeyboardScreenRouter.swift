import UIKit
import SharedUI

/// Full-screen app navigation requested by the keyboard. The app owns presentation;
/// the extension never changes its input-view bounds to impersonate an app screen.
final class AppKeyboardScreenRouter {
    private let platform: AppPlatform
    private let presenter: () -> UIViewController?
    private let isActive: () -> Bool
    private weak var settingsController: UIViewController?
    private weak var previousEditor: UIResponder?
    private(set) var screenPresented = false
    private(set) var themesRequested = false

    init(platform: AppPlatform, presenter: @escaping () -> UIViewController?, isActive: @escaping () -> Bool) {
        self.platform = platform
        self.presenter = presenter
        self.isActive = isActive
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        for name in ["com.ghtnql.kkkeyboard.openSettings", "com.ghtnql.kkkeyboard.openThemes"] {
            CFNotificationCenterAddObserver(center, Unmanaged.passUnretained(self).toOpaque(), { _, observer, name, _, _ in
                guard let observer, let name else { return }
                let router = Unmanaged<AppKeyboardScreenRouter>.fromOpaque(observer).takeUnretainedValue()
                let themes = (name.rawValue as String).hasSuffix("openThemes")
                DispatchQueue.main.async { [weak router] in router?.open(themes: themes) }
            }, name as CFString, nil, .deliverImmediately)
        }
    }

    deinit {
        CFNotificationCenterRemoveEveryObserver(CFNotificationCenterGetDarwinNotifyCenter(), Unmanaged.passUnretained(self).toOpaque())
    }

    func open(themes: Bool) {
        guard isActive(), let root = presenter() else { return }
        guard settingsController == nil else { acknowledge(); return }
        var top = root
        while let presented = top.presentedViewController { top = presented }
        previousEditor = Self.firstResponder(in: (root.view.window as UIView?) ?? root.view)
        root.view.window?.endEditing(true)
        let controller = MainViewControllerKt.AppSettingsViewController(platform: platform, themes: themes, onClose: { [weak self] in
            self?.close()
            return ()
        })
        controller.modalPresentationStyle = .fullScreen
        controller.view.accessibilityIdentifier = themes ? "app.keyboard.themes.fullscreen" : "app.keyboard.settings.fullscreen"
        settingsController = controller
        themesRequested = themes
        screenPresented = true
        top.present(controller, animated: false) { [weak self] in self?.acknowledge() }
    }

    func close() {
        guard let controller = settingsController else { return }
        let editor = previousEditor
        controller.dismiss(animated: false) { [weak self, weak editor] in
            self?.settingsController = nil
            self?.screenPresented = false
            self?.previousEditor = nil
            editor?.becomeFirstResponder()
        }
    }

    private func acknowledge() {
        CFNotificationCenterPostNotification(CFNotificationCenterGetDarwinNotifyCenter(), CFNotificationName("com.ghtnql.kkkeyboard.appScreenOpened" as CFString), nil, nil, true)
    }

    private static func firstResponder(in view: UIView) -> UIResponder? {
        if view.isFirstResponder { return view }
        for child in view.subviews {
            if let responder = firstResponder(in: child) { return responder }
        }
        return nil
    }
}
