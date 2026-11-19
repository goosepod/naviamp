import NaviampShared
import SwiftUI

struct NaviampRootView: UIViewControllerRepresentable {
    @MainActor
    final class Coordinator {
        let application = NaviampApplicationDelegate.runtime.application()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIViewController(context: Context) -> UIViewController {
        context.coordinator.application.viewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    static func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: Coordinator) {
        coordinator.application.releaseViewController(controller: uiViewController)
    }
}
