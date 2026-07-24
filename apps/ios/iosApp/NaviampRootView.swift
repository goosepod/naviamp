import NaviampShared
import SwiftUI

struct NaviampRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        NaviampIosViewControllerKt.NaviampIosViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
