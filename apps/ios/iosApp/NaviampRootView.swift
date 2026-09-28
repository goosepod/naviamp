import NaviampShared
import SwiftUI

struct NaviampRootView: UIViewControllerRepresentable {
    let application: NaviampIosApplication

    final class Coordinator {
        let application: NaviampIosApplication

        init(application: NaviampIosApplication) { self.application = application }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(application: application)
    }

    func makeUIViewController(context: Context) -> UIViewController {
        context.coordinator.application.viewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
