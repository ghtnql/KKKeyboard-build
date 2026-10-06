import UIKit
import SharedUI

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    private let sharedAppPlatform = IOSAppPlatform()
    private let navigation = SharedAppNavigation()

    func applicationDidBecomeActive(_ application: UIApplication) {
        sharedAppPlatform.refreshMonetization()
    }

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = MainViewControllerKt.MainViewControllerWithNavigation(platform: sharedAppPlatform, navigation: navigation)
        window.makeKeyAndVisible()
        self.window = window
        let center = CFNotificationCenterGetDarwinNotifyCenter()
        for name in ["com.ghtnql.kkkeyboard.openSettings", "com.ghtnql.kkkeyboard.openThemes"] {
            CFNotificationCenterAddObserver(center, Unmanaged.passUnretained(self).toOpaque(), { _, observer, name, _, _ in
                guard let observer, let name else { return }
                let app = Unmanaged<AppDelegate>.fromOpaque(observer).takeUnretainedValue()
                let themes = (name.rawValue as String).hasSuffix("openThemes")
                DispatchQueue.main.async {
                    guard UIApplication.shared.applicationState == .active else { return }
                    app.window?.endEditing(true)
                    if themes { app.navigation.openThemes() } else { app.navigation.openSettings() }
                }
            }, name as CFString, nil, .deliverImmediately)
        }
        return true
    }
}
