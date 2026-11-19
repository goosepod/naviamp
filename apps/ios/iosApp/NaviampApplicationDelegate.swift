import NaviampShared
import UIKit

/// UIApplication is the process boundary; shared code owns runtime reuse and disposal policy.
@MainActor
final class NaviampApplicationDelegate: NSObject, UIApplicationDelegate {
    static let runtime = NaviampIosApplicationOwner(
        applicationSupportDirectory: IosApplicationDirectories.supportDirectory(),
        credentialProtector: IosKeychainCredentialProtector()
    )

    func applicationWillTerminate(_ application: UIApplication) {
        Self.runtime.close()
    }
}
