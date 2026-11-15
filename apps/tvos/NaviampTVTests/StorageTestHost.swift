import UIKit

/// Isolated native adapter test host; the actual shared scene is launched separately for UI checks.
@main
final class StorageTestHost: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: session.role)
        configuration.delegateClass = StorageTestScene.self
        return configuration
    }
}

final class StorageTestScene: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {
        guard let scene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: scene)
        window.rootViewController = UIViewController()
        self.window = window
        window.makeKeyAndVisible()
    }
}
