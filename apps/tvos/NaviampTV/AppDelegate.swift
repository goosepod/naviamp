import UIKit
import NaviampTvShared

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: session.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
}

/// Scene/window lifetime only. Startup, retry, failure and reset are delegated to shared Core.
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    private var application: NaviampTvosApplication?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {
        guard let scene = scene as? UIWindowScene else { return }
        let cache = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Naviamp", isDirectory: true)
        let application = NaviampTvosApplication(cacheDirectory: cache.path,
            document: TvosRecoveryDocument(), secretBox: TvosStorageSecretBox(), lock: TvosStorageLock())
        self.application = application
        let window = UIWindow(windowScene: scene)
        window.rootViewController = application.viewController()
        self.window = window
        window.makeKeyAndVisible()
    }

    func sceneDidDisconnect(_ scene: UIScene) {
        application?.close()
        application = nil
        window = nil
    }
}
