import NaviampShared
import SwiftUI

struct NaviampRootView: UIViewControllerRepresentable {
    final class Coordinator {
        let application = NaviampIosApplication(
            applicationSupportDirectory: IosApplicationDirectories.supportDirectory(),
            credentialProtector: IosKeychainCredentialProtector()
        )

        deinit {
            application.close()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIViewController(context: Context) -> UIViewController {
        context.coordinator.application.viewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
