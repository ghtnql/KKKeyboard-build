import UIKit
import SharedUI

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    private let sharedAppPlatform = IOSAppPlatform()
    private let navigation = SharedAppNavigation()
    private var keyboardScreenRouter: AppKeyboardScreenRouter?

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
        keyboardScreenRouter = AppKeyboardScreenRouter(
            platform: sharedAppPlatform,
            presenter: { [weak window] in window?.rootViewController },
            isActive: { UIApplication.shared.applicationState == .active }
        )
        return true
    }
}
