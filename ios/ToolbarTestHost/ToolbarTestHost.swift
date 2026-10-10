import UIKit
import SharedUI

/// A separate simulator-only app. No test code is linked into the shipping app
/// or extension. UIKit owns the input window, rather than a fixed-height mock.
@main
final class ToolbarTestAppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // This target has no App Group entitlement: reset only its own sandbox.
        if let identifier = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: identifier)
        }
        UserDefaults.standard.set("en", forKey: "app.uiLanguage")
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "app.uiLanguage.modifiedAt")
        KeyboardLayoutSettings().setInputLayout("cheonjiin")
        KeyboardLayoutSettings().setNumberRowEnabled(true, for: .portrait)
        KeyboardLayoutSettings().setNumberRowEnabled(true, for: .landscape)
        KeyboardThemeSettings().set(.system)

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = ToolbarHostController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}

private final class ToolbarHostController: UIViewController, UITextFieldDelegate {
    private let field = UITextField()
    private let keyboard = KeyboardViewController()
    private let platform = IOSKeyboardSettingsPlatform()
    private var router: AppKeyboardScreenRouter?
    private let stateLabel = UILabel()
    private weak var fullScreenStateLabel: UILabel?
    private var displayLink: CADisplayLink?
    private var routeCount = 0
    private var focusLossCount = 0
    private var initiallyFocused = false
    private weak var instrumentedController: UIViewController?
    private let routeNames = [
        "com.ghtnql.kkkeyboard.openSettings",
        "com.ghtnql.kkkeyboard.openThemes",
    ]

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        let heading = UILabel()
        heading.text = "Production keyboard toolbar regression host"
        heading.numberOfLines = 0
        heading.font = .preferredFont(forTextStyle: .headline)
        field.borderStyle = .roundedRect
        field.placeholder = "Tap to return to the keyboard"
        field.accessibilityIdentifier = "toolbar.host.field"
        field.delegate = self
        field.autocorrectionType = .no
        field.spellCheckingType = .no
        configureStateLabel(stateLabel)
        let stack = UIStackView(arrangedSubviews: [heading, field, stateLabel])
        stack.axis = .vertical
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            field.heightAnchor.constraint(equalToConstant: 44),
        ])

        addChild(keyboard)
        keyboard.loadViewIfNeeded()
        keyboard.view.frame = CGRect(x: 0, y: 0, width: view.bounds.width, height: 260)
        keyboard.view.autoresizingMask = [.flexibleWidth]
        (keyboard.view as? UIInputView)?.allowsSelfSizing = true
        field.inputView = keyboard.view
        keyboard.didMove(toParent: self)

        router = AppKeyboardScreenRouter(
            platform: platform,
            presenter: { [weak self] in self },
            isActive: { UIApplication.shared.applicationState == .active }
        )
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        for name in routeNames {
            CFNotificationCenterAddObserver(center, Unmanaged.passUnretained(self).toOpaque(), { _, observer, _, _, _ in
                guard let observer else { return }
                let host = Unmanaged<ToolbarHostController>.fromOpaque(observer).takeUnretainedValue()
                DispatchQueue.main.async {
                    guard UIApplication.shared.applicationState == .active else { return }
                    host.routeCount += 1
                    host.updateState()
                }
            }, name as CFString, nil, .deliverImmediately)
        }
        let displayLink = CADisplayLink(target: self, selector: #selector(updateState))
        displayLink.preferredFramesPerSecond = 10
        displayLink.add(to: .main, forMode: .common)
        self.displayLink = displayLink
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if !initiallyFocused {
            initiallyFocused = true
            field.becomeFirstResponder()
        }
    }

    func textFieldDidEndEditing(_ textField: UITextField) {
        focusLossCount += 1
        updateState()
    }

    private func instrumentPresentation(_ controller: UIViewController) {
        // Read-only instrumentation is added after the production router presents
        // its real Compose controller. It cannot open, close, or alter settings.
        let label = UILabel()
        configureStateLabel(label)
        label.font = .systemFont(ofSize: 8)
        label.translatesAutoresizingMaskIntoConstraints = false
        controller.view.addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: controller.view.leadingAnchor, constant: 4),
            label.trailingAnchor.constraint(lessThanOrEqualTo: controller.view.trailingAnchor, constant: -4),
            label.bottomAnchor.constraint(equalTo: controller.view.safeAreaLayoutGuide.bottomAnchor, constant: -2),
        ])
        fullScreenStateLabel = label
        instrumentedController = controller
    }

    private func configureStateLabel(_ label: UILabel) {
        label.accessibilityIdentifier = "toolbar.host.state"
        label.font = .monospacedSystemFont(ofSize: 10, weight: .regular)
        label.numberOfLines = 0
        label.isUserInteractionEnabled = false
    }

    @objc private func updateState() {
        if let controller = presentedViewController, router?.screenPresented == true,
           controller !== instrumentedController {
            instrumentPresentation(controller)
        }
        let screen = router?.screenPresented == true ? (router?.themesRequested == true ? "themes" : "settings") : "keyboard"
        let settings = KeyboardLayoutSettings()
        let visible = keyboard.viewIfLoaded?.window != nil && keyboard.view.bounds.height > 0
        let keyboardHeight = Int(keyboard.view.bounds.height.rounded())
        // Never include field contents in diagnostics.
        let value = "screen=\(screen) focus=\(field.isFirstResponder ? "active" : "inactive") keyboard=\(visible ? "visible" : "hidden") keyboardHeight=\(keyboardHeight) routes=\(routeCount) focusLosses=\(focusLossCount) layout=\(settings.inputLayout) theme=\(KeyboardThemeSettings().read().rawValue) landscapeNumberRow=\(settings.profile(for: .landscape).numberRowEnabled)"
        stateLabel.text = value
        fullScreenStateLabel?.text = value
    }

    deinit {
        displayLink?.invalidate()
        CFNotificationCenterRemoveEveryObserver(
            CFNotificationCenterGetDarwinNotifyCenter(), Unmanaged.passUnretained(self).toOpaque()
        )
    }
}
