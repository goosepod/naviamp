import NaviampShared
import SwiftUI
import UIKit

struct NaviampRootView: UIViewControllerRepresentable {
    @Environment(\.scenePhase) private var scenePhase

    final class Coordinator {
        let application = NaviampIosApplication(
            applicationSupportDirectory: IosApplicationDirectories.supportDirectory(),
            credentialProtector: IosKeychainCredentialProtector()
        )
        private var foreground: Bool?

        func forwardSceneForeground(_ value: Bool) {
            guard foreground != value else { return }
            foreground = value
            let task = value ? nil : BackgroundTaskLease()
            application.hostForegroundChanged(foreground: value, completion: { task?.finish() })
        }

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

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Permission sheets make a scene inactive without putting its relay in the background.
        context.coordinator.forwardSceneForeground(scenePhase != .background)
    }
}

/** UIKit execution lease only; Core decides what backgrounding does to playback and the relay. */
private final class BackgroundTaskLease {
    private var identifier: UIBackgroundTaskIdentifier = .invalid

    init() {
        identifier = UIApplication.shared.beginBackgroundTask(withName: "Naviamp relay cleanup") { [weak self] in
            self?.finish()
        }
    }

    func finish() {
        guard identifier != .invalid else { return }
        let completed = identifier
        identifier = .invalid
        UIApplication.shared.endBackgroundTask(completed)
    }
}
