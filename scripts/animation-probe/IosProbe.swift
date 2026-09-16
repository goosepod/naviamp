import UIKit
import NaviampAnimationProbe

@main
final class ProbeApp: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = IosAnimationProbeKt.animationProbeViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
